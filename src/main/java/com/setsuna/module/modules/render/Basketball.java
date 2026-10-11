package com.setsuna.module.modules.render;

import com.setsuna.event.Listen;
import com.setsuna.event.events.Render2DEvent;
import com.setsuna.event.events.Render3DEvent;
import com.setsuna.event.events.TickEvent;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.render.SkijaUi;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.ColorSetting;
import com.setsuna.setting.settings.DoubleSetting;
import com.setsuna.setting.settings.KeybindSetting;
import com.setsuna.util.client.KeybindUtils;
import com.setsuna.util.render.Render3DUtils;
import com.setsuna.util.render.WorldToScreen;
import io.github.humbleui.skija.Canvas;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import org.joml.Vector3f;

import java.awt.Color;
import java.util.concurrent.ThreadLocalRandom;

/** A render-only basketball mini-game with no world or network mutation. */
public final class Basketball extends Module {

    public static final Basketball INSTANCE = new Basketball();

    private static final double FIELD_WIDTH = 24.0D;
    private static final double FIELD_LENGTH = 40.0D;
    private static final double HOOP_WIDTH = 2.2D;
    private static final double HOOP_DEPTH = 1.2D;
    private static final double HOOP_HEIGHT = 3.0D;
    private static final double PENALTY_WIDTH = 6.0D;
    private static final double PENALTY_DEPTH = 5.8D;
    private static final double DEFENDER_FREE_THROW_Z = -FIELD_LENGTH * 0.5D + PENALTY_DEPTH;
    private static final double CENTER_CIRCLE_RADIUS = 3.2D;
    private static final double PENALTY_SPOT_OFFSET = 4.8D;
    private static final double BALL_RADIUS = 0.42D;
    private static final double DRIBBLE_DISTANCE = 0.82D;
    private static final double PLAYER_CONTACT_DISTANCE = 1.12D;
    private static final double PLAYER_TAKE_FOV_COSINE = Math.cos(Math.toRadians(60.0D));
    private static final double KICKOFF_DISTANCE = 6.0D;
    private static final double PLAYER_KICKOFF_Z = -KICKOFF_DISTANCE;
    private static final double DEFENDER_KICKOFF_Z = KICKOFF_DISTANCE;
    private static final double DEFENDER_CONTACT_DISTANCE = 0.95D;
    private static final double ATTACK_DEFENDER_SPEED = 5.4D / 20.0D;
    private static final double DEFENDER_DRIBBLE_DISTANCE = 0.72D;
    private static final double GRAVITY = 0.055D;
    private static final double FLOOR_BOUNCE = 0.28D;
    private static final double WALL_BOUNCE = 0.55D;
    private static final double AIR_DRAG = 0.992D;
    private static final double GROUND_ROLLING = 0.985D;
    private static final int FULL_CHARGE_TICKS = 40;
    private static final int KICKOFF_COUNTDOWN_TICKS = 32;
    private static final int STEAL_COOLDOWN_TICKS = 10;
    private static final float FIELD_LINE_WIDTH = 2.5F;
    private static final String DEFENDER_NAME = "FS_Overlxrd";
    private static final Identifier DEFENDER_SKIN = Identifier.fromNamespaceAndPath(
            "setsuna", "textures/football/fs_overlxrd.png");

    public final KeybindSetting kickKey = add(new KeybindSetting("Shoot Key", GLFW.GLFW_KEY_G));
    public final DoubleSetting kickStrength = add(new DoubleSetting(
            "Shot Strength", 0.85, 0.35, 1.8, 0.05));
    public final BooleanSetting showScore = add(new BooleanSetting("Show Score", true));
    public final BooleanSetting defender = add(new BooleanSetting("Defender", true));
    public final ColorSetting fieldColor = add(new ColorSetting(
            "Court Color", new Color(164, 96, 48, 90)));
    public final ColorSetting lineColor = add(new ColorSetting(
            "Line Color", new Color(255, 255, 255, 220)));
    public final ColorSetting hoopColor = add(new ColorSetting(
            "Hoop Color", new Color(255, 215, 80, 220)));
    public final ColorSetting backboardColor = add(new ColorSetting(
            "Backboard Color", new Color(235, 240, 255, 210)));
    public final ColorSetting ballColor = add(new ColorSetting(
            "Ball Color", new Color(216, 90, 32, 255)));

    private double originX;
    private double originY;
    private double originZ;
    private double ballX;
    private double ballY;
    private double ballZ;
    private double previousBallX;
    private double previousBallY;
    private double previousBallZ;
    private double velocityX;
    private double velocityY;
    private double velocityZ;
    private int playerScore;
    private int defenderScore;
    private boolean gameStarted;
    private int kickoffCountdownTicks;
    private int kickoffGoTicks;
    private double lastKickoffPlayerX;
    private double lastKickoffPlayerZ;
    private int kickCooldown;
    private int playerStealCooldownTicks;
    private int chargeTicks;
    private boolean charging;
    private boolean dribbling;
    private double dribblePhase;
    private double dribbleDirectionX;
    private double dribbleDirectionZ;
    private double defenderX;
    private double defenderZ;
    private double previousDefenderX;
    private double previousDefenderZ;
    private float defenderYaw;
    private float previousDefenderYaw;
    private float defenderWalkPhase;
    private float defenderLimbSwing;
    private float previousDefenderLimbSwing;
    private boolean defenderMovedThisTick;
    private int defenderKickCooldown;
    private boolean defenderHasBall;
    private int defenderChargeTicks;
    private int defenderChargeTargetTicks;
    private int defenderPossessionCooldown;
    private int defenderStunTicks;
    private double defenderDribbleDirectionX;
    private double defenderDribbleDirectionZ;
    private double defenderShotTargetX;
    private double defenderAvoidanceSide;
    private double defenderDribblePhase;
    private boolean defenderNameVisible;
    private float defenderNameX;
    private float defenderNameY;
    private float actionHudVisibility;
    private float actionHudChargeMix;
    private boolean lastStealAvailable;
    private long lastActionHudFrameNanos;

