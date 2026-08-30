package com.setsuna.module.modules.render;

import com.setsuna.event.Listen;
import com.setsuna.event.events.AttackEvent;
import com.setsuna.event.events.Render2DEvent;
import com.setsuna.event.events.RespawnEvent;
import com.setsuna.event.events.TickEvent;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.render.SkijaUi;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.ButtonSetting;
import com.setsuna.setting.settings.ColorSetting;
import com.setsuna.setting.settings.DoubleSetting;
import com.setsuna.setting.settings.EnumSetting;
import com.setsuna.setting.settings.IntSetting;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.skija.Path;
import io.github.humbleui.types.Point;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.awt.Color;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Shows a short animated confirmation after a recently attacked target dies.
 * Kill ownership is inferred client-side because Setsuna has no server plugin.
 */
public final class KillEffect extends Module {

    public static final KillEffect INSTANCE = new KillEffect();

    public enum Style {
        COMBO,
        BADGE,
        MINIMAL
    }

    public enum Targets {
        PLAYERS,
        ALL
    }

    public enum KillSound {
        NONE,
        CRITICAL,
        LEVEL_UP,
        TOTEM,
        CHALLENGE,
        PLING,
        ANVIL,
        THUNDER
    }

    private static final int MAX_TRACKED_TARGETS = 32;
    private static final long ENTER_DURATION_NANOS = 220_000_000L;
    private static final long EXIT_DURATION_NANOS = 240_000_000L;
    private static final Paint PREVIEW_PAINT = new Paint().setAntiAlias(true);

    public final EnumSetting<Style> style = add(new EnumSetting<>("Style", Style.COMBO));
    public final EnumSetting<Targets> targets = add(new EnumSetting<>("Targets", Targets.PLAYERS));
    public final ColorSetting accent = add(new ColorSetting(
            "Accent", new Color(87, 226, 255), false));
    public final DoubleSetting scale = add(new DoubleSetting("Scale", 1.0, 0.5, 2.0, 0.05));
    public final IntSetting yOffset = add(new IntSetting("Y Offset", 34, -160, 160, 2));
    public final DoubleSetting duration = add(new DoubleSetting(
            "Duration", 1.8, 0.6, 4.0, 0.1));
    public final DoubleSetting confirmWindow = add(new DoubleSetting(
            "Confirm Window", 6.0, 1.0, 12.0, 0.5));
    public final DoubleSetting comboWindow = add(new DoubleSetting(
            "Combo Window", 4.0, 1.0, 10.0, 0.5));
    public final EnumSetting<KillSound> sound = add(new EnumSetting<>(
            "Sound", KillSound.LEVEL_UP));
    public final DoubleSetting soundVolume = add(new DoubleSetting(
            "Sound Volume", 0.75, 0.1, 1.0, 0.05)
            .visibleWhen(() -> !sound.is(KillSound.NONE)));
    public final BooleanSetting targetName = add(new BooleanSetting("Target Name", true));
    public final BooleanSetting distance = add(new BooleanSetting("Distance", false));
    public final BooleanSetting glow = add(new BooleanSetting("Glow", true));
    public final IntSetting glowStrength = add(new IntSetting("Glow Strength", 5, 1, 10, 1)
            .visibleWhen(glow::get));
    public final ButtonSetting preview = add(new ButtonSetting("Preview", this::showPreview));

    private final Map<UUID, TrackedTarget> trackedTargets = new LinkedHashMap<>();

    private Paint effectPaint;
    private long effectStartedNanos;
    private long lastKillNanos;
    private String victimName = "";
    private float victimDistance;
    private int comboCount;
    private int previewCombo;
    private boolean showing;

    private KillEffect() {
        super("Kill Effect", Category.RENDER);
    }

    @Override
    protected void onEnable() {
        effectPaint = new Paint().setAntiAlias(true);
        clearState();
    }

    @Override
    protected void onDisable() {
        clearState();
        if (effectPaint != null) {
            effectPaint.close();
            effectPaint = null;
        }
    }

    @Override
    public String getInfo() {
        return style.displayValue();
    }

