"""Required companion assets for new releases; historical APK-only releases stay readable."""
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import sys
import zipfile

ABIS = ("arm64-v8a", "armeabi-v7a", "x86", "x86_64")


def manifest_path():
    tag = os.environ["RELEASE_TAG"]
    if not re.fullmatch(r"v\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?", tag):
        raise ValueError("Invalid release tag")
    return Path(".github/release-audits") / (tag + ".json")


def manifest():
    data = json.loads(manifest_path().read_text(encoding="utf-8"))
    if data.get("tag") != os.environ["RELEASE_TAG"]:
        raise ValueError("Companion manifest tag mismatch")
    return data


def names():
    tag = os.environ["RELEASE_TAG"]
    return {f"app-{abi}-release.apk" for abi in ABIS} | {
        f"SPlayer-{tag}-corresponding-source.zip", f"SPlayer-{tag}-third-party-notices.zip"}


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def publication_files():
    root = Path("release")
    if not manifest_path().is_file():
        return sorted(root.glob("*.apk"))
    manifest()
    expected = names() | {"SHA256SUMS"}
    if {p.name for p in root.iterdir()} != expected or any(not p.is_file() or p.is_symlink() for p in root.iterdir()):
        raise ValueError("Required release assets missing or extra files present")
    checksums = (root / "SHA256SUMS").read_text(encoding="utf-8").splitlines()
    wanted = [digest(root / name) + "  " + name for name in sorted(names())]
    if checksums != wanted:
        raise ValueError("Release asset checksum mismatch")
    for path in root.glob("*.zip"):
        with zipfile.ZipFile(path) as archive:
            if not archive.namelist() or archive.testzip() is not None:
                raise ValueError("Invalid companion archive")
    return sorted(root.iterdir())


def verify_remote(current, files):
    assets = current["assets"]
    if len(assets) != len(files) or {a["name"] for a in assets} != {p.name for p in files}:
        raise ValueError("Remote release asset set mismatch")
    by_name = {p.name: p for p in files}
    for asset in assets:
        path = by_name[asset["name"]]
        if asset["size"] != path.stat().st_size or asset.get("digest") != "sha256:" + digest(path):
            raise ValueError("Remote release asset digest mismatch: " + path.name)


def prepare():
    data = manifest()  # Required for the normal workflow, never silently skip source material.
    risk = data.get("riskAcceptance", {})
    font = Path("android/app/src/main/assets/iconfont_notification.ttf")
    blob = hashlib.sha1(b"blob " + str(font.stat().st_size).encode() + b"\0" + font.read_bytes()).hexdigest()
    if (risk.get("accepted") is not True or risk.get("releaseTag") != data["tag"]
            or risk.get("gitBlob") != blob or risk.get("file") != font.as_posix()):
        raise ValueError("Font risk acceptance does not cover this release and exact font")
    if not data.get("sourceArchives"):
        raise ValueError("Missing pinned corresponding source")
    spec = importlib.util.spec_from_file_location("source_companions", Path(__file__).with_name("resume-release.py"))
    companions = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(companions)
    # Read licenses from the target checkout, binding notices to the released app.
    companions.TOOLS = Path.cwd() / ".github"
    root = Path("release")
    allowed = {f"app-{abi}-release.apk" for abi in ABIS} | {"SHA256SUMS"}
    if {p.name for p in root.iterdir()} != allowed:
        raise ValueError("Unexpected files before companion preparation")
    companions.make_companions(root, data)
    (root / "SHA256SUMS").write_text("".join(digest(root / name) + "  " + name + "\n" for name in sorted(names())), encoding="utf-8")
    publication_files()


if __name__ == "__main__":
    if sys.argv[1:] != ["prepare"]:
        raise SystemExit("Expected prepare")
    prepare()