    private Basketball() {
        super("Basketball", Category.RENDER);
    }

    @Override
    protected void onEnable() {
        if (noPlayer()) {
            return;
        }
        if (Football.INSTANCE.isEnabled()) {
            Football.INSTANCE.setEnabled(false);
        }
        originX = mc.player.getX();
        originY = mc.player.getY() + 0.05D;
        // Put the player on one side of the kickoff ball and leave equal room
        // for the attacking defender on the other side.
        originZ = mc.player.getZ() + KICKOFF_DISTANCE;
        playerScore = 0;
        defenderScore = 0;
        gameStarted = false;
        kickoffCountdownTicks = 0;
        kickoffGoTicks = 0;
        lastKickoffPlayerX = mc.player.getX();
        lastKickoffPlayerZ = mc.player.getZ();
        resetDefender();
        resetDefenderForKickoff();
        dribbling = false;
        dribbleDirectionX = 0.0D;
        dribbleDirectionZ = 1.0D;
        dribblePhase = 0.0D;
        actionHudVisibility = 0.0F;
        actionHudChargeMix = 0.0F;
        lastStealAvailable = false;
        lastActionHudFrameNanos = System.nanoTime();
        resetBall();
    }

    @Override
    protected void onDisable() {
        velocityX = 0.0D;
        velocityY = 0.0D;
        velocityZ = 0.0D;
        playerScore = 0;
        defenderScore = 0;
        gameStarted = false;
        kickoffCountdownTicks = 0;
        kickoffGoTicks = 0;
        playerStealCooldownTicks = 0;
        chargeTicks = 0;
        charging = false;
        dribbling = false;
        dribblePhase = 0.0D;
        defenderNameVisible = false;
        actionHudVisibility = 0.0F;
        actionHudChargeMix = 0.0F;
    }

    @Listen
    private void onTick(TickEvent.Post event) {
        if (noPlayer()) {
            return;
        }
        if (kickCooldown > 0) {
            kickCooldown--;
        }
        if (defenderKickCooldown > 0) {
            defenderKickCooldown--;
        }
        if (defenderPossessionCooldown > 0) {
            defenderPossessionCooldown--;
        }
        if (playerStealCooldownTicks > 0) {
            playerStealCooldownTicks--;
        }
        if (kickoffGoTicks > 0) {
            kickoffGoTicks--;
        }
        snapshotBallPosition();
        snapshotDefenderPosition();
        if (!gameStarted) {
            updateKickoffCountdown();
            return;
        }
        updateDribble();
        updateCharge();
        updateDefender();
        updateDefenderAnimation();
        if (!dribbling && !defenderHasBall) {
            simulateBall();
        }
    }

    private void updateDribble() {
        boolean holding = mc.screen == null
                && kickKey.isBound()
                && KeybindUtils.isPressed(kickKey.get());
        if (defenderHasBall) {
            if (holding && canPlayerTakeBall()) {
                releaseDefenderPossession(12);
                defenderStunTicks = STEAL_COOLDOWN_TICKS;
                playerStealCooldownTicks = STEAL_COOLDOWN_TICKS;
                dribbling = true;
                Vec3 look = horizontalLook();
                dribbleDirectionX = look.x;
                dribbleDirectionZ = look.z;
            }
            return;
        }
        if (!dribbling) {
            if (holding && canPlayerTakeBall()) {
                dribbling = true;
                Vec3 look = horizontalLook();
                dribbleDirectionX = look.x;
                dribbleDirectionZ = look.z;
            }
            return;
        }
        if (!holding) {
            if (!charging) {
                dribbling = false;
            }
            return;
        }
        if (playerOutsideField()) {
            kickBall(Math.max(0.3D, chargeTicks / (double) FULL_CHARGE_TICKS));
            charging = false;
            chargeTicks = 0;
            return;
        }

        Vec3 look = horizontalLook();
        double dot = dribbleDirectionX * look.x + dribbleDirectionZ * look.z;
        double response = dot > 0.82D ? 1.0D : 0.13D;
        dribbleDirectionX = dribbleDirectionX * (1.0D - response) + look.x * response;
        dribbleDirectionZ = dribbleDirectionZ * (1.0D - response) + look.z * response;
        double directionLength = Math.sqrt(dribbleDirectionX * dribbleDirectionX
                + dribbleDirectionZ * dribbleDirectionZ);
        if (directionLength > 1.0E-4D) {
            dribbleDirectionX /= directionLength;
            dribbleDirectionZ /= directionLength;
        }

        Vec3 player = mc.player.position();
        Vec3 target = player.add(dribbleDirectionX * DRIBBLE_DISTANCE,
                0.0D, dribbleDirectionZ * DRIBBLE_DISTANCE);
        ballX = target.x - originX;
        ballZ = target.z - originZ;
        dribblePhase += 0.38D;
        ballY = BALL_RADIUS + Math.abs(Math.sin(dribblePhase)) * 0.58D;
        velocityX = 0.0D;
        velocityY = 0.0D;
        velocityZ = 0.0D;
    }

