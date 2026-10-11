package com.setsuna.module.modules.combat.killaura;

/** Pure state machine for Exhibobo's three-tick Watchdog block sequence. */
public final class WatchdogAutoBlockState {

    private int blockTicks;

    public Step next(boolean canBlock, boolean velocityReducing) {
        if (!canBlock || velocityReducing) {
            blockTicks = 0;
            return Step.STOP;
        }
        return switch (blockTicks) {
            case 0 -> {
                blockTicks = 1;
                yield Step.ATTACK_BLOCK_BLINK;
            }
            case 1 -> {
                blockTicks = 2;
                yield Step.UNBLOCK_SUPPRESS_ATTACK;
            }
            case 2 -> {
                blockTicks = 0;
                yield Step.FLUSH_ATTACK;
            }
            default -> {
                blockTicks = 0;
                yield Step.STOP;
            }
        };
    }

    public void reset() {
        blockTicks = 0;
    }

    public int blockTicks() {
        return blockTicks;
    }

    public enum Step {
        STOP,
        ATTACK_BLOCK_BLINK,
        UNBLOCK_SUPPRESS_ATTACK,
        FLUSH_ATTACK
    }
}
