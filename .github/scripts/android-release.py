import base64
import hashlib
import importlib.util
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


RELEASE_NOTES_ROOT = Path(__file__).resolve().parent.parent / "release-notes"
RELEASE_TEMPLATE = Path(__file__).resolve().parent.parent / "RELEASE_TEMPLATE.md"
NOTE_SECTIONS = {
    "highlights": "## ✨ 主要更新",
    "features": "## 🚀 新增功能",
    "fixes": "## 🛠 修复与优化",
    "android": "## 📱 Android",
    "known_issues": "## ⚠️ 已知问题",
    "notes": "## 📌 注意事项",
}
_audit_spec = importlib.util.spec_from_file_location("notes_audit", Path(__file__).with_name("release_notes_audit.py"))
notes_audit = importlib.util.module_from_spec(_audit_spec)
_audit_spec.loader.exec_module(notes_audit)
_assets_spec = importlib.util.spec_from_file_location("release_assets", Path(__file__).with_name("release_assets.py"))
release_assets = importlib.util.module_from_spec(_assets_spec)
_assets_spec.loader.exec_module(release_assets)


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
    body = release_notes()
    run("gh", "release", "edit", tag(), "--draft=true")
    for apk in files:
        run("gh", "release", "upload", tag(), str(apk), "--clobber")
    with tempfile.TemporaryDirectory() as directory:
        notes = Path(directory) / "notes.md"
        notes.write_text(body, encoding="utf-8")
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


def compare_url(previous, current):
    if (not re.fullmatch(r"v\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?", previous)
            or previous == current):
        raise ValueError("Release Notes 必须指定不同的有效 previous_tag")
    repository = os.environ["GH_REPO"]
    if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repository):
        raise ValueError("Release Notes 仓库名称无效")
    return f"https://github.com/{repository}/compare/{previous}...{current}"


def validate_release_notes(body, previous, current):
    footer = f"**完整更新日志：** [{previous}...{current}]({compare_url(previous, current)})"
    if len(re.findall(r"完整更新日志|full\s+changelog", body, re.IGNORECASE)) != 1 or not body.endswith(footer + "\n"):
        raise ValueError("Release Notes 必须以唯一且正确的 Full Changelog compare 链接结尾")
    content = body[:-len(footer + "\n")].strip()
    headings = re.findall(r"^[ \t]*#+.*$", content, re.MULTILINE)
    expected = [heading for heading in NOTE_SECTIONS.values() if heading in headings]
    if not headings or headings != expected:
        raise ValueError("Release Notes 仅允许按顺序使用非重复的二级用户更新标题")
    for section in re.split(r"^## .*\n", content, flags=re.MULTILINE)[1:]:
        if not section.strip():
            raise ValueError("Release Notes 不允许空章节")
    if not content.startswith(headings[0] + "\n") or "{{" in body or "}}" in body:
        raise ValueError("Release Notes 模板结构无效")
    entries = [line.removeprefix("- ").strip() for line in content.splitlines() if line.strip() and not line.startswith("## ")]
    if len(entries) != len(set(entries)) or any(not re.search(r"[\u4e00-\u9fff]", entry) for entry in entries):
        raise ValueError("Release Notes 必须使用中文且不得重复条目")
    if re.search(r"<[^>]+>|```|完全修复|无脑|[★⭐]{2,}|\d+(?:\.\d+)?\s*%", content):
        raise ValueError("Release Notes 存在多余 HTML、代码或未经证实的绝对化/比例措辞")
    # 精选内容仍需人工审核；自动门禁阻止常见内部过程和发布证据泄漏。
    forbidden = (
        r"\b(?:CI|GitHub\s+Actions|(?:Android\s+)?UI\s+Test|test\s+(?:evidence|matrix)|"
        r"(?:simulated|mock)\s+data|runners?|artifacts?|build\s+pipeline|release\s+gate|"
        r"environments?|keystore|signing\s+verification|certificate\s+fingerprint)\b",
        r"\bSHA[-\s]?256\b|\b[0-9a-f]{40}\b|\b[0-9a-f]{64}\b",
        r"(?<![A-Za-z])[A-Za-z]:[\\/]|\\\\[^\s\\]+\\|(?<![:/])/(?:tmp|home|Users|private|var|opt|mnt|workspace|root|etc)/(?:[^\s]*)|file://",
        r"blob/dev/CHANGELOG\.md|CPU[-\s]?Z|99\s*%|2015|⭐⭐|\|.*ABI.*\|",
        r"开发检查|独立测试构建|内部验收|测试设备|截图数量|内部待办|模拟数据|合成音频|"
        r"签名验证|签名校验|签名证书|证书指纹|发布门禁|测试范围|测试矩阵|Codex|本(?:次|轮)发布不|已验收|"
        r"\b\d+\s+(?:screenshots?|test\s+devices?)\b|"
        r"\b(?:internal\s+(?:commit|verification|process)|this\s+release\s+(?:does\s+not|doesn't)\s+(?:change|modify))\b",
        # Reject work-in-progress reports, while allowing actual tablet fixes and user limitations.
        r"(?:手机|平板|小屏|双端|设备|批量).{0,16}回归|回归(?:测试|结果|通过|失败)|复验|前置异常|"
        r"(?:剩余|依赖).{0,16}公告.{0,24}(?:确认|适用|排查)|(?:原因|根因).{0,8}(?:待确认|尚未关闭|未关闭)",
        r"开发中.{0,12}占位|软件正在开发|(?:模拟器|测试环境).{0,12}(?:曾出现|测试日志|观察到)|"
        r"许可证据|再分发授权证据|风险接受",
        r"(?:三设备|双端).{0,20}(?:通过|失败)|\b\d+\s*/\s*\d+\s*(?:项)?(?:通过|失败)|"
        r"\bregression\s+(?:tests?|results?|passed|failed)\b",
    )
    if any(re.search(pattern, body, re.IGNORECASE) for pattern in forbidden):
        raise ValueError("Release Notes 包含内部发布信息、本地路径或非正式下载说明")
    if re.search(r"(?<![\w.])v?" + re.escape(current[1:]) + r"(?![\w.-])", content):
        raise ValueError("Release Notes 不应在正文机械重复当前版本号")


