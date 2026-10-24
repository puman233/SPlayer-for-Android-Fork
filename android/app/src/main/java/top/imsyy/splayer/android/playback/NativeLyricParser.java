package top.imsyy.splayer.android.playback;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Minimal official LRC/YRC fallback, independent of the WebView and its timers. */
final class NativeLyricParser {
  private static final Pattern TIME = Pattern.compile("\\[(\\d{1,4}):(\\d{2})(?:[.:](\\d{1,3}))?]");
  private static final Pattern OFFSET = Pattern.compile("\\[offset:([+-]?\\d+)]", Pattern.CASE_INSENSITIVE);
  private static final Pattern YRC_LINE = Pattern.compile("^\\[(\\d+),(\\d+)](.*)$");
  private static final Pattern YRC_WORD = Pattern.compile("\\((\\d+),(\\d+),\\d+\\)(.*?)(?=\\(\\d+,\\d+,\\d+\\)|$)");
  static final class Word {
    final String text; final long start, end;
    Word(String text, long start, long end) { this.text = text; this.start = start; this.end = end; }
  }
  static final class Line {
    final long start; long end; final List<Word> words = new ArrayList<>(); String translation = "";
    Line(long start, long end) { this.start = start; this.end = end; }
  }
  static List<Line> lrc(String raw, String translated, long duration) {
    List<Line> lines = new ArrayList<>();
    long offset = 0;
    Matcher offsetMatcher = OFFSET.matcher(raw == null ? "" : raw);
    if (offsetMatcher.find()) try { offset = Long.parseLong(offsetMatcher.group(1)); } catch (NumberFormatException ignored) { }
    for (String source : (raw == null ? "" : raw).split("\\r?\\n")) {
      Matcher times = TIME.matcher(source); List<Long> starts = new ArrayList<>(); int last = 0;
      while (times.find()) {
        int seconds = Integer.parseInt(times.group(2));
        if (seconds >= 60) continue;
        String fraction = times.group(3);
        long millis = fraction == null ? 0 : Long.parseLong((fraction + "000").substring(0, 3));
        starts.add(Math.max(0, Long.parseLong(times.group(1)) * 60000 + seconds * 1000L + millis + offset));
        last = times.end();
      }
      for (long start : starts) {
        Line line = new Line(start, start); line.words.add(new Word(source.substring(last), start, start)); lines.add(line);
      }
    }
    lines.sort(Comparator.comparingLong(line -> line.start));
    for (int i = 0; i < lines.size(); i++) {
      Line line = lines.get(i);
      line.end = Math.max(line.start + 1, i + 1 < lines.size() ? lines.get(i + 1).start : (duration > line.start ? duration : line.start + 5000));
      String text = line.words.get(0).text; line.words.set(0, new Word(text, line.start, line.end));
    }
    if (translated != null && !translated.isEmpty()) {
      Map<Long, String> translations = translations(translated, duration);
      for (Line line : lines) line.translation = translations.getOrDefault(line.start, "");
    }
    return lines;
  }
  private static Map<Long, String> translations(String raw, long duration) {
    Map<Long, String> map = new HashMap<>();
    for (Line line : lrc(raw, null, duration)) map.put(line.start, line.words.get(0).text);
    return map;
  }
  static List<Line> yrc(String raw, String translated, long duration) {
    List<Line> lines = new ArrayList<>(); Map<Long, String> translations = translations(translated, duration);
    for (String source : (raw == null ? "" : raw).split("\\r?\\n")) {
      Matcher header = YRC_LINE.matcher(source); if (!header.matches()) continue;
      try {
        long start = Long.parseLong(header.group(1)), length = Long.parseLong(header.group(2));
        Line line = new Line(start, Math.addExact(start, length)); Matcher words = YRC_WORD.matcher(header.group(3));
        while (words.find()) {
          long wordStart = Long.parseLong(words.group(1)), wordEnd = Math.addExact(wordStart, Long.parseLong(words.group(2)));
          line.words.add(new Word(words.group(3), wordStart, wordEnd));
        }
        if (!line.words.isEmpty()) { line.translation = translations.getOrDefault(start, ""); lines.add(line); }
      } catch (NumberFormatException | ArithmeticException ignored) { }
    }
    lines.sort(Comparator.comparingLong(line -> line.start)); return lines;
  }
  private NativeLyricParser() { }
}
