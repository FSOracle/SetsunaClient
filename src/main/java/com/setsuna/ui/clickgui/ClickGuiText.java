package com.setsuna.ui.clickgui;

import com.setsuna.render.SkijaUi;
import io.github.humbleui.skija.Canvas;

import java.util.ArrayList;
import java.util.List;

/** Text wrapping shared by ClickGUI variants. It never replaces content with an ellipsis. */
public final class ClickGuiText {

    private ClickGuiText() {
    }

    public static List<String> wrap(String text, float maxWidth, float size, boolean bold) {
        String value = text == null ? "" : text.strip();
        if (value.isEmpty() || maxWidth <= 0.0F) return List.of();
        if (width(value, size, bold) <= maxWidth) return List.of(value);

        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        int lastBreak = -1;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            int length = Character.charCount(codePoint);
            line.appendCodePoint(codePoint);
            if (Character.isWhitespace(codePoint)) lastBreak = line.length() - length;

            if (width(line.toString(), size, bold) > maxWidth) {
                if (lastBreak >= 0) {
                    String completed = line.substring(0, lastBreak).stripTrailing();
                    if (!completed.isEmpty()) lines.add(completed);
                    String remainder = line.substring(lastBreak + 1).stripLeading();
                    line.setLength(0);
                    line.append(remainder);
                } else if (line.length() > length) {
                    String completed = line.substring(0, line.length() - length);
                    if (!completed.isEmpty()) lines.add(completed);
                    line.delete(0, line.length() - length);
                }
                lastBreak = lastWhitespace(line);
            }
            offset += length;
        }
        String completed = line.toString().strip();
        if (!completed.isEmpty()) lines.add(completed);
        return lines;
    }

    public static int lineCount(String text, float maxWidth, float size, boolean bold) {
        return Math.max(1, wrap(text, maxWidth, size, bold).size());
    }

    public static void draw(Canvas canvas, String text, float x, float y, float maxWidth,
                     float lineHeight, int color, float size, boolean bold) {
        List<String> lines = wrap(text, maxWidth, size, bold);
        drawLines(canvas, lines, x, y, lineHeight, color, size, bold);
    }

    private static void drawLines(Canvas canvas, List<String> lines, float x, float y,
                                  float lineHeight, int color, float size, boolean bold) {
        for (int index = 0; index < lines.size(); index++) {
            float lineY = y + index * lineHeight;
            if (bold) {
                SkijaUi.boldText(canvas, lines.get(index), x, lineY, lineHeight, color, size);
            } else {
                SkijaUi.text(canvas, lines.get(index), x, lineY, lineHeight, color, size);
            }
        }
    }

    public static void drawVerticallyCentered(Canvas canvas, String text, float x, float y,
                                              float maxWidth, float height, float lineHeight,
                                              int color, float size, boolean bold) {
        List<String> lines = wrap(text, maxWidth, size, bold);
        float top = y + Math.max(0.0F, (height - lines.size() * lineHeight) * 0.5F);
        drawLines(canvas, lines, x, top, lineHeight, color, size, bold);
    }

    public static void drawCentered(Canvas canvas, String text, float x, float y, float width,
                             float height, float lineHeight, int color, float size, boolean bold) {
        List<String> lines = wrap(text, Math.max(1.0F, width), size, bold);
        float top = y + Math.max(0.0F, (height - lines.size() * lineHeight) * 0.5F);
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            float lineWidth = width(line, size, bold);
            float lineX = x + Math.max(0.0F, (width - lineWidth) * 0.5F);
            if (bold) {
                SkijaUi.boldText(canvas, line, lineX, top + index * lineHeight,
                        lineHeight, color, size);
            } else {
                SkijaUi.text(canvas, line, lineX, top + index * lineHeight,
                        lineHeight, color, size);
            }
        }
    }

    public static void drawRight(Canvas canvas, String text, float right, float y, float maxWidth,
                                 float lineHeight, int color, float size, boolean bold) {
        List<String> lines = wrap(text, maxWidth, size, bold);
        drawRightLines(canvas, lines, right, y, lineHeight, color, size, bold);
    }

    private static void drawRightLines(Canvas canvas, List<String> lines, float right, float y,
                                       float lineHeight, int color, float size, boolean bold) {
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            float lineX = right - width(line, size, bold);
            if (bold) {
                SkijaUi.boldText(canvas, line, lineX, y + index * lineHeight,
                        lineHeight, color, size);
            } else {
                SkijaUi.text(canvas, line, lineX, y + index * lineHeight,
                        lineHeight, color, size);
            }
        }
    }

    public static void drawRightCentered(Canvas canvas, String text, float right, float y,
                                         float maxWidth, float height, float lineHeight,
                                         int color, float size, boolean bold) {
        List<String> lines = wrap(text, maxWidth, size, bold);
        float top = y + Math.max(0.0F, (height - lines.size() * lineHeight) * 0.5F);
        drawRightLines(canvas, lines, right, top, lineHeight, color, size, bold);
    }

    private static int lastWhitespace(CharSequence value) {
        for (int index = value.length() - 1; index >= 0; index--) {
            if (Character.isWhitespace(value.charAt(index))) return index;
        }
        return -1;
    }

    private static float width(String text, float size, boolean bold) {
        return bold ? SkijaUi.boldTextWidth(text, size) : SkijaUi.textWidth(text, size);
    }
}
