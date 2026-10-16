"""Real Git fixtures; all commits/tags/clones exist only in temporary directories."""
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("history_audit", Path(__file__).with_name("release_notes_audit.py"))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class HistoryTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.source = self.root / "source"
        self.source.mkdir()
        self.run_git(self.source, "init", "-b", "fixture")
        self.run_git(self.source, "config", "user.name", "Fixture")
        self.run_git(self.source, "config", "user.email", "fixture@example.invalid")
        self.add_commit("v1.0.0")
        self.add_commit("v1.8.0-beta.3")
        self.add_commit("v2.5.0-beta.6")
        self.previous = Path.cwd()
        os.chdir(self.source)
        self.addCleanup(os.chdir, self.previous)
        environment = patch.dict(os.environ, {"GH_REPO": "fixture/repo"})
        environment.start()
        self.addCleanup(environment.stop)

    def run_git(self, path, *args):
        return subprocess.check_output(["git", "-C", str(path), *args], stderr=subprocess.DEVNULL, encoding="utf-8").strip()

    def add_commit(self, tag):
        (self.source / "CHANGELOG.md").write_text(f"## {tag}\n\n- 用户变化：{tag}\n", encoding="utf-8")
        self.run_git(self.source, "add", "CHANGELOG.md")
        self.run_git(self.source, "commit", "-m", "fixture " + tag)
        self.run_git(self.source, "tag", tag)

    def select(self, releases):
        # Keep actual Git calls; intercept only the public Release listing.
        original = subprocess.check_output
        def command(args, **kwargs):
            if args[0] == "gh":
                return json.dumps([releases])
            return original(args, **kwargs)
        with patch.object(module.subprocess, "check_output", side_effect=command):
            return module.previous_tag("v2.5.0-beta.6")

    def test_mixed_stable_beta_nonconsecutive_tags_detached_head(self):
        self.run_git(self.source, "checkout", "--detach", "v2.5.0-beta.6")
        releases = [{"tag_name": "v1.0.0", "draft": False},
                    {"tag_name": "v1.8.0-beta.3", "draft": False},
                    {"tag_name": "v2.5.0-beta.6", "draft": True}]
        self.assertEqual(self.select(releases), "v1.8.0-beta.3")
        self.assertEqual(len(module.collect_sources("v1.8.0-beta.3", "v2.5.0-beta.6")), 2)

    def test_workflow_dispatch_head_need_not_be_release_tag(self):
        self.add_commit("v3.0.0")
        self.assertEqual(self.select([{"tag_name": "v1.0.0", "draft": False}]), "v1.0.0")
        self.assertEqual(module.previous_tag("v2.5.0-beta.6", "v1.8.0-beta.3"), "v1.8.0-beta.3")

    def test_first_release_stops_with_explicit_diagnostic_not_fake_previous(self):
        with self.assertRaisesRegex(ValueError, "首个 Release"):
            self.select([])

    def test_shallow_clone_is_rejected_then_complete_history_restores_checks(self):
        clone = self.root / "clone"
        self.run_git(self.root, "clone", "--depth", "1", "--no-local", self.source.as_uri(), str(clone))
        os.chdir(clone)
        self.assertEqual(module.git("rev-parse", "--is-shallow-repository"), "true")
        with self.assertRaisesRegex(ValueError, "fetch-depth: 0"):
            self.select([{"tag_name": "v1.0.0", "draft": False}])
        self.run_git(clone, "fetch", "--unshallow", "--tags", "origin")
        self.assertEqual(module.git("rev-parse", "--is-shallow-repository"), "false")
        self.assertEqual(self.select([{"tag_name": "v1.8.0-beta.3", "draft": False}]), "v1.8.0-beta.3")
        self.assertEqual(len(module.collect_sources("v1.8.0-beta.3", "v2.5.0-beta.6")), 2)


if __name__ == "__main__":
    unittest.main()
