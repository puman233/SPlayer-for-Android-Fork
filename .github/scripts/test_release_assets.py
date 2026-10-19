"""Exercise source preparation through the HTTP and filesystem boundaries."""
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

spec = importlib.util.spec_from_file_location("assets", Path(__file__).with_name("release_assets.py"))
assets = importlib.util.module_from_spec(spec)
spec.loader.exec_module(assets)


class AssetPreparationTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        previous = Path.cwd()
        os.chdir(self.temp.name)
        self.addCleanup(os.chdir, previous)
        env = patch.dict(os.environ, {"RELEASE_TAG": "v1.3.0-beta.1", "GH_REPO": "owner/repo"})
        env.start()
        self.addCleanup(env.stop)
        font = Path("android/app/src/main/assets/iconfont_notification.ttf")
        font.parent.mkdir(parents=True)
        font.write_bytes(b"test-font")
        licenses = Path("public/licenses")
        licenses.mkdir(parents=True)
        (licenses / "WASM_SOURCES.md").write_text("Source index")
        Path(".github/release-audits").mkdir(parents=True)
        self.data = {"tag": "v1.3.0-beta.1", "riskAcceptance": {
            "releaseTag": "v1.3.0-beta.1", "accepted": True, "file": font.as_posix(),
            "gitBlob": hashlib.sha1(b"blob 9\0test-font").hexdigest()},
            "sourceArchives": [{"file": "source.tar.gz", "source": "https://codeload.github.com/org/repo/tar.gz/pin",
                "size": 6, "sha256": hashlib.sha256(b"source").hexdigest()}]}
        self.save()
        Path("release").mkdir()
        for abi in ("arm64-v8a", "armeabi-v7a", "x86", "x86_64"):
            Path(f"release/app-{abi}-release.apk").write_bytes(b"apk")
        Path("release/SHA256SUMS").write_text("old")

    def save(self):
        Path(".github/release-audits/v1.3.0-beta.1.json").write_text(json.dumps(self.data), encoding="utf-8")

    def test_pinned_http_source_becomes_verified_companions(self):
        with patch("urllib.request.urlopen", return_value=io.BytesIO(b"source")):
            assets.prepare()
        self.assertEqual(len(assets.publication_files()), 7)
        with zipfile.ZipFile("release/SPlayer-v1.3.0-beta.1-corresponding-source.zip") as archive:
            self.assertEqual(archive.read("source.tar.gz"), b"source")
            self.assertIn("BUILDING.txt", archive.namelist())
        with zipfile.ZipFile("release/SPlayer-v1.3.0-beta.1-third-party-notices.zip") as archive:
            self.assertEqual(json.loads(archive.read("MAINTAINER-RISK-ACCEPTANCE.json"))["releaseTag"], "v1.3.0-beta.1")

    def test_changed_http_source_or_old_risk_acceptance_is_rejected(self):
        with patch("urllib.request.urlopen", return_value=io.BytesIO(b"wrong!")), self.assertRaises(ValueError):
            assets.prepare()
        self.data["riskAcceptance"]["releaseTag"] = "v1.2.0"
        self.save()
        with patch("urllib.request.urlopen") as http, self.assertRaises(ValueError):
            assets.prepare()
        http.assert_not_called()


if __name__ == "__main__":
    unittest.main()
