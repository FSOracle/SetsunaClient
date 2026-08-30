package com.setsuna.module.modules.movement;

import com.setsuna.event.Listen;
import com.setsuna.event.EventBus;
import com.setsuna.event.events.GroundJumpEvent;
import com.setsuna.event.events.KeyboardInputEvent;
import com.setsuna.event.events.PacketEvent;
import com.setsuna.event.events.PlayerTickEvent;
import com.setsuna.event.events.Render3DEvent;
import com.setsuna.event.events.RespawnEvent;
import com.setsuna.manager.RotationManager;
import com.setsuna.mixin.DeltaTrackerTimerAccessor;
import com.setsuna.mixin.LivingEntityAccessor;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.setting.Setting;
import com.setsuna.module.modules.movement.scaffold.ScaffoldMovementPlanner;
import com.setsuna.module.modules.movement.scaffold.ScaffoldMovementPrediction;
import com.setsuna.module.modules.movement.scaffold.ScaffoldPlayerSimulation;
import com.setsuna.module.modules.movement.scaffold.ScaffoldRenderCuller;
import com.setsuna.module.modules.movement.scaffold.ScaffoldTargetFinder;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.ColorSetting;
import com.setsuna.setting.settings.DoubleSetting;
import com.setsuna.setting.settings.EnumSetting;
import com.setsuna.setting.settings.IntSetting;
import com.setsuna.util.network.PacketUtils;
import com.setsuna.util.player.MoveUtils;
import com.setsuna.util.render.Render3DUtils;
import com.setsuna.util.render.animation.Easing;
import com.setsuna.util.rotation.Rot2f;
import com.setsuna.util.rotation.RotationUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.SupportType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * LiquidBounce Scaffold port. The original value groups are flattened with a
 * group prefix because Setsuna's setting model is intentionally one-level.
 */
public final class Scaffold extends Module {

    public static final Scaffold INSTANCE = new Scaffold();

    private static final double VANILLA_GRAVITY = 0.08D;
    private static final double VANILLA_VERTICAL_DRAG = 0.98D;

    private enum ScaffoldMode {
        TellyBridge,
        GodBridge,
        HypixelTest
    }

    private enum HypixelPage {
        General,
        Blocks,
        Technique,
        Tower,
        Movement,
        Placement,
        Network,
        Render
    }

    private enum LegacyPage {
        General,
        Placement,
        Render
    }

    private static final Set<Block> DISALLOWED_BLOCKS = Set.of(
            Blocks.TNT,
            Blocks.COBWEB,
            Blocks.NETHER_PORTAL);
    private static final Set<Block> UNFAVORABLE_BLOCKS = Set.of(
            Blocks.CRAFTING_TABLE,
            Blocks.JIGSAW,
            Blocks.SMITHING_TABLE,
            Blocks.FLETCHING_TABLE,
            Blocks.ENCHANTING_TABLE,
            Blocks.CAULDRON,
            Blocks.MAGMA_BLOCK);

    private enum Technique {
        NORMAL,
        EXPAND,
        GOD_BRIDGE,
        BREEZILY
    }

    private enum SameYMode {
        OFF,
        ON,
        JUMP_KEY,
        FALLING,
        HYPIXEL
    }

    private enum TowerMode {
        NONE,
        MOTION,
        PULLDOWN,
        KARHU,
        VULCAN,
        HYPIXEL
    }

    private enum RotationTiming {
        NORMAL,
        ON_TICK,
        ON_TICK_SNAP
    }

    private enum SwingMode {
        DO_NOT_HIDE,
        HIDE_BOTH,
        HIDE_CLIENT,
        HIDE_SERVER
    }

    private enum TellyResetMode {
        REVERSE,
        RESET
    }

    private enum SprintMode {
        DO_NOT_CHANGE,
        FORCE_SPRINT,
        FORCE_NO_SPRINT,
        NO_SPRINT_ON_PLACE,
        NO_SPRINT_ON_GROUND
    }

    private enum SafeWalkMode {
        NONE,
        SAFE,
        ON_EDGE
    }

    private enum OnEdgeMode {
        STOP,
        INVERT,
        CENTER
    }

    private final EnumSetting<ScaffoldMode> mode = add(new EnumSetting<>("Mode", ScaffoldMode.HypixelTest)
            .onChange(this::onModeChanged));
    private final EnumSetting<HypixelPage> hypixelPage = add(new EnumSetting<>("Hypixel Category", HypixelPage.General)
            .visibleWhen(this::isHypixelTest).displayAs("Category"));
    private final EnumSetting<LegacyPage> legacyPage = add(new EnumSetting<>("Legacy Category", LegacyPage.General)
            .visibleWhen(() -> !isHypixelTest()).displayAs("Category"));

    private final IntSetting delayMin = add(new IntSetting("Delay/Min", 0, 0, 40, 1));
    private final IntSetting delayMax = add(new IntSetting("Delay/Max", 0, 0, 40, 1));
    private final DoubleSetting minDist = add(new DoubleSetting("MinDist", 0.0D, 0.0D, 0.25D, 0.01D));
    private final DoubleSetting timer = add(new DoubleSetting("Timer", 1.0D, 0.01D, 10.0D, 0.01D));

    private final BooleanSetting autoBlock = add(new BooleanSetting("AutoBlock/Enabled", true));
    private final BooleanSetting autoBlockAlways = add(new BooleanSetting("AutoBlock/Always", false)
            .visibleWhen(autoBlock::get));
    private final IntSetting slotResetDelay = add(new IntSetting("AutoBlock/SlotResetDelay", 5, 0, 40, 1)
            .visibleWhen(autoBlock::get));
    private final IntSetting doNotUseBelowCount = add(new IntSetting("AutoBlock/DoNotUseBelowCount", 1, 0, 64, 1)
            .visibleWhen(autoBlock::get));

    private final BooleanSetting prediction = add(new BooleanSetting("Prediction/Enabled", true)
            .onChange(this::onPredictionToggled));
    private final DoubleSetting bootstrapBackoff = add(new DoubleSetting(
            "Prediction/BootstrapBackoff", 0.2D, 0.0D, 0.4D, 0.01D).visibleWhen(prediction::get));
    private final DoubleSetting predictionCutoffDistance = add(new DoubleSetting(
            "Prediction/CutoffDistance", 0.05D, 0.0D, 0.3D, 0.01D).visibleWhen(prediction::get));
    private final IntSetting predictionWarmupPlacements = add(new IntSetting(
            "Prediction/WarmupPlacements", 2, 0, 4, 1).visibleWhen(prediction::get));

    private final EnumSetting<Technique> technique = add(new EnumSetting<>("Technique", Technique.NORMAL));
    private final EnumSetting<SameYMode> sameY = add(new EnumSetting<>("SameY", SameYMode.OFF));
    private final EnumSetting<TowerMode> tower = add(new EnumSetting<>("Tower", TowerMode.NONE));
    private final EnumSetting<SafeWalkMode> safeWalk = add(new EnumSetting<>("SafeWalk", SafeWalkMode.SAFE)
            .onChange(this::onSafeWalkModeChanged));

    private final EnumSetting<ScaffoldTargetFinder.AimMode> rotationMode = add(new EnumSetting<>(
            "Normal/RotationMode", ScaffoldTargetFinder.AimMode.STABILIZED)
            .visibleWhen(() -> technique.is(Technique.NORMAL)));
    private final BooleanSetting requiresSight = add(new BooleanSetting("Normal/RequiresSight", false)
            .visibleWhen(() -> technique.is(Technique.NORMAL)));

    private final BooleanSetting eagle = add(new BooleanSetting("Normal/Eagle/Enabled", false)
            .visibleWhen(() -> technique.is(Technique.NORMAL)));
    private final IntSetting eagleBlocksMin = add(new IntSetting("Normal/Eagle/BlocksToEagleMin", 0, 0, 10, 1)
            .visibleWhen(() -> technique.is(Technique.NORMAL) && eagle.get()));
    private final IntSetting eagleBlocksMax = add(new IntSetting("Normal/Eagle/BlocksToEagleMax", 0, 0, 10, 1)
            .visibleWhen(() -> technique.is(Technique.NORMAL) && eagle.get()));
    private final DoubleSetting eagleDistanceMin = add(new DoubleSetting(
            "Normal/Eagle/EdgeDistanceMin", 0.01D, 0.01D, 1.3D, 0.01D)
            .visibleWhen(() -> technique.is(Technique.NORMAL) && eagle.get()));
    private final DoubleSetting eagleDistanceMax = add(new DoubleSetting(
            "Normal/Eagle/EdgeDistanceMax", 0.05D, 0.01D, 1.3D, 0.01D)
            .visibleWhen(() -> technique.is(Technique.NORMAL) && eagle.get()));
    private final BooleanSetting eagleOnlyOnGround = add(new BooleanSetting("Normal/Eagle/OnlyOnGround", true)
            .visibleWhen(() -> technique.is(Technique.NORMAL) && eagle.get()));

    private final BooleanSetting telly = add(new BooleanSetting("Normal/Telly/Enabled", false)
            .visibleWhen(() -> technique.is(Technique.NORMAL)));
    private final EnumSetting<TellyResetMode> tellyResetMode = add(new EnumSetting<>(
            "Normal/Telly/ResetMode", TellyResetMode.RESET)
            .visibleWhen(() -> technique.is(Technique.NORMAL) && telly.get()));
    private final IntSetting tellyStraightTicks = add(new IntSetting("Normal/Telly/Straight", 0, 0, 5, 1)
            .visibleWhen(() -> technique.is(Technique.NORMAL) && telly.get()));
    private final IntSetting tellyJumpMin = add(new IntSetting("Normal/Telly/JumpMin", 0, 0, 10, 1)
            .visibleWhen(() -> technique.is(Technique.NORMAL) && telly.get()));
    private final IntSetting tellyJumpMax = add(new IntSetting("Normal/Telly/JumpMax", 0, 0, 10, 1)
            .visibleWhen(() -> technique.is(Technique.NORMAL) && telly.get()));
    private final BooleanSetting tellyAimOnTower = add(new BooleanSetting("Normal/Telly/AimOnTower", true)
            .visibleWhen(() -> technique.is(Technique.NORMAL) && telly.get()));

    private final BooleanSetting down = add(new BooleanSetting("Normal/Down", false)
            .visibleWhen(() -> technique.is(Technique.NORMAL)));
    private final BooleanSetting stabilizeMovement = add(new BooleanSetting("Normal/StabilizeMovement", true)
            .visibleWhen(() -> technique.is(Technique.NORMAL)));
    private final BooleanSetting ceiling = add(new BooleanSetting("Normal/Ceiling", false)
            .visibleWhen(() -> technique.is(Technique.NORMAL)));
    private final BooleanSetting headHitter = add(new BooleanSetting("Normal/HeadHitter/Enabled", false)
            .visibleWhen(() -> technique.is(Technique.NORMAL)));
    private final IntSetting headHitterDelayMin = add(new IntSetting("Normal/HeadHitter/JumpDelayMin", 0, 0, 20, 1)
            .visibleWhen(() -> technique.is(Technique.NORMAL) && headHitter.get()));
    private final IntSetting headHitterDelayMax = add(new IntSetting("Normal/HeadHitter/JumpDelayMax", 0, 0, 20, 1)
            .visibleWhen(() -> technique.is(Technique.NORMAL) && headHitter.get()));

    private final IntSetting expandLength = add(new IntSetting("Expand/Length", 4, 1, 10, 1)
            .visibleWhen(() -> technique.is(Technique.EXPAND)));

    private final BooleanSetting godBridgeJump = add(new BooleanSetting("GodBridge/Modes/Jump", true)
            .visibleWhen(() -> technique.is(Technique.GOD_BRIDGE)));
    private final BooleanSetting godBridgeSneak = add(new BooleanSetting("GodBridge/Modes/Sneak", false)
            .visibleWhen(() -> technique.is(Technique.GOD_BRIDGE)));
    private final BooleanSetting godBridgeStopInput = add(new BooleanSetting("GodBridge/Modes/StopInput", false)
            .visibleWhen(() -> technique.is(Technique.GOD_BRIDGE)));
    private final BooleanSetting godBridgeBackwards = add(new BooleanSetting("GodBridge/Modes/Backwards", false)
            .visibleWhen(() -> technique.is(Technique.GOD_BRIDGE)));
    private final IntSetting godBridgeForceSneakBelow = add(new IntSetting(
            "GodBridge/ForceSneakBelowCount", 3, 0, 10, 1)
            .visibleWhen(() -> technique.is(Technique.GOD_BRIDGE)));
    private final IntSetting godBridgeSneakMin = add(new IntSetting("GodBridge/SneakTimeMin", 1, 1, 10, 1)
            .visibleWhen(() -> technique.is(Technique.GOD_BRIDGE)));
    private final IntSetting godBridgeSneakMax = add(new IntSetting("GodBridge/SneakTimeMax", 1, 1, 10, 1)
            .visibleWhen(() -> technique.is(Technique.GOD_BRIDGE)));

