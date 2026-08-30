package com.setsuna.util.legendwatch;

import com.setsuna.module.modules.render.LegendWatch;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LegendChatParser {

    private static final Pattern ENGLISH_CRAFT_PATTERN = Pattern.compile(
            "^([^:：]+?) has crafted the (.+?)[!！] This legendary cannot be crafted again[!！]$");
    private static final Pattern CHINESE_CRAFT_PATTERN = Pattern.compile(
            "^([^:：]+?)(?:\\s+)?(?:已经|已)?(?:制作了|制作出(?:了)?|打造了|锻造了|合成了)\\s*"
                    + "(?:一(?:件|把|个)\\s*)?(?:传奇(?:物品|武器)\\s*)?[:：]?\\s*"
                    + "(.+?)[!！。](?:.*)?$");
    private static final Pattern USERNAME_PATTERN = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final Pattern ELIMINATION_PATTERN = Pattern.compile("^ELIMINATION! (.+?)(?:'s (?:mind|life|soul))? (?:was|has|had) (.+)$");
    private static final Pattern KILLER_WEAPON_PATTERN = Pattern.compile("^.+(?:by|from) ([^ ]+)'s (.+)$");
    private static final Pattern DEATH_NOTE_PATTERN = Pattern.compile("^.+using the Death Note\\.?$");
    private static final Pattern EXACT_DEATH_NOTE_PATTERN = Pattern.compile("^ELIMINATION! (.+?) was assassinated by a player using the Death Note\\.$");
    private static final Pattern[] RESET_PATTERNS = new Pattern[]{
            Pattern.compile(".*has joined.*")
    };

    private static final List<LegendaryKillPattern> EXACT_LEGENDARY_KILL_PATTERNS = List.of(
            exactLegendaryKill(" was sliced up by ", "Dragon Katana"),
            exactLegendaryKill(" was blown apart by ", "Reinforced Elytra's Explosion", "Reinforced Elytra"),
            exactLegendaryKill(" was turned to gold by ", "Midas Sword"),
            exactLegendaryKill(" was electrified by ", "Mjolnir"),
            exactLegendaryKill(" had their soul absorbed by ", "Reaper Scythe"),
            exactLegendaryKill(" was obliterated by a shockwave from ", "Sonic Crossbow"),
            exactLegendaryKill(" was bludgeoned by ", "War Pick"),
            exactLegendaryKill(" was banished to The End by ", "Enderbow"),
            exactLegendaryKill(" was tracked down by ", "Artemis Bow"),
            exactLegendaryKill(" was poked to death by ", "Pufferfish Cannon"),
            exactLegendaryKill(" was stomped by ", "Summoned Ravager", "Ravager Horn"),
            exactLegendaryKill(" was frozen solid by ", "Lich Staff"),
            exactLegendaryKill(" was dazzled by ", "Emerald Blade"),
            exactLegendaryKill(" was clobbered by ", "Void Staff"),
            exactLegendaryKill(" was bashed by ", "Magma Club"),
            exactLegendaryKill(" was sent to the shadow realm by ", "Shadow Blade"),
            exactLegendaryKill(" was impaled by ", "Aiglos"),
            exactLegendaryKill("'s mind was corrupted by ", "Phantom Bow", "Phantom Longbow"),
            exactLegendaryKill(" was swooped down upon by ", "Eagle Eye Bow"),
            exactLegendaryKill(" was charbroiled by ", "Magma Cannon"),
            exactLegendaryKill(" was erased from history by ", "Chrono Sword"),
            exactLegendaryKill(" was shredded to bits by ", "Freezing Chakram"),
            exactLegendaryKill(" was torn apart with fury by ", "Headhunter's Might", "Headhunter's Chestpiece"),
            exactLegendaryKill(" was scattered into petals by ", "Sakura Tessen"),
            exactLegendaryKill(" was woven into a web by ", "Sceptre of Arachne"),
            exactLegendaryKill(" was reduced to ash by ", "Dragonight Staff", "Dragon Sceptre"),
            exactLegendaryKill("'s life was siphoned away by ", "Vampire Sabre"),
            exactLegendaryKill(" was stared to death by ", "Elder Eye of Possession"),
            exactLegendaryKill(" was transmuted to dust by ", "Kim the Transmuter"),
            exactLegendaryKill(" was shredded to bits by ", "Crimson Chainsword"),
            exactLegendaryKill(" was detonated by ", "Armadillo Detonator"),
            exactLegendaryKill(" was banished to the Deep Dark by ", "Sculkweaver's Lantern"),
            exactLegendaryKill(" was clobbered by ", "Hypnosis Staff"),
            exactLegendaryKill(" was impaled by ", "Poseidon's Trident"),
            exactLegendaryKill(" was knocked out by ", "Beehive Blaster"),
            exactLegendaryKill(" was sliced to pieces by ", "Wither Sickles"),
            exactLegendaryKill("'s soul was captured by ", "Soul Gauntlet"),
            exactLegendaryKill(" was bedazzled by ", "Villager Wand"),
            exactLegendaryKill(" was lasered by ", "Guardian Cannon"),
            exactLegendaryKill(" was blown up by ", "Happy Ghast", "Ghastly Whistle"),
            exactLegendaryKill(" was slashed up by ", "Cloud Sword"),
            exactLegendaryKill(" was infected by ", "Corrupted Crossbow"),
            exactLegendaryKill(" was turned to ice by ", "Horn of Winter"),
            exactLegendaryKill(" was pulled apart by ", "Harpoon Launcher"),
            exactLegendaryKill(" was overwhelmed by ", "Excalibur"),
            exactLegendaryKill(" was consumed by ", "Evoker Wand"),
            exactLegendaryKill(" was shrunk to atoms by ", "Shrink Ray"),
            exactLegendaryKill(" was smashed to pieces by ", "Golem Hammer"),
            exactLegendaryKill(" was slurped up by ", "Ribbit Reel")
    );

    public static void handleMessage(String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        String clean = raw.replaceAll("\u00A7[0-9A-FK-ORa-fk-or]", "")
                .trim();

        if (handleCraft(clean)) {
            return;
        }

        if (clean.contains(":") || clean.contains("：")) {
            return;
        }

        for (Pattern reset : RESET_PATTERNS) {
            if (reset.matcher(clean).matches()) {
                CraftTracker.onMatchReset();
                return;
            }
        }

        EliminationEvent exactLegendaryElimination = parseExactLegendaryElimination(clean);
        if (exactLegendaryElimination != null) {
            handleElimination(exactLegendaryElimination);
            return;
        }

        Matcher elimMatcher = ELIMINATION_PATTERN.matcher(clean);
        if (elimMatcher.matches()) {
            handleElimination(parseElimination(elimMatcher));
            return;
        }

    }

    private static boolean handleCraft(String clean) {
        Matcher englishMatcher = ENGLISH_CRAFT_PATTERN.matcher(clean);
        if (englishMatcher.matches()) {
            recordCraft(englishMatcher.group(1), englishMatcher.group(2));
            return true;
        }

        Matcher chineseMatcher = CHINESE_CRAFT_PATTERN.matcher(clean);
        if (!chineseMatcher.matches()) {
            return false;
        }
        String itemName = LegendaryIcons.getCanonicalName(cleanItemName(chineseMatcher.group(2)));
        if (itemName == null) {
            return false;
        }
        CraftTracker.recordCraft(extractUsername(chineseMatcher.group(1).trim()), itemName);
        return true;
    }

    private static void recordCraft(String rawUsername, String rawItemName) {
        String username = extractUsername(rawUsername.trim());
        String cleanedItemName = cleanItemName(rawItemName);
        String itemName = LegendaryIcons.getCanonicalName(cleanedItemName);
        CraftTracker.recordCraft(username, itemName == null ? cleanedItemName : itemName);
    }

    private static String cleanItemName(String itemName) {
        return itemName.trim()
                .replaceAll("^[《〈【\\[（(\\s]+", "")
                .replaceAll("[》〉】\\]）)\\s]+$", "");
    }

    private static void handleElimination(EliminationEvent elimination) {
        CraftTracker.onPlayerEliminated(elimination.slain(), elimination.slayer());
        if (elimination.observedLegendaryName() != null) {
            CraftTracker.onLegendaryKillObserved(
                    elimination.slayer(),
                    elimination.observedLegendaryName(),
                    LegendWatch.INSTANCE.geraldTrackingEnabled()
            );
        }
    }

    private static EliminationEvent parseExactLegendaryElimination(String clean) {
        for (LegendaryKillPattern killPattern : EXACT_LEGENDARY_KILL_PATTERNS) {
            Matcher matcher = killPattern.pattern().matcher(clean);
            if (!matcher.matches()) {
                continue;
            }
            return new EliminationEvent(
                    extractUsername(matcher.group(1).trim()),
                    stripTrailingPunctuation(matcher.group(2).trim()),
                    killPattern.observedLegendaryName()
            );
        }

        Matcher deathNoteMatcher = EXACT_DEATH_NOTE_PATTERN.matcher(clean);
        if (deathNoteMatcher.matches()) {
            return new EliminationEvent(extractUsername(deathNoteMatcher.group(1).trim()), null, null);
        }
        return null;
    }

    private static EliminationEvent parseElimination(Matcher elimMatcher) {
        String slain = extractUsername(elimMatcher.group(1).trim());
        String remainder = elimMatcher.group(2).trim();

        Matcher killerWeaponMatcher = KILLER_WEAPON_PATTERN.matcher(remainder);
        if (killerWeaponMatcher.matches()) {
            String slayer = stripTrailingPunctuation(killerWeaponMatcher.group(1).trim());
            String weaponText = stripTrailingPunctuation(killerWeaponMatcher.group(2).trim());
            return new EliminationEvent(slain, slayer, LegendaryIcons.getCanonicalName(weaponText));
        }

        if (DEATH_NOTE_PATTERN.matcher(remainder).matches()) {
            return new EliminationEvent(slain, null, null);
        }

        String[] words = remainder.split(" ");
        return new EliminationEvent(slain, stripTrailingPunctuation(words[words.length - 1]), null);
    }

    private static String extractUsername(String text) {
        Matcher matcher = USERNAME_PATTERN.matcher(text);
        String username = null;
        while (matcher.find()) {
            username = matcher.group();
        }
        if (username != null) {
            return username;
        }
        return text.contains(" ") ? text.substring(text.lastIndexOf(" ") + 1) : text;
    }

    private static String stripTrailingPunctuation(String text) {
        return text.replaceAll("[.!。！]+$", "").trim();
    }

    private static LegendaryKillPattern exactLegendaryKill(String victimToAction, String itemName) {
        return exactLegendaryKill(victimToAction, itemName, itemName);
    }

    private static LegendaryKillPattern exactLegendaryKill(String victimToAction, String visibleItemName, String canonicalItemName) {
        Pattern pattern = Pattern.compile("^ELIMINATION! (.+?)" + Pattern.quote(victimToAction) + "([^ ]+)'s " + Pattern.quote(visibleItemName) + "$");
        return new LegendaryKillPattern(pattern, canonicalItemName);
    }

    private record EliminationEvent(String slain, String slayer, String observedLegendaryName) {
    }

    private record LegendaryKillPattern(Pattern pattern, String observedLegendaryName) {
    }
}
