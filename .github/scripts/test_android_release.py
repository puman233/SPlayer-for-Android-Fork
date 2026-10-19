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
        notes_root = Path(self.temporary.name) / "notes"
        notes_root.mkdir()
        notes_patch = patch.object(module, "RELEASE_NOTES_ROOT", notes_root)
        notes_patch.start()
        self.addCleanup(notes_patch.stop)
        for value in ("v1.3.0", "v1.3.0-rc.1"):
            (notes_root / (value + ".json")).write_text(json.dumps({
                "previous_tag": "v1.2.0",
                "changes": [{"id": "lyrics", "category": "fixes", "summary": "修复歌词对齐问题。"}],
                "coverage": [{"source": "fixture", "classification": "user-visible", "release_ids": ["lyrics"], "evidence": "歌词修复"}],
            }), encoding="utf-8")
        for name, value in (("previous_tag", "v1.2.0"), ("collect_sources", {"fixture": "歌词修复"})):
            audit_patch = patch.object(module.notes_audit, name, return_value=value)
            audit_patch.start()
            self.addCleanup(audit_patch.stop)

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

    def companion_fixture(self):
        import zipfile
        root = Path(".github/release-audits")
        root.mkdir(parents=True)
        (root / "v1.3.0.json").write_text(json.dumps({"tag": "v1.3.0"}), encoding="utf-8")
        destination = Path("release")
        destination.mkdir()
        for abi in ("arm64-v8a", "armeabi-v7a", "x86", "x86_64"):
            (destination / f"app-{abi}-release.apk").write_bytes(b"apk-" + abi.encode())
        for suffix in ("corresponding-source", "third-party-notices"):
            with zipfile.ZipFile(destination / f"SPlayer-v1.3.0-{suffix}.zip", "w") as archive:
                archive.writestr("source.txt", "source")
        (destination / "SHA256SUMS").write_text("".join(
            hashlib.sha256(p.read_bytes()).hexdigest() + "  " + p.name + "\n"
            for p in sorted(destination.iterdir(), key=lambda p: p.name)), encoding="utf-8")
        return destination

    def test_all_required_assets_are_verified_before_publication(self):
        destination = self.companion_fixture()
        uploaded = []
        def command(*args):
            if "--input" in args:
                uploaded.append(Path(args[-1]).name)
            if "draft=false" in args:
                self.assertEqual(set(uploaded), {p.name for p in destination.iterdir()})
            return ""
        draft = {"id": 7, "draft": True, "target_commitish": "abc", "assets": []}
        verified = {**draft, "assets": [{"name": p.name, "size": p.stat().st_size,
            "digest": "sha256:" + hashlib.sha256(p.read_bytes()).hexdigest()}
            for p in destination.iterdir()]}
        with patch.object(module, "release", side_effect=[draft, verified]), patch.object(module, "run", side_effect=command):
            module.publish()
        self.assertEqual(len(uploaded), 7)

    def test_missing_corrupt_or_extra_assets_never_publish(self):
        destination = self.companion_fixture()
        source = destination / "SPlayer-v1.3.0-corresponding-source.zip"
        original = source.read_bytes()
        draft = {"id": 7, "draft": True, "target_commitish": "abc", "assets": []}
        for fault in ("missing", "corrupt", "extra"):
            with self.subTest(fault=fault):
                source.write_bytes(original)
                if fault == "missing":
                    source.unlink()
                elif fault == "corrupt":
                    source.write_bytes(b"changed")
                else:
                    (destination / "mapping.txt").write_text("junk")
                with patch.object(module, "release", return_value=draft), patch.object(module, "run") as calls:
                    with self.assertRaises(ValueError):
                        module.publish()
                    self.assertFalse(any(("--draft=false" in call.args or "draft=false" in call.args) for call in calls.call_args_list))
        (destination / "mapping.txt").unlink()

    def test_remote_digest_mismatch_keeps_complete_upload_draft(self):
        destination = self.companion_fixture()
        draft = {"id": 7, "draft": True, "target_commitish": "abc", "assets": []}
        remote = {**draft, "assets": [{"name": p.name, "size": p.stat().st_size,
            "digest": "sha256:" + "0" * 64} for p in destination.iterdir()]}
        with patch.object(module, "release", side_effect=[draft, remote]), patch.object(module, "run") as calls:
            with self.assertRaises(ValueError):
                module.publish()
            self.assertEqual(sum("--input" in call.args for call in calls.call_args_list), 7)
            self.assertFalse(any(("--draft=false" in call.args or "draft=false" in call.args) for call in calls.call_args_list))

    def test_current_commit_alias_still_requires_complete_coverage(self):
        path = module.RELEASE_NOTES_ROOT / "v1.3.0.json"
        data = json.loads(path.read_text(encoding="utf-8"))
        data["coverage"][0]["source"] = "commit:CURRENT"
        path.write_text(json.dumps(data), encoding="utf-8")
        with patch.object(module.notes_audit, "git", return_value="a" * 40), patch.object(
                module.notes_audit, "collect_sources", return_value={"commit:" + "a" * 40: "歌词修复"}):
            body, coverage = module.prepare_release_notes()
            self.assertIn("修复歌词对齐", body)
            self.assertEqual(coverage["matrix"][0]["source"], "commit:" + "a" * 40)
        with patch.object(module.notes_audit, "git", return_value="a" * 40), patch.object(
                module.notes_audit, "collect_sources", return_value={"commit:" + "a" * 40: "歌词修复", "commit:" + "b" * 40: "遗漏"}):
            with self.assertRaises(ValueError):
                module.prepare_release_notes()

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
            self.assertNotIn("--generate-notes", commands[0])
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

    def test_notes_are_curated_and_use_one_compare_link(self):
        notes = module.release_notes()
        self.assertIn("## 🛠 修复与优化", notes)
        self.assertIn("修复歌词对齐问题。", notes)
        self.assertNotIn("测试更新", notes)
        self.assertNotIn("旧版内容", notes)
        self.assertEqual(notes.count("完整更新日志"), 1)
        self.assertIn("https://github.com/owner/repo/compare/v1.2.0...v1.3.0", notes)
        self.assertNotIn("{{", notes)

    def test_missing_curated_notes_stops_publishing(self):
        (module.RELEASE_NOTES_ROOT / "v1.3.0.json").unlink()
        with self.assertRaises(ValueError):
            module.release_notes()

    def test_internal_notes_stop_before_any_release_mutation(self):
        (module.RELEASE_NOTES_ROOT / "v1.3.0.json").write_text(json.dumps({
            "previous_tag": "v1.2.0",
            "changes": [{"id": "ci", "category": "fixes", "summary": "CI 已通过。"}],
            "coverage": [{"source": "fixture", "classification": "user-visible", "release_ids": ["ci"], "evidence": "fixture"}],
        }), encoding="utf-8")
        for current in (None, {"draft": True, "target_commitish": "abc", "assets": []}):
            with self.subTest(current=current), patch.object(module, "release", return_value=current), \
                    patch.object(module, "run") as command, self.assertRaises(ValueError):
                module.publish()
            command.assert_not_called()

    def test_resumed_draft_receives_only_project_notes(self):
        Path("release").mkdir()
        Path("release/app.apk").write_bytes(b"apk")
        saved_notes = []
        def command(*args):
            if "--notes-file" in args:
                saved_notes.append(Path(args[args.index("--notes-file") + 1]).read_text(encoding="utf-8"))
            return ""
        current = {"draft": True, "target_commitish": "abc", "assets": []}
        with patch.object(module, "release", return_value=current), patch.object(module, "run", side_effect=command):
            module.publish()
        self.assertEqual(saved_notes, [module.release_notes()])

    def test_flavor_preserves_gradle_output_name(self):
        self.fixture("demoRelease", filename="app-demo-arm64-v8a-release.apk")
        with patch.object(module, "run", side_effect=self.tool):
            module.collect()
        self.assertTrue(Path("release/app-demo-arm64-v8a-release.apk").is_file())


