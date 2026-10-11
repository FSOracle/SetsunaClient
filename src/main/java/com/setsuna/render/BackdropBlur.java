package com.setsuna.render;

import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.FilterTileMode;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.ImageFilter;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.SamplingMode;
import io.github.humbleui.types.Rect;

import java.util.function.Consumer;

/** Draws a captured screen image through a component's current transformed clip. */
final class BackdropBlur {
    private BackdropBlur() {
    }

    static void draw(Canvas canvas, Consumer<Canvas> clipper, Image snapshot,
                     int targetWidth, int targetHeight, float guiWidth, float guiHeight,
                     float strength) {
        if (targetWidth <= 0 || targetHeight <= 0 || guiWidth <= 0.0F || guiHeight <= 0.0F
                || strength <= 0.01F) return;
        int save = canvas.save();
        try {
            // Keep the component's transformed clip, then sample in screen coordinates.
            clipper.accept(canvas);
            canvas.resetMatrix();
            canvas.scale(targetWidth / guiWidth, targetHeight / guiHeight);
            Rect bounds = canvas.getLocalClipBounds();
            float scaleX = snapshot.getWidth() / guiWidth;
            float scaleY = snapshot.getHeight() / guiHeight;
            float padding = Math.max(2.0F, strength * 2.5F);
            int left = Math.max(0, (int) Math.floor((bounds.getLeft() - padding) * scaleX));
            int top = Math.max(0, (int) Math.floor((bounds.getTop() - padding) * scaleY));
            int right = Math.min(snapshot.getWidth(),
                    (int) Math.ceil((bounds.getRight() + padding) * scaleX));
            int bottom = Math.min(snapshot.getHeight(),
                    (int) Math.ceil((bounds.getBottom() + padding) * scaleY));
            if (right <= left || bottom <= top) return;

            float sigma = Math.max(0.5F, strength / 2.0F);
            try (ImageFilter filter = ImageFilter.makeBlur(sigma, sigma, FilterTileMode.CLAMP);
                 Paint paint = new Paint().setAntiAlias(true).setImageFilter(filter)) {
                Rect source = Rect.makeLTRB(left, top, right, bottom);
                Rect destination = Rect.makeLTRB(left / scaleX, top / scaleY,
                        right / scaleX, bottom / scaleY);
                canvas.drawImageRect(snapshot, source, destination, SamplingMode.LINEAR, paint, true);
            }
        } finally {
            canvas.restoreToCount(save);
        }
    }
}
