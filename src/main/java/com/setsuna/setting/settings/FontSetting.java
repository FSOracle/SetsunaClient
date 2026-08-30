package com.setsuna.setting.settings;

import java.util.List;
import java.util.function.Supplier;

/** A persisted string choice whose options can change when font files are imported. */
public final class FontSetting extends StringSetting {

    private final Supplier<List<String>> optionsSupplier;

    public FontSetting(String name, String defaultValue, Supplier<List<String>> optionsSupplier) {
        super(name, defaultValue);
        this.optionsSupplier = optionsSupplier;
    }

    public List<String> options() {
        List<String> options = optionsSupplier.get();
        return options == null || options.isEmpty() ? List.of(defaultValue()) : List.copyOf(options);
    }

    public void cycle(int direction) {
        List<String> options = options();
        int current = indexOf(options, get());
        int next = Math.floorMod((current < 0 ? 0 : current) + direction, options.size());
        set(options.get(next));
    }

    public void reconcile() {
        List<String> options = options();
        int current = indexOf(options, get());
        set(current >= 0 ? options.get(current) : defaultValue());
    }

    public String displayValue() {
        return get();
    }

    @Override
    public void set(String newValue) {
        List<String> options = options();
        int index = indexOf(options, newValue);
        super.set(index >= 0 ? options.get(index) : defaultValue());
    }

    private static int indexOf(List<String> options, String value) {
        if (value == null) return -1;
        for (int index = 0; index < options.size(); index++) {
            if (options.get(index).equalsIgnoreCase(value)) return index;
        }
        return -1;
    }
}
