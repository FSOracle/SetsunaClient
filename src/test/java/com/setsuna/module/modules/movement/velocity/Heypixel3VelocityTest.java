package com.setsuna.module.modules.movement.velocity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Heypixel3VelocityTest {

    @Test
    void attackCountRequiresForwardSprintWithoutSneakingOrUsingItem() {
        assertTrue(Heypixel3Velocity.canTriggerAttackCountNow(true, false, 1.0F, false));
        assertFalse(Heypixel3Velocity.canTriggerAttackCountNow(false, false, 1.0F, false));
        assertFalse(Heypixel3Velocity.canTriggerAttackCountNow(true, true, 1.0F, false));
        assertFalse(Heypixel3Velocity.canTriggerAttackCountNow(true, false, 0.0F, false));
        assertFalse(Heypixel3Velocity.canTriggerAttackCountNow(true, false, -1.0F, false));
        assertFalse(Heypixel3Velocity.canTriggerAttackCountNow(true, false, 1.0F, true));
    }
}