    private final DoubleSetting breezilyEdgeMin = add(new DoubleSetting(
            "Breezily/EdgeDistanceMin", 0.45D, 0.25D, 0.5D, 0.01D)
            .visibleWhen(() -> technique.is(Technique.BREEZILY)));
    private final DoubleSetting breezilyEdgeMax = add(new DoubleSetting(
            "Breezily/EdgeDistanceMax", 0.5D, 0.25D, 0.5D, 0.01D)
            .visibleWhen(() -> technique.is(Technique.BREEZILY)));

    private final DoubleSetting towerMotion = add(new DoubleSetting("Tower/Motion/Motion", 0.42D, 0.0D, 1.0D, 0.01D)
            .visibleWhen(() -> tower.is(TowerMode.MOTION)));
    private final DoubleSetting towerTriggerHeight = add(new DoubleSetting(
            "Tower/Motion/TriggerHeight", 0.78D, 0.76D, 1.0D, 0.01D)
            .visibleWhen(() -> tower.is(TowerMode.MOTION)));
    private final DoubleSetting towerSlow = add(new DoubleSetting("Tower/Motion/Slow", 1.0D, 0.0D, 3.0D, 0.01D)
            .visibleWhen(() -> tower.is(TowerMode.MOTION)));
    private final DoubleSetting towerPulldownTrigger = add(new DoubleSetting(
            "Tower/Pulldown/Trigger", 0.1D, 0.0D, 0.2D, 0.01D)
            .visibleWhen(() -> tower.is(TowerMode.PULLDOWN)));
    private final DoubleSetting towerKarhuTimer = add(new DoubleSetting("Tower/Karhu/Timer", 5.0D, 0.1D, 10.0D, 0.1D)
            .visibleWhen(() -> tower.is(TowerMode.KARHU)));
    private final DoubleSetting towerKarhuTrigger = add(new DoubleSetting(
            "Tower/Karhu/Trigger", 0.06D, 0.0D, 0.2D, 0.01D)
            .visibleWhen(() -> tower.is(TowerMode.KARHU)));
    private final BooleanSetting towerKarhuPulldown = add(new BooleanSetting("Tower/Karhu/Pulldown", true)
            .visibleWhen(() -> tower.is(TowerMode.KARHU)));

    private final DoubleSetting safeEdgeDistanceMin = add(new DoubleSetting(
            "SafeWalk/OnEdge/DistanceMin", 0.1D, 0.05D, 0.5D, 0.01D)
            .visibleWhen(() -> safeWalk.is(SafeWalkMode.ON_EDGE)));
    private final DoubleSetting safeEdgeDistanceMax = add(new DoubleSetting(
            "SafeWalk/OnEdge/DistanceMax", 0.15D, 0.05D, 0.5D, 0.01D)
            .visibleWhen(() -> safeWalk.is(SafeWalkMode.ON_EDGE)));
    private final IntSetting safeKeepMin = add(new IntSetting("SafeWalk/OnEdge/KeepMin", 1, 1, 20, 1)
            .visibleWhen(() -> safeWalk.is(SafeWalkMode.ON_EDGE)));
    private final IntSetting safeKeepMax = add(new IntSetting("SafeWalk/OnEdge/KeepMax", 2, 1, 20, 1)
            .visibleWhen(() -> safeWalk.is(SafeWalkMode.ON_EDGE)));
    private final EnumSetting<OnEdgeMode> safeEdgeMode = add(new EnumSetting<>("SafeWalk/OnEdge/Mode", OnEdgeMode.STOP)
            .visibleWhen(() -> safeWalk.is(SafeWalkMode.ON_EDGE)));
    private final IntSetting safeSneakMin = add(new IntSetting("SafeWalk/OnEdge/SneakMin", 0, 0, 20, 1)
            .visibleWhen(() -> safeWalk.is(SafeWalkMode.ON_EDGE)));
    private final IntSetting safeSneakMax = add(new IntSetting("SafeWalk/OnEdge/SneakMax", 0, 0, 20, 1)
            .visibleWhen(() -> safeWalk.is(SafeWalkMode.ON_EDGE)));
    private final BooleanSetting safeEdgeJump = add(new BooleanSetting("SafeWalk/OnEdge/Jump", false)
            .visibleWhen(() -> safeWalk.is(SafeWalkMode.ON_EDGE)));

    private final BooleanSetting considerInventory = add(new BooleanSetting("Rotations/ConsiderInventory", false));
    private final EnumSetting<RotationTiming> rotationTiming = add(new EnumSetting<>(
            "Rotations/RotationTiming", RotationTiming.NORMAL));
    private final DoubleSetting rotationSpeed = add(new DoubleSetting("Rotations/Speed", 10.0D, 0.1D, 10.0D, 0.1D));
    private final BooleanSetting showRotationModel = add(new BooleanSetting("Rotations/ShowModel", false));
    private final EnumSetting<SwingMode> swing = add(new EnumSetting<>("Swing", SwingMode.DO_NOT_HIDE));

    private final BooleanSetting simulateAttempts = add(new BooleanSetting("SimulatePlacementAttempts/Enabled", false));
    private final IntSetting simulateCpsMin = add(new IntSetting("SimulatePlacementAttempts/CpsMin", 5, 1, 100, 1)
            .visibleWhen(simulateAttempts::get));
    private final IntSetting simulateCpsMax = add(new IntSetting("SimulatePlacementAttempts/CpsMax", 8, 1, 100, 1)
            .visibleWhen(simulateAttempts::get));
    private final BooleanSetting simulateFailedOnly = add(new BooleanSetting(
            "SimulatePlacementAttempts/FailedAttemptsOnly", true).visibleWhen(simulateAttempts::get));

    private final BooleanSetting sprintControl = add(new BooleanSetting("SprintControl/Enabled", false));
    private final EnumSetting<SprintMode> sprintClient = add(new EnumSetting<>(
            "SprintControl/Client", SprintMode.DO_NOT_CHANGE).visibleWhen(sprintControl::get));
    private final EnumSetting<SprintMode> sprintServer = add(new EnumSetting<>(
            "SprintControl/Server", SprintMode.DO_NOT_CHANGE).visibleWhen(sprintControl::get));

    private final BooleanSetting acceleration = add(new BooleanSetting("Acceleration/Enabled", false));
    private final DoubleSetting accelerationMultiplier = add(new DoubleSetting(
            "Acceleration/SpeedMultiplier", 0.6D, 0.1D, 3.0D, 0.01D).visibleWhen(acceleration::get));
    private final BooleanSetting accelerationOnlyGround = add(new BooleanSetting(
            "Acceleration/OnlyOnGround", false).visibleWhen(acceleration::get));

    private final BooleanSetting strafe = add(new BooleanSetting("Strafe/Enabled", false)
            .onChange(this::onStrafeToggled));
    private final DoubleSetting strafeSpeed = add(new DoubleSetting("Strafe/Speed", 0.247D, 0.0D, 5.0D, 0.001D)
            .visibleWhen(strafe::get));
    private final BooleanSetting strafeHypixel = add(new BooleanSetting("Strafe/Hypixel", false)
            .visibleWhen(strafe::get));
    private final BooleanSetting strafeOnlyGround = add(new BooleanSetting("Strafe/OnlyOnGround", false)
            .visibleWhen(strafe::get));

    private final BooleanSetting jumpStrafe = add(new BooleanSetting("StrafeOnJump/Enabled", false));
    private final DoubleSetting jumpStraightMin = add(new DoubleSetting(
            "StrafeOnJump/StraightSpeedMin", 0.48D, 0.1D, 1.0D, 0.01D).visibleWhen(jumpStrafe::get));
    private final DoubleSetting jumpStraightMax = add(new DoubleSetting(
            "StrafeOnJump/StraightSpeedMax", 0.49D, 0.1D, 1.0D, 0.01D).visibleWhen(jumpStrafe::get));
    private final DoubleSetting jumpDiagonalMin = add(new DoubleSetting(
            "StrafeOnJump/DiagonalSpeedMin", 0.48D, 0.1D, 1.0D, 0.01D).visibleWhen(jumpStrafe::get));
    private final DoubleSetting jumpDiagonalMax = add(new DoubleSetting(
            "StrafeOnJump/DiagonalSpeedMax", 0.49D, 0.1D, 1.0D, 0.01D).visibleWhen(jumpStrafe::get));

    private final BooleanSetting speedLimiter = add(new BooleanSetting("SpeedLimiter/Enabled", false));
    private final DoubleSetting speedLimit = add(new DoubleSetting("SpeedLimiter/SpeedLimit", 0.11D, 0.01D, 0.4D, 0.01D)
            .visibleWhen(speedLimiter::get));

    private final BooleanSetting blink = add(new BooleanSetting("Blink/Enabled", false));
    private final IntSetting blinkTimeMin = add(new IntSetting("Blink/TimeMin", 50, 0, 3000, 10)
            .visibleWhen(blink::get));
    private final IntSetting blinkTimeMax = add(new IntSetting("Blink/TimeMax", 250, 0, 3000, 10)
            .visibleWhen(blink::get));
    private final BooleanSetting blinkFlushPlace = add(new BooleanSetting("Blink/FlushOnPlace", false)
            .visibleWhen(blink::get));
    private final BooleanSetting blinkFlushTowering = add(new BooleanSetting("Blink/FlushOnTowering", false)
            .visibleWhen(blink::get));
    private final BooleanSetting blinkFlushSneaking = add(new BooleanSetting("Blink/FlushOnSneaking", false)
            .visibleWhen(blink::get));
    private final BooleanSetting blinkFlushNotSneaking = add(new BooleanSetting("Blink/FlushOnNotSneaking", false)
            .visibleWhen(blink::get));
    private final BooleanSetting blinkFlushOnGround = add(new BooleanSetting("Blink/FlushOnGround", false)
            .visibleWhen(blink::get));
    private final BooleanSetting blinkFlushInAir = add(new BooleanSetting("Blink/FlushInAir", false)
            .visibleWhen(blink::get));

    private final BooleanSetting autoSpeed = add(new BooleanSetting("AutoSpeed", false));
    private final BooleanSetting ledge = add(new BooleanSetting("Ledge", true));

    private final BooleanSetting render = add(new BooleanSetting("Render/Enabled", true));
    private final BooleanSetting renderClump = add(new BooleanSetting("Render/Clump", true).visibleWhen(render::get));
    private final DoubleSetting renderStartSize = add(new DoubleSetting("Render/StartSize", 1.0D, 0.0D, 2.0D, 0.05D)
            .visibleWhen(render::get));
    private final EnumSetting<Easing> renderStartCurve = add(new EnumSetting<>("Render/StartCurve", Easing.LINEAR)
            .visibleWhen(render::get));
    private final DoubleSetting renderEndSize = add(new DoubleSetting("Render/EndSize", 0.8D, 0.0D, 2.0D, 0.05D)
            .visibleWhen(render::get));
    private final EnumSetting<Easing> renderEndCurve = add(new EnumSetting<>("Render/EndCurve", Easing.LINEAR)
            .visibleWhen(render::get));
    private final EnumSetting<Easing> renderFadeInCurve = add(new EnumSetting<>("Render/FadeInCurve", Easing.LINEAR)
            .visibleWhen(render::get));
    private final EnumSetting<Easing> renderFadeOutCurve = add(new EnumSetting<>("Render/FadeOutCurve", Easing.LINEAR)
            .visibleWhen(render::get));
    private final IntSetting renderInTime = add(new IntSetting("Render/InTime", 500, 0, 5000, 50)
            .visibleWhen(render::get));
    private final IntSetting renderOutTime = add(new IntSetting("Render/OutTime", 500, 0, 5000, 50)
            .visibleWhen(render::get));
    private final ColorSetting renderColor = add(new ColorSetting("Render/Color", new Color(0, 255, 0, 90))
            .visibleWhen(render::get));
    private final ColorSetting renderOutline = add(new ColorSetting("Render/OutlineColor", new Color(0, 255, 0, 255))
            .visibleWhen(render::get));

