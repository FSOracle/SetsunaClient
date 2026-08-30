package com.setsuna.script;

import org.luaj.vm2.Globals;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.luaj.vm2.LuaError;

final class LuaScript {

    enum ActionKind {
        GENERIC,
        ATTACK,
        MESSAGE
    }

    private static final long RATE_WINDOW_NANOS = 1_000_000_000L;
    private static final int MAX_ACTIONS_PER_SECOND = 80;
    private static final int MAX_ATTACKS_PER_SECOND = 20;
    private static final int MAX_MESSAGES_PER_SECOND = 5;

    private final Path file;
    private final List<LuaModule> modules = new ArrayList<>();
    private final Deque<Long> actions = new ArrayDeque<>();
    private final Deque<Long> attacks = new ArrayDeque<>();
    private final Deque<Long> messages = new ArrayDeque<>();
    private Globals globals;

    LuaScript(Path file) {
        this.file = file;
    }

    Path file() {
        return file;
    }

    String fileName() {
        return file.getFileName().toString();
    }

    List<LuaModule> modules() {
        return modules;
    }

    void addModule(LuaModule module) {
        if (modules.stream().anyMatch(existing -> existing.id().equals(module.id()))) {
            throw new IllegalArgumentException("Duplicate module id in script: " + module.id());
        }
        modules.add(module);
    }

    void globals(Globals globals) {
        this.globals = globals;
    }

    synchronized void consumeAction(ActionKind kind) {
        long now = System.nanoTime();
        prune(actions, now);
        prune(attacks, now);
        prune(messages, now);
        if (actions.size() >= MAX_ACTIONS_PER_SECOND) {
            throw new LuaError("Script action rate limit exceeded");
        }
        if (kind == ActionKind.ATTACK && attacks.size() >= MAX_ATTACKS_PER_SECOND) {
            throw new LuaError("Script attack rate limit exceeded");
        }
        if (kind == ActionKind.MESSAGE && messages.size() >= MAX_MESSAGES_PER_SECOND) {
            throw new LuaError("Script chat rate limit exceeded");
        }
        actions.addLast(now);
        if (kind == ActionKind.ATTACK) attacks.addLast(now);
        if (kind == ActionKind.MESSAGE) messages.addLast(now);
    }

    void close() {
        modules.clear();
        actions.clear();
        attacks.clear();
        messages.clear();
        globals = null;
    }

    private static void prune(Deque<Long> timestamps, long now) {
        while (!timestamps.isEmpty() && now - timestamps.peekFirst() >= RATE_WINDOW_NANOS) {
            timestamps.removeFirst();
        }
    }
}
