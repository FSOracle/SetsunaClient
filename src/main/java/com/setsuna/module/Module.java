package com.setsuna.module;

import com.setsuna.Setsuna;
import com.setsuna.event.EventBus;
import com.setsuna.i18n.TranslationKey;
import com.setsuna.setting.Setting;
import com.setsuna.notification.NotificationManager;
import com.setsuna.util.StringUtil;
import com.setsuna.util.client.InputBind;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for every feature.
 * <p>
 * A module owns a list of {@link Setting}s and an optional keybind. While
 * enabled it is subscribed to the {@link EventBus}, so any {@code @Listen}
 * methods it declares receive events; {@link #onEnable()}/{@link #onDisable()}
 * bracket that lifecycle for one-off setup and teardown.
 */
public abstract class Module {

    protected final Minecraft mc = Setsuna.mc();

    private final String name;
    private final Category category;
    private final List<Setting<?>> settings = new ArrayList<>();

    private InputBind bind = InputBind.UNBOUND;
    private boolean enabled;
    private boolean hidden;
    private InputBind defaultBind = InputBind.UNBOUND;
    private boolean defaultEnabled;

    /**
     * When {@code false} the keybind and GUI click run {@link #onTrigger()}
     * instead of flipping {@link #enabled} — used by action-style modules such
     * as the GUI opener.
     */
    private boolean toggleable = true;

    private TranslationKey title;

    protected Module(String name, Category category) {
        this.name = name;
        this.category = category;
    }

    // --- setting registration -------------------------------------------------

    /** Registers a setting and returns it, for field-initializer use. */
    protected <T extends Setting<?>> T add(T setting) {
        settings.add(setting);
        return setting;
    }

    public List<Setting<?>> settings() {
        return settings;
    }

    // --- lifecycle ------------------------------------------------------------

    public boolean isEnabled() {
        return enabled;
    }

    public void toggle() {
        setEnabled(!enabled);
    }

    /** Runs the module's primary ClickGUI action without queuing module feedback. */
    public void interactFromClickGui() {
        NotificationManager.INSTANCE.withoutModuleFeedback(() -> {
            if (toggleable) {
                toggle();
            } else {
                trigger();
            }
        });
    }

    public final void setEnabled(boolean value) {
        if (enabled == value) {
            return;
        }
        boolean wasEnabled = enabled;
        beforeEnabledStateChange(value, wasEnabled);
        enabled = value;
        if (enabled) {
            EventBus.INSTANCE.subscribe(this);
            onEnable();
        } else {
            onDisable();
            EventBus.INSTANCE.unsubscribe(this);
        }
        afterEnabledStateChange(value, wasEnabled);
        NotificationManager.INSTANCE.moduleState(name(), enabled);
    }

    /** Invoked for non-toggleable modules when their keybind/button fires. */
    public void trigger() {
        onTrigger();
        NotificationManager.INSTANCE.moduleAction(name());
    }

    protected void onEnable() {
    }

    protected void onDisable() {
    }

    protected void onTrigger() {
    }

    protected void beforeEnabledStateChange(boolean value, boolean wasEnabled) {
    }

    protected void afterEnabledStateChange(boolean value, boolean wasEnabled) {
    }

    /** @return true when there is no player or level to act on. */
    protected boolean noPlayer() {
        return mc.player == null || mc.level == null;
    }

    // --- properties -----------------------------------------------------------

    public String name() {
        return name;
    }

    /** Stable identifier used by config and translation keys. */
    public String id() {
        return StringUtil.slug(name);
    }

    public Category category() {
        return category;
    }

    public int keyBind() {
        return bind.keyBind();
    }

    public void setKeyBind(int keyBind) {
        bind = bind.withKey(keyBind);
    }

    public InputBind bind() {
        return bind;
    }

    public void setBind(InputBind bind) {
        this.bind = bind == null ? InputBind.UNBOUND : bind;
    }

    public boolean isToggleable() {
        return toggleable;
    }

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    protected void setToggleable(boolean toggleable) {
        this.toggleable = toggleable;
    }

    protected void setDefaultKeyBind(int keyBind) {
        bind = new InputBind(keyBind);
        defaultBind = bind;
    }

    /** Captures constructor-defined defaults after the module is registered. */
    public void captureConfigDefaults() {
        defaultBind = bind;
        defaultEnabled = enabled;
    }

    /** Restores a deterministic base before a profile is applied. */
    public void resetConfig() {
        if (toggleable && enabled && !defaultEnabled) {
            setEnabled(false);
        }
        hidden = false;
        for (Setting<?> setting : settings) {
            try {
                setting.reset();
            } catch (RuntimeException error) {
                Setsuna.LOGGER.warn("Failed to reset setting {} for module {}",
                        setting.name(), id(), error);
            }
        }
        bind = defaultBind;
        if (toggleable && defaultEnabled && !enabled) {
            setEnabled(true);
        }
    }

    // --- i18n -----------------------------------------------------------------

    /** Called once at registration to build the {@code setsuna.module.<slug>} key tree. */
    public void bindI18n() {
        this.title = TranslationKey.of(Setsuna.MOD_ID + ".module." + id(), name);
        for (Setting<?> setting : settings) {
            setting.bindTitle(title.child(StringUtil.slug(setting.name()), setting.name()));
        }
    }

    public String displayName() {
        return title != null ? title.get() : name;
    }

    /** Optional short state text shown next to the module in HUDs. */
    public String getInfo() {
        return null;
    }

    // --- persistence ----------------------------------------------------------

    public JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("enabled", enabled);
        root.addProperty("keyBind", bind.keyBind());
        root.add("bind", bind.toJson());
        root.addProperty("hidden", hidden);
        JsonObject settingsObj = new JsonObject();
        for (Setting<?> setting : settings) {
            try {
                JsonElement value = setting.toJson();
                if (value != null) {
                    settingsObj.add(setting.name(), value);
                }
            } catch (RuntimeException error) {
                Setsuna.LOGGER.warn("Failed to serialize setting {} for module {}",
                        setting.name(), id(), error);
            }
        }
        root.add("settings", settingsObj);
        return root;
    }

    public void fromJson(JsonObject root) {
        if (root.has("bind")) {
            try {
                bind = InputBind.fromJson(root.get("bind"));
            } catch (RuntimeException error) {
                Setsuna.LOGGER.warn("Ignoring invalid keybind for module {}", id(), error);
            }
        } else if (root.has("keyBind") && root.get("keyBind").isJsonPrimitive()) {
            try {
                bind = new InputBind(root.get("keyBind").getAsInt());
            } catch (RuntimeException error) {
                Setsuna.LOGGER.warn("Ignoring invalid legacy keybind for module {}", id(), error);
            }
        }
        if (root.has("hidden") && root.get("hidden").isJsonPrimitive()) {
            try {
                hidden = root.get("hidden").getAsBoolean();
            } catch (RuntimeException error) {
                Setsuna.LOGGER.warn("Ignoring invalid hidden state for module {}", id(), error);
            }
        }
        if (root.has("settings") && root.get("settings").isJsonObject()) {
            JsonObject settingsObj = root.getAsJsonObject("settings");
            for (Setting<?> setting : settings) {
                if (settingsObj.has(setting.name())) {
                    try {
                        setting.fromJson(settingsObj.get(setting.name()));
                    } catch (RuntimeException error) {
                        Setsuna.LOGGER.warn("Ignoring invalid setting {} for module {}",
                                setting.name(), id(), error);
                    }
                }
            }
        }
        // Apply enabled state last, so onEnable() sees restored settings.
        if (toggleable && root.has("enabled") && root.get("enabled").isJsonPrimitive()) {
            try {
                setEnabled(root.get("enabled").getAsBoolean());
            } catch (RuntimeException error) {
                Setsuna.LOGGER.warn("Ignoring invalid enabled state for module {}", id(), error);
            }
        }
    }
}
