package com.setsuna.setting;

import com.setsuna.i18n.TranslationKey;
import com.setsuna.i18n.LocalizedText;
import com.google.gson.JsonElement;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * A single named, typed, persistable value owned by a {@link com.setsuna.module.Module}.
 * <p>
 * Concrete subclasses supply the JSON (de)serialization. Fluent configuration
 * methods ({@link #visibleWhen}, {@link #onChange}) return the concrete subtype
 * via a self-type cast so a typed field reference is preserved:
 * <pre>{@code
 * public final BooleanSetting fast = add(new BooleanSetting("Fast", false))
 *         .visibleWhen(mode::isSomething);
 * }</pre>
 *
 * @param <T> the value type
 */
public abstract class Setting<T> {

    private final String name;
    protected T value;
    protected final T defaultValue;

    private BooleanSupplier visibility = () -> true;
    private Consumer<T> onChange;
    private TranslationKey title;
    private String displayNameOverride;

    protected Setting(String name, T defaultValue) {
        this.name = name;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    /** The stable English name; also the basis for the i18n key slug. */
    public String name() {
        return name;
    }

    public T get() {
        return value;
    }

    /** Sets the value, notifying the change listener only on an actual change. */
    public void set(T newValue) {
        if (!Objects.equals(value, newValue)) {
            value = newValue;
            if (onChange != null) {
                onChange.accept(value);
            }
        }
    }

    public void reset() {
        set(defaultValue);
    }

    public T defaultValue() {
        return defaultValue;
    }

    /** Whether this setting should currently be shown in the GUI. */
    public boolean visible() {
        return visibility.getAsBoolean();
    }

    @SuppressWarnings("unchecked")
    public <S extends Setting<T>> S visibleWhen(BooleanSupplier predicate) {
        this.visibility = predicate;
        return (S) this;
    }

    /** Adds another visibility condition without discarding conditions already attached by the setting owner. */
    @SuppressWarnings("unchecked")
    public <S extends Setting<T>> S andVisibleWhen(BooleanSupplier predicate) {
        BooleanSupplier previous = this.visibility;
        this.visibility = () -> previous.getAsBoolean() && predicate.getAsBoolean();
        return (S) this;
    }

    /** Overrides only the GUI label; {@link #name()} remains the stable config and command key. */
    @SuppressWarnings("unchecked")
    public <S extends Setting<T>> S displayAs(String displayName) {
        this.displayNameOverride = displayName;
        return (S) this;
    }

    @SuppressWarnings("unchecked")
    public <S extends Setting<T>> S onChange(Consumer<T> listener) {
        this.onChange = listener;
        return (S) this;
    }

    /** Called once by the owning module to attach the resolved i18n key. */
    public void bindTitle(TranslationKey title) {
        this.title = title;
    }

    public TranslationKey title() {
        return title;
    }

    /** Localized label, falling back to the English name before i18n is bound. */
    public String displayName() {
        return LocalizedText.setting(title, name, displayNameOverride);
    }

    /** Serializes the current value for config storage. */
    public abstract JsonElement toJson();

    /** Restores the value from config storage; malformed input should be ignored. */
    public abstract void fromJson(JsonElement json);
}
