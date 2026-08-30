package com.setsuna.module.modules.combat;

import com.mojang.blaze3d.platform.InputConstants;
import com.setsuna.event.Listen;
import com.setsuna.event.events.AttackSlowDownEvent;
import com.setsuna.event.events.PacketEvent;
import com.setsuna.event.events.PlayerTickEvent;
import com.setsuna.event.events.Render3DEvent;
import com.setsuna.event.events.RespawnEvent;
import com.setsuna.event.events.SendPositionEvent;
import com.setsuna.event.events.SlowdownEvent;
import com.setsuna.manager.FriendManager;
import com.setsuna.manager.HealthManager;
import com.setsuna.manager.RotationManager;
import com.setsuna.manager.target.TargetManager;
import com.setsuna.manager.target.TargetRequest;
import com.setsuna.mixin.KeyMappingAccessor;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.module.modules.combat.killaura.HeypixelKillAuraEngine;
import com.setsuna.module.modules.movement.KeepSprint;
import com.setsuna.module.modules.movement.Scaffold;
import com.setsuna.module.modules.movement.Velocity;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.ColorSetting;
import com.setsuna.setting.settings.DoubleSetting;
import com.setsuna.setting.settings.EnumSetting;
import com.setsuna.setting.settings.IntSetting;
import com.setsuna.setting.settings.KeybindSetting;
import com.setsuna.util.client.KeybindUtils;
import com.setsuna.util.math.MathUtils;
import com.setsuna.util.network.PacketUtils;
import com.setsuna.util.player.TeamColorUtils;
import com.setsuna.util.render.esp.CaptureMarkESP;
import com.setsuna.util.render.esp.CircleESP;
import com.setsuna.util.render.esp.FireflyESP;
import com.setsuna.util.rotation.Priority;
import com.setsuna.util.rotation.RaytraceUtils;
import com.setsuna.util.rotation.Rot2f;
import com.setsuna.util.rotation.RotationUtils;
import com.viaversion.setsunavia.protocoltranslator.ProtocolTranslator;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.mojang.blaze3d.vertex.PoseStack;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;
import java.util.Comparator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Melee combat assistant: acquires targets, drives silent-aim rotations through
 * {@link RotationManager}, and attacks with configurable timing/critical/auto-block
 * behaviour. Ported from the source client; the BedNuker interlock is dropped
 * (module not in this build).
 */
public final class KillAura extends Module {

    public static final KillAura INSTANCE = new KillAura();

    private KillAura() {
        super("Kill Aura", Category.COMBAT);
    }

    private enum Mode {
        OnePointEight,
        OnePointNinePlus,
        Heypixel,
        Normal
    }

    private enum PriorityMode {
        Health,
        Distance,
        FOV
    }

    private enum NormalRotationTiming {
        Normal,
        Snap,
        OnTick
    }

    private enum RaycastMode {
        None,
        Enemy,
        All
    }

    private enum NormalCriticalMode {
        Smart,
        Ignore,
        Always
    }

    public enum TargetMode {
        Single,
        Switch,
        Multiple,
    }

    private enum HeypixelTargetMode {
        Single,
        Switch
    }

    private enum HeypixelAutoBlockMode {
        Off,
        Vanilla,
        Fake
    }

    private enum ESPMode {
        CaptureMark,
        Circle,
        Firefly
    }

    private enum AutoBlockMode {
        None, Basic, Vanilla, Spoof, Hypixel, Blink, Interact, Swap, Legit, Fake, Morden
    }

    private enum BasicUnblockMode {
        StopUsingItem,
        ChangeSlot,
        SwapHand,
        None
    }

    private final EnumSetting<Mode> mode = add(new EnumSetting<>("Mode", Mode.OnePointEight));

    private final EnumSetting<TargetMode> targetMode = add(new EnumSetting<>("Target Mode", TargetMode.Single)
            .visibleWhen(() -> !mode.is(Mode.Heypixel)));
    private final EnumSetting<HeypixelTargetMode> heypixelTargetMode = add(
            new EnumSetting<>("Heypixel Target Mode", HeypixelTargetMode.Switch)
                    .visibleWhen(() -> mode.is(Mode.Heypixel))
                    .displayAs("Target Mode"));
    private final EnumSetting<PriorityMode> priority = add(new EnumSetting<>("Priority", PriorityMode.Distance).visibleWhen(() -> mode.is(Mode.Normal)));
    private final EnumSetting<NormalRotationTiming> normalRotationTiming = add(new EnumSetting<>("Normal Rotation Timing", NormalRotationTiming.Normal).visibleWhen(() -> mode.is(Mode.Normal)));
    private final EnumSetting<RaycastMode> normalRaycast = add(new EnumSetting<>("Normal Raycast", RaycastMode.All).visibleWhen(() -> mode.is(Mode.Normal)));
    private final EnumSetting<NormalCriticalMode> normalCriticals = add(new EnumSetting<>("Normal Criticals", NormalCriticalMode.Smart).visibleWhen(() -> mode.is(Mode.Normal)));
    private final DoubleSetting range = add(new DoubleSetting("Range", 4.0, 1.0, 6.0, 0.01)
            .visibleWhen(() -> !mode.is(Mode.Heypixel)));
    private final DoubleSetting heypixelRange = add(new DoubleSetting("Heypixel Range", 3.0, 3.0, 6.0, 0.1)
            .visibleWhen(() -> mode.is(Mode.Heypixel))
            .displayAs("Range"));
    private final DoubleSetting aimRange = add(new DoubleSetting("Aim Range", 4.0, 1.0, 6.0, 0.1)
            .visibleWhen(() -> !mode.is(Mode.Heypixel)));
    private final DoubleSetting rotationRange = add(new DoubleSetting("Rotation Range", 5.0, 2.0, 8.0, 0.1)
            .visibleWhen(() -> mode.is(Mode.Normal)));
    private final DoubleSetting heypixelRotationRange = add(new DoubleSetting("Heypixel Rotation Range", 5.0, 3.0, 8.0, 0.1)
            .visibleWhen(() -> mode.is(Mode.Heypixel))
            .displayAs("Rotation Range"));
    private final DoubleSetting swingRange = add(new DoubleSetting("Swing Range", 5.0, 3.0, 8.0, 0.1)
            .visibleWhen(() -> mode.is(Mode.Heypixel)));
    private final DoubleSetting scanRangeIncrease = add(new DoubleSetting("Scan Range Increase", 2.5, 0.0, 7.0, 0.1).visibleWhen(() -> mode.is(Mode.Normal)));

    private final IntSetting fov = add(new IntSetting("FOV", 360, 10, 360, 1)
            .visibleWhen(() -> !mode.is(Mode.Heypixel)));
    private final IntSetting heypixelFov = add(new IntSetting("Heypixel FOV", 180, 1, 180, 1)
            .visibleWhen(() -> mode.is(Mode.Heypixel))
            .displayAs("FOV"));

    private final DoubleSetting rotationSpeed = add(new DoubleSetting("Rotation Speed", 10.0, 1.0, 10.0, 0.5).visibleWhen(() -> !mode.is(Mode.Heypixel) && !normalRotationTiming.is(NormalRotationTiming.OnTick)));

    private final IntSetting minCPS = add(new IntSetting("Min CPS", 10, 1, 20, 1).visibleWhen(() -> mode.is(Mode.OnePointEight) || mode.is(Mode.Normal)));
    private final IntSetting maxCPS = add(new IntSetting("Max CPS", 12, 1, 20, 1).visibleWhen(() -> mode.is(Mode.OnePointEight) || mode.is(Mode.Normal)));
    private final IntSetting heypixelAttackCps = add(new IntSetting("Heypixel Attack CPS", 10, 1, 20, 1)
            .visibleWhen(() -> mode.is(Mode.Heypixel))
            .displayAs("Attack CPS"));
    private final IntSetting heypixelSwingCps = add(new IntSetting("Heypixel Swing CPS", 10, 1, 20, 1)
            .visibleWhen(() -> mode.is(Mode.Heypixel))
            .displayAs("Swing CPS"));
    private final BooleanSetting fullCharge = add(new BooleanSetting("Full Charge", true)
            .visibleWhen(() -> !mode.is(Mode.Heypixel)));
    private final BooleanSetting dynamicCharge = add(new BooleanSetting("Dynamic Charge", false).visibleWhen(() -> !mode.is(Mode.Heypixel) && fullCharge.get()));
    private final BooleanSetting player = add(new BooleanSetting("Player", true)
            .visibleWhen(() -> !mode.is(Mode.Heypixel)));
    private final BooleanSetting mob = add(new BooleanSetting("Mob", true)
            .visibleWhen(() -> !mode.is(Mode.Heypixel)));
    private final BooleanSetting animal = add(new BooleanSetting("Animal", true)
            .visibleWhen(() -> !mode.is(Mode.Heypixel)));
    private final BooleanSetting villagers = add(new BooleanSetting("Villagers", false)
            .visibleWhen(() -> !mode.is(Mode.Heypixel)));
    private final BooleanSetting invisible = add(new BooleanSetting("Invisible", true)
            .visibleWhen(() -> !mode.is(Mode.Heypixel)));
    private final BooleanSetting heypixelPlayers = add(new BooleanSetting("Heypixel Players", true)
            .visibleWhen(() -> mode.is(Mode.Heypixel))
            .displayAs("Players"));
    private final BooleanSetting heypixelMobs = add(new BooleanSetting("Heypixel Mobs", false)
            .visibleWhen(() -> mode.is(Mode.Heypixel))
            .displayAs("Hostile"));
    private final BooleanSetting heypixelAnimals = add(new BooleanSetting("Heypixel Animals", false)
            .visibleWhen(() -> mode.is(Mode.Heypixel))
            .displayAs("Passive"));
    private final BooleanSetting heypixelVillagers = add(new BooleanSetting("Heypixel Villagers", false)
            .visibleWhen(() -> mode.is(Mode.Heypixel))
            .displayAs("Villagers"));
    private final BooleanSetting heypixelInvisible = add(new BooleanSetting("Heypixel Invisible", true)
            .visibleWhen(() -> mode.is(Mode.Heypixel))
            .displayAs("Invisible"));
    private final BooleanSetting teamCheck = add(new BooleanSetting("Team Check", true));

