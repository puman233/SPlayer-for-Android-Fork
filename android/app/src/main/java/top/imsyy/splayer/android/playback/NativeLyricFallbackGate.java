package top.imsyy.splayer.android.playback;

/** Per-track bounded retry and stale-response guard; times use elapsedRealtime, never wall clock. */
final class NativeLyricFallbackGate {
  static final class Ticket {
    final long generation, songId;
    Ticket(long generation, long songId) { this.generation = generation; this.songId = songId; }
  }
  private long generation, songId, due;
  private int attempts;
  private boolean pending;
  void select(long id, long now) { generation++; songId = id; due = now + 1500; attempts = 0; pending = false; }
  Ticket request(long now, boolean eligible, boolean hasLyrics) {
    if (!eligible || hasLyrics || pending || songId <= 0 || attempts >= 2 || now < due) return null;
    pending = true; attempts++; due = now + 30000; return new Ticket(generation, songId);
  }
  boolean current(Ticket ticket) { return ticket != null && ticket.generation == generation && ticket.songId == songId; }
  boolean finish(Ticket ticket, boolean terminal) {
    if (!current(ticket)) return false;
    pending = false; if (terminal) attempts = 2; return true;
  }
}
