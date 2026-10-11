package Combat.Killaura.core;

import Combat.Killaura.catalog.ModuleCategory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public abstract class ModuleBase {
    private final String internalName;
    private final String description;
    private final ModuleCategory category;
    private final Map<String, Setting<?>> settingsByName = new LinkedHashMap<>();
    private final Setting<Boolean> drawn;
    private final Setting<Keybind> keybind;
    private final Setting<String> displayName;
    private boolean enabled;
    private String lastEnableDenial = "";

    protected ModuleBase(String internalName, String description, ModuleCategory category) {
        this.internalName = requireText(internalName, "internalName");
        this.description = Objects.requireNonNullElse(description, "");
        this.category = Objects.requireNonNull(category, "category");
        this.drawn = this.addSetting(Setting.of("Drawn", true));
        this.keybind = this.addSetting(Setting.of("Keybind", Keybind.unbound()));
        this.displayName = this.addSetting(Setting.of("DisplayName", this.internalName));
    }

    public final String internalName() {
        return this.internalName;
    }

    public final String displayName() {
        return this.displayName.value();
    }

    public final String description() {
        return this.description;
    }

    public final ModuleCategory category() {
        return this.category;
    }

    public final boolean isEnabled() {
        return this.enabled;
    }

    public final boolean isDrawn() {
        return this.drawn.value();
    }

    public final Keybind keybind() {
        return this.keybind.value();
    }

    public final boolean setDisplayName(String newDisplayName) {
        return this.displayName.set(requireText(newDisplayName, "newDisplayName"));
    }

    public final boolean setDrawn(boolean newDrawn) {
        return this.drawn.set(newDrawn);
    }

    public final boolean setKeybind(Keybind newKeybind) {
        return this.keybind.set(Objects.requireNonNull(newKeybind, "newKeybind"));
    }

    public final String lastEnableDenial() {
        return this.lastEnableDenial;
    }

    public final List<Setting<?>> settings() {
        return Collections.unmodifiableList(new ArrayList<>(this.settingsByName.values()));
    }

    public final Setting<?> setting(String name) {
        return this.settingsByName.get(normalizeName(name));
    }

    public final boolean enable(ModuleEnableGuard guard) {
        Objects.requireNonNull(guard, "guard");
        if (this.enabled) {
            return true;
        }
        ModuleEnableGuard.EnableDecision decision = Objects.requireNonNull(guard.evaluate(this), "guard decision");
        if (!decision.permitted()) {
            this.lastEnableDenial = decision.reason();
            return false;
        }
        this.lastEnableDenial = "";
        this.enabled = true;
        try {
            this.onEnable();
            return true;
        } catch (RuntimeException | Error failure) {
            this.enabled = false;
            throw failure;
        }
    }

    public final void disable() {
        if (!this.enabled) {
            return;
        }
        this.enabled = false;
        this.onDisable();
    }

    public final boolean toggle(ModuleEnableGuard guard) {
        if (this.enabled) {
            this.disable();
            return false;
        }
        return this.enable(guard);
    }

    public final void resetSettings() {
        for (Setting<?> setting : this.settingsByName.values()) {
            reset(setting);
        }
        this.onSettingsReset();
    }

    public final String displayLabel() {
        String suffix = this.hudSuffix();
        return suffix == null || suffix.isBlank() ? this.displayName() : this.displayName() + " [" + suffix + "]";
    }

    protected final <T> Setting<T> addSetting(Setting<T> setting) {
        Objects.requireNonNull(setting, "setting");
        if (this.settingsByName.putIfAbsent(normalizeName(setting.name()), setting) != null) {
            throw new IllegalArgumentException("Duplicate setting name: " + setting.name());
        }
        setting.attachTo(this);
        return setting;
    }

    protected void onEnable() {
    }

    protected void onDisable() {
    }

    protected void onSettingsReset() {
    }

    protected void onSettingChanged(Setting<?> setting, Object oldValue, Object newValue) {
    }

    protected String hudSuffix() {
        return null;
    }

    void settingChanged(Setting<?> setting, Object oldValue, Object newValue) {
        this.onSettingChanged(setting, oldValue, newValue);
    }

    @SuppressWarnings("unchecked")
    private static <T> void reset(Setting<?> setting) {
        ((Setting<T>)setting).reset();
    }

    private static String requireText(String value, String fieldName) {
        String normalizedValue = Objects.requireNonNull(value, fieldName).trim();
        if (normalizedValue.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalizedValue;
    }

    private static String normalizeName(String name) {
        return Objects.requireNonNull(name, "name").toLowerCase(Locale.ROOT);
    }
}
