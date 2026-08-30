package com.setsuna.ui.hud;

import com.setsuna.event.events.Render2DEvent;
import com.setsuna.module.Category;
import com.setsuna.render.SkijaRenderer;
import com.setsuna.render.SkijaUi;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.ColorSetting;
import com.setsuna.setting.settings.DoubleSetting;
import com.setsuna.setting.settings.EnumSetting;
import com.setsuna.setting.settings.IntSetting;
import com.setsuna.ui.UiTheme;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.SamplingMode;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.resources.Identifier;

import java.awt.Color;
import java.util.Locale;

/** Player portrait and elapsed time for the current world or server connection. */
public final class SessionInfoHUD extends EpsilonHudModule {

    public static final SessionInfoHUD INSTANCE = new SessionInfoHUD();

    private static final Paint SKIN_PAINT = new Paint().setAntiAlias(false);

    public final DoubleSetting scale = add(new DoubleSetting("Scale", 1.0, 0.65, 1.8, 0.05));
    public final BooleanSetting showServerIp = add(new BooleanSetting("Show Server IP", false));
    public final BooleanSetting background = add(new BooleanSetting("Background", true));
    public final ColorSetting backgroundColor = add(new ColorSetting("Background Color",
            new Color(UiTheme.withAlpha(UiTheme.SURFACE, 208), true), true)
            .visibleWhen(background::get));
    public final BooleanSetting blur = add(new BooleanSetting("Blur", false));
    public final IntSetting blurStrength = add(new IntSetting("Blur Strength", 6, 1, 16, 1)
            .visibleWhen(blur::get));
    public final BooleanSetting border = add(new BooleanSetting("Border", true));
    public final DoubleSetting borderWidth = add(new DoubleSetting(
            "Border Width", 1.2, 0.5, 4.0, 0.1).visibleWhen(border::get));
    public final DoubleSetting borderRadius = add(new DoubleSetting(
            "Border Radius", 6.0, 0.0, 16.0, 0.5)
            .visibleWhen(() -> background.get() || border.get() || blur.get()));
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

    private Object observedConnection;
    private long sessionStartMillis;

    private SessionInfoHUD() {
        super("Session Info", Category.RENDER, 22, 700, 122.0F, 55.0F);
    }

    @Override
    protected void renderHud(Render2DEvent event) {
        if (noPlayer()) {
            observedConnection = null;
            sessionStartMillis = 0L;
            return;
        }
        updateSessionStart();

        float s = scale.get().floatValue();
        float padding = 6.0F * s;
        float avatarSize = 28.0F * s;
        float gap = 7.0F * s;
        float timeSize = 9.2F * s;
        float serverSize = 6.4F * s;
        float titleSize = 6.8F * s;
        String elapsed = elapsedText();
        String server = serverText();
        boolean showServer = showServerIp.get();
        float textWidth = Math.max(SkijaUi.boldTextWidth(elapsed, timeSize),
                SkijaUi.boldTextWidth("SESSION INFO", titleSize));
        if (showServer) textWidth = Math.max(textWidth, SkijaUi.textWidth(server, serverSize));
        textWidth = Math.min(textWidth, 142.0F * s);
        float width = padding * 2.0F + avatarSize + gap + textWidth;
        float height = 55.0F * s;
        float x = renderX(event, width);
        float y = renderY(event, height);
        float radius = Math.min(borderRadius.get().floatValue() * s, height * 0.5F);
        updateBounds(width, height);
        if (!HudFusionManager.isFused(this, event)) {
            if (blur.get()) {
                HudRenderUtil.blur(event.canvas(), x, y, width, height, radius,
                        blurStrength.get() * 0.55F);
            }
            if (background.get()) {
                HudRenderUtil.coloredSurface(event.canvas(), x, y, width, height, radius,
                        backgroundColor.argb(), HudFusionManager.Edges.NONE);
            }
            if (border.get()) {
                HudRenderUtil.border(event.canvas(), x, y, width, height, radius,
                        borderWidth.get().floatValue() * s, 1.0F, borderMode.get(),
                        borderColor.argb(), borderStart.argb(), borderEnd.argb(), 255);
            }
        }

        float headerHeight = 17.0F * s;
        SkijaUi.boldText(event.canvas(), "SESSION INFO", x + padding, y,
                headerHeight, UiTheme.TEXT, titleSize);
        SkijaUi.fill(event.canvas(), x + padding, y + headerHeight - 1.0F * s,
                width - padding * 2.0F, 1.0F * s,
                UiTheme.withAlpha(UiTheme.accent(), 150));

        float contentY = y + headerHeight;
        float contentHeight = height - headerHeight;
        float avatarX = x + padding;
        float avatarY = contentY + (contentHeight - avatarSize) * 0.5F;
        drawAvatar(event.canvas(), avatarX, avatarY, avatarSize, 5.0F * s);
        float textX = avatarX + avatarSize + gap;
        if (showServer) {
            SkijaUi.boldText(event.canvas(), elapsed, textX, contentY + 2.0F * s,
                    17.0F * s, UiTheme.TEXT, timeSize);
            SkijaUi.text(event.canvas(), HudRenderUtil.fit(server, textWidth, serverSize, false),
                    textX, contentY + 19.0F * s, 12.0F * s,
                    UiTheme.TEXT_MUTED, serverSize);
        } else {
            SkijaUi.boldText(event.canvas(), elapsed, textX, contentY, contentHeight,
                    UiTheme.TEXT, timeSize);
        }
    }

