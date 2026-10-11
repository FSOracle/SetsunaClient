package Combat.Killaura.core;

import java.util.Objects;

public record Keybind(int keyCode, BindMode mode) {
    public static final int UNBOUND_KEY = -1;

    public Keybind {
        Objects.requireNonNull(mode, "mode");
    }

    public static Keybind unbound() {
        return new Keybind(UNBOUND_KEY, BindMode.TOGGLE);
    }

    public boolean isBound() {
        return this.keyCode != UNBOUND_KEY;
    }

    public Keybind withMode(BindMode newMode) {
        return new Keybind(this.keyCode, newMode);
    }
}
