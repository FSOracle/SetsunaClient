package com.setsuna.module.modules.combat;

import com.setsuna.event.Listen;
import com.setsuna.event.events.PlayerTickEvent;
import com.setsuna.event.events.Render3DEvent;
import com.setsuna.event.events.WorldItemSubmitEvent;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.module.modules.combat.killauraplus.KillAuraBlockReason;
import com.setsuna.module.modules.combat.killauraplus.KillAuraConfig;
import com.setsuna.module.modules.combat.killauraplus.KillAuraCriticalMode;
import com.setsuna.module.modules.combat.killauraplus.KillAuraMode;
import com.setsuna.module.modules.combat.killauraplus.KillAuraPlusEngine;
import com.setsuna.module.modules.combat.killauraplus.KillAuraPriority;
import com.setsuna.module.modules.combat.killauraplus.KillAuraRotationMode;
import com.setsuna.module.modules.combat.killauraplus.MinecraftKillAuraContext;
import com.setsuna.module.modules.player.AntiWeb;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.ColorSetting;
import com.setsuna.setting.settings.DoubleSetting;
import com.setsuna.setting.settings.EnumSetting;
import com.setsuna.setting.settings.IntSetting;
import com.setsuna.util.render.esp.CaptureMarkESP;
import com.setsuna.util.render.esp.CircleESP;
import com.setsuna.util.render.esp.FireflyESP;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.awt.Color;

/** Clean host module for the reconstructed Clap KillAura behavior. */
public final class KillAuraPlus extends Module {
    public static final KillAuraPlus INSTANCE = new KillAuraPlus();

    private final MinecraftKillAuraContext context = new MinecraftKillAuraContext(mc);
    private final KillAuraPlusEngine engine = new KillAuraPlusEngine(context);

    private enum ESPMode {
        CaptureMark,
        Circle,
        Firefly,
        Swords
    }

    private final EnumSetting<KillAuraMode> mode = add(
            new EnumSetting<>("Mode", KillAuraMode.SWITCH)
                    .onChange(value -> engine.resetAttackSelection()));
    private final EnumSetting<KillAuraPriority> priority = add(
            new EnumSetting<>("Priority", KillAuraPriority.HEALTH)
                    .onChange(value -> engine.resetAttackSelection()));
    private final EnumSetting<KillAuraRotationMode> rotationMode = add(
            new EnumSetting<>("RotationMode", KillAuraRotationMode.SNAP));
    private final EnumSetting<KillAuraCriticalMode> criticalMode = add(
            new EnumSetting<>("Critical", KillAuraCriticalMode.NONE));
    private final DoubleSetting rotationRange = add(
            new DoubleSetting("RotationRange", 5.0, 2.0, 8.0, 0.1));
    private final IntSetting rotationSpeed = add(
            new IntSetting("RotationSpeed", 90, 5, 180, 1)
                    .visibleWhen(() -> rotationMode.is(KillAuraRotationMode.SMOOTH)));
    private final DoubleSetting range = add(
            new DoubleSetting("Range", 3.0, 2.0, 6.0, 0.1));
    private final IntSetting fov = add(new IntSetting("FOV", 180, 1, 180, 1));
    private final BooleanSetting onlyWeapon = add(new BooleanSetting("OnlyWeapon", false));
    private final BooleanSetting swing = add(new BooleanSetting("Swing", true));
    private final BooleanSetting fakeBlock = add(
            new BooleanSetting("FakeBlock", false)
                    .onChange(value -> {
                        if (!value) engine.clearFakeBlocking();
                    }));
    private final BooleanSetting players = add(new BooleanSetting("Players", true));
    private final BooleanSetting mobs = add(new BooleanSetting("Mobs", false));
    private final BooleanSetting animals = add(new BooleanSetting("Animals", false));
    private final BooleanSetting invisibles = add(new BooleanSetting("Invisibles", false));
    private final BooleanSetting ignoreTeam = add(new BooleanSetting("IgnoreTeam", true));
    private final BooleanSetting ignoreFriends = add(new BooleanSetting("IgnoreFriends", true));
    private final BooleanSetting esp = add(new BooleanSetting("TargetESP", false));
    private final EnumSetting<ESPMode> espMode = add(new EnumSetting<>("ESP Mode", ESPMode.Circle)
            .visibleWhen(esp::get));

