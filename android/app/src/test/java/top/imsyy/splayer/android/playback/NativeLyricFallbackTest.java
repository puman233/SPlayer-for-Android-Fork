package top.imsyy.splayer.android.playback;

import static org.junit.Assert.*;
import org.junit.Test;

public class NativeLyricFallbackTest {
  @Test public void parsesLrcMultipleTimesOffsetAndTranslation() {
    var lines = NativeLyricParser.lrc("[ar:artist]\n[offset:200]\n[00:01.5][00:03.500]hello\n[00:05.00]world", "[00:01.700]你好", 9000);
    assertEquals(3, lines.size()); assertEquals(1700, lines.get(0).start);
    assertEquals(3700, lines.get(0).end); assertEquals("你好", lines.get(0).translation);
    assertEquals("hello", lines.get(1).words.get(0).text); assertEquals(9000, lines.get(2).end);
  }
  @Test public void parsesYrcWordsAndTranslationWithoutWebView() {
    var lines = NativeLyricParser.yrc("[1000,2000](1000,800,0)hello (1800,1200,0)world", "[00:01.000]你好世界", 5000);
    assertEquals(1, lines.size()); assertEquals(2, lines.get(0).words.size());
    assertEquals(1800, lines.get(0).words.get(1).start); assertEquals(3000, lines.get(0).words.get(1).end);
    assertEquals("你好世界", lines.get(0).translation);
  }
  @Test public void ignoresMetadataAndInvalidLinesAndRetainsBlankTimingBoundary() {
    var lines = NativeLyricParser.lrc("[ti:title]\n[00:99]bad\n[00:01]text\n[00:03]", "", 5000);
    assertEquals(2, lines.size()); assertEquals(3000, lines.get(0).end); assertEquals("", lines.get(1).words.get(0).text);
    assertTrue(NativeLyricParser.yrc("[999999999999999999999,100]invalid", "", 0).isEmpty());
  }
  @Test public void givesWebLyricsTimeThenStartsBoundedNativeRequest() {
    var gate = new NativeLyricFallbackGate(); gate.select(2147483650L, 0);
    assertNull(gate.request(1499, true, false)); assertNull(gate.request(1500, true, true));
    var ticket = gate.request(1500, true, false); assertNotNull(ticket); assertEquals(2147483650L, ticket.songId);
    assertNull(gate.request(4000, true, false)); gate.finish(ticket, false);
    assertNull(gate.request(31499, true, false)); assertNotNull(gate.request(31500, true, false));
    assertNull(gate.request(70000, true, false));
  }
  @Test public void rejectsPreviousSongAndPreviousVisitToSameSong() {
    var gate = new NativeLyricFallbackGate(); gate.select(1, 0); var old = gate.request(1500, true, false);
    gate.select(2, 2000); assertFalse(gate.finish(old, false));
    gate.select(1, 4000); assertFalse(gate.current(old)); assertNotNull(gate.request(5500, true, false));
  }
  @Test public void pausedOrDisabledFallbackCannotStartNetworkAndNoLyricsStopsRetries() {
    var gate = new NativeLyricFallbackGate(); gate.select(1, 0);
    assertNull(gate.request(1500, false, false)); var ticket = gate.request(1600, true, false);
    assertTrue(gate.finish(ticket, true)); assertNull(gate.request(70000, true, false));
  }
  @Test public void clearingPlaybackInvalidatesInflightAndPreventsFurtherRequests() {
    var gate = new NativeLyricFallbackGate(); gate.select(1, 0);
    var pending = gate.request(1500, true, false); assertNotNull(pending);
    gate.select(0, 1600);
    assertFalse(gate.finish(pending, false));
    assertNull(gate.request(70000, true, false));
  }
}
