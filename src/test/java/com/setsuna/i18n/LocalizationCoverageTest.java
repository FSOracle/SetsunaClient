package com.setsuna.i18n;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.setsuna.util.StringUtil;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalizationCoverageTest {

    private static final Pattern IMPORT = Pattern.compile("import\\s+([\\w.]+);");
    private static final Pattern REGISTER = Pattern.compile("register\\((\\w+)\\.INSTANCE\\)");
    private static final Pattern MODULE_NAME = Pattern.compile("super\\(\\s*\"([^\"]+)\"");
    private static final Pattern ID_OVERRIDE = Pattern.compile(
            "public\\s+String\\s+id\\s*\\(\\s*\\)\\s*\\{\\s*return\\s+\"([^\"]+)\"");
    private static final Pattern SETTING_NAME = Pattern.compile(
            "new\\s+(?:Boolean|Button|Color|Double|Enum|Font|Int|Keybind|String)Setting"
                    + "(?:<[^>]*>)?\\s*\\(\\s*\"([^\"]+)\"");
    private static final Pattern DISPLAY_OVERRIDE = Pattern.compile("\\.displayAs\\(\"([^\"]+)\"\\)");
    private static final Pattern ENUM_CONSTANT = Pattern.compile(
            "(?m)^\\s*([A-Z][A-Z0-9_]*|[A-Z][a-zA-Z0-9]*)\\s*(?:\\([^;]*?\\))?\\s*[,;]\\s*$");
    private static final Pattern WORD = Pattern.compile("[A-Za-z]+");

    @Test
    void everyRegisteredModuleAndVisibleSettingHasChineseCoverage() throws Exception {
        Path sourceRoot = Path.of("src/main/java");
        String manager = Files.readString(sourceRoot.resolve("com/setsuna/module/ModuleManager.java"));
        JsonObject chinese = JsonParser.parseString(Files.readString(
                Path.of("src/main/resources/assets/setsuna/lang/zh_cn.json"))).getAsJsonObject();

        Map<String, String> imports = new HashMap<>();
        matcherValues(IMPORT, manager).forEach(fqcn ->
                imports.put(fqcn.substring(fqcn.lastIndexOf('.') + 1), fqcn));

        Set<String> labels = new HashSet<>(List.of("Opacity", "X Position", "Y Position"));
        Set<String> enumValues = new HashSet<>();
        List<String> missing = new ArrayList<>();
        for (String className : matcherValues(REGISTER, manager)) {
            String fqcn = imports.get(className);
            assertTrue(fqcn != null, "Missing import for registered module " + className);
            String source = Files.readString(sourceRoot.resolve(fqcn.replace('.', '/') + ".java"));

            Matcher moduleName = MODULE_NAME.matcher(source);
            assertTrue(moduleName.find(), "Missing literal module name in " + className);
            String id = StringUtil.slug(moduleName.group(1));
            Matcher override = ID_OVERRIDE.matcher(source);
            if (override.find()) {
                id = override.group(1);
            }
            if (!chinese.has("setsuna.module." + id)) {
                missing.add("module:" + id);
            }
            labels.addAll(matcherValues(SETTING_NAME, source));
            labels.addAll(matcherValues(DISPLAY_OVERRIDE, source));
            enumValues.addAll(matcherValues(ENUM_CONSTANT, source));
        }

        for (String text : labels) {
            requireTerms(chinese, text, missing, "setting:");
        }
        for (String text : enumValues) {
            requireTerms(chinese, text.replace('_', ' '), missing, "value:");
        }
        assertTrue(missing.isEmpty(), "Missing Chinese localization coverage: " + missing);
    }

    private static void requireTerms(JsonObject chinese, String text,
                                     List<String> missing, String prefix) {
        String expanded = text.replaceAll("(?<=[a-z0-9])(?=[A-Z])", " ")
                .replaceAll("(?<=[A-Z])(?=[A-Z][a-z])", " ");
        Matcher words = WORD.matcher(expanded);
        while (words.find()) {
            String slug = StringUtil.slug(words.group());
            if (!chinese.has("setsuna.term." + slug)) {
                missing.add(prefix + slug);
            }
        }
    }

    private static List<String> matcherValues(Pattern pattern, String source) {
        List<String> values = new ArrayList<>();
        Matcher matcher = pattern.matcher(source);
        while (matcher.find()) {
            values.add(matcher.group(1));
        }
        return values;
    }
}