    private void updateSessionStart() {
        Object connection = mc.getConnection() == null ? mc.level : mc.getConnection();
        if (observedConnection == connection && sessionStartMillis > 0L) return;
        observedConnection = connection;
        long elapsedTicks = Math.max(0L, mc.player.tickCount);
        sessionStartMillis = System.currentTimeMillis() - elapsedTicks * 50L;
    }

    private String elapsedText() {
        long seconds = Math.max(0L, (System.currentTimeMillis() - sessionStartMillis) / 1000L);
        long hours = seconds / 3_600L;
        long minutes = seconds / 60L % 60L;
        long remainingSeconds = seconds % 60L;
        return String.format(Locale.ROOT, "%dH %02dM %02dS",
                hours, minutes, remainingSeconds);
    }

    private String serverText() {
        ServerData server = mc.getCurrentServer();
        return server == null || server.ip == null || server.ip.isBlank()
                ? "Singleplayer" : server.ip;
    }

    private void drawAvatar(Canvas canvas, float x, float y, float size, float radius) {
        int save = canvas.save();
        canvas.clipRRect(RRect.makeXYWH(x, y, size, size, radius), true);
        try {
            Identifier skin = mc.player.getSkin() == null || mc.player.getSkin().body() == null
                    ? null : mc.player.getSkin().body().texturePath();
            if (skin == null) {
                SkijaUi.fill(canvas, x, y, size, size, UiTheme.accent());
                return;
            }
            try (SkijaRenderer.BorrowedImage borrowed = SkijaRenderer.borrowTexture(skin)) {
                if (borrowed == null) {
                    SkijaUi.fill(canvas, x, y, size, size, UiTheme.accent());
                    return;
                }
                drawSkinLayer(canvas, borrowed, x, y, size, 8.0F, 8.0F);
                drawSkinLayer(canvas, borrowed, x, y, size, 40.0F, 8.0F);
            }
        } catch (Throwable ignored) {
            SkijaUi.fill(canvas, x, y, size, size, UiTheme.accent());
        } finally {
            canvas.restoreToCount(save);
        }
    }

    private static void drawSkinLayer(Canvas canvas, SkijaRenderer.BorrowedImage borrowed,
                                      float x, float y, float size,
                                      float sourceX, float sourceY) {
        float textureWidth = borrowed.image().getWidth();
        float textureHeight = borrowed.image().getHeight();
        Rect source = Rect.makeLTRB(
                sourceX / 64.0F * textureWidth,
                sourceY / 64.0F * textureHeight,
                (sourceX + 8.0F) / 64.0F * textureWidth,
                (sourceY + 8.0F) / 64.0F * textureHeight);
        canvas.drawImageRect(borrowed.image(), source, Rect.makeXYWH(x, y, size, size),
                SamplingMode.DEFAULT, SKIN_PAINT, true);
    }

    @Override
    public int editorColor() {
        return borderColor.argb();
    }

    @Override
    public boolean supportsHudFusion() {
        return true;
    }

    @Override
    public boolean hudFusionBorderEnabled() {
        return border.get();
    }

    @Override
    public boolean hudFusionBackgroundEnabled() {
        return background.get();
    }

    @Override
    public HudFusionManager.FusionStyle hudFusionStyle() {
        float s = scale.get().floatValue();
        return new HudFusionManager.FusionStyle(background.get(),
                backgroundColor.argb(), blur.get(),
                blurStrength.get() * 0.55F, false, 0.0F, border.get(),
                borderRadius.get().floatValue() * s,
                borderWidth.get().floatValue() * s, borderMode.get(),
                borderColor.argb(), borderStart.argb(), borderEnd.argb());
    }
}