    private final ColorSetting espColor1 = add(new ColorSetting("ESP Main", new Color(255, 183, 197))
            .visibleWhen(() -> esp.get() && espMode.is(ESPMode.CaptureMark)));
    private final ColorSetting espColor2 = add(new ColorSetting("ESP Second", new Color(255, 133, 161))
            .visibleWhen(() -> esp.get() && espMode.is(ESPMode.CaptureMark)));
    private final DoubleSetting espSize = add(new DoubleSetting("ESP Size", 1.2, 0.5, 3.0, 0.1)
            .visibleWhen(() -> esp.get() && espMode.is(ESPMode.CaptureMark)));
    private final DoubleSetting espRotSpeed = add(new DoubleSetting("Rot Speed", 2.0, 0.5, 10.0, 0.1)
            .visibleWhen(() -> esp.get() && espMode.is(ESPMode.CaptureMark)));
    private final DoubleSetting waveSpeed = add(new DoubleSetting("Wave Speed", 3.0, 0.5, 10.0, 0.1)
            .visibleWhen(() -> esp.get() && espMode.is(ESPMode.CaptureMark)));

    private final ColorSetting sideColor = add(new ColorSetting("TargetESPSideColor", Color.WHITE)
            .visibleWhen(() -> esp.get() && espMode.is(ESPMode.Circle)));
    private final ColorSetting lineColor = add(new ColorSetting("TargetESPLineColor", new Color(255, 255, 255, 233))
            .visibleWhen(() -> esp.get() && espMode.is(ESPMode.Circle)));
    private final DoubleSetting circleRadius = add(new DoubleSetting("TargetESPRadius", 0.75, 0.1, 2.0, 0.05)
            .visibleWhen(() -> esp.get() && espMode.is(ESPMode.Circle)));
    private final DoubleSetting circleAlphaFactor = add(new DoubleSetting("TargetESPAlpha", 1.0, 0.0, 2.0, 0.05)
            .visibleWhen(() -> esp.get() && espMode.is(ESPMode.Circle)));

    private final ColorSetting fireflyColor = add(new ColorSetting("Firefly Color", new Color(149, 149, 149, 255), false)
            .visibleWhen(() -> esp.get() && espMode.is(ESPMode.Firefly)));
    private final EnumSetting<FireflyESP.ColorMode> fireflyColorMode = add(
            new EnumSetting<>("Firefly Color Mode", FireflyESP.ColorMode.Blend)
                    .visibleWhen(() -> esp.get() && espMode.is(ESPMode.Firefly)));
    private final ColorSetting fireflyColor2 = add(new ColorSetting("Firefly Color 2", new Color(255, 133, 161, 255), false)
            .visibleWhen(() -> esp.get() && espMode.is(ESPMode.Firefly)
                    && fireflyColorMode.is(FireflyESP.ColorMode.Blend)));
    private final DoubleSetting fireflyColorMix = add(new DoubleSetting("Firefly Color Mix", 0.65, 0.0, 1.0, 0.05)
            .visibleWhen(() -> esp.get() && espMode.is(ESPMode.Firefly)
                    && fireflyColorMode.is(FireflyESP.ColorMode.Blend)));
    private final DoubleSetting fireflyColorSpeed = add(new DoubleSetting("Firefly Color Speed", 1.2, 0.1, 6.0, 0.1)
            .visibleWhen(() -> esp.get() && espMode.is(ESPMode.Firefly)
                    && fireflyColorMode.is(FireflyESP.ColorMode.Blend)));
    private final DoubleSetting fireflyRainbowSpeed = add(new DoubleSetting("Firefly Rainbow Speed", 1.0, 0.1, 6.0, 0.1)
            .visibleWhen(() -> esp.get() && espMode.is(ESPMode.Firefly)
                    && fireflyColorMode.is(FireflyESP.ColorMode.Rainbow)));
    private final DoubleSetting fireflyRainbowSaturation = add(new DoubleSetting("Firefly Rainbow Saturation", 0.85, 0.1, 1.0, 0.05)
            .visibleWhen(() -> esp.get() && espMode.is(ESPMode.Firefly)
                    && fireflyColorMode.is(FireflyESP.ColorMode.Rainbow)));
    private final DoubleSetting fireflyRainbowBrightness = add(new DoubleSetting("Firefly Rainbow Brightness", 1.0, 0.1, 1.0, 0.05)
            .visibleWhen(() -> esp.get() && espMode.is(ESPMode.Firefly)
                    && fireflyColorMode.is(FireflyESP.ColorMode.Rainbow)));
    private final IntSetting fireflyLength = add(new IntSetting("Firefly Length", 14, 8, 128, 1)
            .visibleWhen(() -> esp.get() && espMode.is(ESPMode.Firefly)));
    private final IntSetting fireflyFactor = add(new IntSetting("Firefly Factor", 8, 1, 10, 1)
            .visibleWhen(() -> esp.get() && espMode.is(ESPMode.Firefly)));
    private final DoubleSetting fireflyShaking = add(new DoubleSetting("Firefly Shaking", 1.8, 0.25, 10.0, 0.25)
            .visibleWhen(() -> esp.get() && espMode.is(ESPMode.Firefly)));
    private final DoubleSetting fireflyAmplitude = add(new DoubleSetting("Firefly Amplitude", 3.0, 0.0, 10.0, 0.25)
            .visibleWhen(() -> esp.get() && espMode.is(ESPMode.Firefly)));

