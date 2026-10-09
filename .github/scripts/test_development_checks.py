import importlib.util
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch


def load(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


checks = load("development_checks", "development-checks.py")
contract = load("release_contract", "release-contract.py")
release = load("release_tools", "android-release.py")


class DevelopmentChecksTests(unittest.TestCase):
    def test_ordinary_source_and_docs_scope(self):
        self.assertEqual(checks.classify([("M", "src/core/player/a.ts")]), ([], True))
        self.assertEqual(checks.classify([("M", "website/src/main.ts")]), ([], False))

    def test_protected_changes_need_review(self):
        paths = ["README.md", "LICENSE", "package.json", "pnpm-lock.yaml", "android/app/build.gradle", ".github/workflows/release.yml"]
        self.assertEqual(checks.classify([("M", path) for path in paths]), (sorted(paths), True))

    def test_artifacts_keys_and_large_deletions_fail(self):
        for path in ["android/key.properties", "release/app.apk", "secret.jks", ".env", "android/local.properties"]:
            with self.subTest(path=path), self.assertRaises(ValueError):
                checks.classify([("A", path)])
        self.assertEqual(checks.classify([("D", f"src/file{i}.ts") for i in range(20)]), (["bulk-business-deletion"], True))

    def test_versions_parse_default_despite_preview_override(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / "android/app").mkdir(parents=True)
            (root / "package.json").write_text('{"version":"1.2.3"}')
            (root / "android/app/build.gradle").write_text('versionName providers.gradleProperty(\'previewVersion\').getOrElse("1.2.3")\nversionCode providers.gradleProperty(\'previewVersionCode\').map { it.toInteger() }.getOrElse(42)')
            self.assertEqual(checks.versions(root), ("1.2.3", 42))
            (root / "package.json").write_text('{"version":"1.2.4"}')
            with self.assertRaises(ValueError):
                checks.versions(root)

    def test_release_gate_needs_authorization_and_successful_exact_dev_ci(self):
        sha = "a" * 40
        env = {"RELEASE_AUTHORIZED": "true", "RELEASE_COMMIT": sha, "GH_REPO": "owner/repo", "REQUIRE_ANDROID_UI": "false"}
        run = {"head_sha": sha, "head_branch": "dev", "conclusion": "success", "event": "push"}
        with patch.dict(os.environ, env), patch.object(checks.subprocess, "check_output", return_value=json.dumps({"workflow_runs": [run]})):
            checks.release_gate()
        for update in [{"head_sha": "b" * 40}, {"head_branch": "other"}, {"conclusion": "failure"}, {"event": "pull_request"}]:
            with patch.dict(os.environ, env), patch.object(checks.subprocess, "check_output", return_value=json.dumps({"workflow_runs": [run | update]})), self.assertRaises(ValueError):
                checks.release_gate()
        with patch.dict(os.environ, env | {"RELEASE_AUTHORIZED": "false"}), patch.object(checks.subprocess, "check_output") as command, self.assertRaises(ValueError):
            checks.release_gate()
        command.assert_not_called()

    def test_required_ui_failure_blocks_release(self):
        sha = "a" * 40
        env = {"RELEASE_AUTHORIZED": "true", "RELEASE_COMMIT": sha, "GH_REPO": "owner/repo", "REQUIRE_ANDROID_UI": "true"}
        ci = {"workflow_runs": [{"head_sha": sha, "head_branch": "dev", "conclusion": "success", "event": "push"}]}
        name = f"android-ui-{sha}-phone"
        artifact = {"artifacts": [{"name": name, "expired": False, "workflow_run": {"id": 42}}]}
        failed_ui = {"path": ".github/workflows/android-ui-test.yml", "conclusion": "failure"}
        with patch.dict(os.environ, env), patch.object(checks.subprocess, "check_output", side_effect=[json.dumps(ci), json.dumps(artifact), json.dumps(failed_ui)]), self.assertRaises(ValueError):
            checks.release_gate()
        with patch.dict(os.environ, env), patch.object(checks.subprocess, "check_output", side_effect=[json.dumps(ci), '{"artifacts":[]}']), self.assertRaises(ValueError):
            checks.release_gate()

    def test_expected_certificate_mismatch_fails_before_copy(self):
        with tempfile.TemporaryDirectory() as temporary:
            previous = Path.cwd()
            try:
                os.chdir(temporary)
                Path("package.json").write_text('{"version":"3.0.15"}')
                Path("splayer-release-cert.der").write_bytes(b"certificate")
                env = {"RELEASE_TAG": "v3.0.15", "ANDROID_HOME": temporary, "RUNNER_TEMP": temporary,
                       "ANDROID_KEYSTORE_PATH": "key", "ANDROID_KEY_ALIAS": "alias", "EXPECTED_ANDROID_CERT_SHA256": "0" * 64}
                with patch.dict(os.environ, env), patch.object(release, "run", return_value=""), self.assertRaises(ValueError):
                    release.collect()
                self.assertFalse(Path("release").exists())
            finally:
                os.chdir(previous)

    def test_missing_four_abis_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            previous = Path.cwd()
            try:
                os.chdir(temporary)
                Path("release").mkdir()
                Path("release/app-arm64-v8a-release.apk").write_bytes(b"apk")
                with self.assertRaises(ValueError):
                    contract.verify_local()
            finally:
                os.chdir(previous)

    def test_preview_wrong_package_or_missing_abi_fails(self):
        with tempfile.TemporaryDirectory() as temporary:
            previous = Path.cwd()
            try:
                os.chdir(temporary)
                root = Path("android/app/build/outputs/apk/debug")
                root.mkdir(parents=True)
                for package in ["top.imsyy.splayer.android", "top.imsyy.splayer.android.debug"]:
                    (root / "output-metadata.json").write_text(json.dumps({"applicationId": package, "elements": []}))
                    with self.assertRaises(ValueError):
                        checks.preview()
            finally:
                os.chdir(previous)

    def test_workflow_contracts_no_preview_publish_or_extra_writes(self):
        workflows = Path(__file__).resolve().parents[1] / "workflows"
        ci = (workflows / "android-ci.yml").read_text(encoding="utf-8")
        preview = (workflows / "android-preview.yml").read_text(encoding="utf-8")
        ui = (workflows / "android-ui-test.yml").read_text(encoding="utf-8")
        for text in [ci, preview, ui]:
            self.assertNotIn("contents: write", text)
            self.assertNotIn("pull_request_target", text)
            self.assertNotIn("gh release", text)
            self.assertNotIn("tags:", text)
        self.assertEqual(ci.count(":app:assembleDebug "), 1)
        self.assertNotIn("assembleDebug", ui)
        self.assertIn("grep -q 'OK (7 tests)'", ui)
        self.assertIn("change-review", ci)
        release_workflow = (workflows / "release.yml").read_text(encoding="utf-8")
        self.assertIn("environment: android-release", release_workflow)
        self.assertLess(release_workflow.index("release-gate"), release_workflow.index("android-release.py signing"))
        self.assertLess(release_workflow.index("android-release.py backup"), release_workflow.index("android-release.py publish"))
        self.assertIn("cancel-in-progress: false", release_workflow)
        pages = (workflows / "pages.yml").read_text(encoding="utf-8")
        self.assertIn("github.event.workflow_run.conclusion == 'success'", pages)
        self.assertNotIn("Android Preview", pages)
        self.assertIn("group: pages", pages)


if __name__ == "__main__":
    unittest.main()
