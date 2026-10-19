"""Verify final release contract without changing historical release behavior."""
import importlib.util
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys

spec = importlib.util.spec_from_file_location("release_assets", Path(__file__).with_name("release_assets.py"))
release_assets = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release_assets)

ABIS = {"arm64-v8a", "armeabi-v7a", "x86", "x86_64"}


def verify_local():
    files = sorted(Path("release").glob("*.apk"))
    if {file.name for file in files} != {f"app-{abi}-release.apk" for abi in ABIS}:
        raise ValueError("正式 APK 必须完整覆盖四种 ABI")
    package = json.loads(Path("package.json").read_text(encoding="utf-8"))["version"]
    gradle = Path("android/app/build.gradle").read_text(encoding="utf-8")
    code = re.search(r'versionCode\s+(?:providers[^\n]*getOrElse\()?([0-9]+)', gradle)[1]
    if os.environ["RELEASE_TAG"] != "v" + package:
        raise ValueError("正式 Tag 与源码版本不一致")
    checksums = []
    for file in files:
        manifest = subprocess.check_output([str(Path(os.environ["ANDROID_HOME"]) / "build-tools/36.0.0/aapt"), "dump", "badging", str(file)], text=True)
        abi = file.name[len("app-"):-len("-release.apk")]
        if ("name='top.imsyy.splayer.android'" not in manifest or
                f"versionName='{package}'" not in manifest or f"versionCode='{code}'" not in manifest or
                f"native-code: '{abi}'" not in manifest or "application-debuggable" in manifest):
            raise ValueError("APK Manifest、版本、ABI 检查失败：" + file.name)
        checksums.append(hashlib.sha256(file.read_bytes()).hexdigest() + "  " + file.name)
    Path("release/SHA256SUMS").write_text("\n".join(checksums) + "\n", encoding="utf-8")


def verify_remote():
    result = subprocess.check_output(["gh", "api", f"repos/{os.environ['GH_REPO']}/releases/tags/{os.environ['RELEASE_TAG']}"], text=True)
    release = json.loads(result)
    assets = {asset["name"]: asset for asset in release["assets"]}
    files = release_assets.publication_files()
    if release["draft"] or set(assets) != {file.name for file in files} or len(files) != (7 if release_assets.manifest_path().is_file() else 4):
        raise ValueError("发布后附件数量或草稿状态异常")
    if release_assets.manifest_path().is_file():
        if release["prerelease"] != bool(re.search(r"alpha|beta|rc", os.environ["RELEASE_TAG"], re.I)) or release["target_commitish"] != os.environ["RELEASE_COMMIT"]:
            raise ValueError("Published channel or target commit mismatch")
        latest = json.loads(subprocess.check_output(["gh", "api", f"repos/{os.environ['GH_REPO']}/releases/latest"], text=True))
        if latest["tag_name"] != release_assets.manifest()["stableTag"]:
            raise ValueError("Latest stable release changed")
    for file in files:
        asset = assets[file.name]
        if asset["size"] != file.stat().st_size or asset.get("digest") != "sha256:" + hashlib.sha256(file.read_bytes()).hexdigest():
            raise ValueError("发布后附件摘要不一致：" + file.name)


if __name__ == "__main__":
    try:
        {"local": verify_local, "remote": verify_remote}[sys.argv[1]]()
    except (ValueError, KeyError, subprocess.CalledProcessError) as error:
        print("::error::" + (str(error) if isinstance(error, ValueError) else type(error).__name__), file=sys.stderr)
        sys.exit(1)
