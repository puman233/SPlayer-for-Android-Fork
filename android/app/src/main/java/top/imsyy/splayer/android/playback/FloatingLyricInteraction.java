package top.imsyy.splayer.android.playback;

/** 交互转换与命中保护，计时和动画由窗口拥有 */
final class FloatingLyricInteraction {
  enum State { IDLE, CONTROLS_VISIBLE, DRAGGING, LOCKED }
  private State state = State.IDLE;

  State state() { return state; }
  boolean locked() { return state == State.LOCKED; }
  boolean controls() { return state == State.CONTROLS_VISIBLE || state == State.DRAGGING; }
  void show() { if (!locked()) state = State.CONTROLS_VISIBLE; }
  void toggle() {
    if (state == State.IDLE) show();
    else if (state == State.CONTROLS_VISIBLE) hide();
  }
  void hide() { if (state == State.CONTROLS_VISIBLE) state = State.IDLE; }
  void lock() { state = State.LOCKED; }
  void unlock() { state = State.CONTROLS_VISIBLE; }
  void drag() { if (!locked()) state = State.DRAGGING; }
  void endDrag() { if (state == State.DRAGGING) state = State.CONTROLS_VISIBLE; }
}
