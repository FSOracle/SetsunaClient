package com.setsuna.script;

import org.luaj.vm2.LuaError;

/** Per-callback quotas and context checks for APIs that can mutate or scan game state. */
final class LuaExecutionGuard {

    private static final int MAX_ACTIONS = 64;
    private static final int MAX_EXPENSIVE_QUERIES = 4;
    private static final ThreadLocal<Frame> CURRENT = new ThreadLocal<>();

    private LuaExecutionGuard() {
    }

    static Scope enter(String event) {
        return enter(event, null);
    }

    static Scope enter(String event, Object owner) {
        Frame previous = CURRENT.get();
        CURRENT.set(new Frame(event, owner));
        return new Scope(previous);
    }

    static void consumeAction() {
        Frame frame = requireCallback();
        if (frame.event.equals("render2d") || frame.event.equals("render3d")
                || frame.event.equals("attack")) {
            throw new LuaError("Game actions are not allowed during " + frame.event);
        }
        if (++frame.actions > MAX_ACTIONS) {
            throw new LuaError("Action limit exceeded for one callback");
        }
    }

    static void consumeExpensiveQuery() {
        Frame frame = requireCallback();
        if (++frame.expensiveQueries > MAX_EXPENSIVE_QUERIES) {
            throw new LuaError("Expensive query limit exceeded for one callback");
        }
    }

    static void requireRuntimeCallback() {
        requireCallback();
    }

    static void requireEvent(String expected) {
        Frame frame = requireCallback();
        if (!frame.event.equals(expected)) {
            throw new LuaError("This API is only available during " + expected);
        }
    }

    static Object requireOwner() {
        Frame frame = requireCallback();
        if (frame.owner == null) {
            throw new LuaError("This API requires a module-owned callback");
        }
        return frame.owner;
    }

    private static Frame requireCallback() {
        Frame frame = CURRENT.get();
        if (frame == null) {
            throw new LuaError("This API is only available inside a module callback");
        }
        return frame;
    }

    private static final class Frame {
        private final String event;
        private final Object owner;
        private int actions;
        private int expensiveQueries;

        private Frame(String event, Object owner) {
            this.event = event;
            this.owner = owner;
        }
    }

    static final class Scope implements AutoCloseable {
        private final Frame previous;

        private Scope(Frame previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }
}
