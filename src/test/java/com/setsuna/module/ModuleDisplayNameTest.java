package com.setsuna.module;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class ModuleDisplayNameTest {

    @Test
    void displayNameOverridePersistsWithoutChangingStableIdentity() {
        TestModule module = new TestModule();
        module.setDisplayName("Aura Plus");

        JsonObject serialized = module.toJson();
        assertEquals("test_module", module.id());
        assertEquals("Aura Plus", serialized.get("displayName").getAsString());

        module.resetDisplayName();
        assertNull(module.displayNameOverride());
        module.fromJson(serialized);
        assertEquals("Aura Plus", module.displayName());
        assertEquals("test_module", module.id());
    }

    private static final class TestModule extends Module {
        private TestModule() {
            super("Test Module", Category.MISC);
        }
    }
}
