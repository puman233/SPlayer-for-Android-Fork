import copy
import importlib.util
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("notes_script", Path(__file__).with_name("android-release.py"))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class ReleaseNotesTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.current = "v1.3.0"
        self.sources = {"one": "新增歌词功能", "two": "支持 Android 10", "fix": "修复切歌问题", "ci": "CI workflow 更新"}
        self.data = {
            "changes": [{"id": "lyrics", "category": "features", "summary": "新增歌词功能。"},
                        {"id": "android", "category": "android", "summary": "支持 Android 10 及以上。"},
                        {"id": "fix", "category": "fixes", "summary": "修复切歌问题。"}],
            "coverage": [
                {"source": "one", "classification": "user-visible", "release_ids": ["lyrics"], "evidence": "歌词代码"},
                {"source": "two", "classification": "user-visible", "release_ids": ["android"], "evidence": "系统配置"},
                {"source": "fix", "classification": "user-visible", "release_ids": ["fix"], "evidence": "播放代码"},
                {"source": "ci", "classification": "internal", "release_ids": [], "exclusion_reason": "内部流程"},
            ],
        }
        for target, name, value in ((module, "RELEASE_NOTES_ROOT", self.root),
                                    (module.notes_audit, "collect_sources", self.sources),
                                    (module.notes_audit, "previous_tag", "v1.2.0")):
            mocker = patch.object(target, name, return_value=value) if name != "RELEASE_NOTES_ROOT" else patch.object(target, name, value)
            mocker.start()
            self.addCleanup(mocker.stop)
        self.environment = patch.dict(os.environ, {"RELEASE_TAG": self.current, "GH_REPO": "owner/repo"})
        self.environment.start()
        self.addCleanup(self.environment.stop)

    def generate(self):
        (self.root / (self.current + ".json")).write_text(json.dumps(self.data), encoding="utf-8")
        return module.release_notes()

    def test_normal_release_has_all_user_changes_and_excludes_internal_changes(self):
        notes = self.generate()
        self.assertIn("新增歌词功能。", notes)
        self.assertIn("支持 Android 10", notes)
        self.assertIn("修复切歌问题。", notes)
        self.assertNotIn("CI", notes)
        self.assertEqual(notes.count("完整更新日志"), 1)
        self.assertIn("https://github.com/owner/repo/compare/v1.2.0...v1.3.0", notes)

    def test_english_generation_fails(self):
        self.data["changes"][0]["summary"] = "Added a lyrics feature."
        with self.assertRaises(ValueError):
            self.generate()

    def test_missing_user_change_mapping_fails(self):
        self.data["coverage"][0]["release_ids"] = []
        with self.assertRaisesRegex(ValueError, "遗漏"):
            self.generate()

    def test_unclassified_or_new_changelog_entry_fails(self):
        self.sources["new"] = "新用户功能"
        with self.assertRaisesRegex(ValueError, "尚未分类"):
            self.generate()

    def test_ambiguous_sources_fail_and_report_original_entry(self):
        self.data["coverage"][0]["classification"] = "ambiguous"
        with self.assertRaisesRegex(ValueError, "新增歌词功能"):
            self.generate()

    def test_internal_exclusion_requires_reason_and_no_public_mapping(self):
        self.assertNotIn("CI", self.generate())
        self.data["coverage"][-1]["exclusion_reason"] = ""
        with self.assertRaises(ValueError):
            self.generate()

    def test_beta_notice_allowed_without_internal_evidence(self):
        self.data["changes"][0].update(category="notes", summary="这是测试版本，可能仍存在问题。")
        self.assertIn("## 📌 注意事项", self.generate())
        self.data["changes"][0]["summary"] += "CI 已通过。"
        with self.assertRaises(ValueError):
            self.generate()

    def test_internal_terms_paths_and_unsupported_claims_fail(self):
        for value in ("CI", "UI Test", "GitHub Actions", "Artifact", "Runner", "Environment", "keystore",
                      "SHA-256", "签名证书", "test matrix", "mock data", "开发检查", "独立测试构建入口",
                      r"C:\Users\example\file", "/tmp/notes", "99%", "★★★★★", "完全修复", "<b>多余</b>"):
            with self.subTest(value=value):
                self.data["changes"][0]["summary"] = "内部内容：" + value
                with self.assertRaises(ValueError):
                    self.generate()

    def test_duplicate_footer_and_dev_link_fail(self):
        notes = self.generate()
        for bad in (notes + notes, notes.replace("/compare/v1.2.0...v1.3.0", "/blob/dev/CHANGELOG.md")):
            with self.subTest(bad=bad), self.assertRaises(ValueError):
                module.validate_release_notes(bad, "v1.2.0", self.current)

    def test_duplicate_change_and_invalid_or_empty_heading_fail(self):
        notes = self.generate()
        for bad in (notes.replace("## 🚀 新增功能", "# 🚀 新增功能"),
                    notes.replace("- 新增歌词功能。", ""),
                    notes.replace("## 🚀 新增功能", "## Unknown")):
            with self.subTest(bad=bad), self.assertRaises(ValueError):
                module.validate_release_notes(bad, "v1.2.0", self.current)
        self.data["changes"].append(copy.deepcopy(self.data["changes"][0]))
        with self.assertRaises(ValueError):
            self.generate()

    def test_optional_sections_are_not_inserted(self):
        notes = self.generate()
        self.assertNotIn("主要更新", notes)
        self.assertNotIn("已知问题", notes)
        self.assertNotIn("注意事项", notes)


class SourceAuditTests(unittest.TestCase):
    def test_parse_current_changelog_and_complete_commit_range(self):
        text = "## v1.3.0\n\n- 用户功能\n- CI 更新\n\n## v1.2.0\n\n- 旧功能"
        with patch.object(module.notes_audit, "git", side_effect=[text, "a" * 40 + " fix: 用户功能"]):
            sources = module.notes_audit.collect_sources("v1.2.0", "v1.3.0")
        self.assertEqual(len(sources), 3)
        self.assertNotIn("旧功能", sources.values())

    def test_previous_release_is_nearest_published_ancestor_not_draft(self):
        releases = [[{"tag_name": "v1.1.0", "draft": False}, {"tag_name": "v1.2.0", "draft": False},
                     {"tag_name": "v1.2.9", "draft": True}]]
        def git(*args):
            if args[0] == "rev-list":
                return "1" if "v1.2.0" in args[-1] else "8"
            return ""
        with patch.dict(os.environ, {"GH_REPO": "owner/repo"}), patch.object(module.notes_audit, "git", side_effect=git), \
                patch.object(module.notes_audit.subprocess, "check_output", return_value=json.dumps(releases)):
            self.assertEqual(module.notes_audit.previous_tag("v1.3.0"), "v1.2.0")

    def test_actual_beta_sources_are_all_covered(self):
        root = Path(__file__).resolve().parent.parent / "release-notes"
        data = json.loads((root / "v3.0.16-beta.6.json").read_text(encoding="utf-8"))
        sources = module.notes_audit.collect_sources("v3.0.15", "v3.0.16-beta.6")
        matrix = module.notes_audit.audit(data, sources)
        self.assertEqual(len(matrix), len(sources))
        self.assertEqual(len(sources), 32)
        self.assertTrue(any(change["id"] == "discover" for change in data["changes"]))


if __name__ == "__main__":
    unittest.main()
