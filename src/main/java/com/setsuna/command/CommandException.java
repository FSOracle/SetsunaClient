package com.setsuna.command;

import java.util.List;

/** User-facing command failure with optional usage lines. */
public final class CommandException extends RuntimeException {

    private final List<String> usageInfo;

    public CommandException(String message) {
        this(message, null, List.of());
    }

    public CommandException(String message, List<String> usageInfo) {
        this(message, null, usageInfo);
    }

    public CommandException(String message, Throwable cause, List<String> usageInfo) {
        super(message, cause);
        this.usageInfo = List.copyOf(usageInfo);
    }

    public List<String> usageInfo() {
        return usageInfo;
    }
}
