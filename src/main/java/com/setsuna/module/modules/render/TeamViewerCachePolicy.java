package com.setsuna.module.modules.render;

final class TeamViewerCachePolicy {

    private TeamViewerCachePolicy() {
    }

    static boolean shouldClear(Object previousLevel, Object nextLevel) {
        return previousLevel != nextLevel;
    }
}
