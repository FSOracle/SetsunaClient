package com.setsuna.module.modules.combat;

import com.setsuna.event.Listen;
import com.setsuna.event.events.PlayerTickEvent;
import com.setsuna.event.events.Render3DEvent;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.module.modules.combat.killauraplus.KillAuraConfig;
import com.setsuna.module.modules.combat.killauraplus.KillAuraCriticalMode;
import com.setsuna.module.modules.combat.killauraplus.KillAuraMode;
import com.setsuna.module.modules.combat.killauraplus.KillAuraPlusEngine;
import com.setsuna.module.modules.combat.killauraplus.KillAuraPriority;
import com.setsuna.module.modules.combat.killauraplus.KillAuraRotationMode;
import com.setsuna.module.modules.combat.killauraplus.MinecraftKillAuraContext;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.ColorSetting;
import com.setsuna.setting.settings.DoubleSetting;
import com.setsuna.setting.settings.EnumSetting;
import com.setsuna.setting.settings.IntSetting;
import com.setsuna.util.render.esp.CaptureMarkESP;
import com.setsuna.util.render.esp.CircleESP;
import com.setsuna.util.render.esp.FireflyESP;
import net.minecraft.world.entity.LivingEntity;

import java.awt.Color;

/** Clean host module for the reconstructed Clap KillAura behavior. */
public final class KillAuraPlus extends Module {
    public static final KillAuraPlus INSTANCE = new KillAuraPlus();

    private final MinecraftKillAuraContext context = new MinecraftKillAuraContext(mc);
    private final KillAuraPlusEngine engine = new KillAuraPlusEngine(context);

    private enum ESPMode {
        CaptureMark,
        Circle,
        Firefly
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
    private final BooleanSetting esp = add(new BooleanSetting("ESP", true));
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

    private final ColorSetting sideColor = add(new ColorSetting("Side Color", Color.WHITE, false));
    private final ColorSetting lineColor = add(new ColorSetting("Line Color", new Color(255, 255, 255, 233))
            .visibleWhen(() -> esp.get() && espMode.is(ESPMode.Circle)));
    private final DoubleSetting circleRadius = add(new DoubleSetting("Circle Radius", 0.75, 0.1, 2.0, 0.05)
            .visibleWhen(() -> esp.get() && espMode.is(ESPMode.Circle)));
    private final DoubleSetting circleAlphaFactor = add(new DoubleSetting("Circle Alpha Factor", 1.0, 0.0, 2.0, 0.05)
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
    }

    @Listen
    private void onTick(PlayerTickEvent.Pre event) {
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
        }
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
