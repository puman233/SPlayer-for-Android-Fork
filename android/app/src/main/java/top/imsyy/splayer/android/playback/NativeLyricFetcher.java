package top.imsyy.splayer.android.playback;

import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import org.json.JSONArray;
import org.json.JSONObject;

/** Bounded official-lyric fallback using the same embedded API as native audio resolution. */
final class NativeLyricFetcher {
  static final class Result {
    final String lrc, yrc; final boolean terminal;
    Result(String lrc, String yrc, boolean terminal) { this.lrc = lrc; this.yrc = yrc; this.terminal = terminal; }
    boolean hasLyrics() { return !"[]".equals(lrc) || !"[]".equals(yrc); }
  }
  private static final class Entry {
    final long created; final Result result;
    Entry(Result result) { this.result = result; created = android.os.SystemClock.elapsedRealtime(); }
  }
  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final ExecutorService cancellationExecutor = Executors.newSingleThreadExecutor();
  private final LinkedHashMap<Long, Entry> cache = new LinkedHashMap<Long, Entry>(32, .75f, true) {
    @Override protected boolean removeEldestEntry(java.util.Map.Entry<Long, NativeLyricFetcher.Entry> entry) { return size() > 32; }
  };
  private String base = "", cookie = "";
  private long revision;
  private HttpURLConnection active;
  synchronized boolean updateContext(String newBase, String newCookie) {
    if (base.equals(newBase) && cookie.equals(newCookie)) return false;
    base = newBase; cookie = newCookie; cache.clear();
    invalidatePending();
    return true;
  }
  synchronized void invalidatePending() {
    revision++;
    if (active != null) {
      HttpURLConnection connection = active;
      active = null;
      // Disconnect on a separate worker: the request executor may be blocked in read().
      cancellationExecutor.execute(connection::disconnect);
    }
  }
  synchronized boolean available() { return !base.isEmpty(); }
  void request(long songId, long duration, Consumer<Result> callback) {
    final String requestBase, requestCookie; final long token;
    synchronized (this) {
      requestBase = base; requestCookie = cookie; token = revision;
      Entry cached = cache.get(songId);
      if (cached != null && android.os.SystemClock.elapsedRealtime() - cached.created < 600000) { callback.accept(cached.result); return; }
    }
    executor.execute(() -> {
      HttpURLConnection connection = null; Result result = null;
      try {
        synchronized (this) { if (token != revision) return; }
        connection = (HttpURLConnection) new URL(requestBase.replaceAll("/+$", "") + "/lyric/new?id=" + songId).openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(8000); connection.setReadTimeout(8000);
        connection.setRequestProperty("Accept", "application/json");
        if (!requestCookie.isEmpty()) connection.setRequestProperty("Cookie", requestCookie);
        synchronized (this) { if (token != revision) return; active = connection; }
        if (connection.getResponseCode() != 200) throw new java.io.IOException("Lyric response unavailable");
        StringBuilder body = new StringBuilder(); char[] buffer = new char[8192];
        try (BufferedReader input = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
          int count; while ((count = input.read(buffer)) != -1) {
            if (body.length() + count > 2000000) throw new java.io.IOException("Lyric response too large");
            body.append(buffer, 0, count);
          }
        }
        JSONObject data = new JSONObject(body.toString());
        if (data.optInt("code", 200) != 200) throw new java.io.IOException("Lyric API unavailable");
        result = new Result(encode(NativeLyricParser.lrc(raw(data, "lrc"), raw(data, "tlyric"), duration)),
            encode(NativeLyricParser.yrc(raw(data, "yrc"), raw(data, "ytlrc").isEmpty() ? raw(data, "tlyric") : raw(data, "ytlrc"), duration)),
            data.optBoolean("nolyric") || data.optBoolean("uncollected"));
        synchronized (this) { if (token == revision && (result.hasLyrics() || result.terminal)) cache.put(songId, new Entry(result)); }
      } catch (Exception error) {
        // Credentials and URLs must never appear in diagnostics.
        Log.w("NativeLyric", "Fallback unavailable: " + error.getClass().getSimpleName());
      } finally {
        if (connection != null) connection.disconnect();
        synchronized (this) { if (active == connection) active = null; if (token != revision) result = null; }
        callback.accept(result);
      }
    });
  }
  private static String raw(JSONObject data, String name) { JSONObject value = data.optJSONObject(name); return value == null ? "" : value.optString("lyric", ""); }
  private static String encode(List<NativeLyricParser.Line> lines) throws Exception {
    JSONArray result = new JSONArray();
    for (var line : lines) {
      JSONArray words = new JSONArray();
      for (var word : line.words) words.put(new JSONObject().put("word", word.text).put("startTime", word.start).put("endTime", word.end));
      result.put(new JSONObject().put("startTime", line.start).put("endTime", line.end).put("words", words).put("translatedLyric", line.translation));
    }
    return result.toString();
  }
}
