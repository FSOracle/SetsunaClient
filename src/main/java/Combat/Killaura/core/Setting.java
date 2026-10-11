package Combat.Killaura.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.BooleanSupplier;
import java.util.function.UnaryOperator;

public final class Setting<T> {
    private final String name;
    private final T defaultValue;
    private final T minimum;
    private final T maximum;
    private final UnaryOperator<T> normalizer;
    private final List<SettingChangeListener<T>> listeners = new ArrayList<>();
    private T value;
    private BooleanSupplier visibility = () -> true;
    private String suffix = "";
    private String description = "";
    private ModuleBase owner;

    private Setting(String name, T defaultValue, T minimum, T maximum, UnaryOperator<T> normalizer) {
        this.name = requireName(name);
        this.defaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
        this.minimum = minimum;
        this.maximum = maximum;
        this.normalizer = Objects.requireNonNull(normalizer, "normalizer");
        this.value = this.normalizer.apply(defaultValue);
    }

    public static <T> Setting<T> of(String name, T defaultValue) {
        return new Setting<>(name, defaultValue, null, null, UnaryOperator.identity());
    }

    public static <T> Setting<T> ranged(
            String name,
            T defaultValue,
            T minimum,
            T maximum,
            Comparator<? super T> comparator) {
        Objects.requireNonNull(minimum, "minimum");
        Objects.requireNonNull(maximum, "maximum");
        Objects.requireNonNull(comparator, "comparator");
        if (comparator.compare(minimum, maximum) > 0) {
            throw new IllegalArgumentException("minimum must not exceed maximum");
        }
        return new Setting<>(name, defaultValue, minimum, maximum, value -> {
            if (comparator.compare(value, minimum) < 0) {
                return minimum;
            }
            if (comparator.compare(value, maximum) > 0) {
                return maximum;
            }
            return value;
        });
    }

    public String name() {
        return this.name;
    }

    public T value() {
        return this.value;
    }

    public T defaultValue() {
        return this.defaultValue;
    }

    public T minimum() {
        return this.minimum;
    }

    public T maximum() {
        return this.maximum;
    }

    public boolean isRanged() {
        return this.minimum != null && this.maximum != null;
    }

    public String suffix() {
        return this.suffix;
    }

    public String description() {
        return this.description;
    }

    public boolean isVisible() {
        return this.visibility.getAsBoolean();
    }

    public boolean set(T newValue) {
        T normalizedValue = this.normalizer.apply(Objects.requireNonNull(newValue, "newValue"));
        T oldValue = this.value;
        for (SettingChangeListener<T> listener : List.copyOf(this.listeners)) {
            if (!listener.beforeChange(this, oldValue, normalizedValue)) {
                return false;
            }
        }
        this.value = normalizedValue;
        if (this.owner != null) {
            this.owner.settingChanged(this, oldValue, normalizedValue);
        }
        return true;
    }

    public boolean reset() {
        return this.set(this.defaultValue);
    }

    public Setting<T> visibleWhen(Predicate<T> visibility) {
        Predicate<T> valuePredicate = Objects.requireNonNull(visibility, "visibility");
        this.visibility = () -> valuePredicate.test(this.value);
        return this;
    }

    public Setting<T> visibleWhen(BooleanSupplier visibility) {
        this.visibility = Objects.requireNonNull(visibility, "visibility");
        return this;
    }

    public Setting<T> withSuffix(String suffix) {
        this.suffix = Objects.requireNonNullElse(suffix, "");
        return this;
    }

    public Setting<T> describedAs(String description) {
        this.description = Objects.requireNonNullElse(description, "");
        return this;
    }

    public Setting<T> onChange(SettingChangeListener<T> listener) {
        this.listeners.add(Objects.requireNonNull(listener, "listener"));
        return this;
    }

    void attachTo(ModuleBase newOwner) {
        Objects.requireNonNull(newOwner, "newOwner");
        if (this.owner != null && this.owner != newOwner) {
            throw new IllegalStateException("Setting already belongs to another module: " + this.name);
        }
        this.owner = newOwner;
    }

    private static String requireName(String name) {
        String normalizedName = Objects.requireNonNull(name, "name").trim();
        if (normalizedName.isEmpty()) {
            throw new IllegalArgumentException("Setting name must not be blank");
        }
        return normalizedName;
    }
}
