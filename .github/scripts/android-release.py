import base64
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import urllib.error
import urllib.parse
import urllib.request


def run(*args):
    return subprocess.check_output(args, text=True, stderr=subprocess.STDOUT)


def tag():
    value = os.environ["RELEASE_TAG"]
    if not re.fullmatch(r"v\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?", value):
        raise ValueError("Tag 必须为 v<versionName>，例如 v3.0.8 或 v3.0.9-rc.1")
    return value


def release():
    base_url = (os.environ.get("GITHUB_API_URL", "https://api.github.com")
                + "/repos/" + os.environ["GH_REPO"] + "/releases")
    headers = {
        "Authorization": "Bearer " + os.environ["GH_TOKEN"],
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
    }
    value = tag()
    request = urllib.request.Request(base_url + "/tags/" + urllib.parse.quote(value, safe=""), headers=headers)
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        if error.code != 404:
            raise
    # 按 Tag 查询可能不返回草稿，分页列表才能可靠恢复未公开版本
    page = 1
    while True:
        request = urllib.request.Request(f"{base_url}?per_page=100&page={page}", headers=headers)
        with urllib.request.urlopen(request, timeout=30) as response:
            releases = json.load(response)
        for current in releases:
            if current["tag_name"] == value:
                return current
        if len(releases) < 100:
            return None
        page += 1


def probe():
    current = release()
    exists = current is not None and not current["draft"]
    if os.environ.get("RELEASE_REPLACE_ID") or os.environ.get("RELEASE_REPAIR_REF"):
        validate_replacement(current)
        exists = False
    with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
        output.write(f"exists={str(exists).lower()}\n")
    if exists:
        print(f"{tag()} 已发布，跳过构建和上传。")


def validate_replacement(current):
    expected = os.environ.get("RELEASE_REPLACE_ID", "")
    ref = os.environ.get("RELEASE_REPAIR_REF", "")
    if not expected.isdigit() or not re.fullmatch(r"[0-9a-f]{40}", ref):
        raise ValueError("同版本修复需要明确 Release ID 与完整提交 SHA")
    if not current or str(current["id"]) != expected or current["tag_name"] != tag():
        raise ValueError("目标 Release ID 或 Tag 不匹配，禁止替换")
    if os.environ.get("RELEASE_COMMIT", ref) != ref:
        raise ValueError("修复检出提交与指定 SHA 不一致")


def backup():
    current = release()
    validate_replacement(current)
    destination = Path("release-backup")
    destination.mkdir(exist_ok=True)
    (destination / "metadata.json").write_text(json.dumps(current, ensure_ascii=False, indent=2), encoding="utf-8")
    for asset in current["assets"]:
        run("gh", "release", "download", tag(), "--pattern", asset["name"], "--dir", str(destination))
        path = destination / asset["name"]
        digest = asset.get("digest", "")
        if not path.is_file() or path.stat().st_size != asset["size"] or not digest.startswith("sha256:") or hashlib.sha256(path.read_bytes()).hexdigest() != digest[7:]:
            raise ValueError("旧附件备份完整性检查失败，禁止替换")


def replace_release(current):
    validate_replacement(current)
    files = sorted(Path("release").glob("*.apk"))
    if not files or {p.name for p in files} != {a["name"] for a in current["assets"]}:
        raise ValueError("修复附件与原发行版架构不一致")
    if not Path("release-backup/metadata.json").is_file():
        raise ValueError("缺少旧发行版备份，禁止替换")
    saved = json.loads(Path("release-backup/metadata.json").read_text(encoding="utf-8"))
    validate_replacement(saved)
    for asset in saved["assets"]:
        path = Path("release-backup") / asset["name"]
        if not path.is_file() or path.stat().st_size != asset["size"] or "sha256:" + hashlib.sha256(path.read_bytes()).hexdigest() != asset.get("digest"):
            raise ValueError("旧发行版备份已损坏，禁止替换")
    run("gh", "release", "edit", tag(), "--draft=true")
    for apk in files:
        run("gh", "release", "upload", tag(), str(apk), "--clobber")
    with tempfile.TemporaryDirectory() as directory:
        notes = Path(directory) / "notes.md"
        notes.write_text(release_notes() + "\n\n同版本修复提交：" + os.environ["RELEASE_COMMIT"], encoding="utf-8")
        run("gh", "release", "edit", tag(), "--notes-file", str(notes), "--target", os.environ["RELEASE_COMMIT"], "--draft=false")
    print(f"已替换 {tag()} 的 {len(files)} 个附件，原 Tag 保留")


