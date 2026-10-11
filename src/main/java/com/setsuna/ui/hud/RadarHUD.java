package com.setsuna.ui.hud;

import com.setsuna.event.events.Render2DEvent;
import com.setsuna.manager.FriendManager;
import com.setsuna.render.SkijaUi;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.ColorSetting;
import com.setsuna.setting.settings.DoubleSetting;
import com.setsuna.setting.settings.EnumSetting;
import com.setsuna.setting.settings.IntSetting;
import com.setsuna.ui.UiTheme;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.ClipMode;
import io.github.humbleui.skija.Path;
import io.github.humbleui.types.Point;
import io.github.humbleui.types.RRect;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;

import java.awt.Color;

/** Compact square radar with interpolated, view-aligned contacts. */
public final class RadarHUD extends HUDModuel {

    public static final RadarHUD INSTANCE = new RadarHUD();
    private static final String[] CARDINALS = {"N", "E", "S", "W"};
    private static final double[] CARDINAL_X = {0.0, 1.0, 0.0, -1.0};
    private static final double[] CARDINAL_Z = {-1.0, 0.0, 1.0, 0.0};

    public enum Filter { Players, Mobs, Animals, All }
    public enum BackgroundStyle { CURRENT, GRID }

    public final DoubleSetting scale = add(new DoubleSetting("Scale", 1.0, 0.65, 1.8, 0.05));
    public final DoubleSetting radarRange = add(new DoubleSetting("Range", 64.0, 8.0, 192.0, 4.0));
    public final EnumSetting<Filter> filter = add(new EnumSetting<>("Filter", Filter.All));
    public final BooleanSetting rotateFacing = add(new BooleanSetting("Rotate With Facing", true));
    public final BooleanSetting background = add(new BooleanSetting("Background", true));
    public final EnumSetting<BackgroundStyle> backgroundStyle =
            add(new EnumSetting<>("Background Style", BackgroundStyle.CURRENT)
                    .visibleWhen(background::get));
    public final DoubleSetting cornerRadius =
            add(new DoubleSetting("Corner Radius", 6.0, 0.0, 18.0, 0.5)
                    .visibleWhen(background::get));
    public final ColorSetting backgroundColor =
            add(new ColorSetting("Background Color", new Color(12, 16, 18), false)
                    .visibleWhen(background::get));
    public final IntSetting backgroundOpacity =
            add(new IntSetting("Background Opacity", 170, 0, 255, 5)
                    .visibleWhen(background::get));

    private RadarHUD() {
        super("Radar HUD", 880, 170, 88.0F, 88.0F);
    }