    private final BooleanSetting throughWalls = add(new BooleanSetting("Through Walls", false));
    private final BooleanSetting onlyWeapon = add(new BooleanSetting("Only Weapon", false).visibleWhen(() -> mode.is(Mode.Heypixel) || mode.is(Mode.Normal)));
    private final BooleanSetting hideFakeSwings = add(new BooleanSetting("Hide Fake Swings", true)
            .visibleWhen(() -> mode.is(Mode.Heypixel)));
    private final BooleanSetting requireAttackKey = add(new BooleanSetting("Require Attack Key", false)
            .visibleWhen(() -> mode.is(Mode.Heypixel)));
    private final BooleanSetting hitSelect = add(new BooleanSetting("Hit Select", false)
            .visibleWhen(() -> mode.is(Mode.Heypixel)));
    private final BooleanSetting heypixelBypass = add(new BooleanSetting("Heypixel Bypass", false)
            .visibleWhen(() -> mode.is(Mode.Heypixel)));
    private final BooleanSetting overrideRaycast = add(new BooleanSetting("Override Raycast", true)
            .visibleWhen(() -> mode.is(Mode.Heypixel)));
    private final BooleanSetting tickLookahead = add(new BooleanSetting("Tick Lookahead", false)
            .visibleWhen(() -> mode.is(Mode.Heypixel) && overrideRaycast.get() && !heypixelBypass.get()));
    private final IntSetting heypixelMaxAngle = add(new IntSetting("Heypixel Max Angle", 90, 5, 360, 5)
            .visibleWhen(() -> mode.is(Mode.Heypixel) && heypixelBypass.get())
            .displayAs("Max Angle"));
    private final BooleanSetting keepSprintFov = add(new BooleanSetting("Keep Sprint FOV", false)
            .visibleWhen(() -> mode.is(Mode.Heypixel)));
    private final BooleanSetting grimKeepSprint = add(new BooleanSetting("Grim Keep Sprint", false)
            .visibleWhen(() -> mode.is(Mode.Heypixel)));
    private final BooleanSetting smartWeapon = add(new BooleanSetting("SmartWeapon", false)
            .visibleWhen(() -> mode.is(Mode.Heypixel)));
    private final BooleanSetting attackCooldown19 = add(new BooleanSetting("1.9+ Attack Cooldown", false)
            .visibleWhen(() -> mode.is(Mode.Heypixel)));

    private final BooleanSetting swingHand = add(new BooleanSetting("SwingHand", true));

    private final BooleanSetting clickTp = add(new BooleanSetting("Click TP", false));
    private final KeybindSetting clickTpKey = add(new KeybindSetting("Click TP Key", GLFW.GLFW_KEY_UNKNOWN).visibleWhen(() -> clickTp.get()));
    private final DoubleSetting clickTpRange = add(new DoubleSetting("Click TP Range", 20.0, 3.0, 64.0, 1.0).visibleWhen(() -> clickTp.get()));
    private final DoubleSetting clickTpStep = add(new DoubleSetting("Click TP Step", 3.0, 0.5, 4.0, 0.1).visibleWhen(() -> clickTp.get()));

    private final BooleanSetting esp = add(new BooleanSetting("ESP", true));

    private final EnumSetting<ESPMode> espMode = add(new EnumSetting<>("ESP Mode", ESPMode.Circle).visibleWhen(() -> esp.get()));

    private final ColorSetting espColor1 = add(new ColorSetting("ESP Main", new Color(255, 183, 197)).visibleWhen(() -> esp.get() && espMode.is(ESPMode.CaptureMark)));
    private final ColorSetting espColor2 = add(new ColorSetting("ESP Second", new Color(255, 133, 161)).visibleWhen(() -> esp.get() && espMode.is(ESPMode.CaptureMark)));
    private final DoubleSetting espSize = add(new DoubleSetting("ESP Size", 1.2, 0.5, 3.0, 0.1).visibleWhen(() -> esp.get() && espMode.is(ESPMode.CaptureMark)));
    private final DoubleSetting espRotSpeed = add(new DoubleSetting("Rot Speed", 2.0, 0.5, 10.0, 0.1).visibleWhen(() -> esp.get() && espMode.is(ESPMode.CaptureMark)));
    private final DoubleSetting waveSpeed = add(new DoubleSetting("Wave Speed", 3.0, 0.5, 10.0, 0.1).visibleWhen(() -> esp.get() && espMode.is(ESPMode.CaptureMark)));

    private final EnumSetting<AutoBlockMode> autoBlock = add(new EnumSetting<>("Auto Block", AutoBlockMode.None)
            .visibleWhen(() -> !mode.is(Mode.Heypixel)));
    private final EnumSetting<HeypixelAutoBlockMode> heypixelAutoBlock = add(
            new EnumSetting<>("Heypixel Auto Block", HeypixelAutoBlockMode.Off)
                    .visibleWhen(() -> mode.is(Mode.Heypixel))
                    .displayAs("Auto Block"));
    private final BooleanSetting autoBlockRequirePress = add(new BooleanSetting("AB Require Press", false).visibleWhen(() -> !mode.is(Mode.Heypixel)
            && !autoBlock.is(AutoBlockMode.None) && !autoBlock.is(AutoBlockMode.Basic)));
    private final DoubleSetting autoBlockRange = add(new DoubleSetting("AB Range", 6.0, 3.0, 8.0, 0.1).visibleWhen(() -> !mode.is(Mode.Heypixel) && !autoBlock.is(AutoBlockMode.None)));
    private final IntSetting attackTick = add(new IntSetting("Attack Tick", 1, 1, 5, 1).visibleWhen(() -> !mode.is(Mode.Heypixel) && autoBlock.is(AutoBlockMode.Swap)));
    private final BooleanSetting basicSimulateVanillaUse = add(new BooleanSetting("AB Simulate Vanilla Use", true)
            .visibleWhen(this::isBasicAutoBlockMode));
    private final EnumSetting<BasicUnblockMode> basicUnblockMode = add(new EnumSetting<>("AB Unblock Mode", BasicUnblockMode.StopUsingItem)
            .visibleWhen(this::isBasicAutoBlockMode));
    private final IntSetting basicReblockMin = add(new IntSetting("AB Reblock Min", 0, 0, 3, 1)
            .visibleWhen(this::isBasicAutoBlockMode));
    private final IntSetting basicReblockMax = add(new IntSetting("AB Reblock Max", 0, 0, 3, 1)
            .visibleWhen(this::isBasicAutoBlockMode));
    private final IntSetting basicPauseMin = add(new IntSetting("AB Pause On Unblock Min", 0, 0, 3, 1)
            .visibleWhen(this::isBasicAutoBlockMode));
    private final IntSetting basicPauseMax = add(new IntSetting("AB Pause On Unblock Max", 0, 0, 3, 1)
            .visibleWhen(this::isBasicAutoBlockMode));
    private final DoubleSetting basicBlockChance = add(new DoubleSetting("AB Chance", 100.0, 0.0, 100.0, 1.0)
            .visibleWhen(this::isBasicAutoBlockMode));
    private final IntSetting basicBlinkTicks = add(new IntSetting("AB Blink", 0, 0, 10, 1)
            .visibleWhen(this::isBasicAutoBlockMode));
    private final BooleanSetting basicPrioritizeBlocking = add(new BooleanSetting("AB Prioritize Blocking", true)
            .visibleWhen(this::isBasicAutoBlockMode));
    private final BooleanSetting basicOnScanRange = add(new BooleanSetting("AB On Scan Range", true)
            .visibleWhen(this::isBasicAutoBlockMode));
    private final BooleanSetting basicOnlyWhenInDanger = add(new BooleanSetting("AB Only When In Danger", false)
            .visibleWhen(this::isBasicAutoBlockMode));
    private final BooleanSetting basicAssumeShield = add(new BooleanSetting("AB Assume Shield", false)
            .visibleWhen(this::isBasicAutoBlockMode));

    private final ColorSetting sideColor = add(new ColorSetting("Side Color", Color.WHITE, false));
    private final ColorSetting lineColor = add(new ColorSetting("Line Color", new Color(255, 255, 255, 233)).visibleWhen(() -> esp.get() && espMode.is(ESPMode.Circle)));
    private final DoubleSetting circleRadius = add(new DoubleSetting("Circle Radius", 0.75, 0.1, 2.0, 0.05).visibleWhen(() -> esp.get() && espMode.is(ESPMode.Circle)));
    private final DoubleSetting circleAlphaFactor = add(new DoubleSetting("Circle Alpha Factor", 1.0, 0.0, 2.0, 0.05).visibleWhen(() -> esp.get() && espMode.is(ESPMode.Circle)));