    private final ScaffoldMovementPlanner movementPlanner = new ScaffoldMovementPlanner();
    private final ScaffoldMovementPrediction movementPrediction = new ScaffoldMovementPrediction(movementPlanner);
    private final ScaffoldTargetFinder targetFinder = new ScaffoldTargetFinder();
    private final LegacyScaffoldEngine legacyEngine = new LegacyScaffoldEngine(this);
    private final List<RenderInfo> renderedPlacements = new ArrayList<>();
    private final Queue<Packet<?>> blinkPackets = new ArrayDeque<>();

    private ScaffoldTargetFinder.Target currentTarget;
    private ScaffoldMovementPlanner.Line currentOptimalLine;
    private ScaffoldMovementPlanner.Line currentTargetLine;
    private Block nextBlock;
    private long blockCountSession;
    private int initialBlockCount;
    private float rawForward;
    private float rawStrafe;
    private Rot2f scaffoldServerRotation;
    private int placementY;
    private int startY;
    private int jumps;
    private int forceSneak;
    private int placementCooldown;
    private boolean wasTowering;
    private int airTicks;

    private int selectedOriginalSlot = -1;
    private int selectedScaffoldSlot = -1;
    private int slotResetTicks;

    private int eaglePlacedBlocks;
    private int eagleCurrentBlocks;
    private double eagleCurrentDistance;
    private int tellyTicksUntilJump;
    private int tellyCurrentJumpTicks;
    private int headHitterCooldown;
    private int movementTicks;
    private int wasPlacedTicks;

    private boolean godBridgeRightSide;
    private ScaffoldPlayerSimulation.Snapshot movementInputSnapshot;
    private float breezilyLastSideways;
    private long breezilyLastAirTime;
    private double breezilyCurrentEdgeDistance;

    private Vec3 safeEdgeCenter;
    private int safeOverwriteTicks;
    private int safeSneakTicks;
    private double safeCurrentEdgeDistance;

    private double jumpOffPosition = Double.NaN;
    private boolean pulldownSequenceActive;
    private boolean karhuSequenceActive;
    private boolean karhuTimerPending;
    private boolean karhuTimerPulse;
    private float originalMsPerTick = Float.NaN;

    private boolean flushingBlink;
    private long blinkPulseStarted;
    private long blinkPulseTime;
    private long nextSimulatedClick;
    private ScaffoldMode runningMode;

    private Scaffold() {
        super("Scaffold", Category.MOVEMENT);
        configureSettingPages();
    }

    <T extends Setting<?>> T registerLegacySetting(T setting) {
        return add(setting);
    }

    boolean isLegacyTellyBridge() {
        return mode.is(ScaffoldMode.TellyBridge);
    }

    boolean isLegacyGodBridge() {
        return mode.is(ScaffoldMode.GodBridge);
    }

    private boolean isHypixelTest() {
        return mode.is(ScaffoldMode.HypixelTest);
    }

    private void onModeChanged(ScaffoldMode selectedMode) {
        if (!isEnabled() || runningMode == selectedMode) {
            return;
        }
        disableImplementation(runningMode);
        runningMode = selectedMode;
        enableImplementation(selectedMode);
    }

    private void enableImplementation(ScaffoldMode selectedMode) {
        if (selectedMode == ScaffoldMode.HypixelTest) {
            enableHypixelTest();
            return;
        }
        legacyEngine.enable();
        EventBus.INSTANCE.subscribe(legacyEngine);
    }

    private void disableImplementation(ScaffoldMode selectedMode) {
        if (selectedMode == null) {
            return;
        }
        if (selectedMode == ScaffoldMode.HypixelTest) {
            disableHypixelTest();
            return;
        }
        legacyEngine.disable();
        EventBus.INSTANCE.unsubscribe(legacyEngine);
    }

    private void configureSettingPages() {
        for (Setting<?> setting : settings()) {
            if (setting == mode || setting == hypixelPage || setting == legacyPage) {
                continue;
            }
            if (legacyEngine.owns(setting)) {
                LegacyPage page = legacyPageFor(setting.name());
                setting.andVisibleWhen(() -> !isHypixelTest() && legacyPage.is(page));
                continue;
            }

            HypixelPage page = hypixelPageFor(setting.name());
            setting.andVisibleWhen(() -> isHypixelTest() && hypixelPage.is(page));
            setting.displayAs(hypixelSettingLabel(setting.name(), page));
        }
    }

    private static HypixelPage hypixelPageFor(String name) {
        if (startsWithAny(name, "AutoBlock/", "Prediction/")) {
            return HypixelPage.Blocks;
        }
        if (name.equals("Technique")
                || startsWithAny(name, "Normal/", "Expand/", "GodBridge/", "Breezily/")) {
            return HypixelPage.Technique;
        }
        if (name.equals("Tower") || name.startsWith("Tower/")) {
            return HypixelPage.Tower;
        }
        if (name.equals("SafeWalk") || name.equals("AutoSpeed") || name.equals("Ledge")
                || startsWithAny(name, "SafeWalk/", "SprintControl/", "Acceleration/", "Strafe/",
                "StrafeOnJump/", "SpeedLimiter/")) {
            return HypixelPage.Movement;
        }
        if (name.startsWith("Rotations/") || name.startsWith("SimulatePlacementAttempts/")
                || name.equals("Swing")) {
            return HypixelPage.Placement;
        }
        if (name.startsWith("Blink/")) {
            return HypixelPage.Network;
        }
        if (name.startsWith("Render/")) {
            return HypixelPage.Render;
        }
        return HypixelPage.General;
    }

    private static LegacyPage legacyPageFor(String name) {
        if (name.equals("Render") || name.equals("Fade") || name.equals("Fade Time")
                || name.equals("Shrink") || name.equals("Side Color") || name.equals("Line Color")) {
            return LegacyPage.Render;
        }
        if (name.equals("Swap Mode") || name.equals("Swap Back") || name.equals("Rotation Mode")
                || name.equals("Raytrace Mode") || name.equals("Rotation Speed")
                || name.equals("Rotation Back Speed") || name.equals("Multi Place")
                || name.equals("Swing Hand")) {
            return LegacyPage.Placement;
        }
        return LegacyPage.General;
    }

    private static String hypixelSettingLabel(String stableName, HypixelPage page) {
        String path = stableName.endsWith("/Enabled")
                ? stableName.substring(0, stableName.length() - "/Enabled".length())
                : stableName;
        if (page == HypixelPage.Tower && path.startsWith("Tower/")) {
            path = path.substring("Tower/".length());
        } else if (page == HypixelPage.Network && path.startsWith("Blink/")) {
            path = path.substring("Blink/".length());
        } else if (page == HypixelPage.Render && path.startsWith("Render/")) {
            path = path.substring("Render/".length());
        }

        String[] parts = path.split("/");
        StringBuilder label = new StringBuilder(path.length() + 8);
        String previous = null;
        for (String part : parts) {
            if (part.equals(previous)) {
                continue;
            }
            if (!label.isEmpty()) {
                label.append(" - ");
            }
            label.append(part.replaceAll("(?<=[a-z0-9])(?=[A-Z])", " "));
            previous = part;
        }
        return label.toString();
    }

