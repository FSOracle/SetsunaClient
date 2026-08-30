package com.setsuna.util.legendwatch;

public record LegendaryInfo(String itemName, long craftedAtTimestamp, boolean predicted, int killCount) {

    public LegendaryInfo(String itemName, long craftedAtTimestamp) {
        this(itemName, craftedAtTimestamp, false, 0);
    }

    public LegendaryInfo asConfirmed() {
        return predicted ? new LegendaryInfo(itemName, craftedAtTimestamp, false, killCount) : this;
    }

    public LegendaryInfo incrementKillCount() {
        return new LegendaryInfo(itemName, craftedAtTimestamp, predicted, killCount + 1);
    }
}
