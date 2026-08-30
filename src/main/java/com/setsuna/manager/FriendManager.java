package com.setsuna.manager;

import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Tracks a set of friend names, so combat/render modules can treat them
 * differently (skip as targets, color them apart). Names are matched by the
 * player's game-profile name.
 */
public final class FriendManager {

    public static final FriendManager INSTANCE = new FriendManager();

    private final Set<String> friends = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    private final Map<String, String> aliases = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    private FriendManager() {
    }

    public boolean isFriend(Player player) {
        String name = player.getGameProfile().name();
        return name != null && friends.contains(name);
    }

    public boolean isFriend(String name) {
        return name != null && friends.contains(name);
    }

    public boolean add(String name) {
        if (name != null && !name.isBlank()) {
            return friends.add(name.trim());
        }
        return false;
    }

    public boolean remove(String name) {
        if (name == null || !friends.remove(name)) {
            return false;
        }
        aliases.remove(name);
        return true;
    }

    public void clear() {
        friends.clear();
        aliases.clear();
    }

    public List<String> all() {
        return List.copyOf(friends);
    }

    public List<Friend> entries() {
        return friends.stream().map(name -> new Friend(name, aliases.get(name))).toList();
    }

    public boolean setAlias(String name, String alias) {
        if (!isFriend(name) || alias == null || alias.isBlank()) {
            return false;
        }
        aliases.put(name, alias.trim());
        return true;
    }

    public String alias(String name) {
        return name == null ? null : aliases.get(name);
    }

    public record Friend(String name, String alias) {
    }
}
