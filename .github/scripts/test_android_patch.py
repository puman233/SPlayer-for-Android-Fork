import copy
import importlib.util
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

spec = importlib.util.spec_from_file_location("patch_script", Path(__file__).with_name("android-patch.py"))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class PatchTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        previous = Path.cwd()
        os.chdir(self.directory.name)
        self.addCleanup(os.chdir, previous)
        env = patch.dict(os.environ, {"RELEASE_TAG": "v3.0.11", "SOURCE_COMMIT": "a" * 40,
                                     "GITHUB_SHA": "a" * 40, "GITHUB_RUN_ID": "123", "PREPARED_RUN_ID": "123",
                                     "GH_REPO": "owner/repo", "ANDROID_HOME": "/sdk"})
        env.start()
        self.addCleanup(env.stop)
        module.BUNDLE.mkdir()
        (module.BUNDLE / "changes.md").write_text("### 自适应修补\n\n- 测试说明", encoding="utf-8")
        (module.BUNDLE / "new").mkdir()
        records = {}
        for index, (abi, name) in enumerate(zip(module.ABIS, module.NAMES)):
            candidate = module.BUNDLE / "new" / name
            candidate.write_bytes(("new-" + abi).encode())
            records[name] = {"old_id": index + 1, "old_label": "original",
                             "old": {"size": 3, "sha256": str(index) * 64},
                             "new": {"size": candidate.stat().st_size, "sha256": module.digest(candidate), "certificate": "c" * 64}}
        self.manifest = {"schema": 1, "tag": "v3.0.11", "tag_ref": "b" * 40,
                         "source_commit": "a" * 40, "prepared_run_id": "123", "prepared_at": "2026-10-03T00:00:00+00:00",
                         "release_id": 42, "target_commitish": "old-commit", "original_body": "原有下载表格和说明",
                         "records": records}
        self.state = {"id": 42, "tag_name": "v3.0.11", "target_commitish": "old-commit",
                      "draft": False, "prerelease": False, "body": self.manifest["original_body"],
                      "assets": [dict(id=record["old_id"], name=name, label="original", state="uploaded",
                                      size=record["old"]["size"], digest="sha256:" + record["old"]["sha256"])
                                 for name, record in records.items()]}
        self.original = copy.deepcopy(self.state)
        self.next_id = 100
        self.failure = None
        self.failed = False
        for name, replacement in [("load_bundle", lambda: self.manifest), ("current_release", lambda: copy.deepcopy(self.state)),
                                  ("tag_ref", lambda: self.manifest["tag_ref"]), ("api", self.api), ("run", self.upload_command)]:
            mock = patch.object(module, name, side_effect=replacement)
            mock.start()
            self.addCleanup(mock.stop)

    def fail_once(self, point):
        if point == self.failure and not self.failed:
            self.failed = True
            raise RuntimeError("模拟中断")

    def api(self, method, path, data=None):
        if path.startswith("releases/assets/"):
            identifier = int(path.rsplit("/", 1)[1])
            item = next(item for item in self.state["assets"] if item["id"] == identifier)
            if method == "DELETE":
                self.fail_once("cleanup")
                self.state["assets"].remove(item)
                return None
            point = "old-rename" if "old-app-" in data["name"] else "new-rename" if data["name"] in module.NAMES and identifier >= 100 else "rollback"
            self.fail_once(point)
            if any(other["id"] != identifier and other["name"] == data["name"] for other in self.state["assets"]):
                raise ValueError("附件名冲突")
            item.update(data)
            return copy.deepcopy(item)
        self.state.update(data)
        self.fail_once("body-response")
        return copy.deepcopy(self.state)

    def upload_command(self, *args):
        self.assertEqual(args[:3], ("gh", "release", "upload"))
        path = Path(args[4])
        self.next_id += 1
        self.state["assets"].append(dict(id=self.next_id, name=path.name, state="uploaded", size=path.stat().st_size,
                                          digest="sha256:" + module.digest(path)))
        self.fail_once("upload-response")
        return "uploaded"

    def test_apply_preserves_release_identity_tag_and_four_names(self):
        module.apply()
        self.assertEqual(self.state["id"], 42)
        self.assertEqual(self.state["target_commitish"], "old-commit")
        self.assertEqual(sorted(item["name"] for item in self.state["assets"]), sorted(module.NAMES))
        self.assertFalse(self.state["draft"])
        self.assertIn(self.manifest["source_commit"], self.state["body"])
        self.assertTrue(self.state["body"].startswith(self.manifest["original_body"]))
        self.assertEqual(module.api.call_count, 13)

    def test_transaction_failure_restores_old_ids_hashes_and_body(self):
        for failure in ("upload-response", "old-rename", "new-rename", "body-response"):
            with self.subTest(failure=failure):
                self.state = copy.deepcopy(self.original)
                self.failure, self.failed = failure, False
                with self.assertRaises(RuntimeError):
                    module.apply()
                self.assertEqual(self.state, self.original)

    def test_cleanup_failure_keeps_new_release_and_is_retryable(self):
        self.failure = "cleanup"
        with self.assertRaises(RuntimeError):
            module.apply()
        self.assertEqual(self.state["body"], module.patch_body(self.manifest))
        module.apply()
        self.assertEqual(sorted(item["name"] for item in self.state["assets"]), sorted(module.NAMES))

    def test_completed_patch_is_idempotent(self):
        module.apply()
        snapshot = copy.deepcopy(self.state)
        module.apply()
        self.assertEqual(self.state, snapshot)
        self.assertEqual(module.run.call_count, 4)

    def test_unknown_assets_external_body_or_tag_stop_before_upload(self):
        for mutation in ("asset", "body", "tag"):
            with self.subTest(mutation=mutation):
                self.state = copy.deepcopy(self.original)
                if mutation == "asset":
                    self.state["assets"].append(dict(id=999, name="user.txt"))
                elif mutation == "body":
                    self.state["body"] = "外部改动"
                else:
                    self.state["id"] = 99
                before = copy.deepcopy(self.state)
                with self.assertRaises(ValueError):
                    module.apply()
                self.assertEqual(self.state, before)
        module.run.assert_not_called()

    def test_interrupted_rename_recovers_then_replaces_all_abis(self):
        name = module.NAMES[0]
        self.upload_command("gh", "release", "upload", "v3.0.11", str(module.BUNDLE / "new" / name))
        self.state["assets"][-1]["name"] = module.prefix(self.manifest) + "new-" + name
        self.state["assets"][0]["name"] = module.prefix(self.manifest) + "old-" + name
        module.apply()
        self.assertEqual(sorted(item["name"] for item in self.state["assets"]), sorted(module.NAMES))

    def test_public_release_and_exact_abi_set_required(self):
        for kind in ("draft", "prerelease", "missing", "duplicate"):
            current = copy.deepcopy(self.original)
            if kind in ("draft", "prerelease"):
                current[kind] = True
            elif kind == "missing":
                current["assets"].pop()
            else:
                current["assets"].append(current["assets"][0])
            with self.assertRaises(ValueError):
                module.validate_release(current)

    def test_apk_checks_package_version_abi_debug_zip_and_webview(self):
        apk = Path("candidate.apk")
        valid = "package: name='top.imsyy.splayer.android' versionCode='30017' versionName='3.0.11'\nnative-code: 'arm64-v8a'"
        def write_config(debug):
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr("assets/capacitor.config.json", json.dumps({"android": {"webContentsDebuggingEnabled": debug}}))
        def tool(*args):
            return valid if "aapt" in args[0] else "Signer #1 certificate SHA-256 digest: " + "c" * 64
        write_config(False)
        with patch.object(module, "run", side_effect=tool):
            self.assertEqual(module.inspect_apk(apk, "arm64-v8a")["versionCode"], 30017)
            write_config(True)
            with self.assertRaises(ValueError):
                module.inspect_apk(apk, "arm64-v8a")
        write_config(False)
        for bad in (valid + "\napplication-debuggable", valid.replace("3.0.11", "3.0.12"),
                    valid.replace("android'", "android.debug'"), valid.replace("arm64-v8a", "x86")):
            with patch.object(module, "run", return_value=bad), self.assertRaises(ValueError):
                module.inspect_apk(apk, "arm64-v8a")


    def test_source_must_be_full_commit_current_version_and_successful_ci(self):
        Path("package.json").write_text('{"version":"3.0.11"}', encoding="utf-8")
        def command(*args):
            if args[:2] == ("git", "rev-parse"):
                return "a" * 40
            if args[0] == "git":
                return ""
            return json.dumps([dict(headSha="a" * 40, status="completed", conclusion="success")])
        with patch.object(module, "run", side_effect=command):
            self.assertEqual(module.validate_request(), "a" * 40)
            with patch.dict(os.environ, {"SOURCE_COMMIT": "dev"}), self.assertRaises(ValueError):
                module.validate_request()
            Path("package.json").write_text('{"version":"3.0.12"}', encoding="utf-8")
            with self.assertRaises(ValueError):
                module.validate_request()
        Path("package.json").write_text('{"version":"3.0.11"}', encoding="utf-8")
        for result in ([], [dict(headSha="a" * 40, status="in_progress", conclusion=None)],
                       [dict(headSha="a" * 40, status="completed", conclusion="failure")]):
            def pending(*args):
                return json.dumps(result) if args[0] == "gh" else "a" * 40 if args[:2] == ("git", "rev-parse") else ""
            with patch.object(module, "run", side_effect=pending), self.assertRaises(ValueError):
                module.validate_request()

    def test_changed_old_hash_stops_without_mutation(self):
        self.state["assets"][0]["digest"] = "sha256:" + "f" * 64
        with self.assertRaises(ValueError):
            module.apply()
        module.run.assert_not_called()

    def test_rollback_does_not_remove_foreign_assets_or_body(self):
        self.state["assets"].append(dict(id=999, name="user.txt", size=4, digest="foreign", state="uploaded"))
        self.state["body"] = "外部说明"
        module.rollback(self.manifest)
        self.assertIn("user.txt", [item["name"] for item in self.state["assets"]])
        self.assertEqual(self.state["body"], "外部说明")


if __name__ == "__main__":
    unittest.main()
