package com.setsuna.ui.screen;

import com.setsuna.Setsuna;
import com.setsuna.render.SkijaUi;
import com.setsuna.ui.UiTheme;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Two-field home screen split by an interactive central utility ring. */
public final class MainMenuScreen extends AbstractSkijaScreen {

    private static final float DIVIDER_ANGLE = 8.0F;
    private static final float IDLE_RADIUS = 11.0F;
    private static final float OPEN_RADIUS = 47.0F;
    private static final float SWITCH_RADIUS = 6.8F;
    private static final float BACKGROUND_DURATION = 0.70F;
    private static final float DOT_GROW_START = 0.58F;
    private static final float DOT_GROW_DURATION = 0.28F;
    private static final float DOT_SETTLE_START = 0.86F;
    private static final float DOT_SETTLE_DURATION = 0.28F;
    private static final float LINE_START = 1.08F;
    private static final float LINE_DURATION = 0.55F;
    private static final float CONTENT_START = 1.34F;
    private static final float CONTENT_DURATION = 0.40F;
    private static final float ENTRANCE_COMPLETE = CONTENT_START + CONTENT_DURATION;
    private static final int UTILITY_SETTINGS = 0;
    private static final int UTILITY_QUIT = 1;
    private static final int UTILITY_ALT = 2;
    private static final Paint LINE_PAINT = new Paint().setAntiAlias(true);
    private static final Paint RING_PAINT = new Paint()
            .setAntiAlias(true)
            .setMode(PaintMode.STROKE);

    private float singleHover;
    private float multiHover;
    private float centerOpen;
    private float quitHover;
    private float settingsHover;
    private float altHover;
    private float switchHover;
    private float singlePress;
    private float multiPress;
    private float quitPress;
    private float settingsPress;
    private float altPress;
    private float switchPress;
    private long openedAt = System.nanoTime();
    private long lastFrame = openedAt;
    private boolean entrancePlayed;
    private Runnable pendingAction;
    private long pendingActionAt;

    public MainMenuScreen() {
        super(Component.literal(Setsuna.NAME));
    }

    @Override
    protected void init() {
        long now = System.nanoTime();
        openedAt = entrancePlayed
                ? now - (long) (ENTRANCE_COMPLETE * 1_000_000_000L)
                : now;
        lastFrame = now;
        pendingAction = null;
        singleHover = 0.0F;
        multiHover = 0.0F;
        centerOpen = 0.0F;
        quitHover = 0.0F;
        settingsHover = 0.0F;
        altHover = 0.0F;
        switchHover = 0.0F;
        singlePress = 0.0F;
        multiPress = 0.0F;
        quitPress = 0.0F;
        settingsPress = 0.0F;
        altPress = 0.0F;
        switchPress = 0.0F;
    }

    @Override
    protected void drawScreen(Canvas canvas) {

        long now = System.nanoTime();
        float delta = frameDelta(now);
        Entrance entrance = entrance(now);
        if (entrance.complete()) entrancePlayed = true;
        Layout layout = layout();
        updateMotion(layout, delta, entrance.interactive());

        canvas.drawColor(0xFF000000);
        if (entrance.background() > 0.0F) {
            int save = canvas.saveLayerAlpha(null, Math.round(255.0F * entrance.background()));
            ScreenBackdrop.drawMainMenu(canvas, width, height,
                    now / 1_000_000_000.0F, mouseX, mouseY, 48);
            canvas.restoreToCount(save);
        }
        drawFields(canvas, layout, entrance.content());
        drawDivider(canvas, layout, entrance.lines());
        drawDestinations(canvas, layout, entrance.content(), now / 1_000_000_000.0F);
        drawCenterControl(canvas, layout, entrance.dotAlpha(),
                entrance.dotScale(), entrance.content());
        drawBrand(canvas, entrance.content());

        if (pendingAction != null && now >= pendingActionAt) {
            Runnable action = pendingAction;
            pendingAction = null;
            action.run();
        }
    }

    private void drawFields(Canvas canvas, Layout layout, float intro) {
        float extent = layout.extent();
        int leftColor = UiTheme.withAlpha(UiTheme.accent(),
                Math.round((5.0F + singleHover * 18.0F) * intro));
        int rightColor = UiTheme.withAlpha(0xFFF1A45D,
                Math.round((4.0F + multiHover * 16.0F) * intro));

        int save = canvas.save();
        try {
            canvas.translate(layout.centerX(), layout.centerY());
            canvas.rotate(DIVIDER_ANGLE);
            SkijaUi.fill(canvas, -extent, -extent, extent, extent * 2.0F, leftColor);
            SkijaUi.fill(canvas, 0.0F, -extent, extent, extent * 2.0F, rightColor);
        } finally {
            canvas.restoreToCount(save);
        }
    }