    private final ColorSetting fireflyColor = add(new ColorSetting("Firefly Color", new Color(149, 149, 149, 255), false).visibleWhen(() -> esp.get() && espMode.is(ESPMode.Firefly)));
    private final EnumSetting<FireflyESP.ColorMode> fireflyColorMode = add(new EnumSetting<>("Firefly Color Mode", FireflyESP.ColorMode.Blend).visibleWhen(() -> esp.get() && espMode.is(ESPMode.Firefly)));
    private final ColorSetting fireflyColor2 = add(new ColorSetting("Firefly Color 2", new Color(255, 133, 161, 255), false).visibleWhen(() -> esp.get() && espMode.is(ESPMode.Firefly) && fireflyColorMode.is(FireflyESP.ColorMode.Blend)));
    private final DoubleSetting fireflyColorMix = add(new DoubleSetting("Firefly Color Mix", 0.65, 0.0, 1.0, 0.05).visibleWhen(() -> esp.get() && espMode.is(ESPMode.Firefly) && fireflyColorMode.is(FireflyESP.ColorMode.Blend)));
    private final DoubleSetting fireflyColorSpeed = add(new DoubleSetting("Firefly Color Speed", 1.2, 0.1, 6.0, 0.1).visibleWhen(() -> esp.get() && espMode.is(ESPMode.Firefly) && fireflyColorMode.is(FireflyESP.ColorMode.Blend)));
    private final DoubleSetting fireflyRainbowSpeed = add(new DoubleSetting("Firefly Rainbow Speed", 1.0, 0.1, 6.0, 0.1).visibleWhen(() -> esp.get() && espMode.is(ESPMode.Firefly) && fireflyColorMode.is(FireflyESP.ColorMode.Rainbow)));
    private final DoubleSetting fireflyRainbowSaturation = add(new DoubleSetting("Firefly Rainbow Saturation", 0.85, 0.1, 1.0, 0.05).visibleWhen(() -> esp.get() && espMode.is(ESPMode.Firefly) && fireflyColorMode.is(FireflyESP.ColorMode.Rainbow)));
    private final DoubleSetting fireflyRainbowBrightness = add(new DoubleSetting("Firefly Rainbow Brightness", 1.0, 0.1, 1.0, 0.05).visibleWhen(() -> esp.get() && espMode.is(ESPMode.Firefly) && fireflyColorMode.is(FireflyESP.ColorMode.Rainbow)));
    private final IntSetting fireflyLength = add(new IntSetting("Firefly Length", 14, 8, 128, 1).visibleWhen(() -> esp.get() && espMode.is(ESPMode.Firefly)));
    private final IntSetting fireflyFactor = add(new IntSetting("Firefly Factor", 8, 1, 10, 1).visibleWhen(() -> esp.get() && espMode.is(ESPMode.Firefly)));
    private final DoubleSetting fireflyShaking = add(new DoubleSetting("Firefly Shaking", 1.8, 0.25, 10.0, 0.25).visibleWhen(() -> esp.get() && espMode.is(ESPMode.Firefly)));
    private final DoubleSetting fireflyAmplitude = add(new DoubleSetting("Firefly Amplitude", 3.0, 0.0, 10.0, 0.25).visibleWhen(() -> esp.get() && espMode.is(ESPMode.Firefly)));

    public LivingEntity target;

    /** True while the aura has a live entity selected for rotation/attack. */
    public boolean hasLockedTarget() {
        return isEnabled() && target != null && target.isAlive() && !target.isRemoved();
    }

    private final HeypixelKillAuraEngine heypixelEngine = new HeypixelKillAuraEngine(mc);
    private EntityHitResult heypixelLookaheadHit;
    private boolean heypixelRuntimeActive;
    private int grimAttackKeepTicks;
    private int grimDamageKeepTicks;
    private int grimDamageWindowTicks;

    private int switchIndex = 0;
    private double attacks = 0.0;

    private boolean blockingState = false;
    private boolean isAutoBlocking = false;
    private boolean abFakeBlockState = false;
    private int blockTick = 0;
    private boolean blinkReset = false;
    private int hypixel3Asw = 0;
    private boolean releasedForAttack = false;

    private boolean basicRuntimeActive = false;
    private boolean basicBlockVisual = false;
    private boolean basicHasBlockedSinceAttack = false;
    private boolean basicInDanger = false;
    private InteractionHand basicEnforcedBlockingHand;
    private int basicReblockTicks;
    private int basicPauseOnUnblockTicks;
    private int basicWaitTicks;
    private int basicTicksSinceLastAttack;
    private int basicFlushTicks;
    private final Queue<Packet<?>> basicBlinkQueue = new ConcurrentLinkedQueue<>();
    private boolean basicPreserveVisualDuringSlotUnblock;
    private boolean auraAttackInProgress;

    private boolean clickTpKeyDown = false;

    @Override
    protected void onDisable() {
        if (basicRuntimeActive) {
            deactivateBasicAutoBlock();
        } else if (!noPlayer() && isAutoBlockPlayerBlocking()) {
            stopBlock();
        }
        resetAutoBlockState();
        heypixelEngine.reset();
        heypixelLookaheadHit = null;
        heypixelRuntimeActive = false;
        grimAttackKeepTicks = 0;
        grimDamageKeepTicks = 0;
        grimDamageWindowTicks = 0;
        target = null;
        switchIndex = 0;
        attacks = 0.0;
        clickTpKeyDown = false;
        auraAttackInProgress = false;
    }

    private void handleClickTp() {
        if (!clickTp.get()) { clickTpKeyDown = false; return; }
        if (noPlayer() || mc.screen != null) return;

        boolean pressed = KeybindUtils.isPressed(clickTpKey.get());
        if (!pressed) { clickTpKeyDown = false; return; }
        if (clickTpKeyDown) return;
        clickTpKeyDown = true;

        if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK) return;
        if (!(mc.hitResult instanceof BlockHitResult blockHit)) return;

        BlockPos pos = blockHit.getBlockPos();
        Vec3 dest = new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
        Vec3 start = mc.player.position();
        if (start.distanceTo(dest) > clickTpRange.get()) return;

