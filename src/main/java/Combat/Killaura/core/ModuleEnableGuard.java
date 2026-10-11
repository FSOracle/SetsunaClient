package Combat.Killaura.core;

import java.util.Objects;

@FunctionalInterface
public interface ModuleEnableGuard {
    EnableDecision evaluate(ModuleBase module);

    record EnableDecision(boolean permitted, String reason) {
        public EnableDecision {
            reason = Objects.requireNonNullElse(reason, "");
            if (permitted && !reason.isEmpty()) {
                throw new IllegalArgumentException("A permitted decision cannot contain a denial reason");
            }
        }

        public static EnableDecision permit() {
            return new EnableDecision(true, "");
        }

        public static EnableDecision deny(String reason) {
            String normalizedReason = Objects.requireNonNull(reason, "reason").trim();
            if (normalizedReason.isEmpty()) {
                throw new IllegalArgumentException("A denial reason is required");
            }
            return new EnableDecision(false, normalizedReason);
        }
    }
}
