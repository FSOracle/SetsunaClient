package tritium.utils.json;

import com.google.gson.*;
import lombok.experimental.UtilityClass;

import java.io.Reader;

/**
 * @author IzumiiKonata
 * @since 2024/11/10 16:59
 */
@UtilityClass
public class JsonUtils {

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public JsonObject toJsonObject(String json) {
        return gson.fromJson(json, JsonObject.class);
    }

    public JsonObject toJsonObject(Reader reader) {
        return gson.fromJson(reader, JsonObject.class);
    }

    public JsonObject toJsonObject(JsonElement element) {
        return gson.fromJson(element, JsonObject.class);
    }

    public JsonArray toJsonArray(String json) {
        return gson.fromJson(json, JsonArray.class);
    }

    public JsonArray toJsonArray(Reader reader) {
        return gson.fromJson(reader, JsonArray.class);
    }

    public JsonArray toJsonArray(JsonElement element) {
        return gson.fromJson(element, JsonArray.class);
    }

    public <T> T parse(String json, Class<? extends T> typeClass) {
        return gson.fromJson(json, typeClass);
    }

    public <T> T parse(Reader reader, Class<? extends T> typeClass) {
        return gson.fromJson(reader, typeClass);
    }

    public <T> T parse(JsonElement element, Class<? extends T> typeClass) {
        return gson.fromJson(element, typeClass);
    }

    public String toJsonString(JsonElement element) {
        return gson.toJson(element);
    }

    public String toJsonString(Object src) {
        return gson.toJson(src);
    }

    /**
     * Does the given JsonObject contain a string field with the given name?
     */
    public static boolean isString(JsonObject jsonObject, String key) {
        return isJsonPrimitive(jsonObject, key) && jsonObject.getAsJsonPrimitive(key).isString();
    }

    /**
     * Is the given JsonElement a string?
     */
    public static boolean isString(JsonElement jsonElement) {
        return jsonElement.isJsonPrimitive() && jsonElement.getAsJsonPrimitive().isString();
    }

    public static boolean isBoolean(JsonObject jsonObject, String key) {
        return isJsonPrimitive(jsonObject, key) && jsonObject.getAsJsonPrimitive(key).isBoolean();
    }

    /**
     * Does the given JsonObject contain an array field with the given name?
     */
    public static boolean isJsonArray(JsonObject jsonObject, String key) {
        return hasField(jsonObject, key) && jsonObject.get(key).isJsonArray();
    }

    /**
     * Does the given JsonObject contain a field with the given name whose type is primitive (String, Java primitive, or
     * Java primitive wrapper)?
     */
    public static boolean isJsonPrimitive(JsonObject jsonObject, String key) {
        return hasField(jsonObject, key) && jsonObject.get(key).isJsonPrimitive();
    }

    /**
     * Does the given JsonObject contain a field with the given name?
     */
    public static boolean hasField(JsonObject jsonObject, String key) {
        return jsonObject != null && jsonObject.get(key) != null;
    }

    /**
     * Gets the string value of the given JsonElement.  Expects the second parameter to be the name of the element's
     * field if an error message needs to be thrown.
     */
    public static String getString(JsonElement jsonElement, String key) {
        if (jsonElement.isJsonPrimitive()) {
            return jsonElement.getAsString();
        } else {
            throw new JsonSyntaxException("Expected " + key + " to be a string, was " + toString(jsonElement));
        }
    }

    /**
     * Gets the string value of the field on the JsonObject with the given name.
     */
    public static String getString(JsonObject jsonObject, String key) {
        if (jsonObject.has(key)) {
            return getString(jsonObject.get(key), key);
        } else {
            throw new JsonSyntaxException("Missing " + key + ", expected to find a string");
        }
    }

    /**
     * Gets the string value of the field on the JsonObject with the given name, or the given default value if the field
     * is missing.
     */
    public static String getString(JsonObject jsonObject, String key, String fallback) {
        return jsonObject.has(key) ? getString(jsonObject.get(key), key) : fallback;
    }

    /**
     * Gets the boolean value of the given JsonElement.  Expects the second parameter to be the name of the element's
     * field if an error message needs to be thrown.
     */
    public static boolean getBoolean(JsonElement jsonElement, String key) {
        if (jsonElement.isJsonPrimitive()) {
            return jsonElement.getAsBoolean();
        } else {
            throw new JsonSyntaxException("Expected " + key + " to be a Boolean, was " + toString(jsonElement));
        }
    }

    /**
     * Gets the boolean value of the field on the JsonObject with the given name.
     */
    public static boolean getBoolean(JsonObject jsonObject, String key) {
        if (jsonObject.has(key)) {
            return getBoolean(jsonObject.get(key), key);
        } else {
            throw new JsonSyntaxException("Missing " + key + ", expected to find a Boolean");
        }
    }

    /**
     * Gets the boolean value of the field on the JsonObject with the given name, or the given default value if the
     * field is missing.
     */
    public static boolean getBoolean(JsonObject jsonObject, String key, boolean fallback) {
        return jsonObject.has(key) ? getBoolean(jsonObject.get(key), key) : fallback;
    }

