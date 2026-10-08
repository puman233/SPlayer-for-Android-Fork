"""Development checks; never creates tags, releases or changes signing materials."""
import argparse
import datetime
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys


def git(*args):
    return subprocess.check_output(["git", *args], text=True, encoding="utf-8").strip()


def versions(root=Path(".")):
    package = json.loads((root / "package.json").read_text(encoding="utf-8"))["version"]
    gradle = (root / "android/app/build.gradle").read_text(encoding="utf-8")
    name = re.search(r'versionName\s+(?:providers[^\n]*getOrElse\()?"([^"]+)"', gradle)
    code = re.search(r'versionCode\s+(?:providers[^\n]*getOrElse\()?([0-9]+)', gradle)
    if not name or not code or name[1] != package:
        raise ValueError("package.json 与 Android 默认版本不一致")
    return package, int(code[1])


def classify(changes):
    review = []
    forbidden = []
    build = False
    for status, path in changes:
        lower = path.lower()
        if lower.endswith((".apk", ".aab", ".apks", ".keystore", ".jks", ".p12")) or Path(path).name in {"key.properties", "local.properties", ".env"}:
            forbidden.append(path)
        if path.startswith((".github/", "android/", "src/", "scripts/", "API/")) or path in {"package.json", "pnpm-lock.yaml", "capacitor.config.ts", "vite.config.ts"} or path.startswith("tsconfig"):
            build = True
        if path.startswith(".github/") or path in {"README.md", "LICENSE", "AGENTS.md", "package.json", "pnpm-lock.yaml", "android/app/build.gradle"}:
            review.append(path)
    if sum(status == "D" and path.startswith(("src/", "android/", "API/")) for status, path in changes) >= 20:
        review.append("bulk-business-deletion")
    if forbidden:
        raise ValueError("禁止提交密钥、本地配置或构建产物：" + ", ".join(forbidden))
    return sorted(set(review)), build


def audit(base):
    # --no-renames prevents renamed protected files from escaping checks.
    raw = subprocess.check_output(["git", "diff", "--no-renames", "--name-status", "-z", base, "HEAD"])
    parts = raw.decode().rstrip("\0").split("\0") if raw else []
    changes = list(zip(parts[::2], parts[1::2]))
    review, build = classify(changes)
    patterns = [rb"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----", rb"\bgh[pousr]_[A-Za-z0-9]{36,}\b", rb"\bgithub_pat_[A-Za-z0-9_]{50,}\b"]
    for _, path in changes:
        file = Path(path)
        if file.is_file() and any(re.search(pattern, file.read_bytes()) for pattern in patterns):
            raise ValueError("疑似真实凭据：" + path + "（内容已隐藏）")
    current = versions()
    old_gradle = git("show", base + ":android/app/build.gradle")
    old_code = re.search(r'versionCode\s+(?:providers[^\n]*getOrElse\()?([0-9]+)', old_gradle)
    if old_code and current[1] < int(old_code[1]):
        raise ValueError("versionCode 不得倒退")
    print("受保护变更需 change-review Environment 人工审查：" + (", ".join(review) or "无"))
    output = os.environ.get("GITHUB_OUTPUT")
    if output:
        with open(output, "a", encoding="utf-8") as stream:
            stream.write(f"review={str(bool(review)).lower()}\nbuild={str(build).lower()}\n")


def tests():
    paths = sorted(Path("src").rglob("*.test.ts"))
    if not paths:
        raise ValueError("未找到 TypeScript 测试")
    subprocess.run(["node", "--import", "tsx", "--test", *map(str, paths)], check=True)


