package com.setsuna.ui.hud;

import com.setsuna.event.events.Render2DEvent;
import com.setsuna.render.SkijaRenderer;
import com.setsuna.setting.settings.DoubleSetting;
import com.setsuna.setting.settings.EnumSetting;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.SamplingMode;
import io.github.humbleui.types.Rect;
import net.minecraft.resources.Identifier;

/** A small animated image HUD with three selectable pig illustrations. */
public final class PigHUD extends HUDModuel {

    public static final PigHUD INSTANCE = new PigHUD();

    private static final Identifier[] PIG_ONE = frames("one", 96);
    private static final Identifier[] PIG_TWO = frames("two", 24);
    private static final Identifier[] PIG_THREE = frames("three", 19);
    private static final Paint IMAGE_PAINT = new Paint().setAntiAlias(true);

    public enum PigStyle {
        ONE,
        TWO,
        THREE
    }

    public final EnumSetting<PigStyle> pigStyle = add(new EnumSetting<>(
            "Pig Style", PigStyle.ONE));
    public final DoubleSetting scale = add(new DoubleSetting(
            "Scale", 1.0, 0.5, 2.0, 0.05));

    private PigHUD() {
        super("Pig HUD", 500, 470, 88.0F, 88.0F);
    }

    @Override
    protected void renderHud(Render2DEvent event) {
        if (noPlayer()) return;
        float size = 88.0F * scale.get().floatValue();
        float x = renderX(event, size);
        float y = renderY(event, size);
        updateBounds(size, size);
        Identifier[] animation = animationFrames();
        int frameDuration = pigStyle.is(PigStyle.THREE) ? 20 : 30;
        int frame = (int) (System.currentTimeMillis() / frameDuration % animation.length);
        Identifier texture = animation[frame];
        try (SkijaRenderer.BorrowedImage image = SkijaRenderer.borrowTexture(texture)) {
            if (image == null) return;
            IMAGE_PAINT.setAlpha(255);
            event.canvas().drawImageRect(image.image(),
                    Rect.makeXYWH(0.0F, 0.0F, image.image().getWidth(), image.image().getHeight()),
                    Rect.makeXYWH(x, y, size, size), SamplingMode.MITCHELL,
                    IMAGE_PAINT, true);
        } catch (Throwable ignored) {
            // A missing resource should not interrupt the rest of the HUD render pass.
        }
    }

    private Identifier[] animationFrames() {
        return switch (pigStyle.get()) {
            case ONE -> PIG_ONE;
            case TWO -> PIG_TWO;
            case THREE -> PIG_THREE;
        };
    }

    private static Identifier[] frames(String style, int count) {
        Identifier[] result = new Identifier[count];
        for (int index = 0; index < count; index++) {
            result[index] = Identifier.fromNamespaceAndPath("setsuna",
                    "textures/hud/pig/" + style + "/frame_"
                            + String.format(java.util.Locale.ROOT, "%03d", index) + ".png");
        }
        return result;
    }
}
