"""Keep the provenance index tied to actual distributed WASM inputs, offline."""
import hashlib
import json
from pathlib import Path
import subprocess
import unittest


ROOT = Path(__file__).resolve().parents[2]
LICENSES = ROOT / "public" / "licenses"


class WasmSourceIndexTests(unittest.TestCase):
    def setUp(self):
        self.index = json.loads((LICENSES / "wasm-sources.json").read_text(encoding="utf-8"))

    def test_all_tracked_wasm_inputs_have_provenance(self):
        tracked = subprocess.check_output(
            ["git", "-C", str(ROOT), "ls-files", "-z", "*.wasm"], encoding="utf-8",
        ).strip("\0").split("\0")
        indexed = [item["file"] for item in self.index["artifacts"]]
        self.assertEqual(len(indexed), len(set(indexed)))
        self.assertEqual(set(indexed), set(tracked))

    def test_index_matches_binary_bytes_not_only_filenames(self):
        for item in self.index["artifacts"]:
            with self.subTest(file=item["file"]):
                path = (ROOT / item["file"]).resolve()
                self.assertIn(ROOT, path.parents)
                data = path.read_bytes()
                self.assertEqual(data[:8], b"\x00asm\x01\x00\x00\x00")
                self.assertEqual(len(data), item["size"])
                self.assertEqual(hashlib.sha256(data).hexdigest(), item["sha256"])

    def test_source_availability_cannot_silently_close_delivery_gap(self):
        # This validates recorded states, not legal compliance or remote uptime.
        for item in self.index["artifacts"] + self.index.get("embeddedArtifacts", []):
            with self.subTest(file=item["file"]):
                self.assertTrue((LICENSES / item["details"]).is_file())
                revision = item.get("sourceRevision", item.get("vendoringRevision"))
                self.assertRegex(revision, r"^[0-9a-f]{40}$")
                self.assertRegex(item["producingProjectSource"], r"^https://github.com/.+/tree/[0-9a-f]{40}$")
                if item["sourceOfferRequired"]:
                    self.assertIsInstance(item["sourceOfferAlreadyPresent"], bool)
                    self.assertEqual(item["sourceOfferMissing"], not item["sourceOfferAlreadyPresent"])
                else:
                    self.assertIsNone(item["sourceOfferAlreadyPresent"])
                    self.assertFalse(item["sourceOfferMissing"])


if __name__ == "__main__":
    unittest.main()
