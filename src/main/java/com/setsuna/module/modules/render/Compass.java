package com.setsuna.module.modules.render;

import com.setsuna.event.Listen;
import com.setsuna.event.events.Render2DEvent;
import com.setsuna.manager.FriendManager;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.render.SkijaUi;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Path;
import io.github.humbleui.types.Point;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/** Draws nearby players as directional arrows around the screen centre. */
public final class Compass extends Module {

    public static final Compass INSTANCE = new Compass();

    private static final float RADIUS = 55.0F;
    private static final float ARROW_SIZE = 7.0F;
    private static final float TEXT_SIZE = 9.0F;
    private static final double MAX_DISTANCE = 128.0;
    private static final double CLOSE_ENEMY_DISTANCE = 15.0;
    private static final int COLOR = 0xE6FFFFFF;
    private static final int CLOSE_ENEMY_COLOR = 0xF2FF3B30;

    private Paint arrowPaint;

    private Compass() {
        super("Compass", Category.RENDER);
    }

    @Override
    protected void onEnable() {
        arrowPaint = new Paint().setAntiAlias(true).setColor(COLOR);
    }

    @Override
    protected void onDisable() {
        if (arrowPaint != null) {
            arrowPaint.close();
            arrowPaint = null;
        }
    }

    @Listen
    private void onRender2D(Render2DEvent event) {
        if (noPlayer() || arrowPaint == null) {
            return;
        }

        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        float centerX = event.width() / 2.0F;
        float centerY = event.height() / 2.0F;
        double selfX = Mth.lerp(partialTick, mc.player.xOld, mc.player.getX());
        double selfZ = Mth.lerp(partialTick, mc.player.zOld, mc.player.getZ());
        float yawRadians = (float) Math.toRadians(mc.player.getYRot(partialTick));
        Canvas canvas = event.canvas();

        for (Player target : mc.level.players()) {
            if (target == mc.player || !target.isAlive() || target.isSpectator()) {
                continue;
            }

            double distance = mc.player.distanceTo(target);
            if (distance > MAX_DISTANCE) {
                continue;
            }

            double targetX = Mth.lerp(partialTick, target.xOld, target.getX());
            double targetZ = Mth.lerp(partialTick, target.zOld, target.getZ());
            double angle = Math.atan2(targetZ - selfZ, targetX - selfX)
                    - yawRadians - Math.PI / 2.0;
            float x = centerX + (float) (Math.sin(angle) * RADIUS);
            float y = centerY - (float) (Math.cos(angle) * RADIUS);

            boolean closeEnemy = distance <= CLOSE_ENEMY_DISTANCE
                    && !FriendManager.INSTANCE.isFriend(target);
            arrowPaint.setColor(closeEnemy ? CLOSE_ENEMY_COLOR : COLOR);
            drawArrow(canvas, x, y, (float) angle);

            String distanceText = Math.round(distance) + "m";
            float textWidth = SkijaUi.textWidth(distanceText, TEXT_SIZE);
            SkijaUi.text(canvas, distanceText, x - textWidth / 2.0F,
                    y - ARROW_SIZE - TEXT_SIZE, TEXT_SIZE, COLOR, TEXT_SIZE);
        }
    }

    private void drawArrow(Canvas canvas, float centerX, float centerY, float angle) {
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);

        Point tip = rotatePoint(centerX, centerY, 0.0F, -ARROW_SIZE, cos, sin);
        Point left = rotatePoint(centerX, centerY,
                -ARROW_SIZE * 0.65F, ARROW_SIZE * 0.8F, cos, sin);
        Point right = rotatePoint(centerX, centerY,
                ARROW_SIZE * 0.65F, ARROW_SIZE * 0.8F, cos, sin);

        try (Path path = Path.makePolygon(new Point[]{tip, left, right}, true)) {
            canvas.drawPath(path, arrowPaint);
        }
    }

    private static Point rotatePoint(float centerX, float centerY, float x, float y,
                                     float cos, float sin) {
        return new Point(
                centerX + x * cos - y * sin,
                centerY + x * sin + y * cos);
    }
}
