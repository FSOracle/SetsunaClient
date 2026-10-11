package com.setsuna.module.modules.combat;

import com.setsuna.module.modules.combat.killaura.WatchdogAutoBlockState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WatchdogAutoBlockStateTest {

    @Test
    void followsAttackBlockUnblockFlushSequence() {
        WatchdogAutoBlockState state = new WatchdogAutoBlockState();
        assertEquals(WatchdogAutoBlockState.Step.ATTACK_BLOCK_BLINK, state.next(true, false));
        assertEquals(WatchdogAutoBlockState.Step.UNBLOCK_SUPPRESS_ATTACK, state.next(true, false));
        assertEquals(WatchdogAutoBlockState.Step.FLUSH_ATTACK, state.next(true, false));
        assertEquals(0, state.blockTicks());
    }

    @Test
    void velocityReductionStopsAndRestartsTheSequence() {
        WatchdogAutoBlockState state = new WatchdogAutoBlockState();
        state.next(true, false);
        assertEquals(WatchdogAutoBlockState.Step.STOP, state.next(true, true));
        assertEquals(WatchdogAutoBlockState.Step.ATTACK_BLOCK_BLINK, state.next(true, false));
    }
}