def signing():
    for name in ("ANDROID_KEYSTORE_BASE64", "ANDROID_KEYSTORE_PASSWORD",
                 "ANDROID_KEY_ALIAS", "ANDROID_KEY_PASSWORD"):
        if not os.environ.get(name):
            raise ValueError(f"缺少 GitHub Actions Secret: {name}")
    content = base64.b64decode("".join(os.environ["ANDROID_KEYSTORE_BASE64"].split()), validate=True)
    if not content:
        raise ValueError("ANDROID_KEYSTORE_BASE64 为空")
    destination = Path(os.environ["ANDROID_KEYSTORE_PATH"])
    destination.write_bytes(content)
    destination.chmod(0o600)


def collect():
    version = tag()[1:]
    if json.loads(Path("package.json").read_text(encoding="utf-8"))["version"] != version:
        raise ValueError("package.json.version 与 Tag 不一致，请同步前端和 Android 版本")
    tools = Path(os.environ["ANDROID_HOME"]) / "build-tools" / "36.0.0"
    certificate = Path(os.environ["RUNNER_TEMP"]) / "splayer-release-cert.der"
    run("keytool", "-exportcert", "-keystore", os.environ["ANDROID_KEYSTORE_PATH"],
        "-storepass:env", "ANDROID_KEYSTORE_PASSWORD", "-alias", os.environ["ANDROID_KEY_ALIAS"],
        "-file", str(certificate))
    fingerprint = hashlib.sha256(certificate.read_bytes()).hexdigest()
    expected = os.environ.get("EXPECTED_ANDROID_CERT_SHA256")
    if expected is not None and (not re.fullmatch(r"[0-9a-fA-F]{64}", expected) or fingerprint != expected.lower()):
        raise ValueError("正式证书与受保护 Environment 中的预期 SHA256 不一致或未配置")
    # 拒绝 Android 标准调试证书，即使误将其配置为正式 Secrets
    details = run("keytool", "-printcert", "-file", str(certificate))
    if re.search(r"CN\s*=\s*Android Debug", details, re.IGNORECASE):
        raise ValueError("Secrets 中的证书是 Android Debug，禁止正式发布")
    root = Path("android/app/build/outputs/apk").resolve()
    destination = Path("release")
    destination.mkdir(exist_ok=False)
    count = 0
    for metadata in sorted(root.rglob("output-metadata.json")):
        data = json.loads(metadata.read_text(encoding="utf-8"))
        variant = data["variantName"]
        if not variant.lower().endswith("release"):
            continue
        if data["artifactType"]["type"] != "APK" or not data["elements"]:
            raise ValueError(f"无效的 APK 元数据: {metadata}")
        for element in data["elements"]:
            source = (metadata.parent / element["outputFile"]).resolve()
            if root not in source.parents or source.suffix != ".apk" or not source.is_file():
                raise ValueError(f"无效的最终 APK 路径: {source}")
            if "unsigned" in source.name.lower() or "debug" in source.name.lower():
                raise ValueError(f"拒绝 debug/unsigned APK: {source.name}")
            if element["versionName"] != version:
                raise ValueError(f"APK 版本不一致: {source.name}: {element['versionName']} != {version}")
            signature = run(str(tools / "apksigner"), "verify", "--verbose", "--print-certs", str(source))
            digests = re.findall(r"Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)", signature)
            if [item.lower() for item in digests] != [fingerprint]:
                raise ValueError(f"APK 签名与正式 keystore 不一致: {source.name}")
            manifest = run(str(tools / "aapt"), "dump", "badging", str(source))
            if (f"name='{data['applicationId']}'" not in manifest
                    or f"versionName='{version}'" not in manifest
                    or f"versionCode='{element['versionCode']}'" not in manifest
                    or "application-debuggable" in manifest):
                raise ValueError(f"APK 清单版本、包名或 debuggable 检查失败: {source.name}")
            # 沿用 AGP 最终文件名，与已有 app-ABI-release.apk 附件保持一致
            name = source.name
            if not re.fullmatch(r"[A-Za-z0-9_.-]+\.apk", name) or (destination / name).exists():
                raise ValueError(f"APK 名称不安全或重复: {name}")
            shutil.copyfile(source, destination / name)
            count += 1
    if not count:
        raise ValueError("android/app/build/outputs/apk 下没有正式 APK")
    print(f"已验证并收集 {count} 个正式 APK。")


