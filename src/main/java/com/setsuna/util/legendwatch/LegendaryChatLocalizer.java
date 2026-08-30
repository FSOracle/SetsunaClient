package com.setsuna.util.legendwatch;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/** Localizes rendered chat text while preserving its effective styles and actions. */
public final class LegendaryChatLocalizer {

    private static final Pattern ENGLISH_CRAFT_ANNOUNCEMENT = Pattern.compile(
            "^[^:：]+? has crafted the .+?[!！] This legendary cannot be crafted again[!！]$");

    private LegendaryChatLocalizer() {
    }

    public static Component localize(Component original) {
        if (original == null) {
            return null;
        }

        boolean craftAnnouncement = ENGLISH_CRAFT_ANNOUNCEMENT.matcher(original.getString()).matches();
        List<StyledRun> runs = flattenRuns(original);
        MutableComponent result = Component.empty();
        boolean changed = false;
        for (StyledRun run : runs) {
            String localized = LegendaryNames.localizeText(run.text());
            if (craftAnnouncement) {
                localized = localizeCraftWording(localized);
            }
            changed |= !localized.equals(run.text());
            result.append(Component.literal(localized).withStyle(run.style()));
        }
        return changed ? result : original;
    }

    private static List<StyledRun> flattenRuns(Component component) {
        List<StyledRun> runs = new ArrayList<>();
        component.<Void>visit((style, text) -> {
            if (text.isEmpty()) {
                return Optional.empty();
            }
            int lastIndex = runs.size() - 1;
            if (lastIndex >= 0 && runs.get(lastIndex).style().equals(style)) {
                StyledRun previous = runs.get(lastIndex);
                runs.set(lastIndex, new StyledRun(previous.text() + text, style));
            } else {
                runs.add(new StyledRun(text, style));
            }
            return Optional.empty();
        }, Style.EMPTY);
        return runs;
    }

    private static String localizeCraftWording(String text) {
        return text.replace("has crafted the", "制作了")
                .replace("! This legendary cannot be crafted again",
                        "！这件传奇武器无法被其他玩家再次制作")
                .replace(" This legendary cannot be crafted again",
                        "这件传奇武器无法被其他玩家再次制作")
                .replace("This legendary cannot be crafted again", "这件传奇武器无法被其他玩家再次制作")
                .replace('!', '！');
    }

    private record StyledRun(String text, Style style) {
    }
}
