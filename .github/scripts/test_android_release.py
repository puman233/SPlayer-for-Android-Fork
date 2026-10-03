import hashlib
import io
import importlib.util
import json
import os
from pathlib import Path
import tempfile
import unittest
import urllib.error
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("release_script", Path(__file__).with_name("android-release.py"))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class ReleaseTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.previous = Path.cwd()
        os.chdir(self.temporary.name)
        self.addCleanup(os.chdir, self.previous)
        self.environment = patch.dict(os.environ, {
            "RELEASE_TAG": "v1.3.0", "GITHUB_SHA": "abc", "ANDROID_HOME": "/sdk",
            "ANDROID_KEYSTORE_PATH": "/private/key", "ANDROID_KEY_ALIAS": "release",
            "RUNNER_TEMP": self.temporary.name,
        })
        self.environment.start()
        self.addCleanup(self.environment.stop)
        Path("package.json").write_text('{"version":"1.3.0"}', encoding="utf-8")
        Path("CHANGELOG.md").write_text('## v1.3.0\n\n### 更新\n\n- 测试更新\n\n## v1.2.0\n\n- 旧版内容\n', encoding="utf-8")
        os.environ["GH_REPO"] = "owner/repo"

    def fixture(self, variant="release", version="1.3.0", filename="app-arm64-v8a-release.apk"):
        root = Path("android/app/build/outputs/apk") / variant
        root.mkdir(parents=True)
        (root / filename).write_bytes(b"apk")
        (root / "output-metadata.json").write_text(json.dumps({
            "variantName": variant, "artifactType": {"type": "APK"}, "applicationId": "app.id",
            "elements": [{"versionName": version, "versionCode": 7, "outputFile": filename,
                          "filters": [{"filterType": "ABI", "value": "arm64-v8a"}]}],
        }), encoding="utf-8")

    def tool(self, *args):
        if args[0] == "keytool":
            if "-exportcert" in args:
                Path(args[-1]).write_bytes(b"certificate")
            return "Owner: CN=Release"
        if "apksigner" in args[0]:
            return "Signer #1 certificate SHA-256 digest: " + hashlib.sha256(b"certificate").hexdigest()
        return "package: name='app.id' versionCode='7' versionName='1.3.0'"

    def test_final_release_only_and_stable_name(self):
        self.fixture()
        self.fixture("debug", "1.3.0-debug", "app-debug.apk")
        with patch.object(module, "run", side_effect=self.tool):
            module.collect()
        self.assertEqual([item.name for item in Path("release").iterdir()],
                         ["app-arm64-v8a-release.apk"])

    def test_mismatch_unsigned_and_debug_only_fail(self):
        for variant, version, filename in [("release", "1.2.0", "app-release.apk"),
                                           ("release", "1.3.0", "app-release-unsigned.apk"),
                                           ("debug", "1.3.0-debug", "app-debug.apk")]:
            with self.subTest(filename=filename), tempfile.TemporaryDirectory() as directory:
                previous = Path.cwd()
                os.chdir(directory)
                try:
                    Path("package.json").write_text('{"version":"1.3.0"}', encoding="utf-8")
                    self.fixture(variant, version, filename)
                    with patch.object(module, "run", side_effect=self.tool), self.assertRaises(ValueError):
                        module.collect()
                finally:
                    os.chdir(previous)

    def test_wrong_signing_certificate_fails(self):
        self.fixture()
        def tool(*args):
            return "Signer #1 certificate SHA-256 digest: 0000" if "apksigner" in args[0] else self.tool(*args)
        with patch.object(module, "run", side_effect=tool), self.assertRaises(ValueError):
            module.collect()

    def test_published_release_never_uploads(self):
        with patch.object(module, "release", return_value={"draft": False}), patch.object(module, "run") as command:
            module.publish()
            command.assert_not_called()

    def test_existing_asset_is_verified_not_uploaded(self):
        Path("release").mkdir()
        Path("release/app.apk").write_bytes(b"apk")
        def command(*args):
            if "download" in args:
                (Path(args[-1]) / "app.apk").write_bytes(b"apk")
            return ""
        current = {"draft": True, "target_commitish": "abc", "assets": [{"name": "app.apk"}]}
        with patch.object(module, "release", return_value=current), patch.object(module, "run", side_effect=command) as calls:
            module.publish()
            self.assertFalse(any("upload" in call.args for call in calls.call_args_list))
            self.assertIn("--prerelease=false", calls.call_args.args)

    def test_existing_asset_mismatch_stays_draft(self):
        Path("release").mkdir()
        Path("release/app.apk").write_bytes(b"new")
        def command(*args):
            (Path(args[-1]) / "app.apk").write_bytes(b"old")
            return ""
        current = {"draft": True, "target_commitish": "abc", "assets": [{"name": "app.apk"}]}
        with patch.object(module, "release", return_value=current), patch.object(module, "run", side_effect=command) as calls:
            with self.assertRaises(ValueError):
                module.publish()
            self.assertFalse(any("edit" in call.args for call in calls.call_args_list))

    def test_prerelease_created_as_draft_and_published_after_upload(self):
        os.environ["RELEASE_TAG"] = "v1.3.0-rc.1"
        Path("CHANGELOG.md").write_text('## v1.3.0-rc.1\n\n- 预发布更新\n', encoding="utf-8")
        Path("release").mkdir()
        Path("release/app.apk").write_bytes(b"apk")
        current = {"draft": True, "target_commitish": "abc", "assets": []}
        with patch.object(module, "release", side_effect=[None, current]), patch.object(module, "run", return_value="") as calls:
            module.publish()
            commands = [call.args for call in calls.call_args_list]
            self.assertIn("--draft", commands[0])
            self.assertIn("--generate-notes", commands[0])
            self.assertIn("--verify-tag", commands[0])
            self.assertIn("--notes-file", commands[0])
            self.assertIn("--prerelease", commands[0])
            self.assertIn("upload", commands[1])
            self.assertIn("--prerelease=true", commands[2])

    def test_invalid_tag_and_missing_secret_fail(self):
        os.environ["RELEASE_TAG"] = "vnot-a-version"
        with self.assertRaises(ValueError):
            module.tag()
        with patch.dict(os.environ, {"ANDROID_KEYSTORE_BASE64": ""}), self.assertRaises(ValueError):
            module.signing()

    def test_draft_is_found_when_tag_endpoint_returns_404(self):
        current = {"tag_name": "v1.3.0", "draft": True}
        response = io.BytesIO(json.dumps([current]).encode())
        missing = urllib.error.HTTPError("https://api.github.com", 404, "Not Found", {}, None)
        with patch.dict(os.environ, {"GH_TOKEN": "test-token", "GH_REPO": "owner/repo"}), \
                patch.object(module.urllib.request, "urlopen", side_effect=[missing, response]) as request:
            self.assertEqual(module.release(), current)
            self.assertIn("per_page=100&page=1", request.call_args.args[0].full_url)

    def test_release_list_is_paginated(self):
        other = [{"tag_name": "v0.0.1"}] * 100
        current = {"tag_name": "v1.3.0", "draft": True}
        missing = urllib.error.HTTPError("https://api.github.com", 404, "Not Found", {}, None)
        with patch.dict(os.environ, {"GH_TOKEN": "test-token", "GH_REPO": "owner/repo"}), \
                patch.object(module.urllib.request, "urlopen", side_effect=[
                    missing, io.BytesIO(json.dumps(other).encode()),
                    io.BytesIO(json.dumps([current]).encode()),
                ]) as request:
            self.assertEqual(module.release(), current)
            self.assertIn("page=2", request.call_args.args[0].full_url)

    def test_api_permission_failure_is_not_treated_as_missing_release(self):
        denied = urllib.error.HTTPError("https://api.github.com", 403, "Forbidden", {}, None)
        with patch.dict(os.environ, {"GH_TOKEN": "test-token", "GH_REPO": "owner/repo"}), \
                patch.object(module.urllib.request, "urlopen", side_effect=denied), \
                self.assertRaises(urllib.error.HTTPError):
            module.release()

    def test_recovery_uses_tag_commit_instead_of_workflow_commit(self):
        Path("release").mkdir()
        Path("release/app.apk").write_bytes(b"apk")
        current = {"draft": True, "target_commitish": "tag-commit", "assets": []}
        with patch.dict(os.environ, {"RELEASE_COMMIT": "tag-commit"}), \
                patch.object(module, "release", return_value=current), \
                patch.object(module, "run", return_value="") as command:
            module.publish()
            self.assertIn("--draft=false", command.call_args.args)

    def test_notes_follow_previous_format_and_only_current_version(self):
        notes = module.release_notes()
        self.assertIn("## 📦 下载与安装", notes)
        self.assertIn("# 更新日志", notes)
        self.assertIn("### 更新", notes)
        self.assertIn("测试更新", notes)
        self.assertNotIn("旧版内容", notes)
        self.assertIn("https://github.com/owner/repo/blob/dev/CHANGELOG.md", notes)
        self.assertNotIn("{{", notes)

    def test_missing_changelog_stops_publishing(self):
        Path("CHANGELOG.md").write_text('## v1.2.0\n\n- 旧版内容\n', encoding="utf-8")
        with self.assertRaises(ValueError):
            module.release_notes()

    def test_flavor_preserves_gradle_output_name(self):
        self.fixture("demoRelease", filename="app-demo-arm64-v8a-release.apk")
        with patch.object(module, "run", side_effect=self.tool):
            module.collect()
        self.assertTrue(Path("release/app-demo-arm64-v8a-release.apk").is_file())


if __name__ == "__main__":
    unittest.main()