    private boolean canPlayerTakeBall() {
        if (noPlayer()) {
            return false;
        }
        if (kickCooldown > 0 || playerOutsideField()) {
            return false;
        }
        Vec3 look = horizontalLook();
        Vec3 relative = worldPosition(ballX, 0.0D, ballZ).subtract(mc.player.position());
        double distance = Math.sqrt(relative.x * relative.x + relative.z * relative.z);
        if (distance > PLAYER_CONTACT_DISTANCE || distance < 1.0E-4D) {
            return false;
        }
        double forward = relative.x * look.x + relative.z * look.z;
        return forward / distance >= PLAYER_TAKE_FOV_COSINE;
    }

    private Vec3 horizontalLook() {
        Vec3 look = mc.player.getLookAngle();
        double length = Math.sqrt(look.x * look.x + look.z * look.z);
        if (length < 1.0E-4D) {
            return new Vec3(0.0D, 0.0D, 1.0D);
        }
        return new Vec3(look.x / length, 0.0D, look.z / length);
    }

    private boolean playerOutsideField() {
        double localX = mc.player.getX() - originX;
        double localZ = mc.player.getZ() - originZ;
        return Math.abs(localX) >= FIELD_WIDTH * 0.5D - 0.3D
                || Math.abs(localZ) >= FIELD_LENGTH * 0.5D - 0.3D;
    }

    private void updateCharge() {
        if (playerStealCooldownTicks > 0) {
            charging = false;
            chargeTicks = 0;
            return;
        }
        if (defenderHasBall) {
            charging = false;
            chargeTicks = 0;
            return;
        }
        boolean kickHeld = kickKey.isBound() && KeybindUtils.isPressed(kickKey.get());
        boolean holding = mc.screen == null
                && kickHeld
                && dribbling
                && kickCooldown <= 0;
        if (holding) {
            charging = true;
            chargeTicks = Math.min(FULL_CHARGE_TICKS, chargeTicks + 1);
            return;
        }
        if (charging) {
            if (dribbling) {
                kickBall(chargeTicks / (double) FULL_CHARGE_TICKS);
            }
            charging = false;
            chargeTicks = 0;
        }
    }

    private void kickBall(double charge) {
        if (!dribbling || defenderHasBall) {
            return;
        }
        Vec3 look = mc.player.getLookAngle();
        double horizontalLength = Math.sqrt(look.x * look.x + look.z * look.z);
        if (horizontalLength < 1.0E-4D) {
            return;
        }

        double clampedCharge = Math.max(0.08D, Math.min(1.0D, charge));
        double power = kickStrength.get() * clampedCharge;
        Vec3 release = mc.player.getEyePosition()
                .add(look.scale(0.72D))
                .add(0.0D, -0.48D, 0.0D);
        ballX = release.x - originX;
        ballY = Math.max(BALL_RADIUS, release.y - originY);
        ballZ = release.z - originZ;
        previousBallX = ballX;
        previousBallY = ballY;
        previousBallZ = ballZ;

        velocityX = look.x * power;
        velocityZ = look.z * power;
        boolean jumpShot = mc.options.keyJump.isDown()
                || !mc.player.onGround() && mc.player.getDeltaMovement().y > 0.05D;
        double arc = jumpShot
                ? 0.44D + clampedCharge * 0.20D
                : 0.32D + clampedCharge * 0.18D;
        velocityY = look.y * power * 0.70D + arc;
        dribbling = false;
        kickCooldown = 5;
    }

    @Listen
    private void onRender3D(Render3DEvent event) {
        if (noPlayer()) {
            return;
        }
        currentPoseStack = event.getPoseStack();

        AABB field = new AABB(
                originX - FIELD_WIDTH * 0.5D, originY - 0.025D, originZ - FIELD_LENGTH * 0.5D,
                originX + FIELD_WIDTH * 0.5D, originY + 0.025D, originZ + FIELD_LENGTH * 0.5D);
        Render3DUtils.drawFilledBox(field, fieldColor.get());

        drawRectangle(-FIELD_WIDTH * 0.5D, FIELD_WIDTH * 0.5D,
                -FIELD_LENGTH * 0.5D, FIELD_LENGTH * 0.5D);
        drawLine(worldPosition(-FIELD_WIDTH * 0.5D, 0.065D, 0.0D),
                worldPosition(FIELD_WIDTH * 0.5D, 0.065D, 0.0D));
        drawCircle(0.0D, 0.065D, 0.0D, CENTER_CIRCLE_RADIUS, 48);
        drawCircle(0.0D, 0.065D, 0.0D, 0.14D, 16);
        drawPenaltyArea(-1.0D);
        drawPenaltyArea(1.0D);
        drawHoop(1.0D);
        if (defenderActive()) {
            drawHoop(-1.0D);
        }
        drawKickoffPoint();

        float partialTick = Mth.clamp(
                mc.getDeltaTracker().getGameTimeDeltaPartialTick(true), 0.0F, 1.0F);
        defenderNameVisible = false;
        if (defenderActive()) {
            drawDefender(partialTick);
        }
        drawRoundBall(worldPosition(
                Mth.lerp(partialTick, previousBallX, ballX),
                Mth.lerp(partialTick, previousBallY, ballY),
                Mth.lerp(partialTick, previousBallZ, ballZ)));
        currentPoseStack = null;
    }

