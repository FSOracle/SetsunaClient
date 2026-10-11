package com.setsuna.ui.screen;

import com.setsuna.render.SkijaUi;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.ClipMode;
import io.github.humbleui.skija.FilterTileMode;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.ImageFilter;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.SamplingMode;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;

/** Real background-image blur, cached across frames and shared by all glass controls. */
final class ScreenGlass {
    private static final int SHADE = 0x5804070A;
    private static final float BLUR_SIGMA = 7.0F;
    private static Image cachedSource;
    private static Image cachedBlur;
    private static float cachedWidth;
    private static float cachedHeight;

    private ScreenGlass() {}

    static Frame drawBackground(Canvas canvas, float width, float height) {
        Frame frame = prepare(ScreenBackdrop.glassBackground(), width, height);
        frame.drawBackground(canvas);
        return frame;
    }

    static Frame prepare(Image source, float width, float height) {
        width = Math.max(1, width);
        height = Math.max(1, height);
        if (source != cachedSource || width != cachedWidth || height != cachedHeight || cachedBlur == null) {
            Image replacement = blur(source, width, height);
            if (cachedBlur != null) cachedBlur.close();
            cachedBlur = replacement;
            cachedSource = source;
            cachedWidth = width;
            cachedHeight = height;
        }
        return new Frame(source, cachedBlur, width, height);
    }

    private static Image blur(Image source, float width, float height) {
        // The image is static: filter once per viewport/image change, never once per widget/frame.
        float rasterScale = Math.min(1, 960.0F / Math.max(width, height));
        int rasterWidth = Math.max(1, Math.round(width * rasterScale));
        int rasterHeight = Math.max(1, Math.round(height * rasterScale));
        try (Surface raw = Surface.makeRasterN32Premul(rasterWidth, rasterHeight);
             Surface filtered = Surface.makeRasterN32Premul(rasterWidth, rasterHeight)) {
            Canvas input = raw.getCanvas();
            input.scale(rasterWidth / width, rasterHeight / height);
            drawPhoto(input, source, width, height);
            try (Image image = raw.makeImageSnapshot();
                 ImageFilter filter = ImageFilter.makeBlur(BLUR_SIGMA * rasterWidth / width,
                         BLUR_SIGMA * rasterHeight / height, FilterTileMode.CLAMP);
                 Paint paint = new Paint().setImageFilter(filter)) {
                filtered.getCanvas().clear(0xFF080B0F);
                filtered.getCanvas().drawImage(image, 0, 0, paint);
            }
            return filtered.makeImageSnapshot();
        }
    }

    private static void drawPhoto(Canvas canvas, Image source, float width, float height) {
        SkijaUi.fill(canvas, 0, 0, width, height, 0xFF080B0F);
        if (source != null) {
            float scale = Math.max(width / source.getWidth(), height / source.getHeight());
            float sourceWidth = width / scale;
            float sourceHeight = height / scale;
            Rect crop = Rect.makeXYWH((source.getWidth() - sourceWidth) * 0.5F,
                    (source.getHeight() - sourceHeight) * 0.5F, sourceWidth, sourceHeight);
            canvas.drawImageRect(source, crop, Rect.makeWH(width, height), SamplingMode.LINEAR, null, true);
        }
        SkijaUi.fill(canvas, 0, 0, width, height, SHADE);
    }

    record Frame(Image source, Image blurred, float width, float height) {
        void drawBackground(Canvas canvas) {
            drawPhoto(canvas, source, width, height);
        }

        void surface(Canvas canvas, UiControls.Box box, float radius) {
            surface(canvas, box, radius, 0x4610171D, 0x50D9E7EA);
        }

        void surface(Canvas canvas, UiControls.Box box, float radius, int tint, int border) {
            surface(canvas, box, radius, tint, border, 1);
        }

        void surface(Canvas canvas, UiControls.Box box, float radius, int tint, int border, float opacity) {
            opacity = Math.clamp(opacity, 0, 1);
            if (opacity <= 0) return;
            int save = canvas.save();
            try (Paint paint = new Paint().setAlphaf(opacity)) {
                canvas.clipRRect(RRect.makeXYWH(box.x(), box.y(), box.width(), box.height(), radius),
                        ClipMode.INTERSECT, true);
                canvas.drawImageRect(blurred, Rect.makeWH(blurred.getWidth(), blurred.getHeight()),
                        Rect.makeWH(width, height), SamplingMode.LINEAR, paint, true);
                SkijaUi.fill(canvas, box.x(), box.y(), box.width(), box.height(), alpha(tint, opacity));
                SkijaUi.gradient(canvas, box.x(), box.y(), box.width(), box.height(),
                        alpha(0x0CFFFFFF, opacity), 0x00FFFFFF, true, 0);
            } finally {
                canvas.restoreToCount(save);
            }
            if ((border >>> 24) > 0) {
                SkijaUi.outline(canvas, box.x(), box.y(), box.width(), box.height(), radius, 0.7F,
                        alpha(border, opacity));
            }
        }

        private static int alpha(int color, float opacity) {
            return (color & 0xFFFFFF) | (Math.round((color >>> 24) * opacity) << 24);
        }
    }
}