    private void drawDivider(Canvas canvas, Layout layout, float reveal) {
        if (reveal <= 0.0F) return;
        int save = canvas.save();
        try {
            canvas.translate(layout.centerX(), layout.centerY());
            canvas.rotate(DIVIDER_ANGLE);
            float centerGap = lerp(IDLE_RADIUS * 0.75F, OPEN_RADIUS - 1.5F,
                    smooth(centerOpen));
            float revealLength = lerp(centerGap, layout.extent(), reveal);
            LINE_PAINT.setStrokeWidth(4.0F)
                    .setColor(UiTheme.withAlpha(0xFFFFFFFF, Math.round(18.0F * reveal)));
            canvas.drawLine(0.0F, -centerGap, 0.0F, -revealLength, LINE_PAINT);
            canvas.drawLine(0.0F, centerGap, 0.0F, revealLength, LINE_PAINT);
            LINE_PAINT.setStrokeWidth(1.25F)
                    .setColor(UiTheme.withAlpha(0xFFFFFFFF, Math.round(218.0F * reveal)));
            canvas.drawLine(0.0F, -centerGap, 0.0F, -revealLength, LINE_PAINT);
            canvas.drawLine(0.0F, centerGap, 0.0F, revealLength, LINE_PAINT);
        } finally {
            canvas.restoreToCount(save);
        }
    }

    private void drawDestinations(Canvas canvas, Layout layout, float intro, float time) {
        float flash = 0.5F + 0.5F * (float) Math.sin(time * 6.0F);
        drawDestination(canvas, "SINGLE PLAYER", layout.leftLabelX(), layout.centerY(),
                -1.0F, singleHover, singlePress, UiTheme.accent(), intro, flash);
        drawDestination(canvas, "MULTI PLAYER", layout.rightLabelX(), layout.centerY(),
                1.0F, multiHover, multiPress, 0xFFF1A45D, intro, flash);
    }

    private void drawDestination(Canvas canvas, String label, float anchorX, float centerY,
                                 float direction, float hover, float press,
                                 int accent, float intro, float flash) {
        float fontSize = width < 520 ? 12.0F : 17.0F;
        float labelWidth = SkijaUi.boldTextWidth(label, fontSize);
        float x = anchorX - labelWidth * 0.5F + direction * (hover * 5.0F - press * 2.0F);
        float y = centerY - 17.0F + press * 1.5F;
        int textColor = mix(0xFFC6CECC, 0xFFFFFFFF, hover);

        float panelProgress = smooth(hover) * intro;
        float panelWidth = (labelWidth + 46.0F) * panelProgress;
        float panelHeight = 38.0F;
        if (panelWidth > 0.5F) {
            float panelX = anchorX - panelWidth * 0.5F;
            float panelY = centerY - panelHeight * 0.5F;
            int panelAlpha = Math.round((112.0F + flash * 34.0F) * panelProgress);
            SkijaUi.rounded(canvas, panelX, panelY, panelWidth, panelHeight, 2.0F,
                    UiTheme.argb(panelAlpha, 0, 0, 0));
            float edgeAlpha = panelProgress * (0.28F + flash * 0.42F);
            SkijaUi.fill(canvas, panelX, panelY, panelWidth, 1.0F,
                    alpha(accent, edgeAlpha));
            SkijaUi.fill(canvas, panelX, panelY + panelHeight - 1.0F, panelWidth, 1.0F,
                    alpha(accent, edgeAlpha));
        }

        SkijaUi.boldText(canvas, label, x, y, 24.0F,
                alpha(textColor, intro * (0.72F + hover * 0.28F)), fontSize);

        float lineWidth = 34.0F + hover * 36.0F;
        float lineX = anchorX - lineWidth * 0.5F;
        SkijaUi.rounded(canvas, lineX, y + 29.0F, lineWidth, 1.5F, 0.75F,
                alpha(accent, intro * (0.32F + hover * 0.68F)));
    }

