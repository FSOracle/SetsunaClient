package com.setsuna.util.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.lwjgl.glfw.GLFW;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/** A module input binding with LiquidBounce-compatible actions and modifiers. */
public record InputBind(int keyBind, BindAction action, Set<Modifier> modifiers) {

    public static final InputBind UNBOUND = new InputBind(
            KeybindUtils.NONE, BindAction.TOGGLE, Set.of());

    public InputBind {
        action = action == null ? BindAction.TOGGLE : action;
        if (modifiers == null || modifiers.isEmpty()) {
            modifiers = Set.of();
        } else {
            EnumSet<Modifier> copy = EnumSet.copyOf(modifiers);
            modifiers = Collections.unmodifiableSet(copy);
        }
    }

    public InputBind(int keyBind) {
        this(keyBind, BindAction.TOGGLE, Set.of());
    }

    public boolean isUnbound() {
        return keyBind == KeybindUtils.NONE;
    }

    public boolean matchesKey(int keyCode) {
        return !KeybindUtils.isMouseButton(keyBind) && keyBind == keyCode;
    }

    public boolean matchesMouse(int button) {
        return KeybindUtils.isMouseButton(keyBind)
                && KeybindUtils.decodeMouseButton(keyBind) == button;
    }

    public boolean matchesModifiers(int rawModifiers) {
        return modifiers.stream().allMatch(modifier -> modifier.isActive(rawModifiers));
    }

    /** Keyboard releases also affect a bind when one of its required modifiers is released. */
    public boolean isAffectedByKeyRelease(int keyCode) {
        if (matchesKey(keyCode)) {
            return true;
        }
        Modifier modifier = Modifier.fromKeyCode(keyCode);
        return modifier != null && modifiers.contains(modifier) && !modifier.isAnyPressed();
    }

    public InputBind withKey(int key) {
        if (key == KeybindUtils.NONE) {
            return UNBOUND;
        }
        return new InputBind(key, action, modifiers);
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("boundKey", keyBind);
        json.addProperty("action", action.tag());
        if (!modifiers.isEmpty()) {
            JsonArray array = new JsonArray();
            modifiers.forEach(modifier -> array.add(modifier.tag()));
            json.add("modifiers", array);
        }
        return json;
    }

    public static InputBind fromJson(JsonElement json) {
        if (json == null || json.isJsonNull()) {
            return UNBOUND;
        }
        if (json.isJsonPrimitive()) {
            return new InputBind(json.getAsInt());
        }
        JsonObject object = json.getAsJsonObject();
        int key = object.has("boundKey") ? object.get("boundKey").getAsInt() : KeybindUtils.NONE;
        BindAction action = object.has("action")
                ? BindAction.fromTag(object.get("action").getAsString())
                : BindAction.TOGGLE;
        EnumSet<Modifier> modifiers = EnumSet.noneOf(Modifier.class);
        if (object.has("modifiers") && object.get("modifiers").isJsonArray()) {
            for (JsonElement element : object.getAsJsonArray("modifiers")) {
                Modifier modifier = Modifier.fromTag(element.getAsString());
                if (modifier != null) {
                    modifiers.add(modifier);
                }
            }
        }
        return new InputBind(key, action, modifiers);
    }

    public String renderText() {
        StringBuilder text = new StringBuilder();
        for (Modifier modifier : modifiers) {
            if (!text.isEmpty()) {
                text.append(" + ");
            }
            text.append(modifier.displayName());
        }
        if (!text.isEmpty()) {
            text.append(" + ");
        }
        text.append(KeybindUtils.format(keyBind));
        return text.append(" (").append(action.tag()).append(')').toString();
    }

    public enum BindAction {
        TOGGLE("Toggle"),
        HOLD("Hold"),
        SMART("Smart");

        private final String tag;

        BindAction(String tag) {
            this.tag = tag;
        }

        public String tag() {
            return tag;
        }

        public static BindAction fromTag(String value) {
            if (value != null) {
                for (BindAction action : values()) {
                    if (action.tag.equalsIgnoreCase(value) || action.name().equalsIgnoreCase(value)) {
                        return action;
                    }
                }
            }
            return TOGGLE;
        }
    }

    public enum Modifier {
        SHIFT("Shift", GLFW.GLFW_MOD_SHIFT, "Shift",
                GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT),
        CONTROL("Control", GLFW.GLFW_MOD_CONTROL, "Ctrl",
                GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL),
        ALT("Alt", GLFW.GLFW_MOD_ALT, "Alt",
                GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT),
        SUPER("Super", GLFW.GLFW_MOD_SUPER, "Super",
                GLFW.GLFW_KEY_LEFT_SUPER, GLFW.GLFW_KEY_RIGHT_SUPER);

        private final String tag;
        private final int bitMask;
        private final String displayName;
        private final int[] keyCodes;

        Modifier(String tag, int bitMask, String displayName, int... keyCodes) {
            this.tag = tag;
            this.bitMask = bitMask;
            this.displayName = displayName;
            this.keyCodes = keyCodes;
        }

        public String tag() {
            return tag;
        }

        public String displayName() {
            return displayName;
        }

        public boolean isActive(int rawModifiers) {
            return (rawModifiers & bitMask) != 0;
        }

        public boolean isAnyPressed() {
            for (int keyCode : keyCodes) {
                if (KeybindUtils.isPressed(keyCode)) {
                    return true;
                }
            }
            return false;
        }

        public static Modifier fromTag(String value) {
            if (value != null) {
                String normalized = value.trim().toUpperCase(Locale.ROOT);
                for (Modifier modifier : values()) {
                    if (modifier.tag.toUpperCase(Locale.ROOT).equals(normalized)
                            || modifier.name().equals(normalized)
                            || modifier == CONTROL && normalized.equals("CTRL")) {
                        return modifier;
                    }
                }
            }
            return null;
        }

        public static Modifier fromKeyCode(int keyCode) {
            for (Modifier modifier : values()) {
                for (int candidate : modifier.keyCodes) {
                    if (candidate == keyCode) {
                        return modifier;
                    }
                }
            }
            return null;
        }
    }
}
