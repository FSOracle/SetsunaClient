package com.setsuna.util.legendwatch;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Canonical names, server aliases, and Chinese display names for legendaries. */
public final class LegendaryNames {

    private static final Map<String, String> CHINESE_NAMES = Map.ofEntries(
            Map.entry("Magma Club", "熔岩巨棒"),
            Map.entry("Pufferfish Cannon", "河豚大炮"),
            Map.entry("War Pick", "战争之镐"),
            Map.entry("Lich Staff", "冰霜法杖"),
            Map.entry("Ravager Horn", "灾厄号角"),
            Map.entry("Hypnosis Staff", "催眠权杖"),
            Map.entry("Wither Sickles", "凋零双镰"),
            Map.entry("Cloud Sword", "轻云之剑"),
            Map.entry("Excalibur", "王者之剑"),
            Map.entry("Kim the Transmuter", "转化师老金"),
            Map.entry("Chrono Sword", "时空之剑"),
            Map.entry("Sceptre of Arachne", "阿拉赫涅权杖"),
            Map.entry("Crimson Chainsword", "猩红链剑"),
            Map.entry("Gerald the Sniffer", "嗅探兽 - 杰拉德"),
            Map.entry("Shrink Ray", "收缩射线"),
            Map.entry("Sculkweaver's Lantern", "幽匿灵灯"),
            Map.entry("Soul Gauntlet", "灵魂手套"),
            Map.entry("Magma Cannon", "岩浆大炮"),
            Map.entry("Dragon Sceptre", "巨龙权杖")
    );

    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("reinforced elytra's explosion", "Reinforced Elytra"),
            Map.entry("summoned ravager", "Ravager Horn"),
            Map.entry("happy ghast", "Ghastly Whistle"),
            Map.entry("phantom bow", "Phantom Longbow"),
            Map.entry("headhunter's might", "Headhunter's Chestpiece"),
            Map.entry("dragonight staff", "Dragon Sceptre"),
            Map.entry("dragonflame catalyst", "Dragon Sceptre"),
            Map.entry("puffenfish cannon", "Pufferfish Cannon"),
            Map.entry("exoalibur", "Excalibur"),
            Map.entry("kim the transmuten", "Kim the Transmuter"),
            Map.entry("crimson chainsuord", "Crimson Chainsword"),
            Map.entry("soulgauntlet", "Soul Gauntlet")
    );

    private static final Map<String, String> CANONICAL_NAMES = buildCanonicalNames();
    private static final List<TextReplacement> TEXT_REPLACEMENTS = buildTextReplacements();

    private LegendaryNames() {
    }

    /** Returns the canonical server name for known aliases and spelling variants. */
    public static String canonicalize(String itemName) {
        if (itemName == null) {
            return null;
        }
        String cleaned = cleanName(itemName);
        return CANONICAL_NAMES.getOrDefault(normalizeKey(cleaned), cleaned);
    }

    /** Returns a Chinese legendary name, leaving unknown names unchanged. */
    public static String getDisplayName(String itemName) {
        if (itemName == null) {
            return null;
        }
        String canonical = canonicalize(itemName);
        return CHINESE_NAMES.getOrDefault(canonical, itemName);
    }

    /** Replaces legendary names inside a visible sentence without changing other text. */
    public static String localizeText(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String localized = text;
        for (TextReplacement replacement : TEXT_REPLACEMENTS) {
            localized = replacement.pattern().matcher(localized)
                    .replaceAll(Matcher.quoteReplacement(replacement.chineseName()));
        }
        return localized;
    }

    private static Map<String, String> buildCanonicalNames() {
        Map<String, String> names = new LinkedHashMap<>();
        ALIASES.forEach((alias, canonical) -> names.put(normalizeKey(alias), canonical));
        CHINESE_NAMES.forEach((canonical, chinese) -> {
            names.put(normalizeKey(canonical), canonical);
            names.put(normalizeKey(chinese), canonical);
        });
        return Map.copyOf(names);
    }

    private static List<TextReplacement> buildTextReplacements() {
        Map<String, String> variants = new LinkedHashMap<>();
        CHINESE_NAMES.forEach(variants::put);
        ALIASES.forEach((alias, canonical) -> {
            String chineseName = CHINESE_NAMES.get(canonical);
            if (chineseName != null) {
                variants.put(alias, chineseName);
            }
        });

        List<TextReplacement> replacements = new ArrayList<>();
        variants.forEach((name, chineseName) -> replacements.add(new TextReplacement(
                name,
                compileNamePattern(name),
                chineseName)));
        replacements.sort(Comparator.comparingInt((TextReplacement value) -> value.sourceName().length())
                .reversed()
                .thenComparing(TextReplacement::sourceName));
        return List.copyOf(replacements);
    }

    private static Pattern compileNamePattern(String name) {
        StringBuilder expression = new StringBuilder("(?<![A-Za-z0-9])");
        StringBuilder literal = new StringBuilder();
        for (int index = 0; index < name.length(); index++) {
            char character = name.charAt(index);
            if (Character.isWhitespace(character)) {
                appendQuoted(expression, literal);
                while (index + 1 < name.length() && Character.isWhitespace(name.charAt(index + 1))) {
                    index++;
                }
                expression.append("\\s+");
            } else if (character == '\'') {
                appendQuoted(expression, literal);
                expression.append("['\u2018\u2019]");
            } else {
                literal.append(character);
            }
        }
        appendQuoted(expression, literal);
        expression.append("(?![A-Za-z0-9])");
        return Pattern.compile(expression.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    private static void appendQuoted(StringBuilder expression, StringBuilder literal) {
        if (literal.isEmpty()) {
            return;
        }
        expression.append(Pattern.quote(literal.toString()));
        literal.setLength(0);
    }

    private static String cleanName(String itemName) {
        String cleaned = itemName.trim()
                .replace('\u2018', '\'')
                .replace('\u2019', '\'')
                .replaceAll("\\s+", " ");
        return cleaned.startsWith("@") ? cleaned.substring(1).trim() : cleaned;
    }

    private static String normalizeKey(String itemName) {
        return cleanName(itemName).toLowerCase(Locale.ROOT);
    }

    private record TextReplacement(String sourceName, Pattern pattern, String chineseName) {
    }
}
