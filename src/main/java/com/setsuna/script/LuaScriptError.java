package com.setsuna.script;

import java.nio.file.Path;
import java.time.Instant;

/** A load or callback failure retained for the {@code .lua errors} command. */
public record LuaScriptError(Path file, String phase, String message, Instant time) {
}