    private void drawCenterControl(Canvas canvas, Layout layout, float dotAlpha,
                                   float dotScale, float content) {
        float progress = smooth(centerOpen) * content;
        float radius = lerp(IDLE_RADIUS, OPEN_RADIUS, progress);
        float centerX = layout.centerX();
        float centerY = layout.centerY();

        float fillRadius = lerp(IDLE_RADIUS * dotScale, OPEN_RADIUS * 0.72F, progress);
        int fillAlpha = Math.round(255.0F * dotAlpha * (1.0F - progress));
        if (fillAlpha > 0) {
            SkijaUi.rounded(canvas, centerX - fillRadius, centerY - fillRadius,
                    fillRadius * 2.0F, fillRadius * 2.0F, fillRadius,
                    UiTheme.argb(fillAlpha, 255, 255, 255));
        }

        if (progress > 0.01F) {
            RING_PAINT.setStrokeWidth(lerp(1.0F, 2.2F, progress))
                    .setColor(UiTheme.withAlpha(0xFFFFFFFF, Math.round(235.0F * progress)));
            canvas.drawCircle(centerX, centerY, radius, RING_PAINT);

            float utilityReveal = clamp((progress - 0.38F) / 0.62F, 0.0F, 1.0F);
            drawUtilityDividers(canvas, centerX, centerY, radius, utilityReveal);
            drawUtility(canvas, "ALT", centerX, centerY - radius * 0.52F,
                    altHover, altPress, 0xFFD3B3FF, utilityReveal);
            drawUtility(canvas, "QUIT", centerX - radius * 0.45F, centerY + radius * 0.28F,
                    quitHover, quitPress, 0xFFE26964, utilityReveal);
            drawUtility(canvas, "SETTINGS", centerX + radius * 0.45F, centerY + radius * 0.28F,
                    settingsHover, settingsPress, UiTheme.accent(), utilityReveal);
            drawSwitchControl(canvas, centerX, centerY, utilityReveal);
        }
    }

    private void drawUtilityDividers(Canvas canvas, float centerX, float centerY,
                                     float radius, float reveal) {
        LINE_PAINT.setStrokeWidth(0.8F)
                .setColor(alpha(0xB8FFFFFF, reveal));
        for (float angle : new float[]{-30.0F, 90.0F, 210.0F}) {
            double radians = Math.toRadians(angle);
            float cos = (float) Math.cos(radians);
            float sin = (float) Math.sin(radians);
            float inner = SWITCH_RADIUS + 2.0F;
            canvas.drawLine(centerX + cos * inner, centerY + sin * inner,
                    centerX + cos * (radius - 1.5F), centerY + sin * (radius - 1.5F),
                    LINE_PAINT);
        }
    }

    private void drawSwitchControl(Canvas canvas, float centerX, float centerY, float reveal) {
        float radius = SWITCH_RADIUS + switchHover * 1.5F - switchPress * 0.7F;
        int fill = mix(0xFFF4F6F5, UiTheme.accent(), switchHover * 0.32F);
        SkijaUi.rounded(canvas, centerX - radius, centerY - radius,
                radius * 2.0F, radius * 2.0F, radius, alpha(fill, reveal));

        String glyph = "\u21C4";
        float fontSize = 5.4F;
        float glyphWidth = SkijaUi.boldTextWidth(glyph, fontSize);
        SkijaUi.boldText(canvas, glyph, centerX - glyphWidth * 0.5F,
                centerY - radius, radius * 2.0F,
                alpha(0xFF111516, reveal), fontSize);
    }

    private void drawUtility(Canvas canvas, String label, float centerX, float centerY,
                             float hover, float press, int accent, float reveal) {
        float fontSize = 5.7F;
        float textWidth = SkijaUi.boldTextWidth(label, fontSize);
        float y = centerY - 6.0F + press;
        SkijaUi.boldText(canvas, label, centerX - textWidth * 0.5F, y,
                12.0F, alpha(mix(0xFFCED5D3, 0xFFFFFFFF, hover), reveal), fontSize);
        float indicator = 7.0F + hover * 10.0F;
        SkijaUi.rounded(canvas, centerX - indicator * 0.5F, centerY + 9.0F,
                indicator, 1.2F, 0.6F, alpha(accent, reveal * (0.35F + hover * 0.65F)));
    }

