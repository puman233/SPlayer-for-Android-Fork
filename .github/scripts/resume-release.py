"""Resume a protected release from frozen, previously verified APK bytes."""
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import urllib.parse
import urllib.request
import zipfile


TOOLS = Path(__file__).resolve().parent.parent
APK_NAMES = {f"app-{abi}-release.apk" for abi in ("arm64-v8a", "armeabi-v7a", "x86", "x86_64")}


def call(*args):
    return subprocess.check_output([str(a) for a in args], encoding="utf-8", stderr=subprocess.STDOUT).strip()


def api(path, *args):
    return json.loads(call("gh", "api", f"repos/{os.environ['GH_REPO']}/{path}", *args))


def require(condition, message):
    if not condition:
        raise ValueError(message)


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def remote_tag_commit(tag):
    obj = api("git/ref/tags/" + urllib.parse.quote(tag, safe=""))["object"]
    for _ in range(5):
        if obj["type"] == "commit":
            return obj["sha"]
        require(obj["type"] == "tag", "远程 Tag 对象类型异常")
        obj = api("git/tags/" + obj["sha"])["object"]
    raise ValueError("远程 Tag 嵌套异常")


def load():
    tag = os.environ["RELEASE_TAG"]
    require(bool(re.fullmatch(r"v\d+\.\d+\.\d+-[0-9A-Za-z.-]+", tag)), "恢复入口仅用于明确的预发布 Tag")
    data = json.loads((TOOLS / "release-audits" / (tag + ".json")).read_text(encoding="utf-8"))
    require(data["tag"] == tag and data["commit"] == os.environ["RELEASE_COMMIT"], "审计版本/提交不匹配")
    require(str(data["draftId"]) == os.environ["RELEASE_RESUME_ID"], "恢复草稿 ID 不匹配")
    require(str(data["artifact"]["id"]) == os.environ["RELEASE_REUSE_ARTIFACT_ID"], "恢复 Artifact ID 不匹配")
    require(data["artifact"]["digest"] == os.environ["RELEASE_REUSE_ARTIFACT_DIGEST"], "恢复 Artifact 摘要不匹配")
    require(os.environ.get("RELEASE_AUTHORIZED") == "true", "缺少手动发布授权")
    require(os.environ.get("REQUIRE_ANDROID_UI") == "true", "恢复发布必须启用手机/平板/小屏 UI 门禁")
    require(not os.environ.get("RELEASE_REPAIR_REF") and not os.environ.get("RELEASE_REPLACE_ID"), "恢复入口禁止同版本替换")
    require(call("git", "rev-parse", "HEAD") == data["commit"], "检出源码不是已验收提交")
    require(call("git", "rev-parse", f"refs/tags/{tag}^{{commit}}") == data["commit"], "Tag 目标不匹配")
    require(remote_tag_commit(tag) == data["commit"], "远程 Tag 被移动，停止发布")
    risk = data["riskAcceptance"]
    require(risk["accepted"] is True and risk["code"] == "THIRD_PARTY_FONT_LICENSE_UNCONFIRMED", "缺少限定的维护者风险接受")
    require(risk["file"] == "android/app/src/main/assets/iconfont_notification.ttf", "风险接受范围不是通知字体")
    require(call("git", "rev-parse", f"{data['commit']}:{risk['file']}") == risk["gitBlob"], "风险接受不适用于当前字体")
    cert = os.environ.get("EXPECTED_ANDROID_CERT_SHA256", "").lower()
    require(bool(re.fullmatch(r"[0-9a-f]{64}", cert)) and cert == data["certificateSha256"], "受保护证书摘要不匹配")
    return data


