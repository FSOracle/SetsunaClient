package com.setsuna.ui.hud;

import com.setsuna.event.events.Render2DEvent;
import com.setsuna.manager.FriendManager;
import com.setsuna.render.SkijaUi;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.DoubleSetting;
import com.setsuna.setting.settings.EnumSetting;
import com.setsuna.ui.UiTheme;
import io.github.humbleui.skija.Canvas;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;

/** Minimal square entity radar with a fixed visual hierarchy. */
public final class RadarHUD extends EpsilonHudModule {

    public static final RadarHUD INSTANCE = new RadarHUD();

    public enum Filter { Players, Mobs, Animals, All }

    public final DoubleSetting scale = add(new DoubleSetting("Scale", 1.0, 0.65, 1.8, 0.05));
    public final DoubleSetting radarRange = add(new DoubleSetting("Range", 64.0, 8.0, 192.0, 4.0));
    public final EnumSetting<Filter> filter = add(new EnumSetting<>("Filter", Filter.All));
    public final BooleanSetting rotateFacing = add(new BooleanSetting("Rotate With Facing", true));

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
        float centerY = y + size * 0.5F;
        float radius = size * 0.5F - 7.0F * s;
        updateBounds(size, size);
        Canvas canvas = event.canvas();

        HudRenderUtil.panel(canvas, x, y, size, size, 170);
        SkijaUi.fill(canvas, x + 6.0F * s, centerY, size - 12.0F * s,
                Math.max(1.0F, 0.6F * s), UiTheme.withAlpha(UiTheme.BORDER, 120));
        SkijaUi.fill(canvas, centerX, y + 6.0F * s, Math.max(1.0F, 0.6F * s),
                size - 12.0F * s, UiTheme.withAlpha(UiTheme.BORDER, 120));
        drawCircle(canvas, centerX, centerY, radius * 0.5F,
                UiTheme.withAlpha(UiTheme.BORDER, 95));
        drawCircle(canvas, centerX, centerY, Math.max(0.0F, radius * 0.5F - 1.0F * s),
                UiTheme.withAlpha(UiTheme.SURFACE, 168));

        double yaw = rotateFacing.get() ? Math.toRadians(mc.player.getYRot()) : 0.0;
        double range = radarRange.get();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity == mc.player || !include(entity)) continue;
            double dx = entity.getX() - mc.player.getX();
            double dz = entity.getZ() - mc.player.getZ();
            double distance = Math.hypot(dx, dz);
            if (distance > range) continue;
            double angle = Math.atan2(dz, dx) - yaw - Math.PI * 0.5;
            float travel = (float) Math.min(1.0, distance / range) * (radius - 3.0F * s);
            float dotX = centerX + (float) Math.sin(angle) * travel;
            float dotY = centerY - (float) Math.cos(angle) * travel;
            drawCircle(canvas, dotX, dotY, 2.0F * s, entityColor(entity));
        }
        drawCircle(canvas, centerX, centerY, 2.2F * s, UiTheme.accent());
        SkijaUi.fill(canvas, centerX - 1.0F * s, centerY - 6.0F * s,
                2.0F * s, 5.0F * s, UiTheme.accent());
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