    private static boolean startsWithAny(String value, String... prefixes) {
        for (String prefix : prefixes) {
            if (value.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onEnable() {
        runningMode = mode.get();
        enableImplementation(runningMode);
        initialBlockCount = getBlockCount();
        blockCountSession++;
    }

    private void enableHypixelTest() {
        resetRuntimeState();
        if (!noPlayer()) {
            placementY = mc.player.blockPosition().getY() - 1;
            startY = mc.player.blockPosition().getY();
            jumps = 2;
            scaffoldServerRotation = new Rot2f(mc.player.getYRot(), mc.player.getXRot());
            captureTimerSpeed();
        }
        refreshRandomizedValues();
        Speed.INSTANCE.setScaffoldActive(autoSpeed.get());
    }

    @Override
    protected void onDisable() {
        disableImplementation(runningMode);
        runningMode = null;
    }

    private void disableHypixelTest() {
        if (!noPlayer() && strafe.get() && strafeHypixel.get()) {
            Vec3 motion = mc.player.getDeltaMovement();
            mc.player.setDeltaMovement(motion.x * 0.5D, motion.y, motion.z * 0.5D);
        }
        restoreSelectedSlot();
        restoreTimerSpeed();
        flushBlinkPackets();
        movementPlanner.reset();
        movementPrediction.reset();
        currentTarget = null;
        currentOptimalLine = null;
        currentTargetLine = null;
        scaffoldServerRotation = null;
        movementInputSnapshot = null;
        renderedPlacements.clear();
        forceSneak = 0;
        safeEdgeCenter = null;
        Speed.INSTANCE.setScaffoldActive(false);
    }

    @Override
    public String getInfo() {
        if (!isHypixelTest()) {
            return mode.is(ScaffoldMode.TellyBridge) ? "TellyBridge" : "GodBridge";
        }
        return isToweringWithoutMutation() ? "Tower " + tower.displayValue() : technique.displayValue();
    }

    /** Called from PlayerMixin so Safe mode uses vanilla ledge clipping without visible sneaking. */
    public boolean shouldSafeWalk() {
        return isEnabled() && isHypixelTest() && !noPlayer() && safeWalk.is(SafeWalkMode.SAFE)
                && !(normalFeaturesRunning() && shouldFallOffForDown());
    }

    /** Applies Scaffold's server-side sprint policy at LocalPlayer's sprint packet boundary. */
    public boolean modifyServerSprint(boolean original) {
        if (shouldSuppressSprint()) {
            return false;
        }
        return original;
    }

    public boolean shouldSuppressSprint() {
        return isEnabled() && isHypixelTest() && !noPlayer();
    }

    public boolean shouldSprintOmnidirectionally() {
        return isEnabled() && isHypixelTest() && sprintControl.get() && sprintClient.is(SprintMode.FORCE_SPRINT);
    }

    public int modifyServerSelectedSlot(int original) {
        return isEnabled() && isHypixelTest() && selectedScaffoldSlot >= 0 ? selectedScaffoldSlot : original;
    }

    public ItemStack modifyMainHandStack(ItemStack original) {
        if (!isEnabled() || !isHypixelTest() || noPlayer() || selectedScaffoldSlot < 0) {
            return original;
        }
        return mc.player.getInventory().getItem(selectedScaffoldSlot);
    }

    public int getBlockCount() {
        if (noPlayer()) {
            return 0;
        }
        if (!isHypixelTest()) {
            return legacyEngine.getBlockCount();
        }
        int total = isValidBlock(mc.player.getOffhandItem()) ? mc.player.getOffhandItem().getCount() : 0;
        if (!autoBlock.get()) {
            ItemStack selected = mc.player.getInventory().getItem(mc.player.getInventory().getSelectedSlot());
            return total + (isValidBlock(selected) ? selected.getCount() : 0);
        }
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (isValidBlock(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    public int getInitialBlockCount() {
        return initialBlockCount;
    }

    public long getBlockCountSession() {
        return blockCountSession;
    }

    /** Returns the stack Scaffold would currently use for its next placement. */
    public ItemStack getPlacementStack() {
        if (noPlayer()) {
            return ItemStack.EMPTY;
        }
        if (!isHypixelTest()) {
            return legacyEngine.getPlacementStack();
        }
        if (selectedScaffoldSlot >= 0) {
            ItemStack selected = mc.player.getInventory().getItem(selectedScaffoldSlot);
            if (isValidBlock(selected)) {
                return selected;
            }
        }
        ItemStack mainHand = mc.player.getInventory().getItem(
                mc.player.getInventory().getSelectedSlot());
        if (isValidBlock(mainHand)) {
            return mainHand;
        }
        ItemStack offhand = mc.player.getOffhandItem();
        if (isValidBlock(offhand)) {
            return offhand;
        }
        if (!autoBlock.get()) {
            return ItemStack.EMPTY;
        }
        int bestSlot = findBestHotbarSlot();
        return bestSlot < 0
                ? ItemStack.EMPTY
                : mc.player.getInventory().getItem(bestSlot);
    }

    @Listen(priority = com.setsuna.event.Priority.NORMAL)
    private void onTellyMovementInput(KeyboardInputEvent event) {
        if (!isHypixelTest() || noPlayer()) {
            return;
        }
        if (normalFeaturesRunning()) {
            applyTellyInput(event);
        }
    }

    /** Mirrors ModuleScaffold's MODEL_STATE handler before stabilization mutates the input. */
    @Listen(priority = -9)
    private void onModelMovementInput(KeyboardInputEvent event) {
        if (!isHypixelTest() || noPlayer()) {
            return;
        }

        rawForward = event.getOriginalForward();
        rawStrafe = event.getOriginalStrafe();
        currentOptimalLine = rawForward == 0.0F && rawStrafe == 0.0F
                ? null
                : movementPlanner.getOptimalMovementLine(rawForward, rawStrafe);
    }

    @Listen(priority = -10)
    private void onStabilizedMovementInput(KeyboardInputEvent event) {
        if (!isHypixelTest() || noPlayer()) {
            return;
        }
        if (normalFeaturesRunning()) {
            applyStabilizedInput(event);
        }
    }

    @Listen(priority = -20)
    private void onMovementInput(KeyboardInputEvent event) {
        if (!isHypixelTest() || noPlayer()) {
            return;
        }

        movementInputSnapshot = ScaffoldPlayerSimulation.simulate(
                mc,
                event.getForward(),
                event.getStrafe(),
                event.isJump(),
                event.isSneak(),
                shouldSafeWalk());
    }

    @Listen(priority = -50)
    private void onSafetyMovementInput(KeyboardInputEvent event) {
        if (!isHypixelTest() || noPlayer()) {
            return;
        }
        if (normalFeaturesRunning() && shouldEagle(event.getForward(), event.getStrafe())) {
            event.setSneak(true);
        }
        if (activeTechnique() == Technique.BREEZILY) {
            applyBreezilyInput(event);
        }
        if (speedLimiter.get() && horizontalSpeed() > speedLimit.get()) {
            event.setForward(0.0F);
            event.setStrafe(0.0F);
        }
        if (forceSneak > 0) {
            event.setSneak(true);
            forceSneak--;
        }
        if (ledge.get()) {
            applyLedgeProtection(event);
        }
    }

    @Listen(priority = -100)
    private void onObjectionMovementInput(KeyboardInputEvent event) {
        if (!isHypixelTest() || noPlayer()) {
            return;
        }
        if (safeWalk.is(SafeWalkMode.ON_EDGE)) {
            applyOnEdgeSafeWalk(event);
        }
        if (normalFeaturesRunning() && shouldFallOffForDown()) {
            event.setSneak(false);
        }
    }

    @Listen(priority = -110)
    private void onSprintMovementInput(KeyboardInputEvent event) {
        if (!isHypixelTest() || noPlayer()) {
            return;
        }
        event.setSprint(false);
    }

    @Listen
    private void onBeforeGroundJump(GroundJumpEvent.Before event) {
        if (!isHypixelTest() || noPlayer()) {
            return;
        }

        switch (tower.get()) {
            case MOTION -> jumpOffPosition = mc.player.getY();
            case PULLDOWN -> pulldownSequenceActive = true;
            case KARHU -> {
                karhuSequenceActive = true;
                karhuTimerPending = true;
            }
            default -> {
            }
        }
    }

    @Listen
    private void onAfterGroundJump(GroundJumpEvent.After event) {
        if (!isHypixelTest() || noPlayer()) {
            return;
        }
        if (normalFeaturesRunning() && telly.get()) {
            tellyTicksUntilJump = 0;
            tellyCurrentJumpTicks = randomInt(tellyJumpMin.get(), tellyJumpMax.get());
        }
        if (jumpStrafe.get()) {
            applyJumpStrafe();
        }
    }

    /** Mirrors the state updates at the start of LiquidBounce's placement coroutine. */
    @Listen(priority = com.setsuna.event.Priority.HIGHEST)
    private void onPlacementStateTick(PlayerTickEvent.Pre event) {
        if (!isHypixelTest() || noPlayer() || mc.player.isSpectator()) {
            return;
        }

        mc.player.setSprinting(false);
        updateIndependentTickState();
        updateAutoSpeedState();
        tickSelectedSlot();
        tickBlinkPulse();
        applyHeadHitterFeature();
        handleTower();
        applyMovementFeatures();
        applyTimerSpeed();

        if (placementCooldown > 0 && --placementCooldown > 0) {
            return;
        }
        updatePlayerState();
    }

    /** RotationUpdateEvent equivalent: prepares this tick's target before movement and placement handlers. */
    @Listen(priority = 300)
    private void onTargetUpdate(PlayerTickEvent.Pre event) {
        if (!isHypixelTest()) {
            return;
        }
        if (noPlayer() || mc.gameMode == null || mc.player.isSpectator()) {
            currentTarget = null;
            currentTargetLine = null;
            restoreSelectedSlot();
            restoreTimerSpeed();
            return;
        }

        int bestSlot = findBestHotbarSlot();
        ItemStack bestStack;
        if (bestSlot < 0) {
            nextBlock = null;
            bestStack = new ItemStack(Items.SANDSTONE, 64);
        } else {
            bestStack = mc.player.getInventory().getItem(bestSlot);
            nextBlock = ((BlockItem) bestStack.getItem()).getBlock();
        }

        Vec3 predictedPosition = movementPrediction.getPredictedPlacementPos(
                currentOptimalLine,
                prediction.get(),
                bootstrapBackoff.get(),
                predictionCutoffDistance.get(),
                predictionWarmupPlacements.get(),
                rawForward,
                rawStrafe,
                shouldSafeWalk());
        if (predictedPosition == null) {
            predictedPosition = mc.player.position();
        }
        Pose predictedPose = eagle.get() && shouldEagle(playerMovementForward(), playerMovementStrafe())
                ? Pose.CROUCHING : Pose.STANDING;

        currentTarget = findPlacementTarget(predictedPosition, predictedPose, bestStack);
        currentTargetLine = currentTarget == null ? null : currentOptimalLine;
        if (rotationTiming.is(RotationTiming.NORMAL)
                && (considerInventory.get() || !(mc.screen instanceof AbstractContainerScreen<?>))) {
            Rot2f targetRotation = getTechniqueRotation(currentTarget);
            if (targetRotation != null) {
                RotationManager.INSTANCE.setRotations(
                        targetRotation,
                        rotationSpeed.get(),
                        com.setsuna.util.rotation.Priority.High,
                        showRotationModel.get());
            }
        }
    }

    /** Runs after target preparation and the feature state handlers, like LiquidBounce's placement coroutine. */
    @Listen(priority = com.setsuna.event.Priority.HIGH)
    private void onPlacementTick(PlayerTickEvent.Pre event) {
        if (!isHypixelTest()) {
            return;
        }
        if (noPlayer() || mc.gameMode == null || mc.player.isSpectator()) {
            return;
        }
        if (placementCooldown > 0) {
            return;
        }
        ScaffoldTargetFinder.Target target = currentTarget;
        Rot2f managedRotation = RotationManager.INSTANCE.getRotation();
        Rot2f currentRotation;
        if ((rotationTiming.is(RotationTiming.ON_TICK) || rotationTiming.is(RotationTiming.ON_TICK_SNAP))
                && target != null) {
            Rot2f requested = getTechniqueRotation(target);
            currentRotation = requested == null ? managedRotation : requested;
        } else {
            currentRotation = managedRotation;
        }
        currentRotation = normalizeRotation(currentRotation);

        BlockHitResult crosshairTarget = getTechniqueCrosshairTarget(target, currentRotation);
        ItemStack clientMainStack = mc.player.getInventory().getItem(mc.player.getInventory().getSelectedSlot());
        boolean mainHandBlock = isValidBlock(clientMainStack);
        boolean offHandBlock = isValidBlock(mc.player.getOffhandItem());
        if (autoBlockAlways.get() && autoBlock.get()) {
            mainHandBlock = handleBlockSelection(mainHandBlock, offHandBlock);
        }

        InteractionHand suitableHand = isValidBlock(mc.player.getMainHandItem())
                ? InteractionHand.MAIN_HAND
                : offHandBlock ? InteractionHand.OFF_HAND : null;
        simulatePlacementAttempt(crosshairTarget, suitableHand);

        if (target == null || crosshairTarget == null) {
            return;
        }
        if (!target.matches(crosshairTarget) || !isValidCrosshairTarget(crosshairTarget)) {
            return;
        }

        if (!autoBlockAlways.get()) {
            mainHandBlock = handleBlockSelection(mainHandBlock, offHandBlock);
        }
        if (!mainHandBlock && !offHandBlock) {
            return;
        }
        InteractionHand hand = mainHandBlock ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        if (!isValidBlock(mc.player.getItemInHand(hand))) {
            return;
        }

        if (rotationTiming.is(RotationTiming.ON_TICK) || rotationTiming.is(RotationTiming.ON_TICK_SNAP)) {
            if (rotationDeltaSquared(getScaffoldServerRotation(), currentRotation) > 1.0E-6D) {
                sendRotationPacket(currentRotation);
            }
            if (rotationTiming.is(RotationTiming.ON_TICK_SNAP)) {
                if (considerInventory.get() || !(mc.screen instanceof AbstractContainerScreen<?>)) {
                    RotationManager.INSTANCE.setRotations(
                            currentRotation,
                            rotationSpeed.get(),
                            com.setsuna.util.rotation.Priority.High,
                            showRotationModel.get());
                }
            }
        }

        ScaffoldMovementPlanner.Line placementLine = currentTargetLine;
        Vec3 previousFallOff = placementLine == null
                ? null
                : movementPrediction.getFallOffPositionOnLine(placementLine);
        ItemStack usedStack = mc.player.getItemInHand(hand);
        int previousCount = usedStack.getCount();
        InteractionResult result = mc.gameMode.useItemOn(mc.player, hand, crosshairTarget);
        boolean passed = result instanceof InteractionResult.Pass;
        if (passed) {
            useItemWithoutTarget(hand, usedStack);
        }
        boolean successful = !passed && isClientHandledSuccess(result);
        if (successful) {
            swing(hand);
            onBlockPlacement(target.placedBlock(), placementLine, previousFallOff);
            currentTarget = null;
            currentTargetLine = null;
            placementCooldown = randomInt(delayMin.get(), delayMax.get());
        }
        if (!passed && !usedStack.isEmpty()
                && (usedStack.getCount() != previousCount || mc.player.hasInfiniteMaterials())) {
            mc.gameRenderer.itemInHandRenderer.itemUsed(hand);
        }

        if (rotationTiming.is(RotationTiming.ON_TICK)) {
            Rot2f camera = new Rot2f(mc.player.getYRot(), mc.player.getXRot());
            if (rotationDeltaSquared(getScaffoldServerRotation(), camera) > 1.0E-6D) {
                sendRotationPacket(new Rot2f(
                        currentRotation.getYaw()
                                + Mth.wrapDegrees(camera.getYaw() - currentRotation.getYaw()),
                        camera.getPitch()));
            }
        }
    }

    @Listen(priority = com.setsuna.event.Priority.HIGH)
    private void onPacketSend(PacketEvent.Send event) {
        if (!isHypixelTest()) {
            return;
        }
        if (noPlayer() || flushingBlink) {
            return;
        }

        Packet<?> packet = event.getPacket();
        if (tower.is(TowerMode.VULCAN) && !playerMoving()
                && mc.player.tickCount % 2 == 0
                && packet instanceof ServerboundMovePlayerPacket movement
                && movement.hasPosition()) {
            double x = movement.getX(mc.player.getX()) + 0.1D;
            double y = movement.getY(mc.player.getY());
            double z = movement.getZ(mc.player.getZ()) + 0.1D;
            packet = movement.hasRotation()
                    ? new ServerboundMovePlayerPacket.PosRot(
                    x, y, z,
                    movement.getYRot(mc.player.getYRot()),
                    movement.getXRot(mc.player.getXRot()),
                    movement.isOnGround(), movement.horizontalCollision())
                    : new ServerboundMovePlayerPacket.Pos(
                    x, y, z, movement.isOnGround(), movement.horizontalCollision());
            event.setPacket(packet);
        }

        trackScaffoldServerRotation(packet);

        if (!blink.get()) {
            return;
        }
        if (shouldFlushBlink(packet)) {
            flushBlinkPackets();
            blinkPulseStarted = System.currentTimeMillis();
            return;
        }

        long elapsed = System.currentTimeMillis() - blinkPulseStarted;
        if (!mc.player.onGround() || elapsed < blinkPulseTime) {
            event.cancel();
            blinkPackets.add(packet);
        }
    }

    @Listen(priority = com.setsuna.event.Priority.HIGHEST)
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!isHypixelTest()) {
            return;
        }
        if (event.getPacket() instanceof ClientboundPlayerPositionPacket
                || event.getPacket() instanceof ClientboundPlayerRotationPacket) {
            flushBlinkPackets();
            currentTarget = null;
            currentTargetLine = null;
            scaffoldServerRotation = null;
            movementPlanner.reset();
            movementPrediction.reset();
        }
    }

    @Listen
    private void onRespawn(RespawnEvent event) {
        if (!isHypixelTest()) {
            return;
        }
        restoreSelectedSlot();
        movementPlanner.reset();
        movementPrediction.reset();
        currentTarget = null;
        currentOptimalLine = null;
        currentTargetLine = null;
        scaffoldServerRotation = null;
        movementInputSnapshot = null;
        nextBlock = null;
        forceSneak = 0;
        placementCooldown = 0;
        wasTowering = false;
        safeEdgeCenter = null;
        pulldownSequenceActive = false;
        karhuSequenceActive = false;
        karhuTimerPending = false;
        karhuTimerPulse = false;
        blinkPackets.clear();
        renderedPlacements.clear();
    }

    @Listen
    private void onRender3D(Render3DEvent event) {
        if (!isHypixelTest() || !render.get() || renderedPlacements.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        long inTime = renderInTime.get();
        long outTime = renderOutTime.get();
        long totalTime = inTime + outTime;
        renderedPlacements.removeIf(info -> now - info.startTime() > totalTime);
        Set<BlockPos> renderedBlocks = renderClump.get()
                ? new HashSet<>(renderedPlacements.stream().map(RenderInfo::pos).toList())
                : Set.of();

        for (RenderInfo info : renderedPlacements) {
            long elapsed = now - info.startTime();
            float alpha;
            double size;
            if (elapsed <= inTime && inTime > 0L) {
                float progress = Mth.clamp((float) elapsed / inTime, 0.0F, 1.0F);
                float sizeFactor = renderStartCurve.get().getFunction().apply(progress);
                float fadeFactor = renderFadeInCurve.get().getFunction().apply(progress);
                size = Mth.lerp(sizeFactor, renderStartSize.get(), 1.0D);
                alpha = fadeFactor;
            } else {
                float progress = outTime <= 0L
                        ? 1.0F
                        : Mth.clamp((float) (elapsed - inTime) / outTime, 0.0F, 1.0F);
                float sizeFactor = renderEndCurve.get().getFunction().apply(progress);
                float fadeFactor = renderFadeOutCurve.get().getFunction().apply(progress);
                size = Mth.lerp(sizeFactor, 1.0D, renderEndSize.get());
                alpha = 1.0F - fadeFactor;
            }

            AABB box = AABB.ofSize(info.pos().getCenter(), size, size, size);
            ScaffoldRenderCuller.CullMask mask = renderClump.get()
                    ? ScaffoldRenderCuller.cull(info.pos(), renderedBlocks)
                    : ScaffoldRenderCuller.ALL;
            Render3DUtils.drawFilledBox(box, withAlpha(renderColor.get(), alpha), mask.faceVertices());
            Render3DUtils.drawOutlineBox(
                    event.getPoseStack(), box, withAlpha(renderOutline.get(), alpha), mask.outlineVertices());
        }
    }

    private ScaffoldTargetFinder.Target findPlacementTarget(
            Vec3 predictedPosition,
            Pose predictedPose,
            ItemStack bestStack) {
        Technique active = activeTechnique();
        return switch (active) {
            case NORMAL -> {
                boolean goingDown = downRequested();
                yield targetFinder.find(
                        getTargetedPosition(BlockPos.containing(predictedPosition)),
                        goingDown ? ScaffoldTargetFinder.downOffsets() : ScaffoldTargetFinder.normalOffsets(),
                        predictedPosition,
                        predictedPose,
                        currentOptimalLine,
                        bestStack,
                        rotationMode.get(),
                        goingDown,
                        getScaffoldServerRotation());
            }
            case EXPAND -> findExpandTarget(predictedPosition, predictedPose, bestStack);
            case GOD_BRIDGE, BREEZILY -> targetFinder.find(
                    getTargetedPosition(BlockPos.containing(predictedPosition)),
                    ScaffoldTargetFinder.normalOffsets(),
                    predictedPosition,
                    predictedPose,
                    null,
                    bestStack,
                    ScaffoldTargetFinder.AimMode.CENTER,
                    false,
                    getScaffoldServerRotation());
        };
    }

    private ScaffoldTargetFinder.Target findExpandTarget(
            Vec3 predictedPosition,
            Pose predictedPose,
            ItemStack bestStack) {
        double yaw = Math.toRadians(mc.player.getYRot());
        BlockPos base = BlockPos.containing(predictedPosition);
        for (int length = 0; length <= expandLength.get(); length++) {
            BlockPos expanded = base.offset(
                    (int) (-Math.sin(yaw) * length),
                    0,
                    (int) (Math.cos(yaw) * length));
            ScaffoldTargetFinder.Target target = targetFinder.find(
                    getTargetedPosition(expanded),
                    ScaffoldTargetFinder.noOffset(),
                    predictedPosition,
                    predictedPose,
                    null,
                    bestStack,
                    ScaffoldTargetFinder.AimMode.CENTER,
                    true,
                    getScaffoldServerRotation());
            if (target != null) {
                return target;
            }
        }
        return null;
    }

    private BlockPos getTargetedPosition(BlockPos blockPos) {
        if (isTowering() || wasTowering) {
            return blockPos.below();
        }
        if (shouldGoDown()) {
            return blockPos.offset(0, -2, 0);
        }
        if (normalFeaturesRunning() && ceiling.get()
                && !mc.level.getBlockState(mc.player.blockPosition().below()).isAir()) {
            return blockPos.offset(0, 3, 0);
        }
        if (mc.player.input != null && mc.player.input.keyPresses.jump()
                && (!playerMoving() || mc.player.horizontalCollision)) {
            return blockPos.below();
        }

        return switch (sameY.get()) {
            case ON -> new BlockPos(blockPos.getX(), placementY, blockPos.getZ());
            case JUMP_KEY -> mc.options.keyJump.isDown()
                    ? new BlockPos(blockPos.getX(), placementY, blockPos.getZ()) : blockPos.below();
            case FALLING -> mc.player.getDeltaMovement().y < 0.2D
                    ? new BlockPos(blockPos.getX(), placementY, blockPos.getZ()) : blockPos.below();
            case HYPIXEL -> {
                if (mc.player.getDeltaMovement().y == -0.15233518685055708D && jumps >= 2) {
                    jumps = 0;
                    yield new BlockPos(blockPos.getX(), startY, blockPos.getZ());
                }
                yield new BlockPos(blockPos.getX(), startY - 1, blockPos.getZ());
            }
            case OFF -> blockPos.below();
        };
    }

    private Rot2f getTechniqueRotation(ScaffoldTargetFinder.Target target) {
        Technique active = activeTechnique();
        if (active == Technique.NORMAL) {
            if (telly.get() && tellyDoNotAim()) {
                if (tellyResetMode.is(TellyResetMode.RESET)) {
                    return null;
                }
                return new Rot2f(
                        Math.round(mc.player.getYRot() / 45.0F) * 45.0F,
                        Math.max(45.0F, mc.player.getXRot()));
            }
            if (target == null) {
                return null;
            }
            if (requiresSight.get()) {
                BlockHitResult raycast = raytraceBlock(target.rotation(), interactionRange());
                if (raycast != null && raycast.getType() == HitResult.Type.BLOCK
                        && raycast.getBlockPos().equals(target.interactedBlockPos())) {
                    return target.rotation();
                }
            }
            return target.rotation();
        }
        if (active == Technique.EXPAND) {
            return target == null ? null : RotationUtils.calculate(target.placedBlock().getCenter());
        }
        if (target == null && rawForward == 0.0F && rawStrafe == 0.0F) {
            return null;
        }

        if (rawForward == 0.0F && rawStrafe == 0.0F) {
            float axis = (float) Math.floor(target.rotation().getYaw() / 90.0F) * 90.0F;
            return new Rot2f(axis + 45.0F, 75.0F);
        }

        float movingYaw = Math.round(
                (ScaffoldMovementPlanner.movementAngle(mc.player.getYRot(), rawForward, rawStrafe) + 180.0F)
                        / 45.0F) * 45.0F;
        boolean straight = movingYaw % 90.0F == 0.0F;
        if (active == Technique.BREEZILY) {
            return new Rot2f(movingYaw, straight ? 80.0F : 75.6F);
        }
        if (!straight) {
            return new Rot2f(movingYaw, 75.6F);
        }
        return godBridgeStraightRotation(movingYaw);
    }

    private Rot2f godBridgeStraightRotation(float movingYaw) {
        if (mc.player.onGround()) {
            double radians = Math.toRadians(movingYaw);
            godBridgeRightSide = Math.floor(mc.player.getX() + Math.cos(radians) * 0.5D) != Math.floor(mc.player.getX())
                    || Math.floor(mc.player.getZ() + Math.sin(radians) * 0.5D) != Math.floor(mc.player.getZ());

            Direction direction = Direction.fromYRot(movingYaw);
            BlockPos inDirection = BlockPos.containing(mc.player.position().add(
                    direction.getStepX() * 0.6D,
                    0.0D,
                    direction.getStepZ() * 0.6D));
            boolean leaningOff = mc.level.getBlockState(mc.player.blockPosition().below()).isAir();
            boolean nextAir = mc.level.getBlockState(inDirection.below()).isAir();
            if (leaningOff && nextAir) {
                godBridgeRightSide = !godBridgeRightSide;
            }
        }
        return new Rot2f(movingYaw + (godBridgeRightSide ? 45.0F : -45.0F), 75.7F);
    }

    private BlockHitResult getTechniqueCrosshairTarget(
            ScaffoldTargetFinder.Target target,
            Rot2f rotation) {
        Technique active = activeTechnique();
        if (target == null && active != Technique.GOD_BRIDGE && active != Technique.BREEZILY) {
            return null;
        }
        BlockHitResult visible = raytraceBlock(rotation, interactionRange());
        return switch (active) {
            case NORMAL -> {
                if (visible != null && target.matches(visible)) {
                    yield visible;
                }
                yield downRequested() ? target.fallbackHitResult() : null;
            }
            case EXPAND -> visible != null && target.matches(visible)
                    ? visible : target.fallbackHitResult();
            case GOD_BRIDGE, BREEZILY -> visible;
        };
    }

    private BlockHitResult raytraceBlock(Rot2f rotation, double range) {
        if (rotation == null || noPlayer()) {
            return null;
        }
        Vec3 eye = mc.player.getEyePosition();
        return raytraceBlockFrom(eye, rotation, range);
    }

    private BlockHitResult raytraceBlockFrom(Vec3 eye, Rot2f rotation, double range) {
        if (rotation == null || eye == null || noPlayer()) {
            return null;
        }
        Vec3 end = eye.add(Vec3.directionFromRotation(rotation.getPitch(), rotation.getYaw()).scale(range));
        return mc.level.clip(new ClipContext(
                eye,
                end,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                mc.player));
    }

    private double interactionRange() {
        return Math.max(mc.player.blockInteractionRange(), mc.player.entityInteractionRange());
    }

    private boolean isValidCrosshairTarget(BlockHitResult hit) {
        if (hit.getType() != HitResult.Type.BLOCK) {
            return false;
        }
        Direction side = hit.getDirection();
        if (side.getAxis() == Direction.Axis.Y) {
            return true;
        }
        Vec3 difference = hit.getLocation().subtract(mc.player.getEyePosition());
        double distance = side == Direction.NORTH || side == Direction.SOUTH
                ? difference.z : difference.x;
        return Math.abs(distance) >= minDist.get();
    }

    private void updatePlayerState() {
        boolean onGround = mc.player.onGround();

        if (onGround) {
            placementY = mc.player.blockPosition().getY() - 1;
            jumps++;
            wasTowering = false;
        }

        if (mc.options.keyJump.isDown()) {
            startY = mc.player.blockPosition().getY();
            jumps = 2;
        }
    }

    private void updateIndependentTickState() {
        if (wasPlacedTicks > 0) {
            wasPlacedTicks--;
        }
        if (mc.player.onGround()) {
            airTicks = 0;
            if (normalFeaturesRunning() && telly.get()) {
                tellyTicksUntilJump++;
            }
        } else {
            airTicks++;
        }
    }

    private void applyHeadHitterFeature() {
        if (!normalFeaturesRunning() || !headHitter.get()) {
            return;
        }
        if (headHitterCooldown > 0) {
            headHitterCooldown--;
        } else if (mc.player.onGround() && playerMoving()
                && !mc.level.getBlockState(mc.player.blockPosition().above(2))
                .getCollisionShape(mc.level, mc.player.blockPosition().above(2)).isEmpty()) {
            mc.player.jumpFromGround();
            headHitterCooldown = randomInt(headHitterDelayMin.get(), headHitterDelayMax.get());
        }
    }

    private void applyMovementFeatures() {
        if (acceleration.get() && (!accelerationOnlyGround.get() || mc.player.onGround())) {
            Vec3 motion = mc.player.getDeltaMovement();
            mc.player.setDeltaMovement(
                    motion.x * accelerationMultiplier.get(),
                    motion.y,
                    motion.z * accelerationMultiplier.get());
        }

        float movementForward = playerMovementForward();
        float movementStrafe = playerMovementStrafe();
        if (isMoving(movementForward, movementStrafe)) {
            movementTicks++;
        } else {
            movementTicks = 0;
        }
        if (strafe.get() && (!strafeOnlyGround.get() || mc.player.onGround())) {
            if (!isMoving(movementForward, movementStrafe)) {
                Vec3 motion = mc.player.getDeltaMovement();
                mc.player.setDeltaMovement(0.0D, motion.y, 0.0D);
                return;
            }
            double speed = strafeSpeed.get();
            if (strafeHypixel.get()) {
                speed = mc.player.getEffect(MobEffects.SPEED) == null ? 0.207D : 0.295D;
                if (mc.player.tickCount % 20 == 0 || movementTicks <= 7) {
                    speed = 0.09800000190734863D;
                }
            }
            setHorizontalSpeed(speed, movementForward, movementStrafe);
        }
    }

    private void onPredictionToggled(boolean enabled) {
        if (!enabled && movementPrediction != null) {
            movementPrediction.reset();
        }
    }

    private void onStrafeToggled(boolean enabled) {
        if (!enabled && strafeHypixel != null && strafeHypixel.get() && !noPlayer()) {
            Vec3 motion = mc.player.getDeltaMovement();
            mc.player.setDeltaMovement(motion.x * 0.5D, motion.y, motion.z * 0.5D);
        }
    }

    private void onSafeWalkModeChanged(SafeWalkMode mode) {
        safeEdgeCenter = null;
        safeOverwriteTicks = 0;
        safeSneakTicks = 0;
        safeCurrentEdgeDistance = randomDouble(safeEdgeDistanceMin.get(), safeEdgeDistanceMax.get());
    }

    private void applyJumpStrafe() {
        float movementForward = playerMovementForward();
        float movementStrafe = playerMovementStrafe();
        if (!isMoving(movementForward, movementStrafe)) {
            Vec3 motion = mc.player.getDeltaMovement();
            mc.player.setDeltaMovement(0.0D, motion.y, 0.0D);
            return;
        }
        float direction = ScaffoldMovementPlanner.movementAngle(
                mc.player.getYRot(), movementForward, movementStrafe) + 180.0F;
        float rounded = Math.round(direction / 45.0F) * 45.0F;
        boolean straight = rounded % 90.0F == 0.0F;
        double speed = straight
                ? randomDouble(jumpStraightMin.get(), jumpStraightMax.get())
                : randomDouble(jumpDiagonalMin.get(), jumpDiagonalMax.get());
        setHorizontalSpeed(speed, movementForward, movementStrafe);
    }

    private void handleTower() {
        handlePulldownSequences();

        boolean towering = isToweringWithoutMutation();
        if (!towering || getBlockCount() <= 0) {
            if (tower.is(TowerMode.MOTION)) {
                jumpOffPosition = Double.NaN;
            }
            return;
        }

        switch (tower.get()) {
            case NONE -> {
            }
            case MOTION -> {
                if (!isBlockBelow()) {
                    jumpOffPosition = Double.NaN;
                    return;
                }
                if (!Double.isNaN(jumpOffPosition)
                        && mc.player.getY() > jumpOffPosition + towerTriggerHeight.get()) {
                    double truncatedY = Math.copySign(Math.floor(Math.abs(mc.player.getY())), mc.player.getY());
                    mc.player.setPos(mc.player.getX(), truncatedY, mc.player.getZ());
                    Vec3 motion = mc.player.getDeltaMovement();
                    mc.player.setDeltaMovement(
                            motion.x * towerSlow.get(),
                            towerMotion.get(),
                            motion.z * towerSlow.get());
                    mc.player.awardStat(Stats.JUMP);
                    jumpOffPosition = mc.player.getY();
                }
            }
            case PULLDOWN, KARHU -> {
            }
            case VULCAN -> {
                if (!isBlockBelow()) {
                    return;
                }
                Vec3 motion = mc.player.getDeltaMovement();
                if (mc.player.tickCount % 2 == 0) {
                    mc.player.setDeltaMovement(motion.x, 0.7D, motion.z);
                } else {
                    mc.player.setDeltaMovement(
                            motion.x,
                            playerMoving() ? 0.42D : 0.6D,
                            motion.z);
                    mc.player.awardStat(Stats.JUMP);
                }
            }
            case HYPIXEL -> {
                if (horizontalSpeed() > 0.01D) {
                    return;
                }
                Vec3 motion = mc.player.getDeltaMovement();
                if (mc.player.onGround()) {
                    mc.player.setDeltaMovement(motion.x, 0.42D, motion.z);
                } else if (motion.y <= 0.0D && motion.y >= -0.09D) {
                    mc.player.setDeltaMovement(motion.x, -0.38D, motion.z);
                }
            }
        }
    }

    private void handlePulldownSequences() {
        if (!tower.is(TowerMode.PULLDOWN)) {
            pulldownSequenceActive = false;
        } else if (pulldownSequenceActive && !mc.player.onGround()
                && mc.player.getDeltaMovement().y < towerPulldownTrigger.get()) {
            if (isBlockBelow()) {
                Vec3 motion = mc.player.getDeltaMovement();
                mc.player.setDeltaMovement(motion.x, -1.0D, motion.z);
            }
            pulldownSequenceActive = false;
        }

        if (!tower.is(TowerMode.KARHU)) {
            karhuSequenceActive = false;
            karhuTimerPending = false;
            karhuTimerPulse = false;
            return;
        }
        if (!karhuSequenceActive) {
            return;
        }
        if (karhuTimerPending && !mc.player.onGround()) {
            karhuTimerPending = false;
            karhuTimerPulse = true;
            if (!towerKarhuPulldown.get()) {
                karhuSequenceActive = false;
                return;
            }
        }
        if (!karhuTimerPending && towerKarhuPulldown.get() && !mc.player.onGround()
                && mc.player.getDeltaMovement().y < towerKarhuTrigger.get()) {
            if (isBlockBelow()) {
                Vec3 motion = mc.player.getDeltaMovement();
                mc.player.setDeltaMovement(motion.x, motion.y - 1.0D, motion.z);
            }
            karhuSequenceActive = false;
        }
    }

    private boolean isBlockBelow() {
        AABB box = mc.player.getBoundingBox().inflate(0.5D, 0.0D, 0.5D).move(0.0D, -1.05D, 0.0D);
        return mc.level.getBlockCollisions(mc.player, box).iterator().hasNext();
    }

    private Technique activeTechnique() {
        return isTowering() ? Technique.NORMAL : technique.get();
    }

    private boolean isTowering() {
        if (!tower.is(TowerMode.NONE) && mc.options.keyJump.isDown()) {
            wasTowering = true;
            return true;
        }
        return false;
    }

    private boolean isToweringWithoutMutation() {
        return !tower.is(TowerMode.NONE) && mc.player != null && mc.options.keyJump.isDown();
    }

    private boolean normalFeaturesRunning() {
        return technique.is(Technique.NORMAL);
    }

    private boolean shouldGoDown() {
        return normalFeaturesRunning() && downRequested();
    }

    private boolean downRequested() {
        return down.get() && mc.options.keyShift.isDown();
    }

    private boolean shouldFallOffForDown() {
        if (!downRequested()) {
            return false;
        }
        BlockPos below = mc.player.blockPosition().offset(0, -2, 0);
        return mc.level.getBlockState(below)
                .isFaceSturdy(mc.level, below, Direction.UP, SupportType.CENTER);
    }

    private boolean shouldEagle(float forward, float strafe) {
        if (!eagle.get() || mc.player.getAbilities().flying
                || shouldFallOffForDown() || eaglePlacedBlocks > 0
                || (eagleOnlyOnGround.get() && !mc.player.onGround())) {
            return false;
        }
        return closeToEdge(forward, strafe,
                Math.min(eagleCurrentDistance, Math.max(0.01D, eagleDistanceMax.get())));
    }

    private void applyTellyInput(KeyboardInputEvent event) {
        if (!telly.get() || mc.player.input == null
                || !isMoving(mc.player.input.getMoveVector().y, mc.player.input.getMoveVector().x)
                || getBlockCount() <= 0 || !mc.player.onGround()) {
            return;
        }
        boolean straight = !RotationManager.INSTANCE.isActive() || tellyStraightTicks.get() == 0;
        if (tellyResetMode.is(TellyResetMode.REVERSE)
                || (straight && tellyTicksUntilJump >= tellyCurrentJumpTicks)) {
            event.setJump(true);
        }
    }

    private boolean tellyDoNotAim() {
        return telly.get()
                && airTicks <= tellyStraightTicks.get()
                && tellyTicksUntilJump >= tellyCurrentJumpTicks
                && !(isToweringWithoutMutation() && tellyAimOnTower.get());
    }

    private void applyStabilizedInput(KeyboardInputEvent event) {
        if (!stabilizeMovement.get() || (event.isJump() && mc.player.onGround())
                || currentOptimalLine == null) {
            return;
        }
        Vec3 nearest = currentOptimalLine.nearestPoint(mc.player.position());
        Vec3 toLine = nearest.subtract(mc.player.position());
        Vec3 horizontalVelocity = mc.player.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D);
        double maxDeviation = toLine.dot(horizontalVelocity) > 0.0D ? 0.075D : 0.2D;
        if (toLine.lengthSqr() < maxDeviation * maxDeviation) {
            return;
        }

        float relative = Mth.wrapDegrees(worldYaw(toLine) - mc.player.getYRot());
        float desiredForward = relative > -90.0F && relative < 90.0F
                ? 1.0F : relative < -90.0F || relative > 90.0F ? -1.0F : 0.0F;
        float desiredStrafe = relative > 0.0F && relative < 180.0F
                ? -1.0F : relative > -180.0F && relative < 0.0F ? 1.0F : 0.0F;
        float forward = event.getForward();
        float strafe = event.getStrafe();
        if (forward == 0.0F) {
            forward = desiredForward;
        }
        if (strafe == 0.0F) {
            strafe = desiredStrafe;
        }
        event.setForward(forward);
        event.setStrafe(strafe);
    }

    private void applyBreezilyInput(KeyboardInputEvent event) {
        if (event.getForward() <= 0.0F || mc.player.isShiftKeyDown()) {
            return;
        }
        BlockPos below = mc.player.blockPosition().below();
        if (mc.level.getBlockState(below).isAir()) {
            breezilyLastAirTime = System.currentTimeMillis();
        } else if (System.currentTimeMillis() - breezilyLastAirTime > 500L) {
            return;
        }

        double modX = mc.player.getX() - Math.floor(mc.player.getX());
        double modZ = mc.player.getZ() - Math.floor(mc.player.getZ());
        double edge = breezilyCurrentEdgeDistance;
        double other = 1.0D - edge;
        float sideways = 0.0F;
        switch (Direction.fromYRot(mc.player.getYRot())) {
            case SOUTH -> {
                if (modX > other) sideways = 1.0F;
                if (modX < edge) sideways = -1.0F;
            }
            case NORTH -> {
                if (modX > other) sideways = -1.0F;
                if (modX < edge) sideways = 1.0F;
            }
            case EAST -> {
                if (modZ > other) sideways = -1.0F;
                if (modZ < edge) sideways = 1.0F;
            }
            case WEST -> {
                if (modZ > other) sideways = 1.0F;
                if (modZ < edge) sideways = -1.0F;
            }
            default -> {
            }
        }
        if (breezilyLastSideways != sideways && sideways != 0.0F) {
            breezilyLastSideways = sideways;
            breezilyCurrentEdgeDistance = randomDouble(breezilyEdgeMin.get(), breezilyEdgeMax.get());
        }
        event.setStrafe(breezilyLastSideways == -1.0F ? 1.0F
                : breezilyLastSideways == 1.0F ? -1.0F : event.getStrafe());
    }

    private void applyLedgeProtection(KeyboardInputEvent event) {
        if (!closeToEdge(event.getForward(), event.getStrafe(), 0.1D)) {
            if (activeTechnique() == Technique.GOD_BRIDGE) {
                applyGodBridgeLedge(event);
            }
            return;
        }

        Rot2f currentRotation = RotationManager.INSTANCE.getRotation();
        int ticks = currentRotation == null ? 0 : remainingRotationTicks(
                Math.sqrt(rotationDeltaSquared(getScaffoldServerRotation(), currentRotation)),
                rotationSpeed.get() * 18.0D);
        if (getBlockCount() <= 0 || ticks >= 1) {
            event.setSneak(true);
            forceSneak = Math.max(forceSneak, Math.max(1, ticks));
        } else if (activeTechnique() == Technique.GOD_BRIDGE) {
            applyGodBridgeLedge(event);
        }
    }

    static int remainingRotationTicks(double rotationDistance, double degreesPerTick) {
        int smoothingSteps = (int) Math.ceil(rotationDistance / Math.max(1.0D, degreesPerTick));
        return Math.max(0, smoothingSteps - 1);
    }

    private void applyGodBridgeLedge(KeyboardInputEvent event) {
        ScaffoldPlayerSimulation.Snapshot snapshot = movementInputSnapshot;
        if (activeTechnique() != Technique.GOD_BRIDGE || currentTarget == null
                || snapshot == null || !snapshot.clipLedged()) {
            return;
        }
        Rot2f rotation = RotationManager.INSTANCE.getRotation();
        Vec3 predictedEye = snapshot.position()
                .add(0.0D, mc.player.getEyeHeight(), 0.0D);
        BlockHitResult hit = raytraceBlockFrom(predictedEye, rotation, interactionRange());
        if (hit != null && currentTarget.matches(hit) && isValidCrosshairTarget(hit)) {
            return;
        }

        if (getBlockCount() < godBridgeForceSneakBelow.get()) {
            event.setSneak(true);
            forceSneak = Math.max(forceSneak, randomInt(godBridgeSneakMin.get(), godBridgeSneakMax.get()));
            return;
        }

        List<Integer> modes = new ArrayList<>(4);
        if (godBridgeJump.get()) modes.add(0);
        if (godBridgeSneak.get()) modes.add(1);
        if (godBridgeStopInput.get()) modes.add(2);
        if (godBridgeBackwards.get()) modes.add(3);
        if (modes.isEmpty()) {
            event.setSneak(true);
            return;
        }
        int mode = modes.get(ThreadLocalRandom.current().nextInt(modes.size()));
        if (mode == 0 && canJumpTwoBlocksHigh()) {
            modes.remove(Integer.valueOf(0));
            mode = modes.isEmpty()
                    ? 1
                    : modes.get(ThreadLocalRandom.current().nextInt(modes.size()));
        }
        switch (mode) {
            case 0 -> event.setJump(true);
            case 1 -> {
                event.setSneak(true);
                forceSneak = Math.max(forceSneak, randomInt(godBridgeSneakMin.get(), godBridgeSneakMax.get()));
            }
            case 2 -> {
                event.setForward(0.0F);
                event.setStrafe(0.0F);
            }
            case 3 -> {
                event.setForward(-1.0F);
                event.setStrafe(0.0F);
            }
            default -> {
            }
        }
    }

    private boolean canJumpTwoBlocksHigh() {
        double verticalMotion = ((LivingEntityAccessor) mc.player).setsuna$invokeGetJumpPower();
        double height = 0.0D;
        while (verticalMotion > 0.0D) {
            height += verticalMotion;
            verticalMotion = (verticalMotion - VANILLA_GRAVITY) * VANILLA_VERTICAL_DRAG;
        }
        return height >= 2.0D;
    }

    private void applyOnEdgeSafeWalk(KeyboardInputEvent event) {
        if (!mc.player.onGround() || event.isSneak()) {
            return;
        }
        if (safeEdgeCenter == null) {
            safeEdgeCenter = mc.player.blockPosition().getBottomCenter();
        }
        boolean onEdge = closeToEdge(
                event.getForward(), event.getStrafe(), Math.min(horizontalSpeed(), safeCurrentEdgeDistance));
        if (onEdge && safeEdgeCenter != null) {
            double currentDistance = safeEdgeCenter.subtract(mc.player.position()).horizontalDistanceSqr();
            double nextDistance = safeEdgeCenter.subtract(
                    mc.player.position().add(mc.player.getDeltaMovement())).horizontalDistanceSqr();
            onEdge = nextDistance > currentDistance;
        }
        if (onEdge && safeOverwriteTicks == 0) {
            safeCurrentEdgeDistance = randomDouble(safeEdgeDistanceMin.get(), safeEdgeDistanceMax.get());
            safeOverwriteTicks = randomInt(safeKeepMin.get(), safeKeepMax.get());
        }
        if (onEdge && safeSneakTicks == 0) {
            safeSneakTicks = randomInt(safeSneakMin.get(), safeSneakMax.get());
        }
        if (safeOverwriteTicks > 0) {
            safeOverwriteTicks--;
            switch (safeEdgeMode.get()) {
                case INVERT -> {
                    event.setForward(-event.getForward());
                    event.setStrafe(-event.getStrafe());
                    event.setJump(false);
                }
                case CENTER -> steerToward(event, safeEdgeCenter);
                case STOP -> {
                    if (horizontalSpeed() > 0.05D) {
                        steerToward(event, safeEdgeCenter);
                    } else {
                        event.setForward(0.0F);
                        event.setStrafe(0.0F);
                        event.setJump(false);
                    }
                }
            }
            if (safeEdgeJump.get()) {
                event.setJump(true);
            }
        }
        if (safeSneakTicks > 0) {
            safeSneakTicks--;
            event.setSneak(true);
        }
        BlockPos support = mc.player.blockPosition().below();
        if (!mc.level.getBlockState(support).getCollisionShape(mc.level, support).isEmpty()) {
            safeEdgeCenter = mc.player.blockPosition().getBottomCenter();
        }
    }

    private void steerToward(KeyboardInputEvent event, Vec3 center) {
        if (center == null) {
            return;
        }
        Vec3 delta = center.subtract(mc.player.position());
        float relative = Mth.wrapDegrees(worldYaw(delta) - mc.player.getYRot());
        double deadAngleComponent = Math.sin(Math.toRadians(20.0D));
        event.setForward(Math.cos(Math.toRadians(relative)) > deadAngleComponent ? 1.0F
                : Math.cos(Math.toRadians(relative)) < -deadAngleComponent ? -1.0F : 0.0F);
        double strafe = -Math.sin(Math.toRadians(relative));
        event.setStrafe(strafe > deadAngleComponent ? 1.0F : strafe < -deadAngleComponent ? -1.0F : 0.0F);
    }

    private boolean closeToEdge(float forward, float strafe, double distance) {
        return ScaffoldPlayerSimulation.isCloseToEdge(
                mc, forward, strafe, distance, shouldSafeWalk());
    }

    private double horizontalSpeed() {
        Vec3 motion = mc.player.getDeltaMovement();
        return Math.sqrt(motion.x * motion.x + motion.z * motion.z);
    }

    private void setHorizontalSpeed(double speed, float forward, float strafe) {
        double[] vector = movementVector(speed, forward, strafe);
        Vec3 motion = mc.player.getDeltaMovement();
        mc.player.setDeltaMovement(vector[0], motion.y, vector[1]);
    }

    private double[] movementVector(double speed, float forward, float strafe) {
        float yaw = mc.player.getYRot();
        if (forward != 0.0F) {
            if (strafe > 0.0F) yaw += forward > 0.0F ? -45.0F : 45.0F;
            if (strafe < 0.0F) yaw += forward > 0.0F ? 45.0F : -45.0F;
            strafe = 0.0F;
            forward = forward > 0.0F ? 1.0F : -1.0F;
        }
        double radians = Math.toRadians(yaw + 90.0F);
        return new double[]{
                forward * speed * Math.cos(radians) + strafe * speed * Math.sin(radians),
                forward * speed * Math.sin(radians) - strafe * speed * Math.cos(radians)};
    }

    private static boolean isMoving(float forward, float strafe) {
        return forward != 0.0F || strafe != 0.0F;
    }

    private float playerMovementForward() {
        return mc.player == null || mc.player.input == null
                ? 0.0F : mc.player.input.getMoveVector().y;
    }

    private float playerMovementStrafe() {
        return mc.player == null || mc.player.input == null
                ? 0.0F : mc.player.input.getMoveVector().x;
    }

    private boolean playerMoving() {
        return isMoving(playerMovementForward(), playerMovementStrafe());
    }

    private static float worldYaw(Vec3 vector) {
        return Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(-vector.x, vector.z)));
    }

    private boolean applySprintMode(SprintMode mode, boolean current, boolean moving) {
        return switch (mode) {
            case FORCE_SPRINT -> moving || current;
            case FORCE_NO_SPRINT -> false;
            case NO_SPRINT_ON_PLACE -> wasPlacedTicks > 0 ? false : current;
            case NO_SPRINT_ON_GROUND -> !mc.player.onGround();
            case DO_NOT_CHANGE -> current;
        };
    }

    private int findBestHotbarSlot() {
        if (noPlayer()) {
            return -1;
        }
        List<Integer> placeable = new ArrayList<>(9);
        for (int slot = 0; slot < 9; slot++) {
            if (isValidBlock(mc.player.getInventory().getItem(slot))) {
                placeable.add(slot);
            }
        }
        if (placeable.isEmpty()) {
            return -1;
        }

        List<Integer> aboveThreshold = placeable.stream()
                .filter(slot -> mc.player.getInventory().getItem(slot).getCount() > doNotUseBelowCount.get())
                .toList();
        List<Integer> candidates = aboveThreshold.isEmpty() ? placeable : aboveThreshold;
        int best = candidates.getFirst();
        for (int slot : candidates) {
            if (compareBlockStacks(
                    mc.player.getInventory().getItem(slot),
                    mc.player.getInventory().getItem(best)) > 0) {
                best = slot;
            }
        }
        return best;
    }

    private int compareBlockStacks(ItemStack first, ItemStack second) {
        Block firstBlock = ((BlockItem) first.getItem()).getBlock();
        Block secondBlock = ((BlockItem) second.getItem()).getBlock();
        BlockState firstState = firstBlock.defaultBlockState();
        BlockState secondState = secondBlock.defaultBlockState();

        int result = Boolean.compare(!isUnfavorable(first), !isUnfavorable(second));
        if (result != 0) return result;
        result = Boolean.compare(
                firstState.isRedstoneConductor(mc.level, BlockPos.ZERO),
                secondState.isRedstoneConductor(mc.level, BlockPos.ZERO));
        if (result != 0) return result;
        result = Boolean.compare(
                firstState.isCollisionShapeFullBlock(mc.level, BlockPos.ZERO),
                secondState.isCollisionShapeFullBlock(mc.level, BlockPos.ZERO));
        if (result != 0) return result;
        result = Float.compare(firstBlock.getFriction(), secondBlock.getFriction());
        if (result != 0) return result;
        result = Float.compare(
                Math.abs(firstBlock.getJumpFactor() - 1.0F),
                Math.abs(secondBlock.getJumpFactor() - 1.0F));
        if (result != 0) return result;
        result = Float.compare(
                Math.abs(firstBlock.getSpeedFactor() - 1.0F),
                Math.abs(secondBlock.getSpeedFactor() - 1.0F));
        if (result != 0) return result;

        double firstHardness = hardnessDistance(firstState, true);
        double secondHardness = hardnessDistance(secondState, true);
        result = Double.compare(secondHardness, firstHardness);
        if (result != 0) return result;
        result = Integer.compare(second.getCount(), first.getCount());
        if (result != 0) return result;
        return Double.compare(
                hardnessDistance(secondState, false),
                hardnessDistance(firstState, false));
    }

    private double hardnessDistance(BlockState state, boolean neutralRange) {
        double hardness = state.getDestroySpeed(mc.level, BlockPos.ZERO);
        if (neutralRange && hardness >= 0.8D && hardness <= 2.0D) {
            return 0.0D;
        }
        return Math.abs(1.7D - hardness);
    }

    private boolean isUnfavorable(ItemStack stack) {
        Block block = ((BlockItem) stack.getItem()).getBlock();
        BlockState state = block.defaultBlockState();
        return block.getFriction() > 0.6F
                || block.getSpeedFactor() < 1.0F
                || block.getJumpFactor() < 1.0F
                || block instanceof BaseEntityBlock
                || !state.isCollisionShapeFullBlock(mc.level, BlockPos.ZERO)
                || UNFAVORABLE_BLOCKS.contains(block);
    }

    private boolean isValidBlock(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)
                || noPlayer()) {
            return false;
        }
        Block block = blockItem.getBlock();
        BlockState state = block.defaultBlockState();
        return state.entityCanStandOnFace(mc.level, BlockPos.ZERO, mc.player, Direction.UP)
                && !(block instanceof FallingBlock)
                && !DISALLOWED_BLOCKS.contains(block);
    }

