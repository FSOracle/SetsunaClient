package com.setsuna.module.modules.movement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VelocityHypixelDecisionTest {

    @Test
    void reductionFormulaMatchesReferenceAndBounds() {
        assertEquals(3, Velocity.computeHypixelReduceTicks(0, 0));
        assertEquals(10, Velocity.computeHypixelReduceTicks(20000, 20000));
        assertEquals(9, Velocity.computeHypixelReduceTicks(-10000, 0));
    }
}