def notes():
    spec = importlib.util.spec_from_file_location("release_notes", TOOLS / "scripts/android-release.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module.prepare_release_notes()[0]


def validate_origin(data, artifact, run, jobs, approvals):
    frozen = data["artifact"]
    require(not artifact["expired"] and artifact["id"] == frozen["id"], "Artifact 已过期或 ID 不匹配")
    require(artifact["name"] == frozen["name"] and artifact["digest"] == frozen["digest"], "Artifact 来源/摘要变化")
    require(artifact["workflow_run"]["id"] == frozen["runId"], "Artifact 不属于已审核运行")
    require(run["id"] == frozen["runId"] and run["head_sha"] == data["commit"], "Artifact 运行提交不匹配")
    require(run["path"] == ".github/workflows/release.yml" and run["event"] == "workflow_dispatch", "Artifact 不是手动受保护发布流程产物")
    require(run["head_repository"]["full_name"] == os.environ["GH_REPO"] and run["status"] == "completed", "Artifact 仓库/运行状态错误")
    require(run["run_attempt"] == frozen["attempt"], "源运行已经重跑，需要重新审核 Artifact")
    steps = [s for j in jobs["jobs"] if j["name"] == "release" for s in j["steps"]]
    required = {"检查签名、版本并收集最终 APK", "验证四架构发布契约并记录摘要", "Run actions/upload-artifact@v4"}
    require(required <= {s["name"] for s in steps if s["conclusion"] == "success"}, "源运行未完成签名/契约/Artifact 上传验证")
    require(any(a["state"] == "approved" and a["user"]["type"] == "User" and
                any(e["name"] == "android-release" for e in a["environments"]) for a in approvals), "源运行缺少人工 Environment 审批")


def extract_artifact(path, destination, data):
    require("sha256:" + sha(path) == data["artifact"]["digest"], "下载 Artifact ZIP 摘要不一致")
    require(set(data["apks"]) == APK_NAMES, "冻结 APK 范围不是四个标准架构")
    expected = APK_NAMES | {"SHA256SUMS"}
    with zipfile.ZipFile(path) as archive:
        items = archive.infolist()
        require(len(items) == len(expected) and {i.filename for i in items} == expected, "Artifact ZIP 文件范围异常")
        require(all(not i.is_dir() and (i.external_attr >> 16) & 0o170000 != 0o120000 for i in items), "Artifact ZIP 不允许符号链接")
        for item in items:
            (destination / item.filename).write_bytes(archive.read(item))
    verify_apk_bytes(destination, data)
    rows = (destination / "SHA256SUMS").read_text(encoding="utf-8").splitlines()
    expected_rows = {v["sha256"] + "  " + name for name, v in data["apks"].items()}
    require(len(rows) == len(expected_rows) and set(rows) == expected_rows, "源运行 SHA256SUMS 不匹配")


def verify_apk_bytes(destination, data):
    require(set(data["apks"]) == APK_NAMES, "冻结 APK 架构不完整")
    require({p.name for p in destination.glob("*.apk")} == set(data["apks"]), "四架构 APK 不完整")
    for name, frozen in data["apks"].items():
        path = destination / name
        require(path.stat().st_size == frozen["size"] and sha(path) == frozen["sha256"], "APK 与原验收字节不一致：" + name)


def certificate(path):
    tool = Path(os.environ["ANDROID_HOME"]) / "build-tools/36.0.0" / ("apksigner.bat" if os.name == "nt" else "apksigner")
    output = call(tool, "verify", "--verbose", "--print-certs", path)
    digests = re.findall(r"Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)", output)
    require(len(digests) == 1 and "Android Debug" not in output, "APK 签名不是单一正式证书")
    return digests[0].lower()


def stable_identity(release):
    return {k: release[k] for k in ("id", "tag_name", "target_commitish", "draft", "prerelease", "body", "updated_at")} | {
        "assets": sorted((a["id"], a["name"], a["size"], a.get("digest")) for a in release["assets"])}


def zip_bytes(path, files):
    # Reproducible companion archives; APKs are never rewritten.
    with zipfile.ZipFile(path, "w", compression=zipfile.ZIP_STORED) as archive:
        for name, value in sorted(files.items()):
            entry = zipfile.ZipInfo(name, date_time=(2020, 1, 1, 0, 0, 0))
            archive.writestr(entry, value)


def companion_names(tag):
    return [f"SPlayer-{tag}-corresponding-source.zip", f"SPlayer-{tag}-third-party-notices.zip"]


def make_companions(destination, data):
    source_files = {}
    for item in data["sourceArchives"]:
        require(item["source"].startswith(("https://codeload.github.com/", "https://codeberg.org/")), "源码来源域名异常")
        request = urllib.request.Request(item["source"], headers={"User-Agent": "SPlayer-Release-Source-Access"})
        with urllib.request.urlopen(request, timeout=120) as response:
            value = response.read()
        require(len(value) == item["size"] and hashlib.sha256(value).hexdigest() == item["sha256"], "源码归档摘要变化，停止发布")
        require(Path(item["file"]).name == item["file"], "源码文件名异常")
        source_files[item["file"]] = value
    source_files["SOURCE-MANIFEST.json"] = (json.dumps(data["sourceArchives"], indent=2) + "\n").encode()
    source_files["SOURCE-ACCESS.md"] = (TOOLS.parent / "public/licenses/WASM_SOURCES.md").read_bytes()
    source_files["BUILDING.txt"] = (
        "The original source archives retain their copyright notices and licenses.\n"
        "The ffmpeg-audio-player Dockerfile and scripts/build-ffmpeg.ts are the build configuration.\n"
        "Its README specifies Docker/Bun: bun install, then bun run build:wasm.\n"
        "It statically links FFmpeg n8.0.1 and SoundTouch 2.4.0 using Emscripten 4.0.22.\n"
        "To modify/relink, edit/replace the respective library sources and rebuild the complete wrapper.\n"
        "This is source access alongside the APK, not a claim of a bit-for-bit rebuild or a written three-year offer.\n"
    ).encode()
    source_name, notice_name = companion_names(data["tag"])
    zip_bytes(destination / source_name, source_files)
    notice_files = {p.name: p.read_bytes() for p in (TOOLS.parent / "public/licenses").iterdir() if p.is_file()}
    notice_files["MAINTAINER-RISK-ACCEPTANCE.json"] = (json.dumps(data["riskAcceptance"], ensure_ascii=False, indent=2) + "\n").encode()
    notice_files["RELEASE-SOURCE-ACCESS.txt"] = (
        f"Release: https://github.com/{os.environ['GH_REPO']}/releases/tag/{data['tag']}\n"
        f"The companion {source_name} provides the pinned FFmpeg wrapper, FFmpeg and SoundTouch source archives and build instructions.\n"
        "This release-access record accompanies the binary downloads; pre-release audit fields in the copied source index are historical audit states.\n"
        "The notification font evidence risk is explicitly accepted for this Beta only; no font license has been invented.\n"
    ).encode()
    zip_bytes(destination / notice_name, notice_files)


def payload(destination, data):
    names = set(data["apks"]) | set(companion_names(data["tag"])) | {"SHA256SUMS"}
    require({p.name for p in destination.iterdir() if p.is_file()} == names, "发布附件范围与契约不一致")
    return {p.name: {"size": p.stat().st_size, "sha256": sha(p)} for p in sorted(destination.iterdir())}


def validate_draft(current, data, body):
    require(current["id"] == data["draftId"] and current["tag_name"] == data["tag"], "目标草稿发生变化")
    require(current["target_commitish"] == data["commit"] and current["prerelease"], "目标提交或 Pre-release 标记变化")
    require(current["body"] == body, "已审核 Release Notes 不一致，禁止改写")


def validate_remote(current, files):
    require({a["name"] for a in current["assets"]} == set(files), "远程附件不完整或存在额外附件")
    for asset in current["assets"]:
        item = files[asset["name"]]
        require(asset["size"] == item["size"] and asset.get("digest") == "sha256:" + item["sha256"], "远程附件摘要不一致")


def prepare(data):
    tools_sha = os.environ["RELEASE_TOOLS_COMMIT"]
    require(not call("git", "diff", "--name-only", data["commit"], tools_sha, "--", "src", "android", "native", "public", "package.json", "pnpm-lock.yaml", "vite.config.ts", "capacitor.config.ts", ":(exclude)public/licenses"), "发布工具提交改变了已验收应用源码")
    runs = api(f"actions/workflows/android-ci.yml/runs?head_sha={tools_sha}&per_page=100")["workflow_runs"]
    require(any(r["head_sha"] == tools_sha and r["head_branch"] == "dev" and r["event"] == "push" and r["conclusion"] == "success" for r in runs), "发布工具提交尚未通过 dev CI")
    artifact = api(f"actions/artifacts/{data['artifact']['id']}")
    run_id = data["artifact"]["runId"]
    validate_origin(data, artifact, api(f"actions/runs/{run_id}"), api(f"actions/runs/{run_id}/jobs?per_page=100"), api(f"actions/runs/{run_id}/approvals"))
    current = api(f"releases/{data['draftId']}")
    validate_draft(current, data, notes())
    stable = api("releases/tags/" + data["stableTag"])
    require(not stable["draft"] and not stable["prerelease"], "稳定版参考状态异常")
    temp = Path(os.environ["RUNNER_TEMP"])
    (temp / "stable-before.json").write_text(json.dumps(stable_identity(stable)), encoding="utf-8")
    destination = Path("release")
    destination.mkdir(exist_ok=True)
    require(not any(destination.iterdir()), "恢复目录不是空目录，禁止覆盖")
    archive = temp / "approved-artifact.zip"
    with archive.open("wb") as stream:
        subprocess.run(["gh", "api", f"repos/{os.environ['GH_REPO']}/actions/artifacts/{data['artifact']['id']}/zip"], stdout=stream, check=True)
    extract_artifact(archive, destination, data)
    for name in data["apks"]:
        require(certificate(destination / name) == data["certificateSha256"], "APK 正式签名不匹配")
    # Reuse the existing four-ABI/manifest/version/non-debuggable contract.
    subprocess.run([sys.executable, "-B", str(TOOLS / "scripts/release-contract.py"), "local"], check=True)
    stable_dir = temp / "stable-reference"
    stable_dir.mkdir(exist_ok=True)
    call("gh", "release", "download", data["stableTag"], "--pattern", "app-arm64-v8a-release.apk", "--dir", stable_dir)
    require(certificate(stable_dir / "app-arm64-v8a-release.apk") == data["certificateSha256"], "与历史稳定版签名不一致")
    make_companions(destination, data)
    names = sorted(set(data["apks"]) | set(companion_names(data["tag"])))
    (destination / "SHA256SUMS").write_text("".join(sha(destination / n) + "  " + n + "\n" for n in names), encoding="utf-8")
    (temp / "approved-payload.json").write_text(json.dumps(payload(destination, data), indent=2), encoding="utf-8")
    print("原四架构 APK 字节、版本、签名、历史签名、Artifact 来源与配套材料验证通过；没有重建 APK。")


def verify(data, published=True):
    files = json.loads((Path(os.environ["RUNNER_TEMP"]) / "approved-payload.json").read_text(encoding="utf-8"))
    require(payload(Path("release"), data) == files, "验证后本地附件变化")
    verify_apk_bytes(Path("release"), data)
    current = api(f"releases/{data['draftId']}")
    validate_draft(current, data, notes())
    require(current["draft"] is not published, "Release 发布状态不符合预期")
    validate_remote(current, files)
    before = json.loads((Path(os.environ["RUNNER_TEMP"]) / "stable-before.json").read_text(encoding="utf-8"))
    # JSON turns tuples into lists; normalize both identities before comparing.
    require(json.loads(json.dumps(stable_identity(api("releases/tags/" + data["stableTag"])))) == before, "稳定版被改变")
    require(api("releases/latest")["tag_name"] == data["stableTag"], "最新稳定版指向变化")
    return current


def publish(data):
    destination = Path("release")
    files = json.loads((Path(os.environ["RUNNER_TEMP"]) / "approved-payload.json").read_text(encoding="utf-8"))
    require(payload(destination, data) == files, "待上传附件与验证记录不同")
    verify_apk_bytes(destination, data)
    current = api(f"releases/{data['draftId']}")
    validate_draft(current, data, notes())
    if not current["draft"]:
        verify(data)
        return
    require({a["name"] for a in current["assets"]} <= set(files), "草稿存在未授权附件")
    for asset in current["assets"]:
        validate_remote({"assets": [asset]}, {asset["name"]: files[asset["name"]]})
    existing = {a["name"] for a in current["assets"]}
    for name in files:
        if name not in existing:
            url = f"https://uploads.github.com/repos/{os.environ['GH_REPO']}/releases/{data['draftId']}/assets?name={urllib.parse.quote(name)}"
            call("gh", "api", "-X", "POST", url, "-H", "Content-Type: application/octet-stream", "--input", destination / name)
    verify(data, published=False)
    # Do not send a body/title/target: approved Release Notes and Tag stay intact.
    api(f"releases/{data['draftId']}", "-X", "PATCH", "-F", "draft=false", "-F", "prerelease=true", "-f", "make_latest=false")
    print("已发布已验收 Beta；Release Notes、目标提交及原 APK 字节保持不变。")


if __name__ == "__main__":
    try:
        data = load()
        command = sys.argv[1]
        if command == "prepare":
            prepare(data)
        elif command == "publish":
            publish(data)
        elif command == "verify":
            current = verify(data)
            print(json.dumps({"url": current["html_url"], "tag": data["tag"], "commit": data["commit"], "certificateSha256": data["certificateSha256"], "assets": [{k: a[k] for k in ("name", "size", "digest")} for a in current["assets"]]}, ensure_ascii=False, indent=2))
        else:
            raise ValueError("未知恢复命令")
    except Exception as error:
        print("::error::" + (str(error) if isinstance(error, ValueError) else type(error).__name__), file=sys.stderr)
        sys.exit(1)
