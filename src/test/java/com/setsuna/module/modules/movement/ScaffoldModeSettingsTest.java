package com.setsuna.module.modules.movement;

import com.google.gson.JsonPrimitive;
import com.setsuna.event.Listen;
import com.setsuna.setting.Setting;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
            mode.fromJson(new JsonPrimitive("HypixelTest"));
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
            mode.fromJson(new JsonPrimitive("HypixelTest"));
            hypixelCategory.fromJson(new JsonPrimitive("General"));
            legacyCategory.fromJson(new JsonPrimitive("General"));
            technique.fromJson(new JsonPrimitive("NORMAL"));
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