class ReplacementTests(unittest.TestCase):
    def test_verified_backup_allows_same_release_replacement(self):
        with tempfile.TemporaryDirectory() as temporary:
            previous = Path.cwd()
            try:
                os.chdir(temporary)
                Path("release").mkdir()
                Path("release/app.apk").write_bytes(b"new")
                Path("release-backup").mkdir()
                Path("release-backup/app.apk").write_bytes(b"old")
                current = {"id": 42, "tag_name": "v3.0.14", "assets": [{"name": "app.apk", "size": 3, "digest": "sha256:" + hashlib.sha256(b"old").hexdigest()}]}
                Path("release-backup/metadata.json").write_text(json.dumps(current), encoding="utf-8")
                notes_written = []
                def command(*args):
                    if "--notes-file" in args:
                        notes_written.append(Path(args[args.index("--notes-file") + 1]).read_text(encoding="utf-8"))
                with patch.dict(os.environ, {"RELEASE_TAG": "v3.0.14", "RELEASE_REPLACE_ID": "42", "RELEASE_REPAIR_REF": "a" * 40, "RELEASE_COMMIT": "a" * 40}), patch.object(module, "run", side_effect=command) as commands, patch.object(module, "release_notes", return_value="repair"):
                    module.replace_release(current)
                    self.assertEqual(commands.call_args_list[0].args, ("gh", "release", "edit", "v3.0.14", "--draft=true"))
                    self.assertEqual(commands.call_args_list[1].args, ("gh", "release", "upload", "v3.0.14", str(Path("release/app.apk")), "--clobber"))
                    self.assertIn("--draft=false", commands.call_args_list[-1].args)
                    self.assertEqual(notes_written, ["repair"])
                    commands.reset_mock()
                    Path("release-backup/app.apk").write_bytes(b"bad")
                    with self.assertRaises(ValueError):
                        module.replace_release(current)
                    commands.assert_not_called()
            finally:
                os.chdir(previous)

    def test_replacement_requires_exact_identity_and_sha(self):
        current = {"id": 42, "tag_name": "v3.0.14"}
        with patch.dict(os.environ, {"RELEASE_TAG": "v3.0.14", "RELEASE_REPLACE_ID": "42", "RELEASE_REPAIR_REF": "a" * 40, "RELEASE_COMMIT": "a" * 40}):
            module.validate_replacement(current)
            for invalid in [None, {"id": 43, "tag_name": "v3.0.14"}, {"id": 42, "tag_name": "v3.0.13"}]:
                with self.assertRaises(ValueError):
                    module.validate_replacement(invalid)
            os.environ["RELEASE_COMMIT"] = "b" * 40
            with self.assertRaises(ValueError):
                module.validate_replacement(current)

    def test_replacement_without_backup_does_not_mutate_release(self):
        with tempfile.TemporaryDirectory() as temporary:
            previous = Path.cwd()
            try:
                os.chdir(temporary)
                Path("release").mkdir()
                Path("release/app.apk").write_bytes(b"apk")
                current = {"id": 42, "tag_name": "v3.0.14", "assets": [{"name": "app.apk"}]}
                with patch.dict(os.environ, {"RELEASE_TAG": "v3.0.14", "RELEASE_REPLACE_ID": "42", "RELEASE_REPAIR_REF": "a" * 40, "RELEASE_COMMIT": "a" * 40}), patch.object(module, "run") as commands:
                    with self.assertRaises(ValueError):
                        module.replace_release(current)
                    commands.assert_not_called()
            finally:
                os.chdir(previous)


if __name__ == "__main__":
    unittest.main()
