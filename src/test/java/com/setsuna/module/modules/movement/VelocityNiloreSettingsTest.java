package com.setsuna.module.modules.movement;

import com.google.gson.JsonPrimitive;
import com.setsuna.setting.Setting;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VelocityNiloreSettingsTest {

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void exposesOnlyTheSelectedHeypixelAntiKbSettings() {
        Velocity velocity = Velocity.INSTANCE;
        Setting<?> mode = setting(velocity, "Mode");
        Setting<?> niloreMode = setting(velocity, "Heypixel/Mode");
        Setting<?> follow = setting(velocity, "Heypixel/Follow Direction");

        try {
            mode.fromJson(new JsonPrimitive("Nilore"));
            assertEquals("Heypixel", mode.toJson().getAsString());
            assertTrue(niloreMode.legacyNames().contains("Nilore/Mode"));
            mode.fromJson(new JsonPrimitive("Heypixel"));
            niloreMode.fromJson(new JsonPrimitive("NoXZ"));
            assertVisible(velocity, "Heypixel/Mode", "Heypixel/Auto Attack Count", "Heypixel/Instant Attack");
            assertHidden(velocity, "Heypixel/Rotate", "Heypixel/Try Attack", "Heypixel/Follow Direction");

            niloreMode.fromJson(new JsonPrimitive("Mix"));
            assertVisible(velocity, "Heypixel/Rotate", "Heypixel/Try Attack", "Heypixel/Movement Override");
            assertHidden(velocity, "Heypixel/Auto Attack Count", "Heypixel/Follow Direction");

            niloreMode.fromJson(new JsonPrimitive("JumpReset"));
            follow.fromJson(new JsonPrimitive(false));
            assertVisible(velocity, "Heypixel/Rotate", "Heypixel/Follow Direction");
            assertHidden(velocity, "Heypixel/Rotate Ticks", "Heypixel/Try Attack");
            follow.fromJson(new JsonPrimitive(true));
            assertVisible(velocity, "Heypixel/Rotate Ticks");

            mode.fromJson(new JsonPrimitive("Cancel"));
            assertHidden(velocity, "Heypixel/Mode", "Heypixel/Debug Log");
        } finally {
            mode.fromJson(new JsonPrimitive("Cancel"));
            niloreMode.fromJson(new JsonPrimitive("NoXZ"));
            follow.fromJson(new JsonPrimitive(false));
        }
    }

    @Test
    void noXzAttackCountUsesDecodedVelocityThresholds() {
        assertEquals(9, NiloreVelocityEngine.attackCountFor(Vec3.ZERO, false, 9));
        assertEquals(0, NiloreVelocityEngine.attackCountFor(new Vec3(0.1D, 0.0D, 0.0D), true, 9));
        assertEquals(3, NiloreVelocityEngine.attackCountFor(new Vec3(0.2D, 0.0D, 0.0D), true, 9));
        assertEquals(4, NiloreVelocityEngine.attackCountFor(new Vec3(0.4D, 0.0D, 0.0D), true, 9));
        assertEquals(5, NiloreVelocityEngine.attackCountFor(new Vec3(1.3D, 0.0D, 0.0D), true, 9));
    }

    @Test
    void knockbackYawMatchesNiloreDirectionConvention() {
        assertEquals(90.0F, NiloreVelocityEngine.knockbackYaw(new Vec3(1.0D, 0.0D, 0.0D)), 0.001F);
        assertEquals(0.0F, NiloreVelocityEngine.knockbackYaw(new Vec3(0.0D, 0.0D, -1.0D)), 0.001F);
    }

    private static Setting<?> setting(Velocity module, String name) {
        return module.settings().stream()
                .filter(setting -> setting.name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private static Set<String> visibleNames(Velocity module) {
        return module.settings().stream()
                .filter(Setting::visible)
                .map(Setting::name)
                .collect(Collectors.toSet());
    }

    private static void assertVisible(Velocity module, String... names) {
        Set<String> visible = visibleNames(module);
        for (String name : names) {
            assertTrue(visible.contains(name), () -> name + " should be visible, got " + visible);
        }
    }

    private static void assertHidden(Velocity module, String... names) {
        Set<String> visible = visibleNames(module);
        for (String name : names) {
            assertFalse(visible.contains(name), () -> name + " should be hidden, got " + visible);
        }
    }
}