    private boolean handleBlockSelection(boolean mainHandBlock, boolean offHandBlock) {
        if (!autoBlock.get()) {
            restoreSelectedSlot();
            return mainHandBlock;
        }
        if (offHandBlock) {
            restoreSelectedSlot();
            return mainHandBlock;
        }
        if (mainHandBlock) {
            restoreSelectedSlot();
            return true;
        }

        int bestSlot = findBestHotbarSlot();
        if (bestSlot < 0) {
            restoreSelectedSlot();
            return false;
        }
        selectHotbarSlot(bestSlot);
        return true;
    }

    private void selectHotbarSlot(int slot) {
        int selected = mc.player.getInventory().getSelectedSlot();
        if (selectedOriginalSlot < 0) {
            selectedOriginalSlot = selected;
        }
        selectedScaffoldSlot = slot;
        slotResetTicks = slotResetDelay.get();
    }

    private void tickSelectedSlot() {
        if (!autoBlock.get()) {
            restoreSelectedSlot();
            return;
        }
        if (selectedOriginalSlot < 0) {
            return;
        }
        if (slotResetTicks > 0) {
            slotResetTicks--;
        } else if (!autoBlockAlways.get()) {
            restoreSelectedSlot();
        }
    }

    private void restoreSelectedSlot() {
        selectedOriginalSlot = -1;
        selectedScaffoldSlot = -1;
        slotResetTicks = 0;
    }