    @Listen
    private void onAttack(AttackEvent event) {
        if (noPlayer() || event.getAttacker() != mc.player
                || !(event.getTarget() instanceof LivingEntity target)
                || target == mc.player || !target.isAlive()) {
            return;
        }
        if (targets.is(Targets.PLAYERS) && !(target instanceof Player)) {
            return;
        }

        long now = System.nanoTime();
        TrackedTarget tracked = new TrackedTarget(
                target,
                now,
                displayName(target),
                mc.player.distanceTo(target));
        trackedTargets.remove(target.getUUID());
        trackedTargets.put(target.getUUID(), tracked);
        trimTrackedTargets();
    }

    @Listen
    private void onTick(TickEvent.Post event) {
        if (noPlayer()) {
            trackedTargets.clear();
            return;
        }

        long now = System.nanoTime();
        long confirmationNanos = secondsToNanos(confirmWindow.get());
        Iterator<TrackedTarget> iterator = trackedTargets.values().iterator();
        while (iterator.hasNext()) {
            TrackedTarget tracked = iterator.next();
            LivingEntity target = tracked.entity();
            if (target.level() != mc.level || now - tracked.attackedAtNanos() > confirmationNanos) {
                iterator.remove();
                continue;
            }
            if (target.isDeadOrDying() || target.getHealth() <= 0.0F) {
                iterator.remove();
                confirmKill(tracked, now);
            }
        }
    }

    @Listen
    private void onRespawn(RespawnEvent event) {
        clearState();
    }

    @Listen
    private void onRender2D(Render2DEvent event) {
        if (!showing || effectPaint == null) {
            return;
        }

        long now = System.nanoTime();
        long displayNanos = secondsToNanos(duration.get());
        long elapsedNanos = now - effectStartedNanos;
        if (elapsedNanos < 0L || elapsedNanos >= displayNanos) {
            showing = false;
            return;
        }

        float elapsed = elapsedNanos / (float) displayNanos;
        float enter = easeOutCubic(clamp(elapsedNanos / (float) ENTER_DURATION_NANOS));
        float remainingExit = clamp((displayNanos - elapsedNanos) / (float) EXIT_DURATION_NANOS);
        float alpha = enter * smoothStep(remainingExit);
        float popScale = lerp(1.58F, 1.0F, enter) * lerp(0.94F, 1.0F, remainingExit);
        float renderScale = scale.get().floatValue() * popScale;
        float centerX = event.width() * 0.5F;
        float centerY = event.height() * 0.5F + yOffset.get() + (1.0F - enter) * 9.0F;
        int color = withAlpha(accent.argb(), alpha);

        Canvas canvas = event.canvas();
        int save = canvas.save();
        canvas.translate(centerX, centerY);
        canvas.scale(renderScale, renderScale);
        try {
            Runnable drawing = () -> drawStyle(canvas, color, alpha, 1.0F - elapsed,
                    comboCount, victimName, victimDistance);
            if (glow.get() && alpha > 0.02F) {
                SkijaUi.glowLayer(canvas, -112.0F, -42.0F, 224.0F, 102.0F,
                        8.0F, glowStrength.get(), drawing);
            }
            drawing.run();
        } finally {
            canvas.restoreToCount(save);
        }
    }

    /** Draws a stable, read-only sample for ClickGUI preview panels. */
    public void renderPreview(Canvas canvas, float x, float y, float width, float height) {
        if (canvas == null || width <= 1.0F || height <= 1.0F) {
            return;
        }

        float contentWidth = style.is(Style.MINIMAL) ? 200.0F : 112.0F;
        float contentHeight = style.is(Style.MINIMAL) ? 44.0F : 72.0F;
        float fitScale = Math.min(
                Math.max(0.1F, width - 16.0F) / contentWidth,
                Math.max(0.1F, height - 16.0F) / contentHeight);
        float scaleProgress = clamp((scale.get().floatValue() - 0.5F) / 1.5F);
        float previewScale = Math.max(0.12F,
                fitScale * lerp(0.58F, 1.0F, scaleProgress));
        int color = accent.argb();

        int save = canvas.save();
        canvas.translate(x + width * 0.5F, y + height * 0.46F);
        canvas.scale(previewScale, previewScale);
        try {
            Runnable drawing = () -> drawStyle(canvas, color, 1.0F, 0.68F,
                    3, "Preview Target", 12.4F);
            if (glow.get()) {
                SkijaUi.glowLayer(canvas, -112.0F, -42.0F, 224.0F, 102.0F,
                        8.0F, glowStrength.get(), drawing);
            }
            drawing.run();
        } finally {
            canvas.restoreToCount(save);
        }
    }

