package com.setsuna.module.modules.movement;

import com.google.gson.JsonPrimitive;
import com.setsuna.event.Listen;
import com.setsuna.setting.Setting;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;

class ScaffoldModeSettingsTest {

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void modeAndCategoryExposeOnlyTheirOwnSettings() {
        Scaffold scaffold = Scaffold.INSTANCE;
        Setting<?> mode = setting(scaffold, "Mode");
        Setting<?> hypixelCategory = setting(scaffold, "Hypixel Category");
        Setting<?> legacyCategory = setting(scaffold, "Legacy Category");
        Setting<?> technique = setting(scaffold, "Technique");

        try {
            mode.fromJson(new JsonPrimitive("Hypixel"));
            hypixelCategory.fromJson(new JsonPrimitive("General"));
            assertVisible(scaffold, "Hypixel Category", "Delay/Min", "SameY");
            assertHidden(scaffold, "Legacy Category", "Swap Mode", "Normal/Eagle/Enabled");

            hypixelCategory.fromJson(new JsonPrimitive("Technique"));
            assertVisible(scaffold, "Technique", "Normal/Eagle/Enabled");
            assertHidden(scaffold, "GodBridge/Modes/Jump", "Delay/Min", "Tower/Motion/Motion", "Swap Mode");

            technique.fromJson(new JsonPrimitive("GOD_BRIDGE"));
            assertVisible(scaffold, "Technique", "GodBridge/Modes/Jump");
            assertHidden(scaffold, "Normal/Eagle/Enabled", "Delay/Min", "Swap Mode");

            hypixelCategory.fromJson(new JsonPrimitive("Placement"));
            assertVisible(scaffold, "Rotations/ShowModel");
            assertHidden(scaffold, "Delay/Min", "Normal/Eagle/Enabled", "Render/Enabled");

            mode.fromJson(new JsonPrimitive("TellyBridge"));
            legacyCategory.fromJson(new JsonPrimitive("General"));
            assertVisible(scaffold, "Legacy Category", "Skip Ticks", "Telly Ticks");
            assertHidden(scaffold, "Hypixel Category", "Snap", "Delay/Min", "Swap Mode");

            mode.fromJson(new JsonPrimitive("GodBridge"));
            assertVisible(scaffold, "Snap", "Keep Y");
            assertHidden(scaffold, "Telly Ticks", "Rotation Back Speed", "Delay/Min");
        } finally {
            mode.fromJson(new JsonPrimitive("Hypixel"));
            hypixelCategory.fromJson(new JsonPrimitive("General"));
            legacyCategory.fromJson(new JsonPrimitive("General"));
            technique.fromJson(new JsonPrimitive("NORMAL"));
        }
    }

    @Test
    void heypixelModeKeepsItsBridgeAndRotationBranchesIsolated() {
        Scaffold scaffold = Scaffold.INSTANCE;
        Setting<?> mode = setting(scaffold, "Mode");
        Setting<?> niloreMode = setting(scaffold, "Heypixel/Mode");
        Setting<?> syncRotation = setting(scaffold, "Heypixel/Sync RotSpeed");

        try {
            mode.fromJson(new JsonPrimitive("Nilore"));
            assertEquals("Heypixel", mode.toJson().getAsString());
            assertTrue(niloreMode.legacyNames().contains("Nilore/Mode"));
            mode.fromJson(new JsonPrimitive("Heypixel"));
            niloreMode.fromJson(new JsonPrimitive("Normal"));
            syncRotation.fromJson(new JsonPrimitive(false));
            assertVisible(scaffold,
                    "Heypixel/Mode",
                    "Heypixel/Eagle",
                    "Heypixel/Rotation Speed",
                    "Heypixel/Switch Mode");
            assertHidden(scaffold,
                    "Heypixel/AirTicks",
                    "Heypixel/Turn Speed",
                    "Hypixel Category",
                    "Legacy Category");

            niloreMode.fromJson(new JsonPrimitive("TellyBridge"));
            assertVisible(scaffold, "Heypixel/AirTicks", "Heypixel/PlaceDelay");
            assertHidden(scaffold, "Heypixel/Eagle");

            syncRotation.fromJson(new JsonPrimitive(true));
            assertVisible(scaffold, "Heypixel/Turn Speed", "Heypixel/Return Speed");
            assertHidden(scaffold, "Heypixel/Rotation Speed");
        } finally {
            mode.fromJson(new JsonPrimitive("Hypixel"));
            niloreMode.fromJson(new JsonPrimitive("Normal"));
            syncRotation.fromJson(new JsonPrimitive(false));
        }
    }

    @Test
    void heypixelRenderSpoofDoesNotReplaceTheLogicalMainHandStack() {
        Scaffold scaffold = Scaffold.INSTANCE;
        Setting<?> mode = setting(scaffold, "Mode");
        boolean wasEnabled = scaffold.isEnabled();
        try {
            if (wasEnabled) {
                scaffold.setEnabled(false);
            }
            mode.fromJson(new JsonPrimitive("Heypixel"));
            scaffold.setEnabled(true);
            ItemStack logicalStack = ItemStack.EMPTY;
            assertSame(logicalStack, scaffold.modifyMainHandStack(logicalStack));
        } finally {
            scaffold.setEnabled(false);
            mode.fromJson(new JsonPrimitive("Hypixel"));
            if (wasEnabled) {
                scaffold.setEnabled(true);
            }
        }
    }