    private final DoubleSetting swordSize = add(new DoubleSetting("Sword Size", 1.0, 0.5, 1.5, 0.05)
            .visibleWhen(() -> esp.get() && espMode.is(ESPMode.Swords)));

    private ItemStack targetSwordStack;
    private int swordEntryTargetId = Integer.MIN_VALUE;
    private long swordEntryStartedNanos;

    private static final long SWORD_ENTRY_DURATION_NANOS = 550_000_000L;
    private static final double SWORD_ORBIT_SPEED = 7.5D;
    private static final float TARGET_SWORD_BASE_SCALE = 0.775F;
    private static final Vector3f TARGET_SWORD_MODEL_AXIS = new Vector3f(-1.0F, 1.0F, 0.0F).normalize();

    private KillAuraPlus() {
        super("Kill Aura+", Category.COMBAT);
    }

    @Override
    public String id() {
        return "kill_aura_plus";
    }

    public boolean isFakeBlocking() {
        return isEnabled() && engine.isFakeBlocking();
    }

    public LivingEntity target() {
        if (!isEnabled()) return null;
        Integer targetId = engine.attackTargetEntityId();
        return targetId == null ? null : context.livingEntity(targetId);
    }

    public boolean hasLockedTarget() {
        LivingEntity target = target();
        return target != null && target.isAlive() && !target.isRemoved();
    }

    public boolean isSwordsEspActive() {
        return isEnabled() && esp.get() && espMode.is(ESPMode.Swords);
    }

    /** Last local gate that prevented an attack, for troubleshooting hit misses. */
    public KillAuraBlockReason lastBlockReason() {
        return engine.lastBlockReason();
    }

    @Override
    public String getInfo() {
        return mode.is(KillAuraMode.SINGLE) ? "Single" : "Switch";
    }

    @Override
    protected void onEnable() {
        engine.reset();
    }

    @Override
    protected void onDisable() {
        engine.reset();
        targetSwordStack = null;
        swordEntryTargetId = Integer.MIN_VALUE;
        swordEntryStartedNanos = 0L;
    }

    @Listen
    private void onTick(PlayerTickEvent.Pre event) {
        if (AutoHead.INSTANCE.isUsingHead()) {
            engine.reset();
            return;
        }
        if (AntiWeb.INSTANCE.isEnabled() && AntiWeb.INSTANCE.blocksOtherActions()) {
            engine.reset();
            return;
        }
        if (context.isUseInputActive() || !context.isNormalAttackSupported()) {
            engine.reset();
            return;
        }
        engine.configureTargetEsp(esp.get(), circleRadius.get().floatValue(),
                circleAlphaFactor.get().floatValue(), sideColor.get(), lineColor.get());
        engine.tick(config(), isEnabled());
    }

    @Listen
    private void onRender3D(Render3DEvent event) {
        if (!esp.get()) return;
        LivingEntity target = target();
        if (target == null) return;

        switch (espMode.get()) {
            case CaptureMark -> CaptureMarkESP.render(
                    event.getPoseStack(), target, espSize.get(), espRotSpeed.get(), waveSpeed.get(),
                    espColor1.get(), espColor2.get());
            case Circle -> CircleESP.render(
                    event.getPoseStack(), target, circleRadius.get().floatValue(),
                    sideColor.get(), lineColor.get(), circleAlphaFactor.get().floatValue());
            case Firefly -> FireflyESP.render(
                    event.getPoseStack(), target, fireflyLength.get(), fireflyFactor.get(),
                    fireflyShaking.get(), fireflyAmplitude.get(), fireflyColor.get(),
                    fireflyColorMode.get(), fireflyColor2.get(), fireflyColorMix.get(),
                    fireflyColorSpeed.get(), fireflyRainbowSpeed.get(),
                    fireflyRainbowSaturation.get(), fireflyRainbowBrightness.get());
            case Swords -> {
                // Vanilla item models are submitted in onWorldItemSubmit.
            }
        }
    }