    private void drawBrand(Canvas canvas, float intro) {
        float x = 22.0F;
        float y = 17.0F;
        SkijaUi.boldText(canvas, Setsuna.NAME.toUpperCase(), x, y, 14.0F,
                alpha(0xFFFFFFFF, intro), 9.0F);
        SkijaUi.text(canvas, cleanVersion(), x, y + 13.0F, 10.0F,
                alpha(0xFF9EA9A6, intro), 6.2F);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT || pendingAction != null) {
            return false;
        }
        if (!entranceReady(System.nanoTime())) return true;

        Layout layout = layout();
        float dx = (float) event.x() - layout.centerX();
        float dy = (float) event.y() - layout.centerY();
        float distance = (float) Math.hypot(dx, dy);
        if (centerOpen > 0.35F && distance <= OPEN_RADIUS + 10.0F) {
            if (distance <= SWITCH_RADIUS + 5.0F) {
                switchPress = 1.0F;
                queue(() -> {
                    TitleScreenMode.useVanilla();
                    minecraft.setScreen(new TitleScreen());
                });
            } else {
                switch (utilityAt(dx, dy)) {
                    case UTILITY_ALT -> {
                        altPress = 1.0F;
                        queue(() -> minecraft.setScreen(new AltManagerScreen(this)));
                    }
                    case UTILITY_QUIT -> {
                        quitPress = 1.0F;
                        queue(minecraft::stop);
                    }
                    default -> {
                        settingsPress = 1.0F;
                        queue(() -> minecraft.setScreen(
                                new OptionsScreen(this, minecraft.options, false)));
                    }
                }
            }
            return true;
        }