    private void simulatePlacementAttempt(
            BlockHitResult hit,
            InteractionHand hand) {
        if (!simulateAttempts.get() || hit == null || hand == null
                || hit.getType() != HitResult.Type.BLOCK
                || !playerMoving() || System.nanoTime() < nextSimulatedClick) {
            return;
        }

        ItemStack stack = mc.player.getItemInHand(hand);
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return;
        }
        UseOnContext useContext = new UseOnContext(mc.player, hand, hit);
        boolean canPlaceOnFace = blockItem.getPlacementState(new BlockPlaceContext(useContext)) != null;
        boolean shouldAttempt;
        if (simulateFailedOnly.get()) {
            shouldAttempt = !canPlaceOnFace;
        } else if (!sameY.is(SameYMode.OFF)
                && (!sameY.is(SameYMode.JUMP_KEY) || mc.options.keyJump.isDown())) {
            shouldAttempt = hit.getBlockPos().getY() == placementY
                    && (hit.getDirection() != Direction.UP || !canPlaceOnFace);
        } else {
            boolean underPlayer = hit.getBlockPos().getY() <= mc.player.getBlockY() - 1;
            boolean towering = hit.getBlockPos().getY() == mc.player.getBlockY() - 1
                    && canPlaceOnFace && hit.getDirection() == Direction.UP;
            shouldAttempt = underPlayer && !towering;
        }