    /**
     * Gets the float value of the given JsonElement.  Expects the second parameter to be the name of the element's
     * field if an error message needs to be thrown.
     */
    public static float getFloat(JsonElement jsonElement, String key) {
        if (jsonElement.isJsonPrimitive() && jsonElement.getAsJsonPrimitive().isNumber()) {
            return jsonElement.getAsFloat();
        } else {
            throw new JsonSyntaxException("Expected " + key + " to be a Float, was " + toString(jsonElement));
        }
    }

    /**
     * Gets the float value of the field on the JsonObject with the given name.
     */
    public static float getFloat(JsonObject jsonObject, String key) {
        if (jsonObject.has(key)) {
            return getFloat(jsonObject.get(key), key);
        } else {
            throw new JsonSyntaxException("Missing " + key + ", expected to find a Float");
        }
    }

    /**
     * Gets the float value of the field on the JsonObject with the given name, or the given default value if the field
     * is missing.
     */
    public static float getFloat(JsonObject jsonObject, String key, float fallback) {
        return jsonObject.has(key) ? getFloat(jsonObject.get(key), key) : fallback;
    }

    /**
     * Gets the integer value of the given JsonElement.  Expects the second parameter to be the name of the element's
     * field if an error message needs to be thrown.
     */
    public static int getInt(JsonElement jsonElement, String key) {
        if (jsonElement.isJsonPrimitive() && jsonElement.getAsJsonPrimitive().isNumber()) {
            return jsonElement.getAsInt();
        } else {
            throw new JsonSyntaxException("Expected " + key + " to be a Int, was " + toString(jsonElement));
        }
    }

    /**
     * Gets the integer value of the field on the JsonObject with the given name.
     */
    public static int getInt(JsonObject jsonObject, String key) {
        if (jsonObject.has(key)) {
            return getInt(jsonObject.get(key), key);
        } else {
            throw new JsonSyntaxException("Missing " + key + ", expected to find a Int");
        }
    }

    /**
     * Gets the integer value of the field on the JsonObject with the given name, or the given default value if the
     * field is missing.
     */
    public static int getInt(JsonObject jsonObject, String key, int fallback) {
        return jsonObject.has(key) ? getInt(jsonObject.get(key), key) : fallback;
    }

    /**
     * Gets the given JsonElement as a JsonObject.  Expects the second parameter to be the name of the element's field
     * if an error message needs to be thrown.
     */
    public static JsonObject getJsonObject(JsonElement jsonElement, String key) {
        if (jsonElement.isJsonObject()) {
            return jsonElement.getAsJsonObject();
        } else {
            throw new JsonSyntaxException("Expected " + key + " to be a JsonObject, was " + toString(jsonElement));
        }
    }

    public static JsonObject getJsonObject(JsonObject base, String key) {
        if (base.has(key)) {
            return getJsonObject(base.get(key), key);
        } else {
            throw new JsonSyntaxException("Missing " + key + ", expected to find a JsonObject");
        }
    }

    /**
     * Gets the JsonObject field on the JsonObject with the given name, or the given default value if the field is
     * missing.
     */
    public static JsonObject getJsonObject(JsonObject jsonObject, String key, JsonObject fallback) {
        return jsonObject.has(key) ? getJsonObject(jsonObject.get(key), key) : fallback;
    }

    /**
     * Gets the given JsonElement as a JsonArray.  Expects the second parameter to be the name of the element's field if
     * an error message needs to be thrown.
     */
    public static JsonArray getJsonArray(JsonElement jsonElement, String key) {
        if (jsonElement.isJsonArray()) {
            return jsonElement.getAsJsonArray();
        } else {
            throw new JsonSyntaxException("Expected " + key + " to be a JsonArray, was " + toString(jsonElement));
        }
    }

    /**
     * Gets the JsonArray field on the JsonObject with the given name.
     */
    public static JsonArray getJsonArray(JsonObject jsonObject, String key) {
        if (jsonObject.has(key)) {
            return getJsonArray(jsonObject.get(key), key);
        } else {
            throw new JsonSyntaxException("Missing " + key + ", expected to find a JsonArray");
        }
    }

    /**
     * Gets the JsonArray field on the JsonObject with the given name, or the given default value if the field is
     * missing.
     */
    public static JsonArray getJsonArray(JsonObject jsonObject, String key, JsonArray fallback) {
        return jsonObject.has(key) ? getJsonArray(jsonObject.get(key), key) : fallback;
    }

    /**
     * Gets a human-readable description of the given JsonElement's type.  For example: "a number (4)"
     */
    public static String toString(JsonElement jsonElement) {
        String s = org.apache.commons.lang3.StringUtils.abbreviateMiddle(String.valueOf(jsonElement), "...", 10);

        if (jsonElement == null) {
            return "null (missing)";
        } else if (jsonElement.isJsonNull()) {
            return "null (json)";
        } else if (jsonElement.isJsonArray()) {
            return "an array (" + s + ")";
        } else if (jsonElement.isJsonObject()) {
            return "an object (" + s + ")";
        } else {
            if (jsonElement.isJsonPrimitive()) {
                JsonPrimitive jsonprimitive = jsonElement.getAsJsonPrimitive();

                if (jsonprimitive.isNumber()) {
                    return "a number (" + s + ")";
                }

                if (jsonprimitive.isBoolean()) {
                    return "a boolean (" + s + ")";
                }
            }

            return s;
        }
    }

}