    @Listen
    private void onRender2D(Render2DEvent event) {
        if (noPlayer()) {
            return;
        }
        Canvas canvas = event.canvas();
        if (showScore.get()) {
            float width = 116.0F;
            float x = event.width() * 0.5F - width * 0.5F;
            SkijaUi.rounded(canvas, x, 16.0F, width, 34.0F, 6.0F, 0xB810141C);
            SkijaUi.text(canvas, "BASKETBALL", x + 12.0F, 21.0F, 9.0F, 0xFFFFFFFF, 7.0F);
            SkijaUi.boldText(canvas, "POINTS  " + playerScore + " : " + defenderScore,
                    x + 12.0F, 34.0F, 12.0F, 0xFFFFFFFF, 11.0F);
        }
        drawActionHud(canvas, event.width(), event.height());
        if (!gameStarted || kickoffGoTicks > 0) {
            float width = 150.0F;
            float x = event.width() * 0.5F - width * 0.5F;
            float y = event.height() - 90.0F;
            SkijaUi.rounded(canvas, x, y, width, 22.0F, 5.0F, 0xD910141C);
            if (kickoffGoTicks > 0) {
                float textWidth = SkijaUi.boldTextWidth("GO!", 11.0F);
                SkijaUi.boldText(canvas, "GO!", x + (width - textWidth) * 0.5F,
                        y + 2.0F, 18.0F, 0xFFFFCF5A, 11.0F);
            } else {
                boolean bothReady = playerAtKickoff();
                float progress = bothReady
                        ? kickoffCountdownTicks / (float) KICKOFF_COUNTDOWN_TICKS : 0.0F;
                SkijaUi.rounded(canvas, x + 10.0F, y + 16.0F, width - 20.0F, 3.0F,
                        1.5F, 0x66727A86);
                SkijaUi.rounded(canvas, x + 10.0F, y + 16.0F,
                        (width - 20.0F) * progress, 3.0F, 1.5F, 0xFFFFCF5A);
                int seconds = Math.max(1, (int) Math.ceil(
                        (KICKOFF_COUNTDOWN_TICKS - kickoffCountdownTicks) / 20.0D));
                String message;
                if (!playerAtKickoff()) {
                    message = "RETURN TO RESTART";
                } else {
                    message = "RESTART IN " + seconds;
                }
                SkijaUi.text(canvas, message,
                        x + 12.0F, y + 1.0F, 14.0F, 0xFFFFCF5A, 8.0F);
            }
        }
        drawDefenderName(canvas);
    }

    private void drawActionHud(Canvas canvas, float screenWidth, float screenHeight) {
        boolean promptVisible = defenderActive() && defenderHasBall;
        boolean cooldownVisible = playerStealCooldownTicks > 0;
        boolean chargeVisible = cooldownVisible || dribbling || charging || chargeTicks > 0;
        if (promptVisible) {
            lastStealAvailable = canPlayerTakeBall();
        }
        updateActionHudAnimation(promptVisible || chargeVisible, chargeVisible);
        if (actionHudVisibility < 0.01F) {
            return;
        }

        float mix = smoothStep(actionHudChargeMix);
        float promptAlpha = actionHudVisibility * (1.0F - mix);
        float chargeAlpha = actionHudVisibility * mix;
        float width = Mth.lerp(mix, 112.0F, 96.0F);
        float x = screenWidth * 0.5F - width * 0.5F;
        float y = screenHeight - 45.0F;
        SkijaUi.rounded(canvas, x, y, width, 26.0F, 5.0F,
                withAlpha(0xFF10141C, actionHudVisibility * 0.78F));

        if (promptAlpha > 0.01F) {
            boolean canSteal = lastStealAvailable;
            int statusColor = canSteal ? 0xFF53D98C : 0xFFFF5964;
            SkijaUi.rounded(canvas, x + 9.0F, y + 9.0F, 8.0F, 8.0F, 4.0F,
                    withAlpha(statusColor, promptAlpha));
            SkijaUi.text(canvas, canSteal ? "STEAL READY" : "CANNOT STEAL",
                    x + 24.0F, y + 5.0F, 16.0F,
                    withAlpha(statusColor, promptAlpha), 8.0F);
        }

        if (chargeAlpha > 0.01F) {
            float barX = x + 9.0F;
            float barWidth = width - 18.0F;
            float progress;
            int fillColor;
            String label;
            if (cooldownVisible) {
                progress = playerStealCooldownTicks / (float) STEAL_COOLDOWN_TICKS;
                fillColor = 0xFF53D98C;
                label = "STEAL COOLDOWN";
            } else {
                progress = Mth.clamp(chargeTicks / (float) FULL_CHARGE_TICKS, 0.0F, 1.0F);
                fillColor = 0xFFF5C542;
                label = "CHARGE " + Math.round(progress * 100.0F) + "%";
            }
            SkijaUi.text(canvas, label, barX, y + 2.0F, 11.0F,
                    withAlpha(0xFFFFFFFF, chargeAlpha), 7.0F);
            SkijaUi.rounded(canvas, barX, y + 18.0F, barWidth, 3.0F, 1.5F,
                    withAlpha(0x66727A86, chargeAlpha));
            SkijaUi.rounded(canvas, barX, y + 18.0F, barWidth * progress, 3.0F, 1.5F,
                    withAlpha(fillColor, chargeAlpha));
        }
    }

