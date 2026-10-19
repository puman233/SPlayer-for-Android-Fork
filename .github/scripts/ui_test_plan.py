"""Explicit repeatable UI regression selection for the three CI window profiles."""
import os
import sys

BASE = (
    "FloatingLyricBackgroundTest#backgroundClockIgnoresStaleWebViewAndSurvivesServiceRestart",
    "FloatingLyricOverlayTest#overlayKeepsFontLocksCleanlyAndRestoresPreferences",
    "PlayerDeviceLayoutTest#playerRemainsReachableAcrossRealRotations",
    "AlbumHeaderGeometryTest#albumTextActionsAndFloatingButtonsFit",
    "ArtistHeaderGeometryTest#artistSongsAndAlbumsKeepHeaderAndRowsSeparate",
    "FloatingLyricResumeTest#failedCloseKeepsWindowEnabledAndReportsFailure",
    "FloatingLyricResumeTest#missingWindowRestoresOnResumeAndWebViewReload",
    "FloatingLyricLongPlaybackTest#fiveMinutesBackgroundAdvancesThreeTracksAndTheirLyrics",
    "PlaybackTransitionTest#fartherResolvedSongCannotCancelImmediateNextAudioWarmup",
    "PlaybackTransitionTest#naturalHttpAudioEndAdvancesAndRecordsTheFirstPlayingEvent",
    "PlaybackTransitionTest#rapidSelectionsDoNotWaitForOrReturnToTheOldHttpAudio",
    "CompactControlInteractionTest#stalledTTMLPriorityFallsBackAndStopsLoading",
    "CompactControlInteractionTest#ttmlToggleRefreshesCurrentSongAndRendersTimedWords",
    "CompactControlInteractionTest#autoModeRendersReadyLyricsBeforeTTMLNetworkCompletes",
    "PortraitPlayerLayoutTest#metadataSwipeMovesTheWholePageWithoutLocalDrift",
    "VolumeTouchInteractionTest#touchOpensVolumeWithoutMutingAndSliderChangesVolume",
)


def select(profile):
    if profile not in {"phone", "small", "tablet"}:
        raise ValueError("Unknown UI profile")
    methods = BASE + (() if profile == "tablet" else (
        "PortraitPlayerLayoutTest#portraitLayoutIsStableAndMetadataStaysAboveControls",))
    return ["top.imsyy.splayer.android.playback." + method for method in methods]


if __name__ == "__main__":
    methods = select(sys.argv[1])
    with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
        output.write("classes=" + ",".join(methods) + "\ncount=" + str(len(methods)) + "\n")