    @Override
    protected void renderHud(Render2DEvent event) {
        if (noPlayer()) return;
        float s = scale.get().floatValue();
        float size = 88.0F * s;
        float x = renderX(event, size);
        float y = renderY(event, size);
        float centerX = x + size * 0.5F;
        float centerY = y + 51.0F * s;
        float radarRadius = 25.0F * s;
        updateBounds(size, size);
        Canvas canvas = event.canvas();

        drawBackground(canvas, x, y, size, s);
        double range = radarRange.get();
        String rangeLabel = Math.round(range) + "m";
        SkijaUi.boldText(canvas, "RADAR", x + 7.0F * s, y + 4.0F * s,
                11.0F * s, UiTheme.TEXT_MUTED, 7.0F * s);
        SkijaUi.text(canvas, rangeLabel,
                x + size - 7.0F * s - SkijaUi.textWidth(rangeLabel, 6.5F * s),
                y + 4.0F * s, 11.0F * s, UiTheme.TEXT_FAINT, 6.5F * s);
        drawGuides(canvas, centerX, centerY, radarRadius, s);

        float partialTick = Mth.clamp(mc.getDeltaTracker().getGameTimeDeltaPartialTick(true), 0.0F, 1.0F);
        double playerX = Mth.lerp(partialTick, mc.player.xOld, mc.player.getX());
        double playerZ = Mth.lerp(partialTick, mc.player.zOld, mc.player.getZ());
        float yaw = mc.player.getYRot(partialTick);
        RadarProjection projection = RadarProjection.of(yaw, rotateFacing.get());
        drawCompass(canvas, centerX, centerY, 30.0F * s, s, projection);
        float pixelsPerBlock = (float) ((radarRadius - 3.0F * s) / range);
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity == mc.player || !entity.isAlive() || entity.isSpectator() || !include(entity)) continue;
            double dx = Mth.lerp(partialTick, entity.xOld, entity.getX()) - playerX;
            double dz = Mth.lerp(partialTick, entity.zOld, entity.getZ()) - playerZ;
            if (dx * dx + dz * dz > range * range) continue;
            float dotX = centerX + (float) projection.screenX(dx, dz) * pixelsPerBlock;
            float dotY = centerY + (float) projection.screenY(dx, dz) * pixelsPerBlock;
            float dotRadius = (entity instanceof Player ? 2.0F : 1.6F) * s;
            drawCircle(canvas, dotX, dotY, dotRadius + 0.75F * s,
                    UiTheme.withAlpha(UiTheme.SURFACE, 225));
            drawCircle(canvas, dotX, dotY, dotRadius, entityColor(entity));
        }
        drawSelf(canvas, centerX, centerY, s, yaw, projection);
    }

    private void drawBackground(Canvas canvas, float x, float y, float size, float scale) {
        if (!background.get()) return;
        float radius = Math.min((float) (cornerRadius.get() * scale), size * 0.5F);
        int color = UiTheme.withAlpha(backgroundColor.get().getRGB(), backgroundOpacity.get());
        drawGlobalBlur(canvas, x, y, size, size, radius, color);
        SkijaUi.rounded(canvas, x, y, size, size, radius, hudSurfaceColor(color));
        SkijaUi.outline(canvas, x + 0.5F, y + 0.5F, size - 1.0F, size - 1.0F,
                Math.max(0.0F, radius - 0.5F), 0.75F,
                UiTheme.withAlpha(UiTheme.BORDER_STRONG, 150));
    }

    private void drawGuides(Canvas canvas, float x, float y, float radius, float scale) {
        int guideColor = UiTheme.withAlpha(UiTheme.TEXT_FAINT, 68);
        if (background.get() && backgroundStyle.is(BackgroundStyle.GRID)) {
            int save = canvas.save();
            try {
                canvas.clipRRect(RRect.makeXYWH(x - radius, y - radius,
                        radius * 2.0F, radius * 2.0F, radius), ClipMode.INTERSECT, true);
                for (int step = -1; step <= 1; step++) {
                    float offset = step * radius * 0.5F;
                    SkijaUi.line(canvas, x + offset, y - radius, x + offset, y + radius,
                            0.6F * scale, guideColor);
                    SkijaUi.line(canvas, x - radius, y + offset, x + radius, y + offset,
                            0.6F * scale, guideColor);
                }
            } finally {
                canvas.restoreToCount(save);
            }
        } else {
            SkijaUi.outline(canvas, x - radius * 0.5F, y - radius * 0.5F,
                    radius, radius, radius * 0.5F, 0.6F * scale, guideColor);
            SkijaUi.line(canvas, x - radius, y, x + radius, y, 0.6F * scale, guideColor);
            SkijaUi.line(canvas, x, y - radius, x, y + radius, 0.6F * scale, guideColor);
        }
        SkijaUi.outline(canvas, x - radius, y - radius, radius * 2.0F, radius * 2.0F,
                radius, 0.75F * scale, UiTheme.withAlpha(UiTheme.TEXT_FAINT, 105));
        for (int index = 0; index < 8; index++) {
            double angle = index * Math.PI * 0.25;
            float dx = (float) Math.sin(angle);
            float dy = (float) Math.cos(angle);
            SkijaUi.line(canvas, x + dx * (radius - 2.0F * scale),
                    y + dy * (radius - 2.0F * scale), x + dx * radius, y + dy * radius,
                    0.75F * scale, UiTheme.withAlpha(UiTheme.TEXT_MUTED, 125));
        }
    }

    private static void drawCompass(Canvas canvas, float x, float y, float radius, float scale,
                                    RadarProjection projection) {
        float fontSize = 6.0F * scale;
        for (int index = 0; index < CARDINALS.length; index++) {
            String label = CARDINALS[index];
            float labelX = x + (float) projection.screenX(CARDINAL_X[index], CARDINAL_Z[index]) * radius;
            float labelY = y + (float) projection.screenY(CARDINAL_X[index], CARDINAL_Z[index]) * radius;
            SkijaUi.text(canvas, label, labelX - SkijaUi.textWidth(label, fontSize) * 0.5F,
                    labelY - 4.0F * scale, 8.0F * scale,
                    index == 0 ? UiTheme.accent() : UiTheme.TEXT_FAINT, fontSize);
        }
    }

    private static void drawSelf(Canvas canvas, float x, float y, float scale, float yaw,
                                 RadarProjection projection) {
        double angle = Math.toRadians(yaw);
        float dx = (float) projection.screenX(-Math.sin(angle), Math.cos(angle));
        float dy = (float) projection.screenY(-Math.sin(angle), Math.cos(angle));
        try (Path arrow = Path.makePolygon(new Point[]{
                new Point(x + dx * 4.5F * scale, y + dy * 4.5F * scale),
                new Point(x - dx * 3.0F * scale - dy * 3.0F * scale,
                        y - dy * 3.0F * scale + dx * 3.0F * scale),
                new Point(x - dx * 1.2F * scale, y - dy * 1.2F * scale),
                new Point(x - dx * 3.0F * scale + dy * 3.0F * scale,
                        y - dy * 3.0F * scale - dx * 3.0F * scale)
        }, true)) {
            SkijaUi.outlinePath(canvas, arrow, 1.5F * scale, UiTheme.SURFACE);
            SkijaUi.fillPath(canvas, arrow, UiTheme.accent());
        }
    }

    private boolean include(Entity entity) {
        return switch (filter.get()) {
            case Players -> entity instanceof Player;
            case Mobs -> entity instanceof Monster;
            case Animals -> entity instanceof Animal;
            case All -> entity instanceof Player || entity instanceof Monster || entity instanceof Animal;
        };
    }

    private int entityColor(Entity entity) {
        if (entity instanceof Player player && FriendManager.INSTANCE.isFriend(player)) return UiTheme.SUCCESS;
        if (entity instanceof Monster) return UiTheme.DANGER;
        if (entity instanceof Animal) return UiTheme.WARNING;
        return UiTheme.INFO;
    }

    private static void drawCircle(Canvas canvas, float centerX, float centerY, float radius, int color) {
        if (radius <= 0.0F) return;
        SkijaUi.rounded(canvas, centerX - radius, centerY - radius,
                radius * 2.0F, radius * 2.0F, radius, color);
    }

    @Override
    public int editorColor() {
        return UiTheme.INFO;
    }

    @Override
    public String getInfo() {
        return filter.get().name();
    }
}