    @Listen
    private void onWorldItemSubmit(WorldItemSubmitEvent event) {
        if (!isEnabled() || !esp.get() || !espMode.is(ESPMode.Swords) || noPlayer()) {
            swordEntryTargetId = Integer.MIN_VALUE;
            return;
        }

        LivingEntity swordTarget = target();
        if (swordTarget == null || swordTarget.isRemoved()) {
            swordEntryTargetId = Integer.MIN_VALUE;
            return;
        }
        long now = System.nanoTime();
        if (swordEntryTargetId != swordTarget.getId()) {
            swordEntryTargetId = swordTarget.getId();
            swordEntryStartedNanos = now;
        }

        if (targetSwordStack == null) targetSwordStack = new ItemStack(Items.DIAMOND_SWORD);
        ItemStackRenderState renderState = new ItemStackRenderState();
        mc.getItemModelResolver().updateForLiving(
                renderState, targetSwordStack, ItemDisplayContext.FIXED, swordTarget);

        float tickDelta = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        Vec3 targetPosition = interpolatedTargetPosition(swordTarget, tickDelta)
                .add(0.0D, swordTarget.getBbHeight() * 0.52D, 0.0D);
        float entryProgress = clamp((now - swordEntryStartedNanos)
                / (float) SWORD_ENTRY_DURATION_NANOS);
        float easedEntry = easeOutCubic(entryProgress);
        double orbitTime = Math.max(0L,
                now - swordEntryStartedNanos - SWORD_ENTRY_DURATION_NANOS) / 1_000_000_000.0D;
        double frontAngle = Math.atan2(
                event.getCameraPosition().z - targetPosition.z,
                event.getCameraPosition().x - targetPosition.x);
        Camera camera = mc.gameRenderer.getMainCamera();
        for (int index = 0; index < 3; index++) {
            double angle = frontAngle + index * Math.toRadians(120.0D)
                    + orbitTime * SWORD_ORBIT_SPEED;
            double bob = Math.sin(now / 1_000_000_000.0D * 1.6D + index * 1.9D) * 0.12D;
            Vec3 orbitPosition = targetPosition.add(
                    Math.cos(angle) * 0.82D,
                    0.32D + bob,
                    Math.sin(angle) * 0.82D);
            Vec3 swordPosition = entryProgress < 1.0F
                    ? lerp(offscreenSwordPosition(camera, event.getCameraPosition(), index),
                    orbitPosition, easedEntry)
                    : orbitPosition;
            Vec3 swordDirection = targetPosition.subtract(swordPosition).normalize();
            submitTargetSword(event, renderState, swordPosition, swordDirection);
        }
    }

    private void submitTargetSword(WorldItemSubmitEvent event, ItemStackRenderState renderState,
                                   Vec3 position, Vec3 direction) {
        Quaternionf orientation = new Quaternionf().rotationTo(
                TARGET_SWORD_MODEL_AXIS,
                new Vector3f((float) direction.x, (float) direction.y, (float) direction.z));
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(position.subtract(event.getCameraPosition()));
        poseStack.mulPose(orientation);
        float scale = TARGET_SWORD_BASE_SCALE * swordSize.get().floatValue();
        poseStack.scale(scale, scale, scale);
        int light = LevelRenderer.getLightCoords(mc.level, BlockPos.containing(position));
        renderState.submit(poseStack, event.getCollector(), light, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }

    private static Vec3 offscreenSwordPosition(Camera camera, Vec3 cameraPosition, int index) {
        Vec3 forward = new Vec3(camera.forwardVector().x(), camera.forwardVector().y(), camera.forwardVector().z());
        Vec3 left = new Vec3(camera.leftVector().x(), camera.leftVector().y(), camera.leftVector().z());
        Vec3 up = new Vec3(camera.upVector().x(), camera.upVector().y(), camera.upVector().z());
        double horizontal = index == 0 ? 3.4D : index == 1 ? -3.4D : 0.0D;
        double vertical = index == 2 ? 2.8D : index == 0 ? 0.65D : -0.55D;
        return cameraPosition.add(forward.scale(1.6D))
                .add(left.scale(horizontal))
                .add(up.scale(vertical));
    }

    private static Vec3 lerp(Vec3 from, Vec3 to, float progress) {
        return from.add(to.subtract(from).scale(progress));
    }

    private static float easeOutCubic(float progress) {
        float inverse = 1.0F - clamp(progress);
        return 1.0F - inverse * inverse * inverse;
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static Vec3 interpolatedTargetPosition(LivingEntity entity, float tickDelta) {
        return new Vec3(
                Mth.lerp(tickDelta, entity.xOld, entity.getX()),
                Mth.lerp(tickDelta, entity.yOld, entity.getY()),
                Mth.lerp(tickDelta, entity.zOld, entity.getZ()));
    }

    private KillAuraConfig config() {
        return new KillAuraConfig(
                mode.get(),
                priority.get(),
                rotationMode.get(),
                criticalMode.get(),
                rotationRange.get(),
                rotationSpeed.get(),
                range.get(),
                fov.get(),
                onlyWeapon.get(),
                swing.get(),
                fakeBlock.get(),
                players.get(),
                mobs.get(),
                animals.get(),
                invisibles.get(),
                ignoreTeam.get(),
                ignoreFriends.get());
    }

}
