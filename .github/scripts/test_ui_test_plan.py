"""The CI test plan preserves all eight original behavioral checks."""
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("ui_plan", Path(__file__).with_name("ui_test_plan.py"))
plan = importlib.util.module_from_spec(spec)
spec.loader.exec_module(plan)


class PlanTests(unittest.TestCase):
    def test_profiles_preserve_original_checks_and_add_regressions(self):
        original = {
            "FloatingLyricBackgroundTest#backgroundClockIgnoresStaleWebViewAndSurvivesServiceRestart",
            "FloatingLyricOverlayTest#overlayKeepsFontLocksCleanlyAndRestoresPreferences",
            "PlayerDeviceLayoutTest#playerRemainsReachableAcrossRealRotations",
            "AlbumHeaderGeometryTest#albumTextActionsAndFloatingButtonsFit",
            "ArtistHeaderGeometryTest#artistSongsAndAlbumsKeepHeaderAndRowsSeparate",
            "FloatingLyricResumeTest#failedCloseKeepsWindowEnabledAndReportsFailure",
            "FloatingLyricResumeTest#missingWindowRestoresOnResumeAndWebViewReload",
            "FloatingLyricLongPlaybackTest#fiveMinutesBackgroundAdvancesThreeTracksAndTheirLyrics",
        }
        for profile, count in (("phone", 17), ("small", 17), ("tablet", 16)):
            selected = plan.select(profile)
            self.assertEqual(len(selected), count)
            self.assertEqual(len(set(selected)), count)
            short = {item.removeprefix("top.imsyy.splayer.android.playback.") for item in selected}
            self.assertTrue(original <= short)
            self.assertIn("PlaybackTransitionTest#rapidSelectionsDoNotWaitForOrReturnToTheOldHttpAudio", short)
            self.assertIn("CompactControlInteractionTest#stalledTTMLPriorityFallsBackAndStopsLoading", short)
        with self.assertRaises(ValueError):
            plan.select("unknown")


if __name__ == "__main__":
    unittest.main()