def prepare_release_notes():
    current = tag()
    source = RELEASE_NOTES_ROOT / (current + ".json")
    if not source.is_file():
        raise ValueError(f"缺少精选用户更新说明：{current}.json，停止发布")
    data = json.loads(source.read_text(encoding="utf-8"))
    if not isinstance(data, dict) or set(data) - {"previous_tag", "changes", "coverage"}:
        raise ValueError("Release Notes 数据字段无效")
    previous = notes_audit.previous_tag(current, data.get("previous_tag"))
    url = compare_url(previous, current)
    sources = notes_audit.collect_sources(previous, current)
    # A committed notes file cannot contain its own SHA. Resolve only the exact Tag commit;
    # every commit and changelog source still passes the existing complete-coverage audit.
    for record in data.get("coverage", []):
        if record.get("source") == "commit:CURRENT":
            record["source"] = "commit:" + notes_audit.git("rev-parse", current + "^{commit}")
    matrix = notes_audit.audit(data, sources)
    sections = []
    for key, heading in NOTE_SECTIONS.items():
        entries = [change["summary"] for change in data["changes"] if change["category"] == key]
        if not isinstance(entries, list) or any(not isinstance(item, str) or not item.strip() or "\n" in item or "\r" in item for item in entries):
            raise ValueError("Release Notes 条目必须是非空的单行文本列表")
        if entries:
            separator = "\n\n" if key == "notes" else "\n"
            lines = [item.strip() if key == "notes" else "- " + item.strip() for item in entries]
            sections.append(heading + "\n\n" + separator.join(lines))
    body = (RELEASE_TEMPLATE.read_text(encoding="utf-8")
            .replace("{{SECTIONS}}", "\n\n".join(sections))
            .replace("{{PREVIOUS_TAG}}", previous)
            .replace("{{CURRENT_TAG}}", current)
            .replace("{{COMPARE_URL}}", url).strip() + "\n")
    validate_release_notes(body, previous, current)
    return body, {"previous_tag": previous, "current_tag": current, "compare_url": url, "matrix": matrix,
                  "user_visible": sum(row["classification"] == "user-visible" for row in matrix),
                  "internal": sum(row["classification"] == "internal" for row in matrix), "ambiguous": 0}


