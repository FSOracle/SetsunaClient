package com.setsuna.util.client;

import java.util.HashSet;
import java.util.Set;

/**
 * Reference-counted request to suppress view bobbing. Multiple modules can hold
 * the suppression at once; bobbing is disabled while any owner is registered.
 * The actual suppression is applied by a camera/bobbing mixin querying
 * {@link #isSuppressed()} (wired up alongside the render modules that need it).
 */
public final class ViewBobbingSuppressor {

    private static final Set<String> owners = new HashSet<>();

    private ViewBobbingSuppressor() {
    }

    public static void acquire(String owner) {
        if (owner != null && !owner.isBlank()) {
            owners.add(owner);
        }
    }

    public static void release(String owner) {
        if (owner != null && !owner.isBlank()) {
            owners.remove(owner);
        }
    }

    public static boolean isSuppressed() {
        return !owners.isEmpty();
    }
}
