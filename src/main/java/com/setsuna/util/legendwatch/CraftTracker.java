package com.setsuna.util.legendwatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class CraftTracker {

    public static final String GERALD_THE_SNIFFER = "Gerald the Sniffer";
    public static final String MIDAS_SWORD = "Midas Sword";
    private static final ConcurrentHashMap<String, List<LegendaryInfo>> CRAFT_MAP = new ConcurrentHashMap<>();

    public static void onMatchReset() {
        CRAFT_MAP.clear();
    }

    public static void recordCraft(String username, String itemName) {
        upsertLegendary(username, itemName, false, System.currentTimeMillis(), 0);
    }

    public static void recordPredicted(String username, String itemName) {
        upsertLegendary(username, itemName, true, System.currentTimeMillis(), 0);
    }

    public static void onPlayerEliminated(String slain, String slayer) {
        List<LegendaryInfo> slainCrafts = CRAFT_MAP.remove(slain);
        if (slainCrafts == null || slainCrafts.isEmpty() || slayer == null || slayer.isBlank()) {
            return;
        }

        List<LegendaryInfo> slayerList = CRAFT_MAP.computeIfAbsent(slayer, k -> Collections.synchronizedList(new ArrayList<>()));
        synchronized (slainCrafts) {
            synchronized (slayerList) {
                for (LegendaryInfo info : slainCrafts) {
                    upsertLegendary(slayerList, info.itemName(), true, info.craftedAtTimestamp(), info.killCount());
                }
            }
        }
    }

    public static void onLegendaryKillObserved(String username, String itemName, boolean geraldTrackingEnabled) {
        if (username == null || username.isBlank() || itemName == null || itemName.isBlank()) {
            return;
        }

        if (geraldTrackingEnabled && hasConfirmedLegendary(username, GERALD_THE_SNIFFER) && !hasConfirmedLegendary(username, itemName)) {
            if (canConfirmGeraldAs(itemName)) {
                replaceGeraldWithConfirmedLegendary(username, itemName);
            } else {
                recordPredicted(username, itemName);
            }
            if (MIDAS_SWORD.equals(itemName)) {
                incrementLegendaryKillCount(username, itemName);
            }
            return;
        }

        if (!confirmLegendary(username, itemName) && !hasLegendary(username, itemName)) {
            recordCraft(username, itemName);
        }
        if (MIDAS_SWORD.equals(itemName)) {
            incrementLegendaryKillCount(username, itemName);
        }
    }

    public static List<LegendaryInfo> getCrafts(String username) {
        List<LegendaryInfo> list = CRAFT_MAP.get(username);
        if (list == null) {
            return List.of();
        }
        synchronized (list) {
            return List.copyOf(list);
        }
    }

    private static boolean hasLegendary(String username, String itemName) {
        return getLegendary(username, itemName) != null;
    }

    private static boolean hasConfirmedLegendary(String username, String itemName) {
        LegendaryInfo info = getLegendary(username, itemName);
        return info != null && !info.predicted();
    }

    private static boolean confirmLegendary(String username, String itemName) {
        List<LegendaryInfo> list = CRAFT_MAP.get(username);
        if (list == null) {
            return false;
        }

        synchronized (list) {
            int index = findLegendaryIndex(list, itemName);
            if (index < 0 || !list.get(index).predicted()) {
                return false;
            }
            list.set(index, list.get(index).asConfirmed());
            return true;
        }
    }

    private static LegendaryInfo getLegendary(String username, String itemName) {
        List<LegendaryInfo> list = CRAFT_MAP.get(username);
        if (list == null) {
            return null;
        }

        synchronized (list) {
            int index = findLegendaryIndex(list, itemName);
            return index >= 0 ? list.get(index) : null;
        }
    }

    private static void upsertLegendary(String username, String itemName, boolean predicted, long timestamp, int killCount) {
        List<LegendaryInfo> list = CRAFT_MAP.computeIfAbsent(username, k -> Collections.synchronizedList(new ArrayList<>()));
        synchronized (list) {
            upsertLegendary(list, itemName, predicted, timestamp, killCount);
        }
    }

    private static void upsertLegendary(List<LegendaryInfo> list, String itemName, boolean predicted, long timestamp, int killCount) {
        int index = findLegendaryIndex(list, itemName);
        if (index < 0) {
            list.add(new LegendaryInfo(itemName, timestamp, predicted, killCount));
            return;
        }

        LegendaryInfo existing = list.get(index);
        long mergedTimestamp = Math.min(existing.craftedAtTimestamp(), timestamp);
        boolean mergedPredicted = existing.predicted() && predicted;
        int mergedKillCount = Math.max(existing.killCount(), killCount);
        list.set(index, new LegendaryInfo(itemName, mergedTimestamp, mergedPredicted, mergedKillCount));
    }

    private static int findLegendaryIndex(List<LegendaryInfo> list, String itemName) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).itemName().equals(itemName)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean canConfirmGeraldAs(String itemName) {
        return isLegendaryConfirmedAnywhere(itemName) || !isLegendaryTrackedAnywhere(itemName);
    }

    private static boolean isLegendaryConfirmedAnywhere(String itemName) {
        for (List<LegendaryInfo> list : CRAFT_MAP.values()) {
            synchronized (list) {
                for (LegendaryInfo info : list) {
                    if (info.itemName().equals(itemName) && !info.predicted()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isLegendaryTrackedAnywhere(String itemName) {
        for (List<LegendaryInfo> list : CRAFT_MAP.values()) {
            synchronized (list) {
                for (LegendaryInfo info : list) {
                    if (info.itemName().equals(itemName)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static void replaceGeraldWithConfirmedLegendary(String username, String itemName) {
        List<LegendaryInfo> list = CRAFT_MAP.get(username);
        if (list == null) {
            recordCraft(username, itemName);
            return;
        }

        synchronized (list) {
            int geraldIndex = findLegendaryIndex(list, GERALD_THE_SNIFFER);
            if (geraldIndex < 0) {
                upsertLegendary(list, itemName, false, System.currentTimeMillis(), 0);
                return;
            }

            LegendaryInfo geraldInfo = list.get(geraldIndex);
            list.remove(geraldIndex);
            upsertLegendary(list, itemName, false, geraldInfo.craftedAtTimestamp(), 0);
        }
    }

    private static void incrementLegendaryKillCount(String username, String itemName) {
        List<LegendaryInfo> list = CRAFT_MAP.get(username);
        if (list == null) {
            return;
        }

        synchronized (list) {
            int index = findLegendaryIndex(list, itemName);
            if (index >= 0) {
                list.set(index, list.get(index).incrementKillCount());
            }
        }
    }
}