        clickTpTo(start, dest);
    }

    private void clickTpTo(Vec3 from, Vec3 to) {
        if (mc.getConnection() == null) return;

        double dist = from.distanceTo(to);
        double step = Math.max(0.5, clickTpStep.get());
        int steps = Math.max(1, (int) Math.ceil(dist / step));
        Vec3 delta = to.subtract(from);
        for (int i = 1; i <= steps; i++) {
            double t = (double) i / steps;
            Vec3 p = from.add(delta.x * t, delta.y * t, delta.z * t);
            mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(
                    p.x, p.y, p.z, mc.player.onGround(), false));
        }
        mc.player.setPos(to.x, to.y, to.z);
        mc.player.setDeltaMovement(0.0, mc.player.getDeltaMovement().y, 0.0);
    }

    @Listen
    private void onTick(PlayerTickEvent.Pre event) {
        handleClickTp();
        if (noPlayer()) {
            deactivateBasicAutoBlock();
            target = null;
            return;
        }
        if (mode.is(Mode.Heypixel)) {
            deactivateBasicAutoBlock();
            tickHeypixel();
            return;
        }
        if (heypixelRuntimeActive) {
            resetHeypixelRuntime();
        }
        releasedForAttack = false;
        boolean basic = isBasicAutoBlockMode();
        if (basic) {
            activateBasicAutoBlock();
            tickBasicAutoBlock();
            if (shouldYieldBasicAutoBlockToPlayerUse()) {
                stopBasicBlocking(false);
                return;
            }
        } else {
            deactivateBasicAutoBlock();
            if (shouldYieldToPlayerItemUse()) {
                resetAutoBlockState();
                return;
            }
            processAutoBlock();
            if ((mc.player.isUsingItem() || mc.player.isBlocking()) && !releasedForAttack) return;
        }

        if (Scaffold.INSTANCE.isEnabled()) {
            target = null;
            if (basic) stopBasicBlocking(false);
            return;
        }

        if (onlyWeapon.get() && !isWeaponHeld()) {
            target = null;
            if (basic) stopBasicBlocking(false);
            return;
        }

        double acquireRange = getAcquireRange();
        List<LivingEntity> targets = TargetManager.INSTANCE.acquireTargets(TargetRequest.of(
                acquireRange,
                fov.get().floatValue(),
                player.get(),
                mob.get(),
                animal.get(),
                villagers.get(),
                invisible.get(),
                living -> isInModeRange(living) && !isTeammate(living),
                64
        ));

        if (targets.isEmpty()) {
            target = null;
            if (basic) {
                basicInDanger = false;
                stopBasicBlocking(false);
            }
            return;
        }

        if (mode.is(Mode.Normal)) {
            targets = sortAdvancedTargets(targets);
        }

        if (targetMode.is(TargetMode.Single)) {
            target = targets.getFirst();
        } else if (targetMode.is(TargetMode.Switch)) {
            if (switchIndex >= targets.size()) {
                switchIndex = 0;
            }
            target = targets.get(switchIndex);
        } else if (targetMode.is(TargetMode.Multiple)) {
            target = targets.getFirst();
        }

        if (basic) {
            makeBasicBlockVisual();
            basicInDanger = isBasicAutoBlockDangerous(targets);
        }

        attacks += MathUtils.getRandom(minCPS.get().doubleValue(), maxCPS.get().doubleValue()) / 20.0;

        boolean attackedThisTick = false;
        if (target != null) {
            Rot2f rotations = getTargetRotations(target);
            if (!mode.is(Mode.Normal) || !normalRotationTiming.is(NormalRotationTiming.OnTick)) {
                RotationManager.INSTANCE.setRotations(rotations, getRotationSpeed(), Priority.Medium);
            }
            if (fullCharge.get()) {
                if (isAttackCharged() && (attackedThisTick = clickTargets(targets, true))) {
                    attacks = 0.0;
                }
            } else if (mode.is(Mode.OnePointEight) || mode.is(Mode.Normal)) {
                while (attacks >= 1.0) {
                    boolean attacked = clickTargets(targets, false);
                    attackedThisTick |= attacked;
                    if (basic && !attacked) {
                        break;
                    }
                    attacks -= 1.0;
                }
            } else {
                if (isAttackCharged()) {
                    attackedThisTick = clickTargets(targets, true);
                }
            }
        }

        if (basic) {
            maintainBasicAutoBlock(attackedThisTick);
        }
    }

    private void tickHeypixel() {
        if (!heypixelRuntimeActive) {
            if (!noPlayer() && isAutoBlockPlayerBlocking()) stopBlock();
            resetAutoBlockState();
            heypixelEngine.reset();
            heypixelRuntimeActive = true;
        }

        if (grimAttackKeepTicks > 0) grimAttackKeepTicks--;
        if (grimDamageKeepTicks > 0) grimDamageKeepTicks--;
        if (grimDamageWindowTicks > 0) grimDamageWindowTicks--;

        if (!shouldRunHeypixel()) {
            heypixelEngine.resetTargeting();
            heypixelLookaheadHit = null;
            target = null;
            updateHeypixelAutoBlock(false);
            return;
        }

        HeypixelKillAuraEngine.Config config = getHeypixelConfig();
        heypixelEngine.update(config, living -> {
            if (living instanceof Player playerTarget && FriendManager.INSTANCE.isFriend(playerTarget)) {
                return false;
            }
            return !isTeammate(living);
        });

        HeypixelKillAuraEngine.CurrentTarget attackTarget = heypixelEngine.getAttackTarget();
        target = attackTarget == null ? null : attackTarget.entity();

        Rot2f rotations = heypixelEngine.nextRotation(
                heypixelBypass.get(),
                heypixelMaxAngle.get().floatValue()
        );
        if (rotations != null) {
            RotationManager.INSTANCE.setRotationsDirect(rotations, Priority.Medium);
        }

        updateHeypixelAutoBlock(attackTarget != null);
        if (mc.screen != null) return;

        HitResult crosshair = mc.hitResult;
        if (attackTarget == null || crosshair == null || crosshair.getType() == HitResult.Type.MISS) {
            tryHeypixelFakeSwing(crosshair);
            return;
        }

        if (mc.player.isUsingItem()) return;

        boolean effectiveOverrideRaycast = overrideRaycast.get() && !heypixelBypass.get();
        if (effectiveOverrideRaycast) {
            if (tickLookahead.get()
                    && (heypixelLookaheadHit == null
                    || heypixelLookaheadHit.getEntity() != attackTarget.entity())) {
                return;
            }
            mc.hitResult = attackTarget.hitResult();
            mc.crosshairPickEntity = attackTarget.entity();
            crosshair = attackTarget.hitResult();
        }

        if (!(crosshair instanceof EntityHitResult entityHit)
                || entityHit.getEntity() != attackTarget.entity()) {
            return;
        }
        if (hitSelect.get() && Velocity.INSTANCE.shouldSuppressKillAuraAttack()) {
            return;
        }

        int smartWeaponSlot = getHeypixelSmartWeaponSlot(attackTarget.entity());
        boolean smartWeaponAttack = smartWeaponSlot != -1;
        if (!heypixelEngine.isAttackAvailable(
                heypixelAttackCps.get(),
                heypixelBypass.get(),
                attackCooldown19.get(),
                smartWeaponAttack)) {
            heypixelEngine.recordUnavailableAttack();
            return;
        }

        if (smartWeaponAttack) {
            selectHotbarSlot(smartWeaponSlot);
        }
        performHeypixelAttack(attackTarget.entity());
    }

    private HeypixelKillAuraEngine.Config getHeypixelConfig() {
        return new HeypixelKillAuraEngine.Config(
                heypixelRange.get(),
                heypixelRotationRange.get(),
                swingRange.get(),
                heypixelFov.get().floatValue(),
                heypixelPlayers.get(),
                heypixelMobs.get(),
                heypixelAnimals.get(),
                heypixelVillagers.get(),
                heypixelInvisible.get(),
                throughWalls.get(),
                heypixelTargetMode.is(HeypixelTargetMode.Switch)
                        ? HeypixelKillAuraEngine.TargetMode.SWITCH
                        : HeypixelKillAuraEngine.TargetMode.SINGLE
        );
    }

    private boolean shouldRunHeypixel() {
        if (noPlayer() || mc.gameMode == null) return false;
        if (isConsumingFoodOrPotion()) return false;
        if (requireAttackKey.get() && !mc.options.keyAttack.isDown()) return false;
        if (onlyWeapon.get() && !isHeypixelWeapon(mc.player.getMainHandItem())) return false;
        return !Scaffold.INSTANCE.isEnabled();
    }

    private boolean isConsumingFoodOrPotion() {
        if (mc.player == null || !mc.player.isUsingItem()) return false;
        ItemStack stack = mc.player.getUseItem();
        return !stack.isEmpty()
                && (stack.getComponents().has(DataComponents.FOOD)
                || stack.getItem() instanceof PotionItem);
    }

    private boolean isHeypixelWeapon(ItemStack stack) {
        return !stack.isEmpty()
                && (stack.is(ItemTags.SWORDS)
                || stack.is(ItemTags.AXES)
                || stack.is(ItemTags.PICKAXES));
    }

    private void tryHeypixelFakeSwing(HitResult crosshair) {
        if (heypixelEngine.getClosestDistance() > swingRange.get()) return;
        if (!heypixelEngine.isFakeSwingAvailable(heypixelSwingCps.get())) return;
        if (crosshair instanceof BlockHitResult) return;

        if (crosshair instanceof EntityHitResult entityHit && mc.gameMode != null) {
            mc.gameMode.attack(mc.player, entityHit.getEntity());
        }
        if (hideFakeSwings.get() && !(crosshair instanceof EntityHitResult)) {
            if (mc.getConnection() != null) {
                mc.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
            }
        } else {
            mc.player.swing(InteractionHand.MAIN_HAND);
        }
        heypixelEngine.recordFakeSwing(heypixelSwingCps.get());
    }

    private int getHeypixelSmartWeaponSlot(LivingEntity attackTarget) {
        if (!smartWeapon.get() || attackTarget == null || !attackTarget.isUsingItem()) return -1;
        ItemStack activeItem = attackTarget.getUseItem();
        if (activeItem.isEmpty() || !(activeItem.getItem() instanceof ShieldItem)) return -1;

        for (int slot = 0; slot < 9; slot++) {
            if (mc.player.getInventory().getItem(slot).getItem() instanceof AxeItem) {
                return slot;
            }
        }
        return -1;
    }

    private void selectHotbarSlot(int slot) {
        if (slot < 0 || slot > 8 || mc.getConnection() == null) return;
        mc.player.getInventory().setSelectedSlot(slot);
        mc.getConnection().send(new ServerboundSetCarriedItemPacket(slot));
    }

    private void performHeypixelAttack(LivingEntity attackTarget) {
        boolean wasSprinting = mc.player.isSprinting();
        Vec3 movement = mc.player.getDeltaMovement();

        heypixelEngine.recordAttack(heypixelAttackCps.get(), heypixelBypass.get());
        mc.gameMode.attack(mc.player, attackTarget);
        if (swingHand.get()) {
            mc.player.swing(InteractionHand.MAIN_HAND);
        } else if (mc.getConnection() != null) {
            mc.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
        }

        if (grimKeepSprint.get() && wasSprinting) {
            grimAttackKeepTicks = 2;
            mc.player.setDeltaMovement(movement.x * 0.6D, mc.player.getDeltaMovement().y, movement.z * 0.6D);
            mc.player.setSprinting(true);
        }
    }

    private void updateHeypixelAutoBlock(boolean hasAttackTarget) {
        if (noPlayer()
                || mc.screen != null
                || isConsumingFoodOrPotion()
                || Scaffold.INSTANCE.isEnabled()
                || !hasAttackTarget
                || heypixelAutoBlock.is(HeypixelAutoBlockMode.Off)) {
            releaseHeypixelAutoBlock();
            return;
        }

        if (heypixelAutoBlock.is(HeypixelAutoBlockMode.Fake)) {
            if (blockingState) stopBlock();
            isAutoBlocking = false;
            abFakeBlockState = true;
            return;
        }

        if (!mc.player.getMainHandItem().is(ItemTags.SWORDS)) {
            releaseHeypixelAutoBlock();
            return;
        }
        if (!blockingState) startHeypixelBlock();
        isAutoBlocking = true;
        abFakeBlockState = false;
    }

    private void startHeypixelBlock() {
        if (mc.getConnection() == null) return;
        mc.getConnection().send(new ServerboundUseItemPacket(
                InteractionHand.MAIN_HAND,
                0,
                mc.player.getYRot(),
                mc.player.getXRot()
        ));
        blockingState = true;
    }

    private void releaseHeypixelAutoBlock() {
        if (blockingState && !noPlayer()) stopBlock();
        isAutoBlocking = false;
        abFakeBlockState = false;
        blockingState = false;
    }

    private void resetHeypixelRuntime() {
        releaseHeypixelAutoBlock();
        heypixelEngine.reset();
        heypixelLookaheadHit = null;
        heypixelRuntimeActive = false;
        grimAttackKeepTicks = 0;
        grimDamageKeepTicks = 0;
        grimDamageWindowTicks = 0;
        target = null;
    }

    @Listen(priority = -1000)
    private void onSendPosition(SendPositionEvent event) {
        if (!mode.is(Mode.Heypixel)
                || !tickLookahead.get()
                || !overrideRaycast.get()
                || heypixelBypass.get()) {
            heypixelLookaheadHit = null;
            return;
        }
        HeypixelKillAuraEngine.CurrentTarget current = heypixelEngine.getAttackTarget();
        if (current == null) {
            heypixelLookaheadHit = null;
            return;
        }
        heypixelLookaheadHit = heypixelEngine.raycastEntity(
                current.entity(),
                heypixelRange.get(),
                new Rot2f(event.getYaw(), event.getPitch())
        );
    }

    @Listen
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!mode.is(Mode.Heypixel) || !grimKeepSprint.get() || mc.player == null) return;
        if (event.getPacket() instanceof ClientboundDamageEventPacket damage
                && damage.entityId() == mc.player.getId()) {
            grimDamageWindowTicks = 4;
        } else if (event.getPacket() instanceof ClientboundSetEntityMotionPacket motion
                && motion.id() == mc.player.getId()
                && grimDamageWindowTicks > 0) {
            grimDamageKeepTicks = Math.max(grimDamageKeepTicks, 6);
            grimDamageWindowTicks = 0;
        }
    }

    @Listen
    private void onPacketSend(PacketEvent.Send event) {
        if (!basicRuntimeActive) {
            return;
        }
        Packet<?> packet = event.getPacket();
        boolean internalSlotUnblockPacket = basicPreserveVisualDuringSlotUnblock
                && packet instanceof ServerboundSetCarriedItemPacket;
        if ((packet instanceof ServerboundSetCarriedItemPacket
                && basicEnforcedBlockingHand == InteractionHand.MAIN_HAND)
                || (packet instanceof ServerboundPlayerActionPacket actionPacket
                && actionPacket.getAction() == ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND)) {
            if (shouldClearBasicBlockVisualOnSlotChange(internalSlotUnblockPacket)) {
                basicBlockVisual = false;
            }
            clearBasicBlockingState();
        }

        if (internalSlotUnblockPacket) {
            flushBasicBlinkQueue();
            return;
        }

        if (!shouldQueueBasicBlinkPacket(
                basicBlockVisual,
                basicEnforcedBlockingHand != null,
                packet instanceof ServerboundUseItemPacket,
                basicFlushTicks,
                basicBlinkTicks.get())) {
            flushBasicBlinkQueue();
            return;
        }

        event.cancel();
        basicBlinkQueue.add(packet);
    }

    @Listen
    private void onRespawn(RespawnEvent event) {
        if (!basicRuntimeActive) {
            return;
        }
        flushBasicBlinkQueue();
        basicBlockVisual = false;
        basicHasBlockedSinceAttack = false;
        basicInDanger = false;
        basicEnforcedBlockingHand = null;
        basicWaitTicks = 0;
        basicTicksSinceLastAttack = 0;
        basicFlushTicks = 0;
        basicPreserveVisualDuringSlotUnblock = false;
        blockingState = false;
        isAutoBlocking = false;
    }

    @Listen(priority = com.setsuna.event.Priority.HIGH)
    private void onSlowdown(SlowdownEvent event) {
        if (event.getPlayer() == mc.player && shouldBypassBasicBlockSlowdown()) {
            event.cancel();
        }
    }

    @Listen(priority = com.setsuna.event.Priority.HIGH)
    private void onAttackSlowDown(AttackSlowDownEvent event) {
        if (shouldCancelAuraAttackSlowdown(KeepSprint.INSTANCE.isEnabled(), auraAttackInProgress)) {
            event.cancel();
        }
    }

    private void flushBasicBlinkQueue() {
        Packet<?> packet;
        while ((packet = basicBlinkQueue.poll()) != null) {
            PacketUtils.sendSilently(packet);
        }
        basicFlushTicks = 0;
    }

    private boolean isAttackCharged() {
        float threshold = (fullCharge.get() && dynamicCharge.get()) ? 0.9f : 1.0f;
        return mc.player.getAttackStrengthScale(0.5f) >= threshold;
    }

    private double getAcquireRange() {
        double acquireRange = mode.is(Mode.Normal)
                ? Math.max(range.get(), rotationRange.get()) + scanRangeIncrease.get()
                : aimRange.get();
        if (isBasicAutoBlockMode() && basicOnScanRange.get()) {
            acquireRange = Math.max(acquireRange, autoBlockRange.get());
        }
        return acquireRange;
    }

    private Rot2f getTargetRotations(LivingEntity entity) {
        if (mode.is(Mode.Normal)) return getNormalRotations(entity);
        return RotationUtils.getRotationsToEntity(entity);
    }

    private boolean clickTargets(List<LivingEntity> targets, boolean singleHit) {
        boolean attacked = false;
        boolean basicPrepared = false;
        if (targetMode.is(TargetMode.Multiple)) {
            for (LivingEntity target : targets) {
                if (usesAdvancedHitCheck() ? canAttackTarget(target) : canAttackTarget(target) && (throughWalls.get() || mc.hitResult.getType() == HitResult.Type.ENTITY)) {
                    if (isBasicAutoBlockMode() && !basicPrepared) {
                        if (!prepareBasicAutoBlockAttack()) {
                            return false;
                        }
                        basicPrepared = true;
                    }
                    doAttack(target);
                    attacked = true;
                    if (singleHit) {
                        break;
                    }
                }
            }
            switchIndex++;
        } else {
            if (usesAdvancedHitCheck() ? canAttackTarget(target) : canAttackTarget(target) && (throughWalls.get() || (mc.hitResult.getType() == HitResult.Type.ENTITY && mc.crosshairPickEntity == target))) {
                if (isBasicAutoBlockMode() && !prepareBasicAutoBlockAttack()) {
                    return false;
                }
                doAttack(target);
                attacked = true;
            }
            if (targetMode.is(TargetMode.Switch)) {
                switchIndex++;
            }
        }
        if (attacked && isBasicAutoBlockMode()) {
            onBasicAutoBlockAttackCompleted();
        }
        return attacked;
    }

    private boolean usesAdvancedHitCheck() {
        return mode.is(Mode.Normal);
    }

    private void doAttack(LivingEntity target) {
        if (mode.is(Mode.Normal) && normalRotationTiming.is(NormalRotationTiming.OnTick)) {
            sendNormalRotationPacket(getNormalRotations(target));
        }
        auraAttackInProgress = true;
        try {
            mc.gameMode.attack(mc.player, target);
        } finally {
            auraAttackInProgress = false;
        }
        mc.player.resetAttackStrengthTicker();
        if (swingHand.get()) {
            mc.player.swing(InteractionHand.MAIN_HAND);
        } else {
            mc.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
        }
        if (mode.is(Mode.Normal) && normalRotationTiming.is(NormalRotationTiming.OnTick)) {
            sendNormalRotationPacket(new Rot2f(mc.player.getYRot(), mc.player.getXRot()));
        }
    }

    private boolean canAttackTarget(LivingEntity target) {
        if (target == null) return false;
        if (isTeammate(target)) return false;
        if (mode.is(Mode.Normal)) return canNormalHit(target);
        return RotationUtils.getEyeDistanceToEntity(target) <= range.get();
    }

    private boolean isTeammate(LivingEntity entity) {
        if (!teamCheck.get() || mc.player == null) return false;
        if (entity instanceof Player playerTarget) {
            if (mc.player.isAlliedTo(playerTarget)) return true;
            if (mc.player.getTeam() != null || playerTarget.getTeam() != null) return false;

            Integer localColor = getTabNameColor(mc.player);
            Integer targetColor = getTabNameColor(playerTarget);
            if (localColor != null && targetColor != null) {
                return TeamColorUtils.sameColor(localColor, targetColor);
            }
            return !mc.player.canHarmPlayer(playerTarget);
        }
        return mc.player.isAlliedTo(entity);
    }

    private Integer getTabNameColor(Player player) {
        if (mc.getConnection() != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(player.getUUID());
            if (info == null) {
                info = mc.getConnection().getPlayerInfoIgnoreCase(player.getGameProfile().name());
            }
            if (info != null) {
                Integer tabColor = TeamColorUtils.getTextColor(info.getTabListDisplayName());
                if (tabColor != null) return tabColor;
            }
        }
        return TeamColorUtils.getTeamTextColor(player);
    }

    private boolean isInModeRange(LivingEntity living) {
        double distance = RotationUtils.getEyeDistanceToEntity(living);
        if (isBasicAutoBlockMode() && basicOnScanRange.get() && distance <= autoBlockRange.get()) {
            return true;
        }
        if (mode.is(Mode.Normal)) {
            Vec3 eyes = mc.player.getEyePosition();
            Vec3 closest = closestPoint(eyes, getExpandedBox(living, 0.0));
            double closestDistance = eyes.distanceTo(closest);
            double visibleRange = Math.max(range.get(), rotationRange.get()) + scanRangeIncrease.get();
            double wallRange = rotationRange.get() + scanRangeIncrease.get();
            return closestDistance <= (canSeePoint(closest) || throughWalls.get() ? visibleRange : wallRange);
        }
        return distance <= aimRange.get();
    }

    private List<LivingEntity> sortAdvancedTargets(List<LivingEntity> targets) {
        Comparator<LivingEntity> comparator = switch (priority.get()) {
            case Health -> Comparator.comparingDouble(this::getPriorityHealth);
            case Distance -> Comparator.comparingDouble(RotationUtils::getEyeDistanceToEntity);
            case FOV -> Comparator.comparingDouble(this::rotationDistance);
        };
        return targets.stream().sorted(comparator).toList();
    }

    private double getPriorityHealth(LivingEntity entity) {
        double health = HealthManager.INSTANCE.getHealth(entity);
        return entity instanceof Player ? health : health * 2.0;
    }

    private float getRotationSpeed() {
        if (mode.is(Mode.Normal)) {
            return normalRotationTiming.is(NormalRotationTiming.Snap) ? 10.0f : rotationSpeed.get().floatValue();
        }
        return rotationSpeed.get().floatValue();
    }

    private void sendNormalRotationPacket(Rot2f rotations) {
        if (mc.getConnection() == null || mc.player == null || rotations == null) return;
        if (Float.isNaN(rotations.getYaw()) || Float.isNaN(rotations.getPitch())) return;

        mc.getConnection().send(new ServerboundMovePlayerPacket.PosRot(
                mc.player.position(),
                rotations.getYaw(),
                rotations.getPitch(),
                mc.player.onGround(),
                mc.player.horizontalCollision
        ));
    }

    private double rotationDistance(LivingEntity entity) {
        Rot2f rotations = getNormalRotations(entity);
        float yawDiff = Math.abs(Mth.wrapDegrees(rotations.getYaw() - mc.player.getYRot()));
        float pitchDiff = Math.abs(Mth.wrapDegrees(rotations.getPitch() - mc.player.getXRot()));
        return yawDiff * yawDiff + pitchDiff * pitchDiff;
    }

    private Rot2f getNormalRotations(LivingEntity entity) {
        Vec3 eyes = mc.player.getEyePosition();
        Vec3 point = selectNormalAimPoint(entity, eyes);
        Rot2f rotations = RotationUtils.calculate(eyes, point);
        float baseYaw = RotationManager.INSTANCE.isActive() ? RotationManager.INSTANCE.getYaw() : mc.player.getYRot();
        return new Rot2f(baseYaw + Mth.wrapDegrees(rotations.getYaw() - baseYaw), Mth.clamp(rotations.getPitch(), -90.0f, 90.0f));
    }

    private Vec3 selectNormalAimPoint(LivingEntity entity, Vec3 eyes) {
        AABB box = getExpandedBox(entity, 0.0);
        Vec3 closest = closestPoint(eyes, box);

        Vec3[] candidates = new Vec3[]{
                closest,
                box.getCenter(),
                entity.getEyePosition(),
                new Vec3(entity.getX(), Mth.lerp(0.75, box.minY, box.maxY), entity.getZ()),
                new Vec3(entity.getX(), Mth.lerp(0.5, box.minY, box.maxY), entity.getZ()),
                new Vec3(entity.getX(), Mth.lerp(0.25, box.minY, box.maxY), entity.getZ())
        };

        Vec3 best = closest;
        double bestAngle = Double.MAX_VALUE;
        for (Vec3 candidate : candidates) {
            if (!isPointInNormalRange(candidate)) continue;
            Rot2f rotations = RotationUtils.calculate(eyes, candidate);
            double angle = rotationDifference(rotations);
            if (angle < bestAngle) {
                best = candidate;
                bestAngle = angle;
            }
        }
        return best;
    }

    private boolean isPointInNormalRange(Vec3 point) {
        double distance = mc.player.getEyePosition().distanceTo(point);
        if (distance <= range.get() && canSeePoint(point)) return true;
        return throughWalls.get() && distance <= rotationRange.get();
    }

    private boolean canNormalHit(LivingEntity entity) {
        if (!canNormalCritical(entity)) {
            return false;
        }

        Rot2f rotations = normalRotationTiming.is(NormalRotationTiming.OnTick)
                ? getNormalRotations(entity)
                : RotationManager.INSTANCE.isActive() ? RotationManager.INSTANCE.getRotation() : getNormalRotations(entity);

        double rayRange = Math.max(range.get(), rotationRange.get());
        HitResult result = RaytraceUtils.raytrace(rotations, rayRange, 0.1f);

        if (result instanceof EntityHitResult entityHitResult) {
            if (normalRaycast.is(RaycastMode.Enemy) && !(entityHitResult.getEntity() instanceof LivingEntity living && !isTeammate(living))) {
                return false;
            }
            if (normalRaycast.is(RaycastMode.All) && entityHitResult.getEntity() != entity) {
                return false;
            }
            if (entityHitResult.getEntity() == entity) {
                return mc.player.getEyePosition().distanceTo(entityHitResult.getLocation()) <= range.get();
            }
        }

        if (normalRaycast.is(RaycastMode.None)) {
            Vec3 closest = closestPoint(mc.player.getEyePosition(), getExpandedBox(entity, 0.1));
            return mc.player.getEyePosition().distanceTo(closest) <= range.get()
                    && (throughWalls.get() || canSeePoint(closest));
        }

        if (!throughWalls.get()) return false;

        Vec3 closest = closestPoint(mc.player.getEyePosition(), getExpandedBox(entity, 0.1));
        return mc.player.getEyePosition().distanceTo(closest) <= rotationRange.get();
    }

    private boolean canNormalCritical(LivingEntity entity) {
        return switch (normalCriticals.get()) {
            case Ignore -> true;
            case Always -> wouldDoNormalCritical();
            case Smart -> !shouldWaitForNormalCrit(entity);
        };
    }

    private boolean shouldWaitForNormalCrit(LivingEntity entity) {
        if (mc.player.isFallFlying()) return false;
        if (!allowsNormalCritical(false)) return false;
        if (mc.player.getDeltaMovement().y < -0.08) return false;

        float nextPossibleCrit = calculateTicksUntilNextNormalCrit();
        float ticksTillFall = (float) (mc.player.getDeltaMovement().y / 0.08);
        float ticksTillCrit = Math.max(nextPossibleCrit, ticksTillFall);
        float damageOnCrit = 0.5f * 0.75f;
        float damageLostWaiting = getNormalCooldownDamageFactor(ticksTillCrit);

        if (damageOnCrit <= damageLostWaiting) return false;

        if (entity != null && RotationUtils.getEyeDistanceToEntity(entity) > range.get() + 0.75) {
            return false;
        }

        return ticksTillCrit > 0.0f && ticksTillCrit < 8.0f;
    }

    private boolean wouldDoNormalCritical() {
        return canDoNormalCritical(false, false) && mc.player.fallDistance > 0.0f;
    }

    private boolean canDoNormalCritical(boolean ignoreOnGround, boolean ignoreSprint) {
        return allowsNormalCritical(ignoreOnGround)
                && mc.player.getAttackStrengthScale(0.5f) > 0.9f
                && (!mc.player.isSprinting() || ignoreSprint);
    }

    private boolean allowsNormalCritical(boolean ignoreOnGround) {
        return !mc.player.isFallFlying()
                && !mc.player.isInWater()
                && !mc.player.isInLava()
                && !mc.player.isPassenger()
                && !mc.player.onClimbable()
                && !mc.player.isNoGravity()
                && !mc.player.getAbilities().flying
                && !mc.player.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS)
                && !mc.player.hasEffect(net.minecraft.world.effect.MobEffects.LEVITATION)
                && !mc.player.hasEffect(net.minecraft.world.effect.MobEffects.SLOW_FALLING)
                && (ignoreOnGround || !mc.player.onGround());
    }

    private float calculateTicksUntilNextNormalCrit() {
        float durationToWait = mc.player.getCurrentItemAttackStrengthDelay() * 0.9f - 0.5f;
        float waitedDuration = mc.player.getAttackStrengthScale(0.5f) * mc.player.getCurrentItemAttackStrengthDelay();
        return Math.max(durationToWait - waitedDuration, 0.0f);
    }

    private float getNormalCooldownDamageFactor(float tickDelta) {
        float base = (tickDelta + 0.5f) / mc.player.getCurrentItemAttackStrengthDelay();
        return Math.min(1.0f, 0.2f + base * base * 0.8f);
    }

    private AABB getExpandedBox(LivingEntity entity, double extra) {
        return entity.getBoundingBox().inflate(entity.getPickRadius() + extra);
    }

    private Vec3 closestPoint(Vec3 eyes, AABB box) {
        return new Vec3(
                Mth.clamp(eyes.x, box.minX, box.maxX),
                Mth.clamp(eyes.y, box.minY, box.maxY),
                Mth.clamp(eyes.z, box.minZ, box.maxZ)
        );
    }

    private boolean canSeePoint(Vec3 point) {
        return mc.level.clip(new ClipContext(mc.player.getEyePosition(), point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player)).getType() == HitResult.Type.MISS;
    }

    private double rotationDifference(Rot2f rotations) {
        float baseYaw = RotationManager.INSTANCE.isActive() ? RotationManager.INSTANCE.getYaw() : mc.player.getYRot();
        float basePitch = RotationManager.INSTANCE.isActive() ? RotationManager.INSTANCE.getPitch() : mc.player.getXRot();
        float yawDiff = Math.abs(Mth.wrapDegrees(rotations.getYaw() - baseYaw));
        float pitchDiff = Math.abs(Mth.wrapDegrees(rotations.getPitch() - basePitch));
        return yawDiff * yawDiff + pitchDiff * pitchDiff;
    }

    private boolean isBasicAutoBlockMode() {
        return !mode.is(Mode.Heypixel) && autoBlock.is(AutoBlockMode.Basic);
    }

    private void activateBasicAutoBlock() {
        if (basicRuntimeActive) {
            return;
        }
        if (blockingState && !noPlayer()) {
            stopBlock();
        }
        resetAutoBlockState();
        basicRuntimeActive = true;
        basicBlockVisual = false;
        basicHasBlockedSinceAttack = false;
        basicInDanger = false;
        basicEnforcedBlockingHand = null;
        basicReblockTicks = randomBasicTicks(basicReblockMin, basicReblockMax);
        basicPauseOnUnblockTicks = randomBasicTicks(basicPauseMin, basicPauseMax);
        basicWaitTicks = 0;
        basicTicksSinceLastAttack = 0;
        basicFlushTicks = 0;
        basicPreserveVisualDuringSlotUnblock = false;
    }

    private void deactivateBasicAutoBlock() {
        if (!basicRuntimeActive) {
            return;
        }
        if (!noPlayer()) {
            stopBasicBlocking(false);
        }
        flushBasicBlinkQueue();
        basicRuntimeActive = false;
        basicBlockVisual = false;
        basicHasBlockedSinceAttack = false;
        basicInDanger = false;
        basicEnforcedBlockingHand = null;
        basicWaitTicks = 0;
        basicTicksSinceLastAttack = 0;
        basicFlushTicks = 0;
        basicPreserveVisualDuringSlotUnblock = false;
        blockingState = false;
        isAutoBlocking = false;
    }

    private void tickBasicAutoBlock() {
        if (basicFlushTicks < Integer.MAX_VALUE) {
            basicFlushTicks++;
        }
        if (basicTicksSinceLastAttack < Integer.MAX_VALUE) {
            basicTicksSinceLastAttack++;
        }
        if (basicWaitTicks > 0) {
            basicWaitTicks--;
        }
    }

    private void makeBasicBlockVisual() {
        if (basicRuntimeActive) {
            basicBlockVisual = true;
        }
    }

    private boolean shouldYieldBasicAutoBlockToPlayerUse() {
        if (basicEnforcedBlockingHand != null && isBasicBlockingServerside()) {
            return false;
        }
        boolean usingItem = mc.player.isUsingItem();
        boolean usingBlockItem = usingItem && mc.player.getUseItem().getUseAnimation() == ItemUseAnimation.BLOCK;
        boolean useKeyRequestsItem = isUseKeyPressedOnAny()
                && (hasUseDuration(mc.player.getMainHandItem()) || hasUseDuration(mc.player.getOffhandItem()));
        return (usingItem && !usingBlockItem) || useKeyRequestsItem;
    }

    private boolean prepareBasicAutoBlockAttack() {
        if (!basicRuntimeActive || basicWaitTicks > 0) {
            return false;
        }
        if (isPrioritizingBasicBlocking()) {
            return false;
        }
        if (isBasicBlockingServerside()) {
            if (!basicUnblockMode.is(BasicUnblockMode.None)) {
                boolean unblocked = stopBasicBlocking(true);
                if (unblocked && basicPauseOnUnblockTicks > 0) {
                    basicWaitTicks = basicPauseOnUnblockTicks;
                    return false;
                }
            }
        } else if (mc.player.isUsingItem()) {
            return false;
        }
        return true;
    }

    private boolean isPrioritizingBasicBlocking() {
        if (mc.player.isUsingItem()) {
            basicHasBlockedSinceAttack = true;
        }
        if (!basicRuntimeActive || !basicPrioritizeBlocking.get() || findBasicBlockableHand() == null) {
            return false;
        }
        return !basicHasBlockedSinceAttack && (!basicOnlyWhenInDanger.get() || basicInDanger);
    }

    private void onBasicAutoBlockAttackCompleted() {
        basicTicksSinceLastAttack = 0;
        basicHasBlockedSinceAttack = false;
        if (shouldReblockBasicImmediately(basicReblockTicks)) {
            startBasicBlocking();
        }
    }

    private void maintainBasicAutoBlock(boolean attackedThisTick) {
        if (!basicRuntimeActive || target == null) {
            stopBasicBlocking(false);
            return;
        }
        if (basicWaitTicks > 0) {
            return;
        }

        double targetDistance = RotationUtils.getEyeDistanceToEntity(target);
        boolean inInteractionRange = targetDistance <= range.get();
        boolean inScanRange = basicOnScanRange.get() && targetDistance <= autoBlockRange.get();
        if (!inInteractionRange && !inScanRange) {
            stopBasicBlocking(false);
            return;
        }

        if (shouldStartBasicReblock(attackedThisTick, basicTicksSinceLastAttack, basicReblockTicks)) {
            startBasicBlocking();
        }
    }

    private boolean startBasicBlocking() {
        if (!basicRuntimeActive
                || !passesBasicBlockChance(ThreadLocalRandom.current().nextInt(100), basicBlockChance.get())) {
            return false;
        }
        if (basicOnlyWhenInDanger.get() && !basicInDanger) {
            stopBasicBlocking(false);
            return false;
        }
        if (mc.player.isUsingItem()) {
            basicHasBlockedSinceAttack = true;
            return false;
        }

        InteractionHand blockHand = findBasicBlockableHand();
        if (blockHand == null) {
            return false;
        }
        Rot2f rotation = RotationManager.INSTANCE.isActive()
                ? RotationManager.INSTANCE.getRotation()
                : new Rot2f(mc.player.getYRot(), mc.player.getXRot());

        boolean blocked;
        if (basicSimulateVanillaUse.get()) {
            InteractionHand successfulHand = useBasicItemStrict(rotation);
            blocked = successfulHand == blockHand;
        } else {
            blocked = useBasicItem(blockHand, rotation);
        }

        if (blocked) {
            basicReblockTicks = randomBasicTicks(basicReblockMin, basicReblockMax);
            basicEnforcedBlockingHand = blockHand;
            basicHasBlockedSinceAttack = true;
            blockingState = true;
            isAutoBlocking = true;
        }
        basicBlockVisual = true;
        return true;
    }

    private InteractionHand useBasicItemStrict(Rot2f rotation) {
        for (InteractionHand hand : InteractionHand.values()) {
            if (useBasicItem(hand, rotation)) {
                return hand;
            }
        }
        return null;
    }

    private boolean useBasicItem(InteractionHand hand, Rot2f rotation) {
        if (mc.gameMode == null) {
            return false;
        }
        float oldYaw = mc.player.getYRot();
        float oldPitch = mc.player.getXRot();
        InteractionResult result;
        try {
            mc.player.setYRot(rotation.getYaw());
            mc.player.setXRot(rotation.getPitch());
            result = mc.gameMode.useItem(mc.player, hand);
        } finally {
            mc.player.setYRot(oldYaw);
            mc.player.setXRot(oldPitch);
        }

        if (result instanceof InteractionResult.Success success) {
            if (success.swingSource() == InteractionResult.SwingSource.CLIENT) {
                mc.player.swing(hand);
            }
            mc.gameRenderer.itemInHandRenderer.itemUsed(hand);
            return true;
        }
        return false;
    }

    private InteractionHand findBasicBlockableHand() {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = mc.player.getItemInHand(hand);
            if (stack.getUseAnimation() == ItemUseAnimation.BLOCK
                    && stack.isItemEnabled(mc.level.enabledFeatures())
                    && !mc.player.getCooldowns().isOnCooldown(stack)) {
                return hand;
            }
        }
        if (basicAssumeShield.get() && !basicBlocksAttacksExisting()
                && mc.player.getMainHandItem().is(ItemTags.SWORDS)) {
            return InteractionHand.MAIN_HAND;
        }
        return null;
    }

    private boolean isBasicBlockingServerside() {
        if (mc.player.isBlocking()) {
            return true;
        }
        if (!mc.player.isUsingItem() || basicBlocksAttacksExisting()) {
            return false;
        }
        ItemStack usingItem = mc.player.getUseItem();
        return usingItem == mc.player.getMainHandItem() && usingItem.is(ItemTags.SWORDS)
                || usingItem == mc.player.getOffhandItem() && usingItem.getItem() instanceof ShieldItem;
    }

    private boolean basicBlocksAttacksExisting() {
        ProtocolVersion version = ProtocolTranslator.getTargetVersion();
        return version.olderThanOrEqualTo(ProtocolVersion.v1_8)
                || version.newerThanOrEqualTo(ProtocolVersion.v1_21_5);
    }

    private boolean stopBasicBlocking(boolean pauses) {
        if (!pauses) {
            basicBlockVisual = false;
            if (isUseKeyPressedOnAny()) {
                return false;
            }
        }
        if (!isBasicBlockingServerside()) {
            return false;
        }

        basicPauseOnUnblockTicks = randomBasicTicks(basicPauseMin, basicPauseMax);
        InteractionHand enforcedHand = basicEnforcedBlockingHand;
        return switch (basicUnblockMode.get()) {
            case StopUsingItem -> {
                releaseBasicUsingItem();
                yield true;
            }
            case ChangeSlot -> {
                int currentSlot = mc.player.getInventory().getSelectedSlot();
                boolean previousPreserveVisual = basicPreserveVisualDuringSlotUnblock;
                basicPreserveVisualDuringSlotUnblock = pauses;
                try {
                    sendSlotPacket(getBasicChangeSlot(currentSlot));
                    sendSlotPacket(currentSlot);
                } finally {
                    basicPreserveVisualDuringSlotUnblock = previousPreserveVisual;
                }
                if (enforcedHand == InteractionHand.MAIN_HAND) {
                    mc.player.stopUsingItem();
                    clearBasicBlockingState();
                    yield true;
                }
                yield false;
            }
            case SwapHand -> {
                sendBasicSwapHandPacket();
                sendBasicSwapHandPacket();
                clearBasicBlockingState();
                yield true;
            }
            case None -> {
                if (!pauses) {
                    releaseBasicUsingItem();
                    yield true;
                }
                yield false;
            }
        };
    }

    private void releaseBasicUsingItem() {
        if (mc.gameMode != null) {
            mc.gameMode.releaseUsingItem(mc.player);
        }
        clearBasicBlockingState();
    }

    private void sendBasicSwapHandPacket() {
        if (mc.getConnection() != null) {
            mc.getConnection().send(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                    BlockPos.ZERO, Direction.DOWN, 0));
        }
    }

    private void clearBasicBlockingState() {
        basicEnforcedBlockingHand = null;
        blockingState = false;
        isAutoBlocking = false;
    }

    private boolean isBasicAutoBlockDangerous(List<LivingEntity> candidates) {
        for (LivingEntity candidate : candidates) {
            if (RotationUtils.getEyeDistanceToEntity(candidate) <= range.get()
                    && isLookingAtLocalPlayer(candidate)) {
                return true;
            }
        }
        return false;
    }

    private boolean isLookingAtLocalPlayer(LivingEntity candidate) {
        Vec3 start = candidate.getEyePosition();
        Vec3 end = start.add(candidate.getLookAngle().scale(range.get()));
        Vec3 hit = mc.player.getBoundingBox().inflate(mc.player.getPickRadius()).clip(start, end).orElse(null);
        if (hit == null) {
            return false;
        }
        if (throughWalls.get()) {
            return true;
        }
        HitResult obstruction = mc.level.clip(new ClipContext(
                start, hit, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, candidate));
        return obstruction.getType() == HitResult.Type.MISS;
    }

    private static int randomBasicTicks(IntSetting first, IntSetting second) {
        int min = Math.min(first.get(), second.get());
        int max = Math.max(first.get(), second.get());
        return min == max ? min : ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    private boolean isUseKeyPressedOnAny() {
        if (mc.options.keyUse.isDown()) {
            return true;
        }
        InputConstants.Key key = ((KeyMappingAccessor) (Object) mc.options.keyUse).setsuna$getKey();
        if (key == InputConstants.UNKNOWN) {
            return false;
        }
        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(mc.getWindow().handle(), key.getValue()) == GLFW.GLFW_PRESS;
        }
        return key.getType() == InputConstants.Type.KEYSYM
                && InputConstants.isKeyDown(mc.getWindow(), key.getValue());
    }

    static boolean passesBasicBlockChance(int roll, double chance) {
        return roll <= chance;
    }

    static boolean shouldStartBasicReblock(boolean attackedThisTick, int ticksSinceAttack, int reblockTicks) {
        return !attackedThisTick && ticksSinceAttack >= reblockTicks;
    }

    static boolean shouldReblockBasicImmediately(int reblockTicks) {
        return reblockTicks == 0;
    }

    static int getBasicChangeSlot(int currentSlot) {
        return (currentSlot + 1) % 9;
    }

    static boolean shouldClearBasicBlockVisualOnSlotChange(boolean internalSlotUnblockPacket) {
        return !internalSlotUnblockPacket;
    }

    static boolean shouldQueueBasicBlinkPacket(
            boolean blockVisual,
            boolean enforcedBlocking,
            boolean useItemPacket,
            int flushTicks,
            int blinkTicks) {
        return blockVisual && !enforcedBlocking && !useItemPacket && flushTicks < blinkTicks;
    }

    static boolean shouldCancelAuraAttackSlowdown(boolean keepSprintEnabled, boolean auraAttackInProgress) {
        return keepSprintEnabled && auraAttackInProgress;
    }

    static boolean shouldBypassBasicBlockSlowdownState(
            boolean basicRunning,
            boolean enforcedBlocking,
            boolean usingItem,
            boolean usingEnforcedHand,
            boolean blockingServerside) {
        return basicRunning && enforcedBlocking && usingItem && usingEnforcedHand && blockingServerside;
    }

    public boolean shouldBypassBasicBlockSlowdown() {
        if (noPlayer()) {
            return false;
        }
        return shouldBypassBasicBlockSlowdownState(
                isEnabled() && isBasicAutoBlockMode() && basicRuntimeActive,
                basicEnforcedBlockingHand != null,
                mc.player.isUsingItem(),
                basicEnforcedBlockingHand != null
                        && mc.player.getUsedItemHand() == basicEnforcedBlockingHand,
                isBasicBlockingServerside());
    }

    private void processAutoBlock() {
        if (noPlayer() || autoBlock.is(AutoBlockMode.None)) {
            resetAutoBlockState();
            return;
        }
        if (!canAutoBlock()) {
            if (isAutoBlockPlayerBlocking()) stopBlock();
            resetAutoBlockState();
            return;
        }
        boolean hasTarget = hasValidBlockTarget();
        if (!hasTarget) {
            if (isAutoBlockPlayerBlocking()) stopBlock();
            isAutoBlocking = false;
            abFakeBlockState = false;
            blockTick = 0;
            hypixel3Asw = 0;
            return;
        }

        switch (autoBlock.get()) {
            case Vanilla -> {
                if (!isAutoBlockPlayerBlocking()) startBlock();
                isAutoBlocking = true;
                abFakeBlockState = false;
            }
            case Spoof -> {
                int curSlot = mc.player.getInventory().getSelectedSlot();
                if (!isAutoBlockPlayerBlocking() && blockTick == 0) {
                    int emptySlot = findEmptySlot(curSlot);
                    sendSlotPacket(emptySlot);
                    sendSlotPacket(curSlot);
                    blockTick = 1;
                } else if (blockTick == 1) {
                    blockTick = 0;
                }
                isAutoBlocking = true;
                abFakeBlockState = false;
            }
            case Hypixel -> {
                switch (blockTick) {
                    case 0 -> { if (!isAutoBlockPlayerBlocking()) startBlock(); blockTick = 1; }
                    case 1 -> {
                        if (isAutoBlockPlayerBlocking()) { stopBlock(); releasedForAttack = true; }
                        blockTick = 0;
                    }
                    default -> blockTick = 0;
                }
                isAutoBlocking = true;
                abFakeBlockState = true;
            }
            case Blink -> {
                switch (blockTick) {
                    case 0 -> { if (!isAutoBlockPlayerBlocking()) startBlock(); blinkReset = true; blockTick = 1; }
                    case 1 -> {
                        if (isAutoBlockPlayerBlocking()) { stopBlock(); releasedForAttack = true; }
                        blockTick = 0;
                    }
                    default -> blockTick = 0;
                }
                isAutoBlocking = true;
                abFakeBlockState = true;
            }
            case Interact -> {
                int curItem = mc.player.getInventory().getSelectedSlot();
                switch (blockTick) {
                    case 0 -> { if (!isAutoBlockPlayerBlocking()) startBlock(); blinkReset = true; blockTick = 1; }
                    case 1 -> {
                        if (isAutoBlockPlayerBlocking()) {
                            sendSlotPacket(findEmptySlot(curItem));
                            releasedForAttack = true;
                        }
                        blockTick = 0;
                    }
                    default -> blockTick = 0;
                }
                isAutoBlocking = true;
                abFakeBlockState = true;
            }
            case Swap -> {
                int curItem = mc.player.getInventory().getSelectedSlot();
                switch (blockTick) {
                    case 0 -> {
                        int swordSlot = findSwordSlot(curItem);
                        if (swordSlot != -1) { if (!isAutoBlockPlayerBlocking()) startBlock(); blockTick = 1; }
                    }
                    case 1 -> {
                        int swordSlot = findSwordSlot(curItem);
                        if (swordSlot == -1) {
                            blockTick = 0;
                        } else if (!isAutoBlockPlayerBlocking()) {
                            startBlock();
                        } else {
                            sendSlotPacket(swordSlot);
                            startBlock();
                            releasedForAttack = true;
                            blockTick = 0;
                        }
                    }
                    default -> blockTick = 0;
                }
                isAutoBlocking = true;
                abFakeBlockState = true;
            }
            case Legit -> {
                switch (blockTick) {
                    case 0 -> { if (!isAutoBlockPlayerBlocking()) startBlock(); blockTick = 1; }
                    case 1 -> {
                        if (isAutoBlockPlayerBlocking()) { stopBlock(); releasedForAttack = true; }
                        blockTick = 0;
                    }
                    default -> blockTick = 0;
                }
                isAutoBlocking = true;
                abFakeBlockState = false;
            }
            case Fake -> {
                isAutoBlocking = false;
                abFakeBlockState = true;
                releasedForAttack = true;
            }
            case Morden -> {
                switch (hypixel3Asw) {
                    case 0 -> { if (isAutoBlockPlayerBlocking()) stopBlock(); hypixel3Asw = 1; }
                    case 1 -> { if (isAutoBlockPlayerBlocking()) stopBlock(); hypixel3Asw = 2; }
                    case 2 -> {
                        if (!isAutoBlockPlayerBlocking()) startBlock();
                        blinkReset = true;
                        releasedForAttack = true;
                        hypixel3Asw = 0;
                    }
                    default -> hypixel3Asw = 0;
                }
                isAutoBlocking = true;
                abFakeBlockState = true;
            }
            case Basic, None -> {
            }
        }
    }

    private boolean canAutoBlock() {
        if (!isWeaponHeld()) return false;
        return !autoBlockRequirePress.get() || mc.player.isUsingItem();
    }

    private boolean shouldYieldToPlayerItemUse() {
        boolean usingItem = mc.player.isUsingItem();
        boolean useKeyRequestsItem = mc.options.keyUse.isDown()
                && (hasUseDuration(mc.player.getMainHandItem()) || hasUseDuration(mc.player.getOffhandItem()));
        return shouldYieldToItemUse(
                usingItem,
                usingItem && isWeaponStack(mc.player.getUseItem()),
                useKeyRequestsItem);
    }

    static boolean shouldYieldToItemUse(boolean usingItem, boolean usingWeapon, boolean useKeyRequestsItem) {
        return (usingItem && !usingWeapon) || useKeyRequestsItem;
    }

    private boolean hasUseDuration(ItemStack stack) {
        return !stack.isEmpty() && stack.getUseDuration(mc.player) > 0;
    }

    private boolean hasValidBlockTarget() {
        return target != null && RotationUtils.getEyeDistanceToEntity(target) <= autoBlockRange.get();
    }

    private boolean isAutoBlockPlayerBlocking() {
        return blockingState;
    }

    private void startBlock() {
        if (mc.getConnection() == null) return;
        mc.getConnection().send(new ServerboundUseItemPacket(
                InteractionHand.MAIN_HAND, 0, mc.player.getYRot(), mc.player.getXRot()));
        mc.player.startUsingItem(InteractionHand.MAIN_HAND);
        blockingState = true;
    }

    private void stopBlock() {
        if (mc.getConnection() == null) return;
        mc.getConnection().send(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM,
                BlockPos.ZERO, Direction.DOWN, 0));
        mc.player.stopUsingItem();
        blockingState = false;
    }

    private void sendSlotPacket(int slot) {
        if (mc.getConnection() == null) return;
        mc.getConnection().send(new ServerboundSetCarriedItemPacket(slot));
    }

    private void resetAutoBlockState() {
        isAutoBlocking = false;
        abFakeBlockState = false;
        blockTick = 0;
        blinkReset = false;
        hypixel3Asw = 0;
        blockingState = false;
        releasedForAttack = false;
    }

    private int findEmptySlot(int excludeSlot) {
        for (int i = 0; i < 9; i++) {
            if (i == excludeSlot) continue;
            if (mc.player.getInventory().getItem(i).isEmpty()) return i;
        }
        for (int i = 0; i < 9; i++) {
            if (i == excludeSlot) continue;
            if (!isWeaponStack(mc.player.getInventory().getItem(i))) return i;
        }
        return Math.floorMod(excludeSlot - 1, 9);
    }

    private int findSwordSlot(int excludeSlot) {
        for (int i = 0; i < 9; i++) {
            if (i == excludeSlot) continue;
            if (isWeaponStack(mc.player.getInventory().getItem(i))) return i;
        }
        return -1;
    }

    private boolean isWeaponStack(net.minecraft.world.item.ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.is(ItemTags.SWORDS)
                || stack.is(ItemTags.AXES)
                || stack.getItem() instanceof TridentItem
                || stack.getItem() instanceof MaceItem;
    }

    public boolean isAutoFakeBlocking() {
        return isEnabled() && abFakeBlockState && isWeaponHeld();
    }

    public boolean isAutoBlockVisualActive() {
        return isEnabled()
                && (basicRuntimeActive && basicBlockVisual || isAutoBlocking || abFakeBlockState)
                && isWeaponHeld();
    }

    public boolean shouldFakeBlock() {
        return isEnabled()
                && mode.is(Mode.Heypixel)
                && heypixelAutoBlock.is(HeypixelAutoBlockMode.Fake)
                && target != null
                && RotationUtils.getEyeDistanceToEntity(target) <= heypixelRange.get()
                && isWeaponHeld();
    }

    private boolean isWeaponHeld() {
        Item item = mc.player.getMainHandItem().getItem();
        return item == Items.WOODEN_SWORD
                || item == Items.COPPER_SWORD
                || item == Items.STONE_SWORD
                || item == Items.GOLDEN_SWORD
                || item == Items.IRON_SWORD
                || item == Items.DIAMOND_SWORD
                || item == Items.NETHERITE_SWORD
                || item instanceof AxeItem
                || mc.player.getMainHandItem().getItem() instanceof MaceItem
                || mc.player.getMainHandItem().getItem() instanceof TridentItem;
    }

    @Listen
    private void onRender3D(Render3DEvent event) {
        if (!esp.get()) return;
        if (target == null) return;

        PoseStack stack = event.getPoseStack();

        switch (espMode.get()) {
            case CaptureMark -> CaptureMarkESP.render(
                    stack,
                    target,
                    espSize.get(),
                    espRotSpeed.get(),
                    waveSpeed.get(),
                    espColor1.get(),
                    espColor2.get()
            );
            case Circle -> CircleESP.render(
                    stack,
                    target,
                    circleRadius.get().floatValue(),
                    sideColor.get(),
                    lineColor.get(),
                    circleAlphaFactor.get().floatValue()
            );
            case Firefly -> FireflyESP.render(
                    stack,
                    target,
                    fireflyLength.get(),
                    fireflyFactor.get(),
                    fireflyShaking.get(),
                    fireflyAmplitude.get(),
                    fireflyColor.get(),
                    fireflyColorMode.get(),
                    fireflyColor2.get(),
                    fireflyColorMix.get(),
                    fireflyColorSpeed.get(),
                    fireflyRainbowSpeed.get(),
                    fireflyRainbowSaturation.get(),
                    fireflyRainbowBrightness.get()
            );
        }
    }
}
