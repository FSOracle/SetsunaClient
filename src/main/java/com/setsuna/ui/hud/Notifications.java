package com.setsuna.ui.hud;

import com.setsuna.event.events.Render2DEvent;
import com.setsuna.module.Category;
import com.setsuna.notification.NotificationManager;
import com.setsuna.render.SkijaUi;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.EnumSetting;
import com.setsuna.setting.settings.IntSetting;
import com.setsuna.ui.UiTheme;

import java.util.List;

/** Compact notification stack with one consistent presentation. */
public final class Notifications extends EpsilonHudModule {

    public static final Notifications INSTANCE = new Notifications();

    private static final float BASE_WIDTH = 184.0F;
    private static final float BASE_HEIGHT = 30.0F;
    private static final float GAP = 4.0F;
    private static final int EXIT_TIME = 220;

    public enum Position { TOP_CENTER, TOP_RIGHT, TOP_LEFT, BOTTOM_RIGHT, BOTTOM_LEFT, CUSTOM }

    public final BooleanSetting moduleState = add(new BooleanSetting("Module State", true));
    public final BooleanSetting moduleActions = add(new BooleanSetting("Module Actions", true));
    public final IntSetting duration = add(new IntSetting("Display Time", 2000, 500, 5000, 100));
    public final IntSetting maxVisible = add(new IntSetting("Max Visible", 4, 1, 6, 1));
    public final EnumSetting<Position> position = add(new EnumSetting<>("Position", Position.TOP_CENTER));
    public final IntSetting scalePercent = add(new IntSetting("Scale", 100, 60, 160, 5));

    private Notifications() {
        super("Notifications", Category.HUD, 500, 0, BASE_WIDTH, BASE_HEIGHT);
        setEnabled(true);
    }

    @Override
    protected void onDisable() {
        NotificationManager.INSTANCE.clear();
    }

    @Override
    protected void renderHud(Render2DEvent event) {
        float scale = scalePercent.get() / 100.0F;
        float width = Math.min(BASE_WIDTH * scale, event.width() - 12.0F);
        float height = BASE_HEIGHT * scale;
        if (width < 92.0F || event.height() < height + 12.0F) return;
        int capacity = Math.max(1, (int) ((event.height() - 12.0F) / (height + GAP * scale)));
        List<NotificationManager.Entry> entries = NotificationManager.INSTANCE.visible(
                duration.get() + EXIT_TIME, Math.min(maxVisible.get(), capacity));
        updateBounds(width, height);
        if (entries.isEmpty()) return;

        float x = startX(event.width(), width);
        float startY = startY(event.height(), height, entries.size(), scale);
        float direction = stacksUpward() ? -1.0F : 1.0F;
        long now = System.currentTimeMillis();
        for (int index = 0; index < entries.size(); index++) {
            NotificationManager.Entry entry = entries.get(index);
            float y = startY + index * (height + GAP * scale) * direction;
            float progress = animationProgress(entry, now);
            if (progress <= 0.01F) continue;
            drawEntry(event, entry, x, y, width, height, scale, progress, now);
        }
    }