    private void updateActionHudAnimation(boolean visible, boolean chargeMode) {
        long now = System.nanoTime();
        float delta = Math.min(0.05F, Math.max(0.0F,
                (now - lastActionHudFrameNanos) / 1_000_000_000.0F));
        lastActionHudFrameNanos = now;
        if (actionHudVisibility < 0.01F && chargeMode) {
            actionHudChargeMix = 1.0F;
        }
        actionHudVisibility = animate(actionHudVisibility, visible ? 1.0F : 0.0F, 11.0F, delta);
        actionHudChargeMix = animate(actionHudChargeMix, chargeMode ? 1.0F : 0.0F, 13.0F, delta);
    }

    private static float animate(float current, float target, float speed, float delta) {
        float result = current + (target - current) * (1.0F - (float) Math.exp(-speed * delta));
        return Math.abs(result - target) < 0.001F ? target : result;
    }

    private static float smoothStep(float value) {
        float clamped = Mth.clamp(value, 0.0F, 1.0F);
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    private static int withAlpha(int color, float alpha) {
        int value = Mth.clamp(Math.round((color >>> 24) * Mth.clamp(alpha, 0.0F, 1.0F)), 0, 255);
        return color & 0x00FFFFFF | value << 24;
    }

    private void simulateBall() {
        ballX += velocityX;
        ballY += velocityY;
        ballZ += velocityZ;
        velocityY -= GRAVITY;
        velocityX *= AIR_DRAG;
        velocityZ *= AIR_DRAG;

        if (ballY < BALL_RADIUS) {
            ballY = BALL_RADIUS;
            if (Math.abs(velocityY) > 0.04D) {
                velocityY = -velocityY * FLOOR_BOUNCE;
            } else {
                velocityY = 0.0D;
            }
            velocityX *= GROUND_ROLLING;
            velocityZ *= GROUND_ROLLING;
        }
        double sideLimit = FIELD_WIDTH * 0.5D - BALL_RADIUS;
        if (Math.abs(ballX) > sideLimit) {
            ballX = Math.copySign(sideLimit, ballX);
            velocityX = -velocityX * WALL_BOUNCE;
        }
        double end = FIELD_LENGTH * 0.5D;
        if (Math.abs(ballZ) > end) {
            boolean insideHoop = Math.abs(ballX) <= HOOP_WIDTH * 0.5D
                    && ballY >= HOOP_HEIGHT - 0.75D && ballY <= HOOP_HEIGHT + 0.55D;
            if (insideHoop && ballZ > 0.0D) {
                playerScore++;
                beginKickoff();
            } else if (insideHoop && ballZ < 0.0D
                    && defenderActive()) {
                defenderScore++;
                beginKickoff();
            } else {
                ballZ = Math.copySign(end - BALL_RADIUS, ballZ);
                velocityZ = -velocityZ * WALL_BOUNCE;
            }
        }
    }

    private void updateDefender() {
        if (!defenderActive()) {
            resetDefender();
            return;
        }

        updateAttackingDefender();
        defenderYaw = yawTowards(ballX - defenderX, ballZ - defenderZ, defenderYaw);
    }

    private void updateAttackingDefender() {
        if (defenderStunTicks > 0) {
            defenderStunTicks--;
            return;
        }
        if (defenderHasBall) {
            updateDefenderPossession();
            return;
        }

        moveDefenderTowards(ballX, ballZ, ATTACK_DEFENDER_SPEED);
        double dx = ballX - defenderX;
        double dz = ballZ - defenderZ;
        if (defenderKickCooldown > 0 || defenderPossessionCooldown > 0
                || ballY > 1.25D
                || dx * dx + dz * dz > DEFENDER_CONTACT_DISTANCE * DEFENDER_CONTACT_DISTANCE) {
            return;
        }

        takeDefenderPossession();
    }

    private void takeDefenderPossession() {
        if (dribbling || charging) {
            kickCooldown = Math.max(kickCooldown, 8);
        }
        dribbling = false;
        playerStealCooldownTicks = 0;
        defenderHasBall = true;
        defenderChargeTicks = 0;
        defenderChargeTargetTicks = ThreadLocalRandom.current().nextInt(28, FULL_CHARGE_TICKS + 1);
        defenderShotTargetX = ThreadLocalRandom.current().nextDouble(
                -HOOP_WIDTH * 0.32D, HOOP_WIDTH * 0.32D);
        defenderAvoidanceSide = ThreadLocalRandom.current().nextBoolean() ? 1.0D : -1.0D;
        defenderDribbleDirectionX = 0.0D;
        defenderDribbleDirectionZ = -1.0D;
        charging = false;
        chargeTicks = 0;
        velocityX = 0.0D;
        velocityY = 0.0D;
        velocityZ = 0.0D;
    }

    private void updateDefenderPossession() {
        double hoopTargetZ = -FIELD_LENGTH * 0.5D;
        double hoopDx = defenderShotTargetX - defenderX;
        double hoopDz = hoopTargetZ - defenderZ;
        double hoopDistance = Math.sqrt(hoopDx * hoopDx + hoopDz * hoopDz);
        if (hoopDistance > 1.0E-4D) {
            hoopDx /= hoopDistance;
            hoopDz /= hoopDistance;
        } else {
            hoopDx = 0.0D;
            hoopDz = -1.0D;
        }

        double playerX = mc.player.getX() - originX;
        double playerZ = mc.player.getZ() - originZ;
        double playerDx = playerX - defenderX;
        double playerDz = playerZ - defenderZ;
        double playerDistance = Math.sqrt(playerDx * playerDx + playerDz * playerDz);
        double desiredX = hoopDx;
        double desiredZ = hoopDz;
        if (playerDistance < 4.5D && playerDistance > 1.0E-4D) {
            double proximity = 1.0D - playerDistance / 4.5D;
            double cross = hoopDx * playerDz - hoopDz * playerDx;
            if (Math.abs(cross) > 0.08D) {
                defenderAvoidanceSide = cross > 0.0D ? -1.0D : 1.0D;
            }
            desiredX += hoopDz * defenderAvoidanceSide * proximity * 1.45D;
            desiredZ -= hoopDx * defenderAvoidanceSide * proximity * 1.45D;
            desiredX -= playerDx / playerDistance * proximity * 0.35D;
            desiredZ -= playerDz / playerDistance * proximity * 0.35D;
        }
        desiredZ = Math.min(-0.2D, desiredZ);
        double desiredLength = Math.sqrt(desiredX * desiredX + desiredZ * desiredZ);
        desiredX /= desiredLength;
        desiredZ /= desiredLength;

        defenderDribbleDirectionX = Mth.lerp(0.28D, defenderDribbleDirectionX, desiredX);
        defenderDribbleDirectionZ = Mth.lerp(0.28D, defenderDribbleDirectionZ, desiredZ);
        double directionLength = Math.sqrt(defenderDribbleDirectionX * defenderDribbleDirectionX
                + defenderDribbleDirectionZ * defenderDribbleDirectionZ);
        if (directionLength > 1.0E-4D) {
            defenderDribbleDirectionX /= directionLength;
            defenderDribbleDirectionZ /= directionLength;
        }

        moveDefenderTowards(defenderX + defenderDribbleDirectionX,
                defenderZ + defenderDribbleDirectionZ, ATTACK_DEFENDER_SPEED);
        if (defenderZ <= DEFENDER_FREE_THROW_Z + 1.0E-4D) {
            defenderZ = DEFENDER_FREE_THROW_Z;
            ballX = Mth.clamp(defenderX + defenderDribbleDirectionX * DEFENDER_DRIBBLE_DISTANCE,
                    -FIELD_WIDTH * 0.5D + BALL_RADIUS, FIELD_WIDTH * 0.5D - BALL_RADIUS);
            ballZ = defenderZ;
            ballY = Math.max(BALL_RADIUS, ballY);
            defenderChargeTicks = Math.max(defenderChargeTicks,
                    Math.round(FULL_CHARGE_TICKS * 0.70F));
            defenderShoot();
            return;
        }
        ballX = Mth.clamp(defenderX + defenderDribbleDirectionX * DEFENDER_DRIBBLE_DISTANCE,
                -FIELD_WIDTH * 0.5D + BALL_RADIUS, FIELD_WIDTH * 0.5D - BALL_RADIUS);
        ballZ = Mth.clamp(defenderZ + defenderDribbleDirectionZ * DEFENDER_DRIBBLE_DISTANCE,
                -FIELD_LENGTH * 0.5D + BALL_RADIUS, FIELD_LENGTH * 0.5D - BALL_RADIUS);
        defenderDribblePhase += 0.38D;
        ballY = BALL_RADIUS + Math.abs(Math.sin(defenderDribblePhase)) * 0.58D;
        velocityX = 0.0D;
        velocityY = 0.0D;
        velocityZ = 0.0D;

        defenderChargeTicks++;
        if (defenderChargeTicks >= defenderChargeTargetTicks) {
            defenderShoot();
        }
    }

    private void defenderShoot() {
        double targetZ = -FIELD_LENGTH * 0.5D - HOOP_DEPTH * 0.5D;
        double directionX = defenderShotTargetX - ballX;
        double directionZ = targetZ - ballZ;
        double length = Math.sqrt(directionX * directionX + directionZ * directionZ);
        if (length < 1.0E-4D) {
            directionX = 0.0D;
            directionZ = -1.0D;
            length = 1.0D;
        }

        double charge = defenderChargeTicks / (double) FULL_CHARGE_TICKS;
        double power = 0.68D + charge * 0.24D;
        velocityX = directionX / length * power;
        velocityZ = directionZ / length * power;
        velocityY = 0.42D + charge * 0.18D;
        defenderHasBall = false;
        defenderChargeTicks = 0;
        defenderChargeTargetTicks = 0;
        defenderKickCooldown = 22;
        defenderPossessionCooldown = 18;
    }

    private void releaseDefenderPossession(int cooldown) {
        defenderHasBall = false;
        defenderChargeTicks = 0;
        defenderChargeTargetTicks = 0;
        defenderPossessionCooldown = Math.max(defenderPossessionCooldown, cooldown);
    }

    private void moveDefenderTowards(double targetX, double targetZ, double maxStep) {
        double dx = targetX - defenderX;
        double dz = targetZ - defenderZ;
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance <= 1.0E-4D) {
            return;
        }
        double step = Math.min(maxStep, distance);
        defenderX += dx / distance * step;
        defenderZ += dz / distance * step;
        defenderX = Mth.clamp(defenderX,
                -FIELD_WIDTH * 0.5D + 0.35D, FIELD_WIDTH * 0.5D - 0.35D);
        defenderZ = Mth.clamp(defenderZ,
                DEFENDER_FREE_THROW_Z, FIELD_LENGTH * 0.5D - 0.35D);
        if (step > 1.0E-4D) {
            defenderMovedThisTick = true;
            defenderWalkPhase += (float) (step * 3.5D);
        }
    }