def release_notes():
    value = tag()
    changelog = Path("CHANGELOG.md").read_text(encoding="utf-8")
    match = re.search(r"^## " + re.escape(value) + r"\s*\n(.*?)(?=^## |\Z)", changelog, re.MULTILINE | re.DOTALL)
    if not match or not match.group(1).strip():
        raise ValueError(f"CHANGELOG.md 缺少 {value} 的更新日志，停止发布")
    changes = re.sub(r"\n---\s*$", "", match.group(1).strip()).strip()
    template = Path(__file__).resolve().parent.parent / "RELEASE_TEMPLATE.md"
    return (template.read_text(encoding="utf-8")
            .replace("{{CHANGELOG}}", changes)
            .replace("{{FULL_CHANGELOG_URL}}", f"https://github.com/{os.environ['GH_REPO']}/blob/dev/CHANGELOG.md"))


def publish():
    value = tag()
    current = release()
    if os.environ.get("RELEASE_REPLACE_ID"):
        replace_release(current)
        return
    if current and not current["draft"]:
        print("Release 已发布，跳过上传。")
        return
    prerelease = bool(re.search(r"alpha|beta|rc", value, re.IGNORECASE))
    commit = os.environ.get("RELEASE_COMMIT", os.environ["GITHUB_SHA"])
    if current is None:
        with tempfile.TemporaryDirectory() as directory:
            notes_file = Path(directory) / "release-notes.md"
            notes_file.write_text(release_notes(), encoding="utf-8")
            args = ["gh", "release", "create", value, "--verify-tag", "--title", value,
                    "--generate-notes", "--notes-file", str(notes_file), "--draft", "--target", commit]
            if prerelease:
                args.append("--prerelease")
            run(*args)
        current = release()
    if current is None:
        raise ValueError("创建后仍无法读取 Release 草稿，请检查 GitHub API 状态及 contents: write 权限")
    if current["target_commitish"] != commit:
        raise ValueError("已有草稿不是本工作流当前提交创建的，请人工检查，禁止覆盖")
    assets = {item["name"] for item in current["assets"]}
    files = sorted(Path("release").glob("*.apk"))
    if not files or assets - {item.name for item in files}:
        raise ValueError("草稿包含不属于本次构建的附件，或未找到 APK，停止发布")
    for apk in files:
        if apk.name in assets:
            # 中断重试时验证已上传附件，禁止静默覆盖或重复上传
            with tempfile.TemporaryDirectory() as directory:
                run("gh", "release", "download", value, "--pattern", apk.name, "--dir", directory)
                existing = Path(directory) / apk.name
                if hashlib.sha256(existing.read_bytes()).digest() != hashlib.sha256(apk.read_bytes()).digest():
                    raise ValueError(f"已有附件与本次构建不同，请人工检查草稿: {apk.name}")
        else:
            run("gh", "release", "upload", value, str(apk))
    run("gh", "release", "edit", value, "--draft=false", f"--prerelease={str(prerelease).lower()}")
    print(f"已发布 {value}，APK 数量: {len(files)}")


if __name__ == "__main__":
    try:
        {"probe": probe, "signing": signing, "collect": collect, "backup": backup, "publish": publish}[sys.argv[1]]()
    except Exception as error:
        # 外部命令的完整输出可能包含敏感信息，仅显示异常类型
        if isinstance(error, ValueError):
            message = str(error)
        elif isinstance(error, subprocess.CalledProcessError):
            message = f"{Path(error.cmd[0]).name} 执行失败，退出码 {error.returncode}；为保护签名信息，未输出完整命令参数"
        elif isinstance(error, urllib.error.HTTPError):
            message = f"GitHub Release API 请求失败，HTTP {error.code}，请检查 Actions 权限或 GitHub 服务状态"
        else:
            message = type(error).__name__
        print(f"::error::{message}", file=sys.stderr)
        sys.exit(1)