    private void drawEntry(Render2DEvent event, NotificationManager.Entry entry, float x, float y,
                           float width, float height, float scale, float progress, long now) {
        float slide = (1.0F - ease(progress)) * (width + 10.0F);
        float drawX = docksLeft() ? x - slide : x + slide;
        int alpha = Math.round(255.0F * progress);
        int accent = withAlpha(entry.type().color(), alpha);
        HudRenderUtil.panel(event.canvas(), drawX, y, width, height, Math.round(184.0F * progress));
        SkijaUi.fill(event.canvas(), drawX + 4.0F * scale, y + 5.0F * scale,
                2.0F * scale, height - 10.0F * scale, accent);

        String glyph = switch (entry.type()) {
            case SUCCESS -> "+";
            case ERROR -> "x";
            case WARNING -> "!";
            case INFO -> "i";
        };
        float glyphSize = 7.6F * scale;
        float glyphWidth = SkijaUi.boldTextWidth(glyph, glyphSize);
        SkijaUi.boldText(event.canvas(), glyph, drawX + 10.0F * scale - glyphWidth * 0.5F,
                y, height, accent, glyphSize);

        float textX = drawX + 17.0F * scale;
        float textWidth = width - 23.0F * scale;
        String title = HudRenderUtil.fit(entry.title(), textWidth, 7.8F * scale, true);
        String message = HudRenderUtil.fit(entry.message(), textWidth, 6.7F * scale, false);
        float textBlockY = y + (height - 22.0F * scale) * 0.5F;
        SkijaUi.boldText(event.canvas(), title, textX, textBlockY,
                12.0F * scale, withAlpha(UiTheme.TEXT, alpha), 7.8F * scale);
        SkijaUi.text(event.canvas(), message, textX, textBlockY + 12.0F * scale,
                10.0F * scale, withAlpha(UiTheme.TEXT_MUTED, Math.round(220.0F * progress)),
                6.7F * scale);

        float remaining = 1.0F - Math.min(1.0F,
                Math.max(0L, now - entry.visibleAt()) / (float) Math.max(1, duration.get()));
        SkijaUi.fill(event.canvas(), drawX + 5.0F * scale, y + height - 2.0F * scale,
                (width - 10.0F * scale) * remaining, Math.max(1.0F, scale), accent);
    }

    private float animationProgress(NotificationManager.Entry entry, long now) {
        long elapsed = Math.max(0L, now - entry.visibleAt());
        if (elapsed < 190L) return elapsed / 190.0F;
        if (elapsed <= duration.get()) return 1.0F;
        return 1.0F - Math.min(1.0F, (elapsed - duration.get()) / (float) EXIT_TIME);
    }

    private float startX(float screenWidth, float width) {
        return switch (position.get()) {
            case TOP_CENTER -> (screenWidth - width) * 0.5F;
            case TOP_LEFT, BOTTOM_LEFT -> 6.0F;
            case TOP_RIGHT, BOTTOM_RIGHT -> screenWidth - width - 6.0F;
            case CUSTOM -> HudRenderUtil.normalizedPosition(xPosition, screenWidth, width);
        };
    }

    private float startY(float screenHeight, float height, int count, float scale) {
        return switch (position.get()) {
            case TOP_CENTER, TOP_LEFT, TOP_RIGHT -> 6.0F;
            case BOTTOM_LEFT, BOTTOM_RIGHT -> screenHeight - height - 6.0F;
            case CUSTOM -> {
                float y = HudRenderUtil.normalizedPosition(yPosition, screenHeight, height);
                float stack = (count - 1) * (height + GAP * scale);
                yield stacksUpward()
                        ? HudRenderUtil.clamp(y, 6.0F + stack, screenHeight - height - 6.0F)
                        : HudRenderUtil.clamp(y, 6.0F,
                        Math.max(6.0F, screenHeight - height - 6.0F - stack));
            }
        };
    }

    public void useCustomPosition() {
        position.set(Position.CUSTOM);
    }

    private boolean stacksUpward() {
        return position.is(Position.BOTTOM_LEFT) || position.is(Position.BOTTOM_RIGHT)
                || position.is(Position.CUSTOM) && yPosition.get() > 700;
    }

    private boolean docksLeft() {
        return position.is(Position.TOP_LEFT) || position.is(Position.BOTTOM_LEFT)
                || position.is(Position.CUSTOM) && xPosition.get() < 500;
    }

    private static float ease(float value) {
        float x = HudRenderUtil.clamp(value, 0.0F, 1.0F);
        return 1.0F - (float) Math.pow(1.0F - x, 3.0);
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    @Override
    public int editorColor() {
        return UiTheme.INFO;
    }
}
