package top.imsyy.splayer.android.playback;

import static org.junit.Assert.*;
import org.junit.Test;

public class FloatingLyricPolicyTest {
  @Test public void fontIsBoundedAcrossViewportAndFontScale() {
    for (float[] viewport : new float[][] {{320, 480}, {360, 800}, {800, 360}, {1280, 800}}) {
      for (float scale : new float[] {1, 1.5f, 2}) {
        float size = FloatingLyricPolicy.defaultFont(viewport[0], viewport[1],
            viewport[0] * 0.84f - 28, 72, scale, true);
        assertTrue(size >= 16 && size <= 32);
      }
    }
  }

  @Test public void normalizedPositionSurvivesRotationAndInsets() {
    float x = FloatingLyricPolicy.normalized(58, 10, 360, 264);
    assertEquals(0.5f, x, 0.001f);
    assertEquals(72, FloatingLyricPolicy.project(x, 24, 800, 704));
    assertEquals(58, FloatingLyricPolicy.project(x, 10, 360, 264));
    assertEquals(24, FloatingLyricPolicy.project(x, 24, 40, 48));
    assertEquals(0, FloatingLyricPolicy.normalized(100, 0, 40, 48), 0);
  }

  @Test public void shortLyricsStayStillAndLongLyricsReachTail() {
    assertEquals(0, FloatingLyricPolicy.scroll(0, 3, 8000, 10000), 0);
    assertEquals(0, FloatingLyricPolicy.scroll(300, 3, 650, 10000), 0);
    assertTrue(FloatingLyricPolicy.scroll(300, 3, 1500, 10000) > 0);
    assertEquals(300, FloatingLyricPolicy.scroll(300, 3, 9000, 10000), 0);
  }

  @Test public void shortDurationDoesNotCauseUnreadableSpeed() {
    float offset = FloatingLyricPolicy.scroll(900, 1, 1650, 1000);
    assertTrue(offset <= 90.01f);
  }

  @Test public void lockingCannotBeOverwrittenByTouchOrOldTimeout() {
    FloatingLyricInteraction state = new FloatingLyricInteraction();
    state.show(); state.lock(); state.show(); state.hide(); state.drag(); state.endDrag();
    assertEquals(FloatingLyricInteraction.State.LOCKED, state.state());
    assertFalse(state.controls());
    state.unlock();
    assertTrue(state.controls());
    state.drag(); state.hide();
    assertEquals(FloatingLyricInteraction.State.DRAGGING, state.state());
    state.endDrag(); state.hide();
    assertEquals(FloatingLyricInteraction.State.IDLE, state.state());
  }
}
