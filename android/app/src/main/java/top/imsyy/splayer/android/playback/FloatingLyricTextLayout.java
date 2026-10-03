package top.imsyy.splayer.android.playback;

import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextDirectionHeuristics;
import android.text.TextPaint;

/** 完整行塑形与字宽缓存，滚动帧只更新偏移和高亮边界 */
final class FloatingLyricTextLayout {
  final TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
  final StaticLayout layout;
  final float width;
  final boolean rtl;
  private FloatingLyricService.Line measuredLine;
  float[] wordWidths;
  private final Matrix shaderMatrix = new Matrix();
  private LinearGradient highlightShader;
  private int previousPlayed, previousUnplayed;
  private float previousDensity;

  FloatingLyricTextLayout(String text, float size, int weight) {
    paint.setTextSize(size);
    paint.setTypeface(Typeface.create(Typeface.DEFAULT, weight >= 600 ? Typeface.BOLD : Typeface.NORMAL));
    width = Math.max(1, Layout.getDesiredWidth(text, paint));
    rtl = TextDirectionHeuristics.FIRSTSTRONG_LTR.isRtl(text, 0, text.length());
    layout = StaticLayout.Builder.obtain(text, 0, text.length(), paint, (int) Math.ceil(width) + 1)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).setMaxLines(1)
        .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR).build();
  }

  void prepareWords(FloatingLyricService.Line line) {
    if (measuredLine == line) return;
    measuredLine = line;
    wordWidths = new float[line.words.size()];
    float total = 0;
    for (int i = 0; i < wordWidths.length; i++) {
      wordWidths[i] = paint.measureText(line.words.get(i).text);
      total += wordWidths[i];
    }
    if (total > 0) for (int i = 0; i < wordWidths.length; i++) wordWidths[i] *= width / total;
  }

  void highlight(float boundary, float density, int played, int unplayed) {
    if (highlightShader == null || played != previousPlayed || unplayed != previousUnplayed
        || density != previousDensity) {
      previousPlayed = played;
      previousUnplayed = unplayed;
      previousDensity = density;
      highlightShader = new LinearGradient(-density * 0.5f, 0, density * 0.5f, 0,
          rtl ? unplayed : played, rtl ? played : unplayed, Shader.TileMode.CLAMP);
    }
    shaderMatrix.setTranslate(boundary, 0);
    highlightShader.setLocalMatrix(shaderMatrix);
    paint.setShader(highlightShader);
  }
}
