package com.setsuna.script;

import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaError;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LuaExecutionGuardTest {

    @Test
    void actionsAreForbiddenDuringRenderingAndAttackCallbacks() {
        for (String event : new String[]{"render2d", "render3d", "attack"}) {
            try (LuaExecutionGuard.Scope ignored = LuaExecutionGuard.enter(event)) {
                assertThrows(LuaError.class, LuaExecutionGuard::consumeAction);
            }
        }
    }

    @Test
    void callbackActionQuotaIsEnforced() {
        try (LuaExecutionGuard.Scope ignored = LuaExecutionGuard.enter("tick")) {
            for (int index = 0; index < 64; index++) {
                assertDoesNotThrow(LuaExecutionGuard::consumeAction);
            }
            assertThrows(LuaError.class, LuaExecutionGuard::consumeAction);
        }
    }

    @Test
    void expensiveQueryQuotaIsEnforced() {
        try (LuaExecutionGuard.Scope ignored = LuaExecutionGuard.enter("tick")) {
            for (int index = 0; index < 4; index++) {
                assertDoesNotThrow(LuaExecutionGuard::consumeExpensiveQuery);
            }
            assertThrows(LuaError.class, LuaExecutionGuard::consumeExpensiveQuery);
        }
    }

    @Test
    void eventSpecificApisRejectOtherAndStaleCallbacks() {
        try (LuaExecutionGuard.Scope ignored = LuaExecutionGuard.enter("render2d")) {
            assertDoesNotThrow(() -> LuaExecutionGuard.requireEvent("render2d"));
            assertThrows(LuaError.class, () -> LuaExecutionGuard.requireEvent("render3d"));
        }
        assertThrows(LuaError.class, () -> LuaExecutionGuard.requireEvent("render2d"));
    }

    @Test
    void moduleOwnedCallbacksExposeOnlyTheirCurrentOwner() {
        Object owner = new Object();
        try (LuaExecutionGuard.Scope ignored = LuaExecutionGuard.enter("tick", owner)) {
            assertSame(owner, LuaExecutionGuard.requireOwner());
        }
        try (LuaExecutionGuard.Scope ignored = LuaExecutionGuard.enter("tick")) {
            assertThrows(LuaError.class, LuaExecutionGuard::requireOwner);
        }
    }

    @Test
    void scriptSpecificAttackAndMessageRatesAreEnforced() {
        LuaScript attackScript = new LuaScript(Path.of("attacks.lua"));
        for (int index = 0; index < 20; index++) {
            assertDoesNotThrow(() -> attackScript.consumeAction(LuaScript.ActionKind.ATTACK));
        }
        assertThrows(LuaError.class,
                () -> attackScript.consumeAction(LuaScript.ActionKind.ATTACK));

        LuaScript messageScript = new LuaScript(Path.of("messages.lua"));
        for (int index = 0; index < 5; index++) {
            assertDoesNotThrow(() -> messageScript.consumeAction(LuaScript.ActionKind.MESSAGE));
        }
        assertThrows(LuaError.class,
                () -> messageScript.consumeAction(LuaScript.ActionKind.MESSAGE));
    }
}