    private void confirmKill(TrackedTarget tracked, long now) {
        long comboNanos = secondsToNanos(comboWindow.get());
        comboCount = lastKillNanos != 0L && now - lastKillNanos <= comboNanos
                ? comboCount + 1
                : 1;
        lastKillNanos = now;
        showEffect(tracked.name(), tracked.distance(), now);
    }

    private void showPreview() {
        previewCombo = previewCombo % 6 + 1;
        comboCount = previewCombo;
        showEffect("Preview Target", 12.4F, System.nanoTime());
    }

    private void showEffect(String name, float targetDistance, long now) {
        victimName = ellipsize(name, 24);
        victimDistance = targetDistance;
        effectStartedNanos = now;
        showing = true;
        playSelectedSound();
    }

    private void playSelectedSound() {
        SoundEvent event = switch (sound.get()) {
            case NONE -> null;
            case CRITICAL -> SoundEvents.PLAYER_ATTACK_CRIT;
            case LEVEL_UP -> SoundEvents.PLAYER_LEVELUP;
            case TOTEM -> SoundEvents.TOTEM_USE;
            case CHALLENGE -> SoundEvents.UI_TOAST_CHALLENGE_COMPLETE;
            case PLING -> SoundEvents.NOTE_BLOCK_PLING.value();
            case ANVIL -> SoundEvents.ANVIL_LAND;
            case THUNDER -> SoundEvents.LIGHTNING_BOLT_THUNDER;
        };
        if (event != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(
                    event, 1.0F, soundVolume.get().floatValue()));
        }
    }

    private void drawStyle(Canvas canvas, int color, float alpha, float progress,
                           int displayedCombo, String displayedName, float displayedDistance) {
        switch (style.get()) {
            case COMBO -> drawCombo(canvas, color, alpha, progress,
                    displayedCombo, displayedName, displayedDistance);
            case BADGE -> drawBadge(canvas, color, alpha, progress,
                    displayedCombo, displayedName, displayedDistance);
            case MINIMAL -> drawMinimal(canvas, color, alpha, progress,
                    displayedCombo, displayedName, displayedDistance);
        }
    }

    private void drawCombo(Canvas canvas, int color, float alpha, float progress,
                           int displayedCombo, String displayedName, float displayedDistance) {
        int dim = withAlpha(0xFFFFFFFF, alpha * 0.18F);
        int white = withAlpha(0xFFFFFFFF, alpha);
        strokeCircle(canvas, 0.0F, 0.0F, 22.0F, 1.1F, dim);
        strokeArc(canvas, 0.0F, 0.0F, 22.0F, -90.0F,
                360.0F * clamp(progress), 2.0F, color);

        drawDiamond(canvas, 0.0F, 0.0F, 15.5F, 1.1F, withAlpha(color, alpha * 0.72F));
        drawSkull(canvas, 0.0F, -0.5F, color, alpha);

        for (int i = 0; i < 5; i++) {
            float x = (i - 2) * 6.0F;
            int pipColor = i < Math.min(displayedCombo, 5) ? color : dim;
            fillCircle(canvas, x, 27.0F, 1.35F, pipColor);
        }
        if (displayedCombo > 5) {
            drawCenteredText(canvas, "+" + (displayedCombo - 5), 31.0F,
                    7.0F, color, 6.5F);
        }

        drawCenteredText(canvas, comboLabel(displayedCombo), 34.0F, 10.0F, white, 8.5F);
        drawTargetLine(canvas, 45.0F, alpha, displayedName, displayedDistance);
    }

    private void drawBadge(Canvas canvas, int color, float alpha, float progress,
                           int displayedCombo, String displayedName, float displayedDistance) {
        int dim = withAlpha(0xFFFFFFFF, alpha * 0.20F);
        int white = withAlpha(0xFFFFFFFF, alpha);
        Point[] shield = {
                new Point(0.0F, -22.0F), new Point(19.0F, -10.0F),
                new Point(14.0F, 14.0F), new Point(0.0F, 24.0F),
                new Point(-14.0F, 14.0F), new Point(-19.0F, -10.0F)
        };
        drawPolygon(canvas, shield, false, 1.2F, dim);
        drawSkull(canvas, 0.0F, -1.0F, color, alpha);

        float sweep = 22.0F * clamp(progress);
        for (int side : new int[]{-1, 1}) {
            for (int i = 0; i < Math.min(displayedCombo, 4); i++) {
                float inset = i * 5.0F;
                SkijaUi.line(canvas, side * (27.0F + inset), -sweep * 0.45F,
                        side * (31.0F + inset), 0.0F, 1.5F, color);
                SkijaUi.line(canvas, side * (31.0F + inset), 0.0F,
                        side * (27.0F + inset), sweep * 0.45F, 1.5F, color);
            }
        }

        drawCenteredText(canvas, comboLabel(displayedCombo), 30.0F, 10.0F, white, 8.5F);
        drawTargetLine(canvas, 41.0F, alpha, displayedName, displayedDistance);
    }

    private void drawMinimal(Canvas canvas, int color, float alpha, float progress,
                             int displayedCombo, String displayedName, float displayedDistance) {
        String title = comboLabel(displayedCombo);
        String detail = targetLine(displayedName, displayedDistance);
        float titleWidth = SkijaUi.textWidth(title, 8.5F);
        float detailWidth = detail.isEmpty() ? 0.0F : SkijaUi.textWidthWithFallback(detail, 7.0F);
        float width = Math.max(92.0F, Math.min(200.0F, Math.max(titleWidth, detailWidth) + 52.0F));
        float height = detail.isEmpty() ? 28.0F : 37.0F;
        float left = -width * 0.5F;
        float top = -height * 0.5F;

        SkijaUi.rounded(canvas, left, top, width, height, 5.0F,
                withAlpha(0xE60A0E14, alpha));
        SkijaUi.rounded(canvas, left, top, 3.0F, height * clamp(progress),
                1.5F, color);
        drawSkull(canvas, left + 19.0F, 0.0F, color, alpha);
        SkijaUi.text(canvas, title, left + 37.0F, top + 5.0F,
                10.0F, withAlpha(0xFFFFFFFF, alpha), 8.5F);
        if (!detail.isEmpty()) {
            SkijaUi.textWithFallback(canvas, detail, left + 37.0F, top + 18.0F,
                    10.0F, withAlpha(0xFFB9C1CC, alpha), 7.0F);
        }
    }

    private void drawSkull(Canvas canvas, float centerX, float centerY, int color, float alpha) {
        int cutout = withAlpha(0xFF10151C, alpha);
        fillCircle(canvas, centerX, centerY - 2.5F, 7.7F, color);
        SkijaUi.rounded(canvas, centerX - 5.4F, centerY + 2.0F,
                10.8F, 6.0F, 2.0F, color);
        SkijaUi.rounded(canvas, centerX - 4.8F, centerY - 4.5F,
                3.0F, 2.4F, 0.8F, cutout);
        SkijaUi.rounded(canvas, centerX + 1.8F, centerY - 4.5F,
                3.0F, 2.4F, 0.8F, cutout);
        for (int i = -1; i <= 1; i++) {
            SkijaUi.fill(canvas, centerX + i * 3.0F - 0.55F,
                    centerY + 4.0F, 1.1F, 4.0F, cutout);
        }
    }

    private void drawTargetLine(Canvas canvas, float top, float alpha,
                                String displayedName, float displayedDistance) {
        String line = targetLine(displayedName, displayedDistance);
        if (!line.isEmpty()) {
            drawCenteredFallbackText(canvas, line, top, 9.0F,
                    withAlpha(0xFFD7DEE8, alpha), 7.5F);
        }
    }

    private String targetLine(String displayedName, float displayedDistance) {
        if (!targetName.get() && !distance.get()) {
            return "";
        }
        String distanceText = String.format(Locale.ROOT, "%.1fm", displayedDistance);
        if (!targetName.get()) {
            return distanceText;
        }
        return distance.get() ? displayedName + "  " + distanceText : displayedName;
    }

    private static String comboLabel(int displayedCombo) {
        return switch (displayedCombo) {
            case 1 -> "ELIMINATED";
            case 2 -> "DOUBLE KILL";
            case 3 -> "TRIPLE KILL";
            case 4 -> "QUAD KILL";
            case 5 -> "PENTA KILL";
            default -> displayedCombo + " KILL CHAIN";
        };
    }

    private void strokeCircle(Canvas canvas, float x, float y, float radius,
                              float thickness, int color) {
        Paint paint = renderPaint();
        paint.setMode(PaintMode.STROKE).setStrokeWidth(thickness).setColor(color);
        canvas.drawCircle(x, y, radius, paint);
        paint.setMode(PaintMode.FILL).setStrokeWidth(1.0F);
    }

    private void strokeArc(Canvas canvas, float x, float y, float radius,
                           float start, float sweep, float thickness, int color) {
        Paint paint = renderPaint();
        paint.setMode(PaintMode.STROKE).setStrokeWidth(thickness).setColor(color);
        canvas.drawArc(x - radius, y - radius, x + radius, y + radius,
                start, sweep, false, paint);
        paint.setMode(PaintMode.FILL).setStrokeWidth(1.0F);
    }

    private void fillCircle(Canvas canvas, float x, float y, float radius, int color) {
        Paint paint = renderPaint();
        paint.setMode(PaintMode.FILL).setColor(color);
        canvas.drawCircle(x, y, radius, paint);
    }

    private void drawDiamond(Canvas canvas, float x, float y, float radius,
                             float thickness, int color) {
        Point[] points = {
                new Point(x, y - radius), new Point(x + radius, y),
                new Point(x, y + radius), new Point(x - radius, y)
        };
        drawPolygon(canvas, points, false, thickness, color);
    }

    private void drawPolygon(Canvas canvas, Point[] points, boolean fill,
                             float thickness, int color) {
        try (Path path = Path.makePolygon(points, true)) {
            Paint paint = renderPaint();
            paint.setMode(fill ? PaintMode.FILL : PaintMode.STROKE)
                    .setStrokeWidth(thickness).setColor(color);
            canvas.drawPath(path, paint);
        } finally {
            renderPaint().setMode(PaintMode.FILL).setStrokeWidth(1.0F);
        }
    }

    private Paint renderPaint() {
        return effectPaint != null ? effectPaint : PREVIEW_PAINT;
    }

    private static void drawCenteredText(Canvas canvas, String text, float top,
                                         float height, int color, float size) {
        float width = SkijaUi.textWidth(text, size);
        SkijaUi.text(canvas, text, -width * 0.5F, top, height, color, size);
    }

    private static void drawCenteredFallbackText(Canvas canvas, String text, float top,
                                                 float height, int color, float size) {
        float width = SkijaUi.textWidthWithFallback(text, size);
        SkijaUi.textWithFallback(canvas, text, -width * 0.5F, top, height, color, size);
    }

    private void trimTrackedTargets() {
        while (trackedTargets.size() > MAX_TRACKED_TARGETS) {
            Iterator<UUID> iterator = trackedTargets.keySet().iterator();
            if (!iterator.hasNext()) {
                return;
            }
            iterator.next();
            iterator.remove();
        }
    }

    private void clearState() {
        trackedTargets.clear();
        showing = false;
        effectStartedNanos = 0L;
        lastKillNanos = 0L;
        victimName = "";
        victimDistance = 0.0F;
        comboCount = 0;
        previewCombo = 0;
    }

    private static String displayName(LivingEntity target) {
        String name = target.getDisplayName().getString();
        return name.isBlank() ? target.getName().getString() : name;
    }

    private static String ellipsize(String value, int maxCodePoints) {
        String safe = value == null ? "" : value.strip();
        int count = safe.codePointCount(0, safe.length());
        if (count <= maxCodePoints) {
            return safe;
        }
        int end = safe.offsetByCodePoints(0, Math.max(1, maxCodePoints - 3));
        return safe.substring(0, end) + "...";
    }

    private static long secondsToNanos(double seconds) {
        return (long) (seconds * 1_000_000_000.0);
    }

    private static float easeOutCubic(float value) {
        float remaining = 1.0F - clamp(value);
        return 1.0F - remaining * remaining * remaining;
    }

    private static float smoothStep(float value) {
        float clamped = clamp(value);
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    private static float lerp(float from, float to, float progress) {
        return from + (to - from) * progress;
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static int withAlpha(int color, float alpha) {
        int baseAlpha = color >>> 24;
        int adjusted = Math.max(0, Math.min(255, Math.round(baseAlpha * clamp(alpha))));
        return adjusted << 24 | color & 0x00FFFFFF;
    }

    private record TrackedTarget(
            LivingEntity entity,
            long attackedAtNanos,
            String name,
            float distance) {
    }
}