    private void updateDefenderAnimation() {
        float target = defenderMovedThisTick ? Mth.sin(defenderWalkPhase) * 22.0F : 0.0F;
        float response = defenderMovedThisTick ? 0.62F : 0.38F;
        defenderLimbSwing = Mth.lerp(response, defenderLimbSwing, target);
    }

    private static float yawTowards(double dx, double dz, float fallback) {
        if (dx * dx + dz * dz < 1.0E-6D) {
            return fallback;
        }
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    private void drawRectangle(double left, double right, double startZ, double endZ) {
        drawLine(worldPosition(left, 0.065D, startZ), worldPosition(right, 0.065D, startZ));
        drawLine(worldPosition(right, 0.065D, startZ), worldPosition(right, 0.065D, endZ));
        drawLine(worldPosition(right, 0.065D, endZ), worldPosition(left, 0.065D, endZ));
        drawLine(worldPosition(left, 0.065D, endZ), worldPosition(left, 0.065D, startZ));
    }

    private void drawPenaltyArea(double direction) {
        double end = direction * FIELD_LENGTH * 0.5D;
        double penaltyLine = end - direction * PENALTY_DEPTH;
        drawRectangle(-PENALTY_WIDTH * 0.5D, PENALTY_WIDTH * 0.5D, end, penaltyLine);
        double spotZ = end - direction * PENALTY_SPOT_OFFSET;
        drawCircle(0.0D, 0.065D, spotZ, 0.12D, 16);
        drawArc(0.0D, 0.065D, spotZ, 0.0D, 360.0D, 1.8D, 24);
    }

    private void drawCircle(double x, double y, double z, double radius, int segments) {
        if (currentPoseStack != null) {
            Render3DUtils.drawCylinder(currentPoseStack, worldPosition(x, y, z), radius,
                    0.01D, lineColor.get().getRGB(), FIELD_LINE_WIDTH, segments);
        }
    }

    private void drawColoredCircle(double x, double y, double z, double radius,
                                   int segments, int color) {
        if (currentPoseStack != null) {
            Render3DUtils.drawCylinder(currentPoseStack, worldPosition(x, y, z), radius,
                    0.035D, color, 4.0F, segments);
        }
    }

    private void drawArc(double centerX, double y, double centerZ,
                         double startDegrees, double endDegrees,
                         double radius, int segments) {
        double previous = Math.toRadians(startDegrees);
        for (int index = 1; index <= segments; index++) {
            double angle = Math.toRadians(startDegrees
                    + (endDegrees - startDegrees) * index / segments);
            drawLine(worldPosition(centerX + Math.cos(previous) * radius, y,
                            centerZ + Math.sin(previous) * radius),
                    worldPosition(centerX + Math.cos(angle) * radius, y,
                            centerZ + Math.sin(angle) * radius));
            previous = angle;
        }
    }

    private void drawHoop(double direction) {
        double z = direction * FIELD_LENGTH * 0.5D;
        double backboardZ = z + direction * HOOP_DEPTH * 0.45D;
        double supportZ = z + direction * HOOP_DEPTH;
        drawHoopBeam(0.0D, HOOP_HEIGHT + 0.42D, backboardZ,
                3.4D, 1.85D, 0.14D, backboardColor.get());
        drawHoopBeam(0.0D, 1.55D, supportZ, 0.28D, 3.1D, 0.28D);
        drawHoopBeam(0.0D, 2.78D, z + direction * HOOP_DEPTH * 0.62D,
                0.28D, 0.28D, HOOP_DEPTH * 0.75D);
        drawColoredCircle(0.0D, HOOP_HEIGHT, z, 0.76D, 32, hoopColor.get().getRGB());
    }

    private void drawHoopBeam(double x, double y, double z,
                              double width, double height, double depth) {
        drawHoopBeam(x, y, z, width, height, depth, hoopColor.get());
    }

    private void drawHoopBeam(double x, double y, double z,
                              double width, double height, double depth, Color color) {
        Vec3 center = worldPosition(x, y, z);
        Render3DUtils.drawFilledBox(new AABB(
                center.x - width * 0.5D, center.y - height * 0.5D, center.z - depth * 0.5D,
                center.x + width * 0.5D, center.y + height * 0.5D, center.z + depth * 0.5D),
                color);
    }

    private void drawRoundBall(Vec3 center) {
        Render3DUtils.drawFilledSphere(center, BALL_RADIUS, ballColor.get(), 16, 24);
    }

    private void drawKickoffPoint() {
        drawCircle(0.0D, 0.07D, PLAYER_KICKOFF_Z, 0.68D, 32);
        drawCircle(0.0D, 0.07D, PLAYER_KICKOFF_Z, 0.12D, 16);
        if (defenderActive()) {
            drawCircle(0.0D, 0.07D, DEFENDER_KICKOFF_Z, 0.68D, 32);
            drawCircle(0.0D, 0.07D, DEFENDER_KICKOFF_Z, 0.12D, 16);
        }
    }

    private void drawDefender(float partialTick) {
        double renderX = Mth.lerp(partialTick, previousDefenderX, defenderX);
        double renderZ = Mth.lerp(partialTick, previousDefenderZ, defenderZ);
        float renderYaw = previousDefenderYaw
                + Mth.wrapDegrees(defenderYaw - previousDefenderYaw) * partialTick;
        float renderLimbSwing = Mth.lerp(partialTick,
                previousDefenderLimbSwing, defenderLimbSwing);
        Vec3 feet = worldPosition(renderX, 0.07D, renderZ);
        Render3DUtils.drawSkinnedPlayer(currentPoseStack, feet, renderYaw,
                renderLimbSwing, DEFENDER_SKIN);

        Vector3f projected = WorldToScreen.getWorldPositionToScreen(feet.add(0.0D, 2.08D, 0.0D));
        if (Float.isFinite(projected.x) && Float.isFinite(projected.y)
                && projected.z >= 0.0F && projected.z <= 1.0F) {
            float guiScale = (float) mc.getWindow().getGuiScale();
            defenderNameX = projected.x / guiScale;
            defenderNameY = projected.y / guiScale;
            defenderNameVisible = true;
        }
    }

    private void drawDefenderName(Canvas canvas) {
        if (!defenderNameVisible) {
            return;
        }
        float fontSize = 9.0F;
        float height = 13.0F;
        float width = SkijaUi.textWidth(DEFENDER_NAME, fontSize) + 8.0F;
        float x = defenderNameX - width * 0.5F;
        float y = defenderNameY - height - 3.0F;
        SkijaUi.rounded(canvas, x, y, width, height, 3.0F, 0xB810141C);
        SkijaUi.text(canvas, DEFENDER_NAME, x + 4.0F, y, height,
                0xFFFFFFFF, fontSize);
    }

    private void drawLine(Vec3 from, Vec3 to) {
        if (currentPoseStack != null) {
            Render3DUtils.drawLine(currentPoseStack, from, to,
                    lineColor.get().getRGB(), FIELD_LINE_WIDTH);
        }
    }

    private com.mojang.blaze3d.vertex.PoseStack currentPoseStack;

    private Vec3 worldPosition(double x, double y, double z) {
        return new Vec3(originX + x, originY + y, originZ + z);
    }

    private void resetBall() {
        releaseDefenderPossession(10);
        ballX = 0.0D;
        ballY = BALL_RADIUS;
        ballZ = 0.0D;
        snapshotBallPosition();
        velocityX = 0.0D;
        velocityY = 0.0D;
        velocityZ = 0.0D;
    }

    private void beginKickoff() {
        gameStarted = false;
        kickoffCountdownTicks = 0;
        kickoffGoTicks = 0;
        lastKickoffPlayerX = mc.player.getX();
        lastKickoffPlayerZ = mc.player.getZ();
        playerStealCooldownTicks = 0;
        dribbling = false;
        charging = false;
        chargeTicks = 0;
        beginKickoffReset();
    }

    private void beginKickoffReset() {
        resetBall();
        resetDefenderForKickoff();
    }

    private boolean playerAtKickoff() {
        if (noPlayer()) {
            return false;
        }
        double localX = mc.player.getX() - originX;
        double localZ = mc.player.getZ() - originZ;
        return localX * localX <= 1.0D
                && Math.abs(localZ - PLAYER_KICKOFF_Z) <= 1.0D;
    }

    private void updateKickoffCountdown() {
        double playerX = mc.player.getX();
        double playerZ = mc.player.getZ();
        double moveX = playerX - lastKickoffPlayerX;
        double moveZ = playerZ - lastKickoffPlayerZ;
        boolean stationary = moveX * moveX + moveZ * moveZ <= 0.0004D;

        if (playerAtKickoff() && stationary) {
            kickoffCountdownTicks++;
            if (kickoffCountdownTicks >= KICKOFF_COUNTDOWN_TICKS) {
                gameStarted = true;
                kickoffCountdownTicks = 0;
                kickoffGoTicks = 20;
                resetBall();
                resetDefenderForKickoff();
            }
        } else {
            kickoffCountdownTicks = 0;
        }
        lastKickoffPlayerX = playerX;
        lastKickoffPlayerZ = playerZ;
    }

    private boolean defenderActive() {
        return defender.get();
    }

    private void snapshotBallPosition() {
        previousBallX = ballX;
        previousBallY = ballY;
        previousBallZ = ballZ;
    }

    private void resetDefender() {
        defenderX = 0.0D;
        defenderZ = DEFENDER_KICKOFF_Z;
        previousDefenderX = defenderX;
        previousDefenderZ = defenderZ;
        defenderYaw = 180.0F;
        previousDefenderYaw = defenderYaw;
        defenderWalkPhase = 0.0F;
        defenderLimbSwing = 0.0F;
        previousDefenderLimbSwing = 0.0F;
        defenderMovedThisTick = false;
        defenderKickCooldown = 0;
        defenderHasBall = false;
        defenderChargeTicks = 0;
        defenderChargeTargetTicks = 0;
        defenderPossessionCooldown = 0;
        defenderStunTicks = 0;
        defenderDribbleDirectionX = 0.0D;
        defenderDribbleDirectionZ = -1.0D;
        defenderDribblePhase = 0.0D;
        defenderShotTargetX = 0.0D;
        defenderAvoidanceSide = 1.0D;
    }

    private void resetDefenderForKickoff() {
        resetDefender();
    }

    private void snapshotDefenderPosition() {
        previousDefenderX = defenderX;
        previousDefenderZ = defenderZ;
        previousDefenderYaw = defenderYaw;
        previousDefenderLimbSwing = defenderLimbSwing;
        defenderMovedThisTick = false;
    }
}
