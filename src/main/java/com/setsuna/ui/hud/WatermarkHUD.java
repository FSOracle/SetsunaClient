package com.setsuna.ui.hud;

import com.setsuna.Setsuna;
import com.setsuna.event.events.Render2DEvent;
import com.setsuna.render.SkijaRenderer;
import com.setsuna.render.SkijaUi;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.ColorSetting;
import com.setsuna.setting.settings.DoubleSetting;
import com.setsuna.setting.settings.EnumSetting;
import com.setsuna.setting.settings.IntSetting;
import com.setsuna.setting.settings.StringSetting;
import com.setsuna.ui.UiTheme;
import com.setsuna.ui.clickgui.PopClickGuiScreen;
import com.setsuna.util.render.ColorUtils;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.FilterTileMode;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.ImageFilter;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.SamplingMode;
import io.github.humbleui.types.Rect;
import net.minecraft.resources.Identifier;

import java.awt.Color;

/**
 * Client identity watermark with optional branding, author credit, and shadow.
 */
public final class WatermarkHUD extends EpsilonHudModule {

    private static final Identifier LOGO_TEXTURE = Identifier.fromNamespaceAndPath(
            "setsuna", "textures/hud/watermark_logo.png");
    private static final Paint LOGO_PAINT = new Paint().setAntiAlias(true);

    public static final WatermarkHUD INSTANCE = new WatermarkHUD();

    public final DoubleSetting scale = add(new DoubleSetting("Scale", 1.0, 0.65, 3.2, 0.05));
    public final BooleanSetting devName = add(new BooleanSetting("Dev Name", false));
    public final StringSetting rename = add(new StringSetting("Rename", ""));
    public final ColorSetting textColor = add(new ColorSetting("Text Color",
            new Color(255, 255, 255), false));
    public final BooleanSetting firstCharacterRainbow = add(
            new BooleanSetting("First Character Rainbow", false));
    public final BooleanSetting logo = add(new BooleanSetting("Logo", false));
    public final BooleanSetting logoAntiAlias = add(new BooleanSetting("Logo Anti-Alias", true)
            .visibleWhen(logo::get));
    public final BooleanSetting background = add(new BooleanSetting("Background", true));
    public final ColorSetting backgroundColor = add(new ColorSetting("Background Color",
            new Color(12, 16, 18, 174), true).visibleWhen(background::get));
    public final BooleanSetting border = add(new BooleanSetting("Border", true));
    public final DoubleSetting borderRadius = add(new DoubleSetting(
            "Border Radius", 4.0, 0.0, 14.0, 0.5)
            .visibleWhen(() -> background.get() || border.get()));
    public final EnumSetting<HudRenderUtil.BorderMode> borderMode = add(
            new EnumSetting<>("Border Mode", HudRenderUtil.BorderMode.Single)
                    .visibleWhen(border::get));
    public final ColorSetting borderColor = add(new ColorSetting("Border Color",
            new Color(75, 155, 255), false).visibleWhen(() -> border.get()
            && borderMode.is(HudRenderUtil.BorderMode.Single)));
    public final ColorSetting borderStart = add(new ColorSetting("Border Start",
            new Color(66, 225, 255), false).visibleWhen(() -> border.get()
            && borderMode.is(HudRenderUtil.BorderMode.Gradient)));
    public final ColorSetting borderEnd = add(new ColorSetting("Border End",
            new Color(126, 92, 255), false).visibleWhen(() -> border.get()
            && borderMode.is(HudRenderUtil.BorderMode.Gradient)));
    public final BooleanSetting shadow = add(new BooleanSetting("Shadow", false));
    public final BooleanSetting glow = add(new BooleanSetting("Glow", false));
    public final IntSetting glowStrength = add(new IntSetting("Glow Strength", 8, 1, 10, 1)
            .visibleWhen(glow::get));

    private WatermarkHUD() {
        super("Watermark HUD", 4, 4, 90.0F, 14.0F);
        setEnabled(true);
    }