def release_notes():
    body, coverage = prepare_release_notes()
    if os.environ.get("RUNNER_TEMP"):
        (Path(os.environ["RUNNER_TEMP"]) / "release-notes-coverage.json").write_text(
            json.dumps(coverage, ensure_ascii=False, indent=2), encoding="utf-8")
    return body


def publish():
    value = tag()
    current = release()
    if os.environ.get("RELEASE_REPLACE_ID"):
        replace_release(current)
        return
    if current and not current["draft"]:
        print("Release 已发布，跳过上传。")
        return
    body = release_notes()
    prerelease = bool(re.search(r"alpha|beta|rc", value, re.IGNORECASE))
    commit = os.environ.get("RELEASE_COMMIT", os.environ["GITHUB_SHA"])
    if current is None:
        with tempfile.TemporaryDirectory() as directory:
            notes_file = Path(directory) / "release-notes.md"
            notes_file.write_text(body, encoding="utf-8")
            args = ["gh", "release", "create", value, "--verify-tag", "--title", value,
                    "--notes-file", str(notes_file), "--draft", "--target", commit]
            if prerelease:
                args.append("--prerelease")
            run(*args)
        current = release()
    if current is None:
        raise ValueError("创建后仍无法读取 Release 草稿，请检查 GitHub API 状态及 contents: write 权限")
    if current["target_commitish"] != commit:
        raise ValueError("已有草稿不是本工作流当前提交创建的，请人工检查，禁止覆盖")
    assets = {item["name"] for item in current["assets"]}
    files = release_assets.publication_files()
    if not files or assets - {item.name for item in files}:
        raise ValueError("草稿包含不属于本次构建的附件，或未找到 APK，停止发布")
    for apk in files:
        if apk.name in assets:
            # 中断重试时验证已上传附件，禁止静默覆盖或重复上传。
            if release_assets.manifest_path().is_file():
                asset = next(item for item in current["assets"] if item["name"] == apk.name)
                release_assets.verify_remote({"assets": [asset]}, [apk])
            else:
                with tempfile.TemporaryDirectory() as directory:
                    run("gh", "release", "download", value, "--pattern", apk.name, "--dir", directory)
                    existing = Path(directory) / apk.name
                    if hashlib.sha256(existing.read_bytes()).digest() != hashlib.sha256(apk.read_bytes()).digest():
                        raise ValueError(f"已有附件与本次构建不同，请人工检查草稿: {apk.name}")
        else:
            if release_assets.manifest_path().is_file():
                # Address the authenticated draft by ID; /releases/tags may hide drafts.
                upload_url = f"https://uploads.github.com/repos/{os.environ['GH_REPO']}/releases/{current['id']}/assets?name={urllib.parse.quote(apk.name)}"
                run("gh", "api", "-X", "POST", upload_url, "-H", "Content-Type: application/octet-stream", "--input", str(apk))
            else:
                run("gh", "release", "upload", value, str(apk))
    if release_assets.manifest_path().is_file():
        verified = release()
        if (not verified or not verified["draft"] or verified["id"] != current["id"]
                or verified["target_commitish"] != commit):
            raise ValueError("Draft identity changed before publication")
        release_assets.publication_files()
        release_assets.verify_remote(verified, files)
    with tempfile.TemporaryDirectory() as directory:
        notes_file = Path(directory) / "release-notes.md"
        notes_file.write_text(body, encoding="utf-8")
        if release_assets.manifest_path().is_file():
            run("gh", "api", "-X", "PATCH", f"repos/{os.environ['GH_REPO']}/releases/{current['id']}",
                "-F", "body=@" + str(notes_file), "-F", "draft=false",
                "-F", "prerelease=" + str(prerelease).lower(), "-f", "make_latest=false")
        else:
            run("gh", "release", "edit", value, "--notes-file", str(notes_file), "--draft=false", f"--prerelease={str(prerelease).lower()}")
    print(f"已发布 {value}，附件数量: {len(files)}")


if __name__ == "__main__":
    try:
        {"probe": probe, "notes": release_notes, "signing": signing, "collect": collect, "backup": backup, "publish": publish}[sys.argv[1]]()
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
