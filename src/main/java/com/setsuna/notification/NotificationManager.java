package com.setsuna.notification;

import com.setsuna.ui.hud.Notifications;

import java.util.ArrayList;
import java.util.List;

/** Thread-safe notification queue; async auth/music workers may post here. */
public final class NotificationManager {

    public static final NotificationManager INSTANCE = new NotificationManager();

    private static final int QUEUE_LIMIT = 24;

    private final List<Entry> entries = new ArrayList<>();
    private final ThreadLocal<Integer> moduleFeedbackSuppression =
            ThreadLocal.withInitial(() -> 0);
    private boolean ready;

    private NotificationManager() {
    }

    /** Enables delivery after startup config restoration has completed. */
    public synchronized void start() {
        entries.clear();
        ready = true;
    }

    public synchronized void clear() {
        entries.clear();
    }

    public synchronized void post(NotificationType type, String title, String message) {
        if (!ready || !Notifications.INSTANCE.isEnabled()) return;

        String safeTitle = sanitize(title, "Setsuna");
        String safeMessage = sanitize(message, "Updated");
        long now = System.currentTimeMillis();
        if (!entries.isEmpty()) {
            Entry newest = entries.getFirst();
            if (newest.title.equals(safeTitle) && newest.message.equals(safeMessage)
                    && now - newest.createdAt < 350L) {
                return;
            }
        }

        entries.addFirst(new Entry(type == null ? NotificationType.INFO : type,
                safeTitle, safeMessage, now));
        while (entries.size() > QUEUE_LIMIT) {
            entries.removeLast();
        }
    }

    public synchronized void moduleState(String moduleName, boolean enabled) {
        if (moduleFeedbackSuppression.get() > 0 || !ready
                || !Notifications.INSTANCE.isEnabled() || !Notifications.INSTANCE.moduleState.get()) return;
        post(enabled ? NotificationType.SUCCESS : NotificationType.WARNING,
                moduleName, enabled ? "Enabled" : "Disabled");
    }

    public synchronized void moduleAction(String moduleName) {
        if (moduleFeedbackSuppression.get() > 0 || !ready
                || !Notifications.INSTANCE.isEnabled() || !Notifications.INSTANCE.moduleActions.get()) return;
        post(NotificationType.INFO, moduleName, "Opened");
    }

    public void withoutModuleFeedback(Runnable action) {
        int depth = moduleFeedbackSuppression.get();
        moduleFeedbackSuppression.set(depth + 1);
        try {
            action.run();
        } finally {
            if (depth == 0) {
                moduleFeedbackSuppression.remove();
            } else {
                moduleFeedbackSuppression.set(depth);
            }
        }
    }

    /** Returns newest-first visible entries and starts their timeout on first render. */
    public synchronized List<Entry> visible(long durationMillis, int limit) {
        long now = System.currentTimeMillis();
        long duration = Math.max(250L, durationMillis);
        entries.removeIf(entry -> entry.visibleAt > 0L && now - entry.visibleAt >= duration);

        int count = Math.min(Math.max(0, limit), entries.size());
        List<Entry> result = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            Entry entry = entries.get(index);
            if (entry.visibleAt == 0L) entry.visibleAt = now;
            result.add(entry);
        }
        return result;
    }

    private static String sanitize(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        return value.replace('\n', ' ').replace('\r', ' ').trim();
    }

    public static final class Entry {
        private final NotificationType type;
        private final String title;
        private final String message;
        private final long createdAt;
        private long visibleAt;

        private Entry(NotificationType type, String title, String message, long createdAt) {
            this.type = type;
            this.title = title;
            this.message = message;
            this.createdAt = createdAt;
        }

        public NotificationType type() {
            return type;
        }

        public String title() {
            return title;
        }

        public String message() {
            return message;
        }

        public long visibleAt() {
            return visibleAt;
        }
    }
}