    @Override
    protected void renderHud(Render2DEvent event) {
        if (noPlayer()) return;
        float s = scale.get().floatValue();
        float font = 11.0F * s;
        float textHeight = font * 1.28F;
        float padX = 5.0F * s;
        float padY = 2.0F * s;

        String customName = rename.get().trim();
        String text = customName.isEmpty() ? "" : customName + " " + Setsuna.VERSION;
        if (devName.get()) {
            text += (text.isEmpty() ? "" : " / ") + Setsuna.DEVELOPER;
        }
        String displayText = text;

        boolean transferLogo = logo.get() && mc.screen instanceof PopClickGuiScreen;
        SkijaRenderer.BorrowedImage logoImage = null;
        if (logo.get() && !transferLogo) {
            try {
                logoImage = SkijaRenderer.borrowTexture(LOGO_TEXTURE);
            } catch (Throwable ignored) {
                // Keep the watermark text visible if the logo resource cannot be loaded.
            }
        }
        try (SkijaRenderer.BorrowedImage borrowed = logoImage) {
            boolean drawLogo = borrowed != null;
            boolean hasLogo = drawLogo || transferLogo;
            float logoSize = textHeight;
            float logoGap = 3.0F * s;
            float logoExtra = hasLogo ? logoSize + logoGap : 0.0F;
            float width = SkijaUi.textWidthWithFallback(displayText, font) + padX * 2.0F + logoExtra;
            float height = textHeight + padY * 2.0F;
            float x = renderX(event, width);
            float y = renderY(event, height);
            float textX = x + padX + logoExtra;
            Rect logoBounds = Rect.makeXYWH(x + padX, y + padY, logoSize, logoSize);
            int color = textColor.argb();
            int rainbowColor = ColorUtils.rainbow(2600L, (color >>> 24) & 0xFF).getRGB();
            boolean rainbowFirst = firstCharacterRainbow.get();
            boolean antialiasLogo = logoAntiAlias.get();
            float radius = Math.min(borderRadius.get().floatValue() * s, height * 0.5F);
            updateBounds(width, height);

            if (background.get()) {
                HudRenderUtil.coloredSurface(event.canvas(), x, y, width, height,
                        radius, backgroundColor.argb(), HudFusionManager.Edges.NONE);
            }
            if (border.get()) {
                HudRenderUtil.border(event.canvas(), x, y, width, height,
                        radius, 1.2F * s,
                        1.0F, borderMode.get(), borderColor.argb(), borderStart.argb(),
                        borderEnd.argb(), 255);
            }
            Runnable drawContent = () -> {
                if (drawLogo) drawLogo(event.canvas(), borrowed.image(), logoBounds, antialiasLogo);
                drawWatermarkText(event.canvas(), displayText, textX, y + padY,
                        textHeight, font, color, rainbowColor, rainbowFirst, false);
            };
            if (glow.get()) {
                SkijaUi.glowLayer(event.canvas(), x, y, width, height,
                        4.0F * s, glowStrength.get(), drawContent);
            }
            if (shadow.get()) {
                if (drawLogo) {
                    drawLogoShadow(event.canvas(), borrowed.image(), logoBounds, s, antialiasLogo);
                    drawLogo(event.canvas(), borrowed.image(), logoBounds, antialiasLogo);
                }
                drawWatermarkText(event.canvas(), displayText, textX, y + padY,
                        textHeight, font, color, rainbowColor, rainbowFirst, true);
            } else {
                drawContent.run();
            }
        }
    }

    private static void drawWatermarkText(Canvas canvas, String text, float x, float y,
                                          float height, float fontSize, int color,
                                          int rainbowColor, boolean rainbowFirst,
                                          boolean shadow) {
        if (!rainbowFirst || text.isEmpty()) {
            if (shadow) {
                SkijaUi.textShadowWithFallback(canvas, text, x, y, height, color, fontSize);
            } else {
                SkijaUi.textWithFallback(canvas, text, x, y, height, color, fontSize);
            }
            return;
        }

        int firstEnd = text.offsetByCodePoints(0, 1);
        String first = text.substring(0, firstEnd);
        String rest = text.substring(firstEnd);
        if (shadow) {
            SkijaUi.textShadowWithFallback(canvas, first, x, y, height, rainbowColor, fontSize);
        } else {
            SkijaUi.textWithFallback(canvas, first, x, y, height, rainbowColor, fontSize);
        }
        float restX = x + SkijaUi.textWidthWithFallback(first, fontSize);
        if (shadow) {
            SkijaUi.textShadowWithFallback(canvas, rest, restX, y, height, color, fontSize);
        } else {
            SkijaUi.textWithFallback(canvas, rest, restX, y, height, color, fontSize);
        }
    }

    private static void drawLogo(Canvas canvas, Image image, Rect bounds, boolean antiAlias) {
        Rect source = Rect.makeXYWH(0.0F, 0.0F, image.getWidth(), image.getHeight());
        LOGO_PAINT.setAntiAlias(antiAlias).setColor(0xFFFFFFFF).setAlpha(255);
        if (!antiAlias) {
            LOGO_PAINT.setImageFilter(null);
            canvas.drawImageRect(image, source, bounds, SamplingMode.DEFAULT, LOGO_PAINT, true);
            return;
        }
        try (ImageFilter filter = ImageFilter.makeBlur(0.32F, 0.32F, FilterTileMode.DECAL)) {
            LOGO_PAINT.setImageFilter(filter);
            canvas.drawImageRect(image, source, bounds, SamplingMode.MITCHELL, LOGO_PAINT, true);
        } finally {
            LOGO_PAINT.setImageFilter(null).setAlpha(255);
        }
    }

    private static void drawLogoShadow(Canvas canvas, Image image, Rect bounds, float scale,
                                       boolean antiAlias) {
        float offset = Math.max(0.7F, scale);
        float sigma = Math.max(0.35F, scale * 0.45F);
        Rect source = Rect.makeXYWH(0.0F, 0.0F, image.getWidth(), image.getHeight());
        try (ImageFilter filter = ImageFilter.makeDropShadowOnly(
                offset, offset, sigma, sigma, 0xA0000000)) {
            LOGO_PAINT.setAntiAlias(antiAlias).setImageFilter(filter)
                    .setColor(0xFFFFFFFF).setAlpha(255);
            canvas.drawImageRect(image, source, bounds,
                    antiAlias ? SamplingMode.MITCHELL : SamplingMode.DEFAULT,
                    LOGO_PAINT, true);
        } finally {
            LOGO_PAINT.setImageFilter(null).setAlpha(255);
        }
    }

    @Override
    public int editorColor() {
        return textColor.argb();
    }
}
