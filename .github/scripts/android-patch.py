import hashlib
from datetime import datetime, timezone
import importlib.util
import json
import os
from pathlib import Path
import re
import shutil
import sys
import urllib.parse
import urllib.request
import zipfile

spec = importlib.util.spec_from_file_location("android_release", Path(__file__).with_name("android-release.py"))
release_tools = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release_tools)
run = release_tools.run
ABIS = ("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
NAMES = tuple(f"app-{abi}-release.apk" for abi in ABIS)
BUNDLE = Path("patch-bundle")


def api(method, path, data=None):
    url = os.environ.get("GITHUB_API_URL", "https://api.github.com") + "/repos/" + os.environ["GH_REPO"] + "/" + path
    headers = {"Authorization": "Bearer " + os.environ["GH_TOKEN"],
               "Accept": "application/vnd.github+json", "X-GitHub-Api-Version": "2022-11-28"}
    payload = None if data is None else json.dumps(data).encode()
    if payload is not None:
        headers["Content-Type"] = "application/json"
    request = urllib.request.Request(url, data=payload, headers=headers, method=method)
    with urllib.request.urlopen(request, timeout=60) as response:
        content = response.read()
        return json.loads(content) if content else None


def current_release():
    return api("GET", "releases/tags/" + urllib.parse.quote(release_tools.tag(), safe=""))


def tag_ref():
    return api("GET", "git/ref/tags/" + urllib.parse.quote(release_tools.tag(), safe=""))["object"]["sha"]


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def asset_matches(asset, record):
    return asset.get("state") == "uploaded" and asset.get("size") == record["size"] and asset.get("digest") == "sha256:" + record["sha256"]


def download(asset, destination):
    # 下载公开附件无需携带令牌，避免跨域重定向泄露认证头。
    with urllib.request.urlopen(asset["browser_download_url"], timeout=120) as response, destination.open("wb") as output:
        shutil.copyfileobj(response, output)
    record = {"size": destination.stat().st_size, "sha256": digest(destination)}
    if not asset_matches(asset, record):
        raise ValueError("旧附件下载与 GitHub digest 不一致: " + asset["name"])
    return record


def validate_request():
    source = os.environ["SOURCE_COMMIT"]
    if not re.fullmatch(r"[0-9a-f]{40}", source) or run("git", "rev-parse", "HEAD").strip() != source:
        raise ValueError("source_commit 必须为当前检出的完整 40 位提交")
    value = release_tools.tag()
    if "-" in value or json.loads(Path("package.json").read_text(encoding="utf-8"))["version"] != value[1:]:
        raise ValueError("同版本修补仅允许当前正式版本，不能修改版本号")
    run("git", "merge-base", "--is-ancestor", value + "^{commit}", source)
    checks = json.loads(run("gh", "run", "list", "--workflow", "android-ci.yml", "--commit", source,
                           "--limit", "20", "--json", "headSha,status,conclusion"))
    if not any(item["headSha"] == source and item["status"] == "completed" and item["conclusion"] == "success" for item in checks):
        raise ValueError("修补源码提交的 Android CI 尚未成功")
    return source


def inspect_apk(path, abi):
    tools = Path(os.environ["ANDROID_HOME"]) / "build-tools" / "36.0.0"
    manifest = run(str(tools / "aapt"), "dump", "badging", str(path))
    match = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", manifest)
    if not match or match[1] != "top.imsyy.splayer.android" or match[3] != release_tools.tag()[1:] or "application-debuggable" in manifest:
        raise ValueError("APK 包名、正式版本或 debuggable 不符合要求: " + path.name)
    native = re.search(r"native-code: (.*)", manifest)
    if not native or re.findall(r"'([^']+)'", native[1]) != [abi]:
        raise ValueError("APK ABI 与文件名不一致: " + path.name)
    signature = run(str(tools / "apksigner"), "verify", "--verbose", "--print-certs", str(path))
    fingerprints = re.findall(r"Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)", signature)
    if len(fingerprints) != 1:
        raise ValueError("APK 必须只有一个有效签名证书")
    with zipfile.ZipFile(path) as archive:
        if archive.testzip() is not None:
            raise ValueError("APK ZIP 校验失败")
        config = json.loads(archive.read("assets/capacitor.config.json"))
        if config.get("android", {}).get("webContentsDebuggingEnabled") is not False:
            raise ValueError("正式 APK 不允许开启 WebView debugging")
    return {"package": match[1], "versionCode": int(match[2]), "versionName": match[3],
            "certificate": fingerprints[0].lower(), "abi": abi,
            "size": path.stat().st_size, "sha256": digest(path)}


def validate_release(current):
    if current.get("draft") or current.get("prerelease") or current.get("tag_name") != release_tools.tag():
        raise ValueError("只能修补已公开的既有正式 Release")
    if sorted(item["name"] for item in current["assets"]) != sorted(NAMES):
        raise ValueError("现有 Release 必须恰好包含四个标准 ABI APK，禁止覆盖未知附件")


def check():
    validate_request()
    validate_release(current_release())


def prepare():
    source = validate_request()
    current = current_release()
    validate_release(current)
    BUNDLE.mkdir(exist_ok=False)
    (BUNDLE / "old").mkdir()
    shutil.copytree("release", BUNDLE / "new")
    if sorted(item.name for item in (BUNDLE / "new").iterdir()) != sorted(NAMES):
        raise ValueError("候选包必须完整包含四个正式 ABI")
    assets = {item["name"]: item for item in current["assets"]}
    records = {}
    for abi, name in zip(ABIS, NAMES):
        old_path, new_path = BUNDLE / "old" / name, BUNDLE / "new" / name
        download(assets[name], old_path)
        old, new = inspect_apk(old_path, abi), inspect_apk(new_path, abi)
        for key in ("package", "versionCode", "versionName", "certificate", "abi"):
            if old[key] != new[key]:
                raise ValueError("新旧 APK 的版本、签名或 ABI 不相同: " + name)
        records[name] = {"old": old, "new": new, "old_id": assets[name]["id"], "old_label": assets[name].get("label") or ""}
    manifest = {"schema": 1, "tag": release_tools.tag(), "tag_ref": tag_ref(), "source_commit": source,
                "workflow_commit": os.environ["GITHUB_SHA"], "prepared_run_id": os.environ["GITHUB_RUN_ID"],
                "release_id": current["id"], "target_commitish": current["target_commitish"],
                "original_body": current.get("body") or "", "records": records,
                "prepared_at": datetime.now(timezone.utc).isoformat()}
    notes = release_tools.release_notes().split("# 更新日志\n", 1)[1]
    notes = notes.split("### 桌面歌词与横屏播放器（已发布内容）", 1)[0].strip().replace("（尚未发布）", "（同版本修补）")
    (BUNDLE / "changes.md").write_text(notes, encoding="utf-8")
    manifest["changes_sha256"] = digest(BUNDLE / "changes.md")
    (BUNDLE / "manifest.json").write_text(json.dumps(manifest, indent=2, ensure_ascii=False), encoding="utf-8")
    print("四 ABI 候选包与旧包已验证；现有 Release 未修改。")


def load_bundle():
    manifest = json.loads((BUNDLE / "manifest.json").read_text(encoding="utf-8"))
    source = validate_request()
    if manifest["schema"] != 1 or manifest["source_commit"] != source or manifest["tag"] != release_tools.tag() or manifest["prepared_run_id"] != os.environ["PREPARED_RUN_ID"]:
        raise ValueError("准备产物与当前版本、源码提交或运行 ID 不匹配")
    prepared = api("GET", "actions/runs/" + os.environ["PREPARED_RUN_ID"])
    if (prepared["conclusion"] != "success" or prepared["event"] != "workflow_dispatch"
            or prepared["path"] != ".github/workflows/android-patch.yml" or prepared["head_sha"] != manifest["workflow_commit"]):
        raise ValueError("产物必须来自本仓库成功的同版本修补准备工作流")
    if sorted(manifest["records"]) != sorted(NAMES) or digest(BUNDLE / "changes.md") != manifest["changes_sha256"]:
        raise ValueError("准备清单或更新说明不完整")
    for abi, name in zip(ABIS, NAMES):
        for kind in ("old", "new"):
            if inspect_apk(BUNDLE / kind / name, abi) != manifest["records"][name][kind]:
                raise ValueError("准备产物校验失败: " + name)
    return manifest


def patch_body(manifest):
    marker = f"<!-- splayer-patch:{manifest['source_commit']}:{manifest['prepared_run_id']} -->"
    repo = os.environ["GH_REPO"]
    rows = "\n".join(f"| `{name}` | `{record['new']['sha256']}` |" for name, record in manifest["records"].items())
    provenance = (f"\n\n{marker}\n## 同版本 APK 修补\n\n"
                  f"版本与 Tag 保持 **{manifest['tag']}**，仅替换安装包。已安装用户可重新下载覆盖安装。\n\n"
                  f"源码：[ {manifest['source_commit']} ](https://github.com/{repo}/commit/{manifest['source_commit']})；"
                  f"准备工作流：[运行 {manifest['prepared_run_id']}](https://github.com/{repo}/actions/runs/{manifest['prepared_run_id']})。\n\n"
                  f"准备时间（UTC）：{manifest['prepared_at']}。\n\n"
                  f"签名证书 SHA-256：`{manifest['records'][NAMES[0]]['new']['certificate']}`。\n\n"
                  f"| APK | SHA-256 |\n| --- | --- |\n{rows}\n\n")
    return manifest["original_body"] + provenance + (BUNDLE / "changes.md").read_text(encoding="utf-8")


def rename(asset_id, name, label=""):
    return api("PATCH", f"releases/assets/{asset_id}", {"name": name, "label": label})


def prefix(manifest):
    return "patch-" + manifest["prepared_run_id"] + "-" + manifest["source_commit"][:12] + "-"


def rollback(manifest):
    current = current_release()
    assets = {item["id"]: item for item in current["assets"]}
    original_ids = {record["old_id"] for record in manifest["records"].values()}
    stage_names = {prefix(manifest) + "new-" + name for name in NAMES}
    staged = [item for item in assets.values() if item["id"] not in original_ids
              and (item["name"] in stage_names or (
                  (item["name"].startswith(prefix(manifest)) or item["name"] in NAMES)
                  and any(asset_matches(item, record["new"]) for record in manifest["records"].values())))]
    # 新附件先移出标准名称，再恢复旧附件，最后清理暂存附件。
    for item in staged:
        rename(item["id"], prefix(manifest) + "rollback-" + str(item["id"]) + ".apk")
    for name, record in manifest["records"].items():
        if record["old_id"] not in assets or not asset_matches(assets[record["old_id"]], record["old"]):
            raise ValueError("回滚旧附件缺失，必须使用已保存的准备产物恢复: " + name)
        rename(record["old_id"], name, record["old_label"])
    if current.get("body") == patch_body(manifest):
        api("PATCH", "releases/" + str(manifest["release_id"]), {"body": manifest["original_body"]})
    for item in staged:
        api("DELETE", f"releases/assets/{item['id']}")


def validate_base(current, manifest):
    if (current["id"] != manifest["release_id"] or current["target_commitish"] != manifest["target_commitish"]
            or current["draft"] or current["prerelease"] or tag_ref() != manifest["tag_ref"]):
        raise ValueError("Release 身份、状态或 Tag 已改变，停止替换")
    if current.get("body", "") not in (manifest["original_body"], patch_body(manifest)):
        raise ValueError("Release 正文在准备之后被外部修改，停止替换")
    old_ids = {record["old_id"] for record in manifest["records"].values()}
    for item in current["assets"]:
        if item["id"] in old_ids:
            name = next(name for name, record in manifest["records"].items() if record["old_id"] == item["id"])
            if item["name"] not in (name, prefix(manifest) + "old-" + name) or not asset_matches(item, manifest["records"][name]["old"]):
                raise ValueError("旧附件与准备快照不一致")
        elif item["name"] in NAMES:
            if not asset_matches(item, manifest["records"][item["name"]]["new"]):
                raise ValueError("同名附件不是已验证的候选包")
        elif not (item["name"].startswith(prefix(manifest)) and any(asset_matches(item, record["new"]) for record in manifest["records"].values())):
            raise ValueError("Release 含未知附件，禁止删除或覆盖")


def finish_cleanup(manifest):
    originals = {record["old_id"]: record for record in manifest["records"].values()}
    for item in current_release()["assets"]:
        if item["name"].startswith(prefix(manifest)):
            if item["id"] not in originals or not asset_matches(item, originals[item["id"]]["old"]):
                raise ValueError("拒绝清理无法确认来源的暂存附件")
            api("DELETE", f"releases/assets/{item['id']}")
    validate_release(current_release())


def apply():
    manifest = load_bundle()
    current = current_release()
    validate_base(current, manifest)
    by_name = {item["name"]: item for item in current["assets"]}
    complete = all(name in by_name and asset_matches(by_name[name], record["new"]) for name, record in manifest["records"].items())
    if complete and current.get("body") == patch_body(manifest):
        finish_cleanup(manifest)
        (BUNDLE / "published.json").write_text(json.dumps(current_release(), indent=2, ensure_ascii=False), encoding="utf-8")
        print("已完成相同修补，已核对并清理暂存附件。")
        return
    old_ids = {record["old_id"] for record in manifest["records"].values()}
    if not old_ids.issubset({item["id"] for item in current["assets"]}):
        raise ValueError("旧附件缺失，停止自动替换")
    if any(item["id"] not in old_ids or item["name"] not in NAMES for item in current["assets"]):
        rollback(manifest)
    staging = BUNDLE / "staging"
    staging.mkdir(exist_ok=True)
    try:
        staged = {}
        for name, record in manifest["records"].items():
            candidate = staging / (prefix(manifest) + "new-" + name)
            shutil.copyfile(BUNDLE / "new" / name, candidate)
            run("gh", "release", "upload", manifest["tag"], str(candidate))
            asset = next(item for item in current_release()["assets"] if item["name"] == candidate.name)
            if not asset_matches(asset, record["new"]):
                raise ValueError("暂存附件的 GitHub digest 校验失败")
            staged[name] = asset["id"]
        if tag_ref() != manifest["tag_ref"]:
            raise ValueError("Tag 在上传期间改变")
        validate_base(current_release(), manifest)
        for name, record in manifest["records"].items():
            rename(record["old_id"], prefix(manifest) + "old-" + name, record["old_label"])
        for name in NAMES:
            rename(staged[name], name, "同版本修补 " + manifest["source_commit"][:12])
        by_name = {item["name"]: item for item in current_release()["assets"]}
        if not all(asset_matches(by_name[name], record["new"]) for name, record in manifest["records"].items()):
            raise ValueError("标准附件最终校验失败")
        body = patch_body(manifest)
        api("PATCH", "releases/" + str(manifest["release_id"]), {"body": body})
        final = current_release()
        if final.get("body") != body or final["draft"] or final["prerelease"] or tag_ref() != manifest["tag_ref"]:
            raise ValueError("最终 Release 正文、状态或 Tag 校验失败")
    except Exception:
        rollback(manifest)
        raise
    # 旧附件只在四 ABI 与正文全部提交后清理，清理失败可重跑同一准备包。
    finish_cleanup(manifest)
    (BUNDLE / "published.json").write_text(json.dumps(current_release(), indent=2, ensure_ascii=False), encoding="utf-8")
    print("现有 Release 的四 ABI APK 已替换；未创建 Release、未修改 Tag。")


if __name__ == "__main__":
    try:
        {"check": check, "prepare": prepare, "apply": apply}[sys.argv[1]]()
    except Exception as error:
        message = str(error) if isinstance(error, ValueError) else type(error).__name__
        print("::error::" + message, file=sys.stderr)
        sys.exit(1)
