package top.imsyy.splayer.android.playback;

import android.content.Context;
import android.os.Looper;
import android.os.SystemClock;
import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Test-only stack samples: method names and timing, never request or account data. */
final class MainThreadTrace implements AutoCloseable {
  private final AtomicBoolean running = new AtomicBoolean(true);
  private final List<String> samples = new ArrayList<>();
  private final Thread worker;
  private final File output;

  MainThreadTrace(Context context, String name) {
    File dir = new File(context.getExternalFilesDir(null), "detail-regression");
    if (!dir.exists() && !dir.mkdirs()) throw new AssertionError("Cannot create trace directory");
    output = new File(dir, name + ".txt");
    Thread main = Looper.getMainLooper().getThread();
    long started = SystemClock.elapsedRealtime();
    worker = new Thread(() -> {
      while (running.get()) {
        long at = SystemClock.elapsedRealtime();
        Thread.State state = main.getState();
        StackTraceElement[] stack = main.getStackTrace();
        StringBuilder line = new StringBuilder().append(at - started).append(' ')
            .append(state).append(" sampleCostMs=")
            .append(SystemClock.elapsedRealtime() - at);
        for (int i = 0; i < Math.min(24, stack.length); i++) line.append("\n  ").append(stack[i]);
        synchronized (samples) { samples.add(line.toString()); }
        try { Thread.sleep(100); }
        catch (InterruptedException done) { return; }
      }
    }, "ui-test-main-stack-sampler");
    worker.setDaemon(true);
    worker.start();
  }

  @Override public void close() throws Exception {
    running.set(false);
    worker.interrupt();
    worker.join(1000);
    try (PrintWriter out = new PrintWriter(output, "UTF-8")) {
      synchronized (samples) { for (String sample : samples) out.println(sample); }
    }
  }
}