        if (shouldAttempt) {
            int previousCount = stack.getCount();
            InteractionResult result = mc.gameMode.useItemOn(mc.player, hand, hit);
            boolean passed = result instanceof InteractionResult.Pass;
            if (passed) {
                useItemWithoutTarget(hand, stack);
            } else if (isClientHandledSuccess(result)) {
                swing(hand);
                BlockPos placed = hit.getBlockPos().relative(hit.getDirection());
                onBlockPlacement(placed, null, null);
            }
            if (!passed && !stack.isEmpty()
                    && (stack.getCount() != previousCount || mc.player.hasInfiniteMaterials())) {
                mc.gameRenderer.itemInHandRenderer.itemUsed(hand);
            }
        }
        int cps = Math.max(1, randomInt(simulateCpsMin.get(), simulateCpsMax.get()));
        nextSimulatedClick = System.nanoTime() + 1_000_000_000L / cps;
    }

    private void onBlockPlacement(
            BlockPos placed,
            ScaffoldMovementPlanner.Line placementLine,
            Vec3 previousFallOff) {
        movementPlanner.trackPlacedBlock(placed);
        movementPrediction.onPlace(placementLine, previousFallOff, prediction.get());
        wasPlacedTicks = 1;

        if (eagle.get()) {
            eaglePlacedBlocks++;
            if (eaglePlacedBlocks > eagleCurrentBlocks) {
                eaglePlacedBlocks = 0;
                eagleCurrentBlocks = randomInt(eagleBlocksMin.get(), eagleBlocksMax.get());
                eagleCurrentDistance = randomDouble(eagleDistanceMin.get(), eagleDistanceMax.get());
            }
        }
        if (render.get() && renderedPlacements.stream().noneMatch(info -> info.pos().equals(placed))) {
            renderedPlacements.add(new RenderInfo(placed.immutable(), System.currentTimeMillis()));
        }
        if (blink.get()) {
            blinkPulseTime = randomInt(blinkTimeMin.get(), blinkTimeMax.get());
            blinkPulseStarted = System.currentTimeMillis();
        }
        if (selectedOriginalSlot >= 0) {
            slotResetTicks = slotResetDelay.get();
        }
    }

    private void swing(InteractionHand hand) {
        switch (swing.get()) {
            case DO_NOT_HIDE -> mc.player.swing(hand);
            case HIDE_BOTH -> {
            }
            case HIDE_CLIENT -> mc.getConnection().send(new ServerboundSwingPacket(hand));
            case HIDE_SERVER -> mc.player.swing(hand, false);
        }
    }

    private static boolean isClientHandledSuccess(InteractionResult result) {
        if (!result.consumesAction()) {
            return false;
        }
        return !(result instanceof InteractionResult.Success success)
                || success.swingSource() == InteractionResult.SwingSource.CLIENT;
    }

    private void useItemWithoutTarget(InteractionHand hand, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        InteractionResult result = mc.gameMode.useItem(mc.player, hand);
        if (result instanceof InteractionResult.Success success) {
            if (success.swingSource() == InteractionResult.SwingSource.CLIENT) {
                swing(hand);
            }
            mc.gameRenderer.itemInHandRenderer.itemUsed(hand);
        }
    }

    private void sendRotationPacket(Rot2f rotation) {
        if (mc.getConnection() == null) {
            return;
        }
        mc.getConnection().send(new ServerboundMovePlayerPacket.PosRot(
                mc.player.getX(),
                mc.player.getY(),
                mc.player.getZ(),
                rotation.getYaw(),
                rotation.getPitch(),
                mc.player.onGround(),
                mc.player.horizontalCollision));
    }

    private Rot2f getScaffoldServerRotation() {
        if (scaffoldServerRotation != null) {
            return scaffoldServerRotation;
        }
        if (!noPlayer()) {
            return new Rot2f(mc.player.getYRot(), mc.player.getXRot());
        }
        return RotationManager.INSTANCE.getLastRotation();
    }

    private void trackScaffoldServerRotation(Packet<?> packet) {
        if (!(packet instanceof ServerboundMovePlayerPacket movement) || !movement.hasRotation()) {
            return;
        }
        Rot2f previous = getScaffoldServerRotation();
        scaffoldServerRotation = new Rot2f(
                movement.getYRot(previous.getYaw()),
                movement.getXRot(previous.getPitch()));
    }

    private boolean shouldFlushBlink(Packet<?> packet) {
        return (blinkFlushPlace.get() && packet instanceof ServerboundUseItemOnPacket)
                || (blinkFlushTowering.get() && isToweringWithoutMutation())
                || (blinkFlushSneaking.get() && mc.player.isShiftKeyDown())
                || (blinkFlushNotSneaking.get() && !mc.player.isShiftKeyDown())
                || (blinkFlushOnGround.get() && mc.player.onGround())
                || (blinkFlushInAir.get() && !mc.player.onGround());
    }

    private void tickBlinkPulse() {
        if (!blink.get()) {
            flushBlinkPackets();
            return;
        }
        if (!blinkPackets.isEmpty()
                && System.currentTimeMillis() - blinkPulseStarted >= blinkPulseTime) {
            flushBlinkPackets();
            blinkPulseStarted = System.currentTimeMillis();
        }
    }

    private void flushBlinkPackets() {
        if (flushingBlink) {
            return;
        }
        flushingBlink = true;
        try {
            Packet<?> packet;
            while ((packet = blinkPackets.poll()) != null) {
                PacketUtils.sendSilently(packet);
            }
        } finally {
            flushingBlink = false;
        }
    }

    private void captureTimerSpeed() {
        DeltaTracker tracker = mc.getDeltaTracker();
        if (tracker instanceof DeltaTrackerTimerAccessor accessor) {
            originalMsPerTick = accessor.setsuna$getMsPerTick();
        }
    }

    private void applyTimerSpeed() {
        DeltaTracker tracker = mc.getDeltaTracker();
        if (!(tracker instanceof DeltaTrackerTimerAccessor accessor)) {
            return;
        }
        if (Float.isNaN(originalMsPerTick)) {
            originalMsPerTick = accessor.setsuna$getMsPerTick();
        }
        double speed = timer.get();
        if (karhuTimerPulse) {
            speed = towerKarhuTimer.get();
        }
        accessor.setsuna$setMsPerTick((float) (originalMsPerTick / Math.max(0.01D, speed)));
        karhuTimerPulse = false;
    }

    private void restoreTimerSpeed() {
        DeltaTracker tracker = mc.getDeltaTracker();
        if (!Float.isNaN(originalMsPerTick) && tracker instanceof DeltaTrackerTimerAccessor accessor) {
            accessor.setsuna$setMsPerTick(originalMsPerTick);
        }
        originalMsPerTick = Float.NaN;
    }

    private void updateAutoSpeedState() {
        Speed.INSTANCE.setScaffoldActive(autoSpeed.get());
    }

    private void refreshRandomizedValues() {
        eagleCurrentBlocks = randomInt(eagleBlocksMin.get(), eagleBlocksMax.get());
        eagleCurrentDistance = randomDouble(eagleDistanceMin.get(), eagleDistanceMax.get());
        tellyCurrentJumpTicks = randomInt(tellyJumpMin.get(), tellyJumpMax.get());
        breezilyCurrentEdgeDistance = randomDouble(breezilyEdgeMin.get(), breezilyEdgeMax.get());
        safeCurrentEdgeDistance = randomDouble(safeEdgeDistanceMin.get(), safeEdgeDistanceMax.get());
        blinkPulseTime = 0L;
    }

    private void resetRuntimeState() {
        movementPlanner.reset();
        movementPrediction.reset();
        currentTarget = null;
        currentOptimalLine = null;
        currentTargetLine = null;
        nextBlock = null;
        rawForward = 0.0F;
        rawStrafe = 0.0F;
        scaffoldServerRotation = null;
        forceSneak = 0;
        placementCooldown = 0;
        wasTowering = false;
        airTicks = 0;
        selectedOriginalSlot = -1;
        selectedScaffoldSlot = -1;
        slotResetTicks = 0;
        eaglePlacedBlocks = 0;
        tellyTicksUntilJump = 0;
        headHitterCooldown = 0;
        movementTicks = 0;
        wasPlacedTicks = 0;
        godBridgeRightSide = false;
        movementInputSnapshot = null;
        breezilyLastSideways = 0.0F;
        breezilyLastAirTime = 0L;
        safeEdgeCenter = null;
        safeOverwriteTicks = 0;
        safeSneakTicks = 0;
        jumpOffPosition = Double.NaN;
        pulldownSequenceActive = false;
        karhuSequenceActive = false;
        karhuTimerPending = false;
        karhuTimerPulse = false;
        originalMsPerTick = Float.NaN;
        blinkPackets.clear();
        blinkPulseStarted = System.currentTimeMillis();
        nextSimulatedClick = 0L;
        renderedPlacements.clear();
    }

    private static Rot2f normalizeRotation(Rot2f rotation) {
        return rotation == null
                ? new Rot2f(0.0F, 0.0F)
                : new Rot2f(Mth.wrapDegrees(rotation.getYaw()), Mth.clamp(rotation.getPitch(), -90.0F, 90.0F));
    }

    private static double rotationDeltaSquared(Rot2f first, Rot2f second) {
        if (first == null || second == null) {
            return Double.POSITIVE_INFINITY;
        }
        double yaw = Mth.wrapDegrees(first.getYaw() - second.getYaw());
        double pitch = first.getPitch() - second.getPitch();
        return yaw * yaw + pitch * pitch;
    }

    private static int randomInt(int first, int second) {
        int min = Math.min(first, second);
        int max = Math.max(first, second);
        return min == max ? min : ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    private static double randomDouble(double first, double second) {
        double min = Math.min(first, second);
        double max = Math.max(first, second);
        return min == max ? min : ThreadLocalRandom.current().nextDouble(min, Math.nextUp(max));
    }

    private static Color withAlpha(Color color, float factor) {
        int alpha = Mth.clamp(Math.round(color.getAlpha() * Mth.clamp(factor, 0.0F, 1.0F)), 0, 255);
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    private record RenderInfo(BlockPos pos, long startTime) {
    }
}
