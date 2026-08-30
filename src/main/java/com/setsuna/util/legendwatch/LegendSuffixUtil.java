package com.setsuna.util.legendwatch;

import com.setsuna.module.modules.render.LegendWatch;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

public class LegendSuffixUtil {

    public static Component appendIfLegendary(Component original, String username) {
        LegendWatch module = LegendWatch.INSTANCE;
        if (!module.isEnabled() || !module.vanillaNameTagsEnabled()) {
            return original;
        }

        List<LegendaryInfo> crafts = CraftTracker.getCrafts(cleanUsername(username));
        if (crafts.isEmpty()) {
            return original;
        }

        MutableComponent result = Component.empty().append(original);
        for (LegendaryInfo info : crafts) {
            if (info.predicted() && !module.predictedEnabled()) {
                continue;
            }

            result.append(Component.literal(" "));
            String scoreSuffix = getMidasSuffix(info, module);
            if (module.iconsEnabled()) {
                result.append(LegendaryIcons.getDisplay(info.itemName()));
                if (!scoreSuffix.isEmpty()) {
                    result.append(Component.literal(scoreSuffix).withStyle(ChatFormatting.GOLD));
                }
                if (info.predicted()) {
                    result.append(Component.literal("?").withStyle(ChatFormatting.GRAY));
                }
            } else if (info.predicted()) {
                result.append(Component.literal(LegendaryNames.getDisplayName(info.itemName()) + scoreSuffix + "?")
                        .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            } else {
                result.append(Component.literal(LegendaryNames.getDisplayName(info.itemName()) + scoreSuffix)
                        .withStyle(ChatFormatting.GOLD));
            }
        }
        return result;
    }

    public static String getPlainSuffix(String username) {
        LegendWatch module = LegendWatch.INSTANCE;
        if (!module.isEnabled() || !module.customNameTagsEnabled()) {
            return "";
        }

        List<LegendaryInfo> crafts = CraftTracker.getCrafts(cleanUsername(username));
        if (crafts.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        for (LegendaryInfo info : crafts) {
            if (info.predicted() && !module.predictedEnabled()) {
                continue;
            }
            builder.append(" ").append(LegendaryNames.getDisplayName(info.itemName()))
                    .append(getMidasSuffix(info, module));
            if (info.predicted()) {
                builder.append("?");
            }
        }
        return builder.toString();
    }

    public static String cleanUsername(String text) {
        if (text == null) {
            return "";
        }
        String clean = text.replaceAll("\u00A7[0-9a-fk-or]", "").replaceAll("[^\\x00-\\x7F]", "").trim();
        return clean.contains(" ") ? clean.substring(clean.lastIndexOf(" ") + 1) : clean;
    }

    private static String getMidasSuffix(LegendaryInfo info, LegendWatch module) {
        if (!CraftTracker.MIDAS_SWORD.equals(info.itemName()) || info.killCount() <= 0) {
            return "";
        }
        return switch (module.midasDisplayMode()) {
            case SharpnessEstimate -> " S" + estimateMidasSharpness(info.killCount());
            case KillCount -> " x" + info.killCount();
            case Off -> "";
        };
    }

    private static int estimateMidasSharpness(int killCount) {
        if (killCount <= 2) {
            return killCount;
        }
        return 2 + ((killCount - 1) / 2);
    }
}
