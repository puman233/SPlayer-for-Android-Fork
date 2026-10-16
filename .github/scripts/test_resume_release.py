import copy
import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile


spec = importlib.util.spec_from_file_location("resume_release", Path(__file__).with_name("resume-release.py"))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class ResumeTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.dest = self.root / "release"
        self.dest.mkdir()
        self.environment = patch.dict(os.environ, {"GH_REPO": "owner/repo", "RUNNER_TEMP": self.temp.name})
        self.environment.start()
        self.addCleanup(self.environment.stop)
        self.data = {"tag": "v1.2.0-beta.1", "commit": "a" * 40, "draftId": 12,
                     "artifact": {"id": 1, "runId": 2, "attempt": 1, "name": "verified", "digest": "sha256:abc"},
                     "apks": {f"app-{abi}-release.apk": {"size": 3, "sha256": hashlib.sha256(b"apk").hexdigest()}
                              for abi in ("arm64-v8a", "armeabi-v7a", "x86", "x86_64")}}
        self.artifact = {"id": 1, "name": "verified", "digest": "sha256:abc", "expired": False, "workflow_run": {"id": 2}}
        self.run = {"id": 2, "head_sha": "a" * 40, "head_repository": {"full_name": "owner/repo"},
                    "path": ".github/workflows/release.yml", "event": "workflow_dispatch", "status": "completed", "run_attempt": 1}
        self.jobs = {"jobs": [{"name": "release", "steps": [{"name": name, "conclusion": "success"} for name in
                             ["检查签名、版本并收集最终 APK", "验证四架构发布契约并记录摘要", "Run actions/upload-artifact@v4"]]}]}
        self.approvals = [{"state": "approved", "user": {"type": "User"}, "environments": [{"name": "android-release"}]}]

    def origin(self, artifact=None, run=None, jobs=None, approvals=None):
        module.validate_origin(self.data, self.artifact if artifact is None else artifact,
                               self.run if run is None else run, self.jobs if jobs is None else jobs,
                               self.approvals if approvals is None else approvals)

    def test_accepts_verified_artifact_from_failed_publish_job(self):
        self.origin(run=self.run | {"conclusion": "failure"})

    def test_rejects_other_commit_fork_workflow_event_attempt_or_unfinished_run(self):
        for key, value in [("head_sha", "b" * 40), ("head_repository", {"full_name": "fork/repo"}),
                           ("path", ".github/workflows/other.yml"), ("event", "push"),
                           ("run_attempt", 2), ("status", "in_progress")]:
            with self.subTest(key=key), self.assertRaises(ValueError):
                self.origin(run=self.run | {key: value})

    def test_rejects_expired_renamed_or_changed_artifact(self):
        for change in [{"expired": True}, {"name": "other"}, {"digest": "sha256:other"}, {"workflow_run": {"id": 3}}]:
            with self.subTest(change=change), self.assertRaises(ValueError):
                self.origin(artifact=self.artifact | change)

    def test_requires_real_environment_approval(self):
        for approvals in [[], [{"state": "approved", "user": {"type": "Bot"}, "environments": [{"name": "android-release"}]}],
                          [{"state": "approved", "user": {"type": "User"}, "environments": [{"name": "development"}]}]]:
            with self.subTest(approvals=approvals), self.assertRaises(ValueError):
                self.origin(approvals=approvals)

    def test_requires_signature_contract_and_upload_success(self):
        for index in range(3):
            jobs = copy.deepcopy(self.jobs)
            jobs["jobs"][0]["steps"][index]["conclusion"] = "failure"
            with self.subTest(index=index), self.assertRaises(ValueError):
                self.origin(jobs=jobs)

    def artifact_zip(self, extra=None, corrupt_sum=False):
        path = self.root / "input.zip"
        with zipfile.ZipFile(path, "w") as z:
            for name in self.data["apks"]:
                z.writestr(name, b"apk")
            sums = "\n".join(v["sha256"] + "  " + n for n, v in self.data["apks"].items()) + "\n"
            z.writestr("SHA256SUMS", "wrong" if corrupt_sum else sums)
            if extra:
                z.writestr(extra, b"unexpected")
        self.data["artifact"]["digest"] = "sha256:" + module.sha(path)
        return path

    def test_original_apk_bytes_and_checksum_file_are_preserved(self):
        module.extract_artifact(self.artifact_zip(), self.dest, self.data)
        self.assertTrue(all((self.dest / n).read_bytes() == b"apk" for n in self.data["apks"]))

    def test_rejects_archive_path_traversal_or_extra_files(self):
        for name in ["../other.apk", "extra.txt"]:
            with self.subTest(name=name), self.assertRaises(ValueError):
                module.extract_artifact(self.artifact_zip(extra=name), self.dest, self.data)
            self.assertEqual(list(self.dest.iterdir()), [])

    def test_rejects_changed_zip_digest_or_original_checksum_file(self):
        path = self.artifact_zip()
        self.data["artifact"]["digest"] = "sha256:wrong"
        with self.assertRaises(ValueError):
            module.extract_artifact(path, self.dest, self.data)
        with self.assertRaises(ValueError):
            module.extract_artifact(self.artifact_zip(corrupt_sum=True), self.dest, self.data)

    def test_rejects_changed_apk_after_extraction(self):
        module.extract_artifact(self.artifact_zip(), self.dest, self.data)
        next(self.dest.glob("*.apk")).write_bytes(b"changed")
        with self.assertRaises(ValueError):
            module.verify_apk_bytes(self.dest, self.data)

    def test_never_rewrites_approved_body_or_target(self):
        draft = {"id": 12, "tag_name": self.data["tag"], "target_commitish": self.data["commit"], "prerelease": True, "body": "approved"}
        module.validate_draft(draft, self.data, "approved")
        for change in [{"body": "other"}, {"target_commitish": "b" * 40}, {"id": 99}, {"prerelease": False}]:
            with self.subTest(change=change), self.assertRaises(ValueError):
                module.validate_draft(draft | change, self.data, "approved")

    def test_requires_complete_remote_asset_sizes_and_digests(self):
        files = {"a.apk": {"size": 3, "sha256": "abc"}}
        module.validate_remote({"assets": [{"name": "a.apk", "size": 3, "digest": "sha256:abc"}]}, files)
        for assets in [[], [{"name": "a.apk", "size": 3, "digest": "sha256:wrong"}],
                       [{"name": "a.apk", "size": 4, "digest": "sha256:abc"}]]:
            with self.subTest(assets=assets), self.assertRaises(ValueError):
                module.validate_remote({"assets": assets}, files)

    def test_companion_zip_is_deterministic_and_retains_bytes(self):
        one, two = self.root / "one.zip", self.root / "two.zip"
        module.zip_bytes(one, {"LICENSE": b"original", "SOURCE": b"source"})
        module.zip_bytes(two, {"SOURCE": b"source", "LICENSE": b"original"})
        self.assertEqual(one.read_bytes(), two.read_bytes())
        with zipfile.ZipFile(one) as z:
            self.assertEqual(z.read("LICENSE"), b"original")

    def test_load_fails_closed_without_authorization_ui_certificate_or_frozen_identity(self):
        tools = self.root / ".github"
        audit = tools / "release-audits"
        audit.mkdir(parents=True)
        data = self.data | {"certificateSha256": "f" * 64,
                            "riskAcceptance": {"accepted": True, "code": "THIRD_PARTY_FONT_LICENSE_UNCONFIRMED",
                                               "file": "android/app/src/main/assets/iconfont_notification.ttf", "gitBlob": "c" * 40}}
        (audit / (data["tag"] + ".json")).write_text(json.dumps(data), encoding="utf-8")
        env = {"RELEASE_TAG": data["tag"], "RELEASE_COMMIT": data["commit"], "RELEASE_RESUME_ID": "12",
               "RELEASE_REUSE_ARTIFACT_ID": "1", "RELEASE_REUSE_ARTIFACT_DIGEST": "sha256:abc",
               "RELEASE_AUTHORIZED": "true", "REQUIRE_ANDROID_UI": "true",
               "EXPECTED_ANDROID_CERT_SHA256": "f" * 64, "RELEASE_REPAIR_REF": "", "RELEASE_REPLACE_ID": ""}
        def git(*args):
            return "c" * 40 if args[-1].endswith(":android/app/src/main/assets/iconfont_notification.ttf") else "a" * 40
        with patch.object(module, "TOOLS", tools), patch.object(module, "call", side_effect=git), \
                patch.object(module, "remote_tag_commit", return_value="a" * 40), patch.dict(os.environ, env):
            self.assertEqual(module.load()["draftId"], 12)
            for key, value in [("RELEASE_AUTHORIZED", "false"), ("REQUIRE_ANDROID_UI", "false"),
                               ("EXPECTED_ANDROID_CERT_SHA256", "e" * 64), ("RELEASE_RESUME_ID", "99"),
                               ("RELEASE_REUSE_ARTIFACT_ID", "99"), ("RELEASE_REPLACE_ID", "99")]:
                with self.subTest(key=key), patch.dict(os.environ, {key: value}), self.assertRaises(ValueError):
                    module.load()

    def test_remote_annotated_tag_is_resolved_without_moving_it(self):
        with patch.object(module, "api", side_effect=[{"object": {"type": "tag", "sha": "b" * 40}},
                                                     {"object": {"type": "commit", "sha": "a" * 40}}]):
            self.assertEqual(module.remote_tag_commit("v1.2.0-beta.1"), "a" * 40)

    def test_cannot_change_frozen_names_to_extract_outside_destination(self):
        path = self.artifact_zip()
        self.data["apks"]["../outside.apk"] = self.data["apks"].pop(next(iter(self.data["apks"])))
        with self.assertRaises(ValueError):
            module.extract_artifact(path, self.dest, self.data)
        self.assertEqual(list(self.dest.iterdir()), [])

    def publish_fixture(self):
        module.extract_artifact(self.artifact_zip(), self.dest, self.data)
        for name in module.companion_names(self.data["tag"]):
            (self.dest / name).write_bytes(b"fixture companion")
        files = module.payload(self.dest, self.data)
        (self.root / "approved-payload.json").write_text(json.dumps(files), encoding="utf-8")
        return {"id": 12, "tag_name": self.data["tag"], "target_commitish": self.data["commit"],
                "prerelease": True, "draft": True, "body": "approved", "assets": []}

    def test_publish_checks_remote_before_flip_and_sends_no_body_target_or_title(self):
        draft = self.publish_fixture()
        previous = Path.cwd()
        os.chdir(self.root)
        try:
            with patch.object(module, "api", return_value=draft) as api, patch.object(module, "notes", return_value="approved"), \
                    patch.object(module, "call"), patch.object(module, "verify") as verify:
                module.publish(self.data)
                verify.assert_called_once_with(self.data, published=False)
                patch_args = api.call_args.args
                self.assertIn("draft=false", patch_args)
                self.assertIn("prerelease=true", patch_args)
                self.assertIn("make_latest=false", patch_args)
                self.assertFalse(any("body" in str(a) or "target" in str(a) or "title" in str(a) for a in patch_args))
        finally:
            os.chdir(previous)

    def test_remote_verification_failure_prevents_publication(self):
        draft = self.publish_fixture()
        previous = Path.cwd()
        os.chdir(self.root)
        try:
            with patch.object(module, "api", return_value=draft) as api, patch.object(module, "notes", return_value="approved"), \
                    patch.object(module, "call"), patch.object(module, "verify", side_effect=ValueError("digest")):
                with self.assertRaises(ValueError):
                    module.publish(self.data)
                self.assertFalse(any("PATCH" in c.args for c in api.call_args_list))
        finally:
            os.chdir(previous)


if __name__ == "__main__":
    unittest.main()