def preview():
    root = Path("android/app/build/outputs/apk/debug")
    metadata = json.loads((root / "output-metadata.json").read_text(encoding="utf-8"))
    if metadata["applicationId"] != "top.imsyy.splayer.android.debug":
        raise ValueError("CI 测试包必须使用隔离 applicationId")
    expected = {"armeabi-v7a", "arm64-v8a", "x86", "x86_64"}
    files = []
    actual = set()
    for item in metadata["elements"]:
        abi = next((f["value"] for f in item["filters"] if f["filterType"] == "ABI"), None)
        actual.add(abi)
        path = (root / item["outputFile"]).resolve()
        if root.resolve() not in path.parents or path.suffix != ".apk" or not path.is_file():
            raise ValueError("测试 APK 路径无效")
        tools = Path(os.environ["ANDROID_HOME"]) / "build-tools/36.0.0"
        subprocess.run([str(tools / "apksigner"), "verify", str(path)], check=True)
        manifest = subprocess.check_output([str(tools / "aapt"), "dump", "badging", str(path)], text=True)
        if (f"name='{metadata['applicationId']}'" not in manifest or
                f"versionName='{item['versionName']}'" not in manifest or
                f"versionCode='{item['versionCode']}'" not in manifest or
                f"native-code: '{abi}'" not in manifest):
            raise ValueError("测试 APK Manifest、版本或 ABI 不匹配")
        files.append({"file": path.name, "abi": abi, "versionName": item["versionName"],
                      "versionCode": item["versionCode"], "size": path.stat().st_size,
                      "sha256": hashlib.sha256(path.read_bytes()).hexdigest()})
    if actual != expected or len(files) != 4:
        raise ValueError("测试 APK 架构不完整")
    report = {"commit": git("rev-parse", "HEAD"), "builtAt": datetime.datetime.now(datetime.timezone.utc).isoformat(),
              "variant": metadata["variantName"], "applicationId": metadata["applicationId"], "files": files}
    (root / "build-info.json").write_text(json.dumps(report, indent=2), encoding="utf-8")


def release_gate():
    if os.environ.get("RELEASE_AUTHORIZED") != "true":
        raise ValueError("正式发布需要手动明确授权")
    sha = os.environ["RELEASE_COMMIT"]
    if not re.fullmatch(r"[0-9a-f]{40}", sha):
        raise ValueError("发布提交 SHA 无效")
    result = subprocess.check_output(["gh", "api", f"repos/{os.environ['GH_REPO']}/actions/workflows/android-ci.yml/runs?head_sha={sha}&status=success&per_page=100"], text=True)
    runs = json.loads(result)["workflow_runs"]
    if not any(run["head_sha"] == sha and run["conclusion"] == "success" and run["event"] == "push" and run["head_branch"] == "dev" for run in runs):
        raise ValueError("发布提交尚未通过 dev Android CI")
    if os.environ.get("REQUIRE_ANDROID_UI") == "true":
        for device in ["phone", "tablet", "small"]:
            name = f"android-ui-{sha}-{device}"
            response = subprocess.check_output(["gh", "api", f"repos/{os.environ['GH_REPO']}/actions/artifacts?name={name}&per_page=100"], text=True)
            valid = False
            for artifact in json.loads(response)["artifacts"]:
                if artifact["name"] != name or artifact["expired"]:
                    continue
                run_id = artifact["workflow_run"]["id"]
                result = subprocess.check_output(["gh", "api", f"repos/{os.environ['GH_REPO']}/actions/runs/{run_id}"], text=True)
                ui = json.loads(result)
                if ui["path"] == ".github/workflows/android-ui-test.yml" and ui["conclusion"] == "success":
                    valid = True
                    break
            if not valid:
                raise ValueError("当前提交缺少成功 UI 验收：" + device)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=["audit", "versions", "tests", "preview", "release-gate"])
    parser.add_argument("--base")
    args = parser.parse_args()
    try:
        if args.command == "audit":
            if not args.base:
                raise ValueError("audit 必须指定基准提交")
            audit(args.base)
        else:
            {"versions": versions, "tests": tests, "preview": preview, "release-gate": release_gate}[args.command]()
    except (ValueError, KeyError, subprocess.CalledProcessError) as error:
        print("::error::" + (str(error) if isinstance(error, ValueError) else type(error).__name__), file=sys.stderr)
        sys.exit(1)