    @Test
    void disablingScaffoldReleasesRotationForEveryImplementation() throws ReflectiveOperationException {
        Scaffold scaffold = Scaffold.INSTANCE;
        Field runningMode = Scaffold.class.getDeclaredField("runningMode");
        Field legacyEngine = Scaffold.class.getDeclaredField("legacyEngine");
        Field heypixelEngine = Scaffold.class.getDeclaredField("niloreEngine");
        Field rotationOwner = com.setsuna.manager.RotationManager.class.getDeclaredField("rotationOwner");
        Field rotationActive = com.setsuna.manager.RotationManager.class.getDeclaredField("active");
        runningMode.setAccessible(true);
        legacyEngine.setAccessible(true);
        heypixelEngine.setAccessible(true);
        rotationOwner.setAccessible(true);
        rotationActive.setAccessible(true);

        Object[] modes = runningMode.getType().getEnumConstants();
        Object legacyOwner = legacyEngine.get(scaffold);
        Object heypixelOwner = heypixelEngine.get(scaffold);
        try {
            assertDisableReleases(scaffold, runningMode, rotationOwner, rotationActive,
                    enumValue(modes, "Hypixel"), scaffold);
            assertDisableReleases(scaffold, runningMode, rotationOwner, rotationActive,
                    enumValue(modes, "TellyBridge"), legacyOwner);
            assertDisableReleases(scaffold, runningMode, rotationOwner, rotationActive,
                    enumValue(modes, "Heypixel"), heypixelOwner);
        } finally {
            runningMode.set(scaffold, null);
            com.setsuna.manager.RotationManager.INSTANCE.setActive(false);
        }
    }

    @Test
    void targetIsPreparedBeforeFeatureStateAndPlacement() throws NoSuchMethodException {
        Listen placementState = Scaffold.class
                .getDeclaredMethod("onPlacementStateTick", com.setsuna.event.events.PlayerTickEvent.Pre.class)
                .getAnnotation(Listen.class);
        Listen placement = Scaffold.class
                .getDeclaredMethod("onPlacementTick", com.setsuna.event.events.PlayerTickEvent.Pre.class)
                .getAnnotation(Listen.class);
        Listen targetUpdate = Scaffold.class
                .getDeclaredMethod("onTargetUpdate", com.setsuna.event.events.PlayerTickEvent.Pre.class)
                .getAnnotation(Listen.class);

        assertTrue(targetUpdate.priority() > placementState.priority());
        assertTrue(placementState.priority() > placement.priority());
    }

    @Test
    void movementModelIsCapturedBeforeStabilizationAndSimulation() throws NoSuchMethodException {
        Listen model = listener("onModelMovementInput");
        Listen stabilize = listener("onStabilizedMovementInput");
        Listen simulation = listener("onMovementInput");
        Listen safety = listener("onSafetyMovementInput");

        assertTrue(model.priority() > stabilize.priority());
        assertTrue(stabilize.priority() > simulation.priority());
        assertTrue(simulation.priority() > safety.priority());
    }

    @Test
    void ledgeDoesNotSneakWhenRotationCanFinishThisTick() {
        assertEquals(0, Scaffold.remainingRotationTicks(180.0D, 180.0D));
        assertEquals(0, Scaffold.remainingRotationTicks(0.01D, 180.0D));
        assertEquals(1, Scaffold.remainingRotationTicks(180.01D, 180.0D));
    }

    private static Listen listener(String method) throws NoSuchMethodException {
        return Scaffold.class
                .getDeclaredMethod(method, com.setsuna.event.events.KeyboardInputEvent.class)
                .getAnnotation(Listen.class);
    }

    @Test
    void hypixelScaffoldExposesTellyKeepYAndTowerModes() {
        Scaffold scaffold = Scaffold.INSTANCE;
        Setting<?> sameY = setting(scaffold, "SameY");
        Setting<?> tower = setting(scaffold, "Tower");

        sameY.fromJson(new JsonPrimitive("TELLY"));
        tower.fromJson(new JsonPrimitive("TELLY"));

        assertEquals("TELLY", sameY.toJson().getAsString());
        assertEquals("TELLY", tower.toJson().getAsString());

        sameY.fromJson(new JsonPrimitive("OFF"));
        tower.fromJson(new JsonPrimitive("NONE"));
    }

    private static Object enumValue(Object[] values, String name) {
        for (Object value : values) {
            if (((Enum<?>) value).name().equals(name)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Missing scaffold mode " + name);
    }

    private static void assertDisableReleases(
            Scaffold scaffold,
            Field runningMode,
            Field rotationOwner,
            Field rotationActive,
            Object mode,
            Object owner) throws IllegalAccessException {
        runningMode.set(scaffold, mode);
        rotationOwner.set(com.setsuna.manager.RotationManager.INSTANCE, owner);
        rotationActive.setBoolean(com.setsuna.manager.RotationManager.INSTANCE, true);

        scaffold.releaseRotationsOnDisable();

        assertFalse(com.setsuna.manager.RotationManager.INSTANCE.isActive(),
                () -> mode + " should release its rotation when Scaffold closes");
    }

    private static Setting<?> setting(Scaffold scaffold, String name) {
        return scaffold.settings().stream()
                .filter(setting -> setting.name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private static void assertVisible(Scaffold scaffold, String... names) {
        Set<String> visible = visibleNames(scaffold);
        for (String name : names) {
            assertTrue(visible.contains(name), () -> name + " should be visible, got " + visible);
        }
    }

    private static void assertHidden(Scaffold scaffold, String... names) {
        Set<String> visible = visibleNames(scaffold);
        for (String name : names) {
            assertFalse(visible.contains(name), () -> name + " should be hidden, got " + visible);
        }
    }

    private static Set<String> visibleNames(Scaffold scaffold) {
        return scaffold.settings().stream()
                .filter(Setting::visible)
                .map(Setting::name)
                .collect(Collectors.toSet());
    }
}