        if (sideAt(layout, (float) event.x(), (float) event.y()) < 0) {
            singlePress = 1.0F;
            queue(() -> minecraft.setScreen(new SelectWorldScreen(this)));
        } else {
            multiPress = 1.0F;
            queue(() -> minecraft.setScreen(minecraft.options.skipMultiplayerWarning
                    ? new JoinMultiplayerScreen(this)
                    : new SafetyScreen(this)));
        }
        return true;
    }

    private void updateMotion(Layout layout, float delta, boolean interactive) {
        float dx = mouseX - layout.centerX();
        float dy = mouseY - layout.centerY();
        float distance = (float) Math.hypot(dx, dy);
        float centerRange = centerOpen > 0.08F ? OPEN_RADIUS + 14.0F : IDLE_RADIUS + 10.0F;
        boolean centerTarget = interactive && distance <= centerRange;
        centerOpen = approach(centerOpen, centerTarget ? 1.0F : 0.0F, delta, 10.0F);

        boolean utilityActive = interactive && centerOpen > 0.18F
                && distance <= OPEN_RADIUS + 14.0F;
        boolean switchActive = utilityActive && distance <= SWITCH_RADIUS + 5.0F;
        int utility = utilityAt(dx, dy);
        switchHover = approach(switchHover, switchActive ? 1.0F : 0.0F,
                delta, 14.0F);
        quitHover = approach(quitHover,
                utilityActive && !switchActive && utility == UTILITY_QUIT ? 1.0F : 0.0F,
                delta, 14.0F);
        settingsHover = approach(settingsHover,
                utilityActive && !switchActive && utility == UTILITY_SETTINGS ? 1.0F : 0.0F,
                delta, 14.0F);
        altHover = approach(altHover,
                utilityActive && !switchActive && utility == UTILITY_ALT ? 1.0F : 0.0F,
                delta, 14.0F);

        int side = sideAt(layout, mouseX, mouseY);
        singleHover = approach(singleHover, interactive && !utilityActive && side < 0 ? 1.0F : 0.0F,
                delta, 8.0F);
        multiHover = approach(multiHover, interactive && !utilityActive && side >= 0 ? 1.0F : 0.0F,
                delta, 8.0F);
        singlePress = approach(singlePress, 0.0F, delta, 20.0F);
        multiPress = approach(multiPress, 0.0F, delta, 20.0F);
        quitPress = approach(quitPress, 0.0F, delta, 20.0F);
        settingsPress = approach(settingsPress, 0.0F, delta, 20.0F);
        altPress = approach(altPress, 0.0F, delta, 20.0F);
        switchPress = approach(switchPress, 0.0F, delta, 20.0F);
    }

    private static int utilityAt(float dx, float dy) {
        float angle = (float) Math.toDegrees(Math.atan2(dy, dx));
        if (angle < 0.0F) {
            angle += 360.0F;
        }
        if (angle >= 90.0F && angle < 210.0F) {
            return UTILITY_QUIT;
        }
        if (angle >= 210.0F && angle < 330.0F) {
            return UTILITY_ALT;
        }
        return UTILITY_SETTINGS;
    }

    private static int sideAt(Layout layout, float x, float y) {
        float dx = x - layout.centerX();
        float dy = y - layout.centerY();
        double radians = Math.toRadians(DIVIDER_ANGLE);
        float localX = (float) (Math.cos(radians) * dx + Math.sin(radians) * dy);
        return localX < 0.0F ? -1 : 1;
    }

    private void queue(Runnable action) {
        pendingAction = action;
        pendingActionAt = System.nanoTime() + 105_000_000L;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return event.isEscape() || super.keyPressed(event);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    private Layout layout() {
        float centerX = width * 0.5F;
        float centerY = height * 0.5F;
        float extent = (float) Math.hypot(width, height) + 8.0F;
        float inset = width < 520 ? width * 0.24F : Math.min(210.0F, width * 0.25F);
        return new Layout(centerX, centerY, extent, inset, width - inset);
    }

    private float frameDelta(long now) {
        float delta = Math.min(0.05F, Math.max(0.0F,
                (now - lastFrame) / 1_000_000_000.0F));
        lastFrame = now;
        return delta;
    }

    private Entrance entrance(long now) {
        float elapsed = (now - openedAt) / 1_000_000_000.0F;
        float background = easeOut(stage(elapsed, 0.0F, BACKGROUND_DURATION));
        float dotGrow = easeOut(stage(elapsed, DOT_GROW_START, DOT_GROW_DURATION));
        float dotSettle = easeOut(stage(elapsed, DOT_SETTLE_START, DOT_SETTLE_DURATION));
        float dotScale = dotGrow < 1.0F
                ? lerp(0.18F, 1.55F, dotGrow)
                : lerp(1.55F, 1.0F, dotSettle);
        float lines = easeOut(stage(elapsed, LINE_START, LINE_DURATION));
        float content = easeOut(stage(elapsed, CONTENT_START, CONTENT_DURATION));
        return new Entrance(background, dotGrow, dotScale, lines, content,
                elapsed >= LINE_START + LINE_DURATION * 0.92F,
                elapsed >= ENTRANCE_COMPLETE);
    }

    private boolean entranceReady(long now) {
        return entrancePlayed || (now - openedAt) / 1_000_000_000.0F
                >= LINE_START + LINE_DURATION * 0.92F;
    }

    private static float stage(float elapsed, float start, float duration) {
        return clamp((elapsed - start) / duration, 0.0F, 1.0F);
    }

    private static float easeOut(float value) {
        float remaining = 1.0F - clamp(value, 0.0F, 1.0F);
        return 1.0F - remaining * remaining * remaining;
    }

    private static float approach(float current, float target, float delta, float speed) {
        return current + (target - current) * (1.0F - (float) Math.exp(-speed * delta));
    }

    private static float smooth(float value) {
        float clamped = clamp(value, 0.0F, 1.0F);
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    private static float lerp(float start, float end, float amount) {
        return start + (end - start) * amount;
    }

    private static int mix(int first, int second, float amount) {
        float value = clamp(amount, 0.0F, 1.0F);
        int alpha = Math.round(((first >>> 24) & 0xFF)
                + (((second >>> 24) & 0xFF) - ((first >>> 24) & 0xFF)) * value);
        int red = Math.round(((first >>> 16) & 0xFF)
                + (((second >>> 16) & 0xFF) - ((first >>> 16) & 0xFF)) * value);
        int green = Math.round(((first >>> 8) & 0xFF)
                + (((second >>> 8) & 0xFF) - ((first >>> 8) & 0xFF)) * value);
        int blue = Math.round((first & 0xFF) + ((second & 0xFF) - (first & 0xFF)) * value);
        return UiTheme.argb(alpha, red, green, blue);
    }

    private static int alpha(int color, float opacity) {
        int sourceAlpha = color >>> 24;
        return UiTheme.withAlpha(color,
                Math.round(sourceAlpha * clamp(opacity, 0.0F, 1.0F)));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String cleanVersion() {
        return Setsuna.VERSION.replaceFirst("^[vV]", "");
    }

    private record Layout(float centerX, float centerY, float extent,
                          float leftLabelX, float rightLabelX) {
    }

    private record Entrance(float background, float dotAlpha, float dotScale,
                            float lines, float content, boolean interactive,
                            boolean complete) {
    }
}
