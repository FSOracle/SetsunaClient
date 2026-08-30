package com.setsuna.util.alt;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.UUID;

public final class Alt {

    public static final Codec<Alt> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("username").forGetter(Alt::getUsername),
            Codec.STRING.optionalFieldOf("refreshToken", "").forGetter(Alt::getRefreshToken),
            Codec.STRING.optionalFieldOf("accessToken", "").forGetter(Alt::getAccessToken),
            Codec.STRING.optionalFieldOf("userUUID", "").forGetter(Alt::getUserUUID),
            Codec.LONG.optionalFieldOf("lastRefreshedTime", 0L).forGetter(Alt::getLastRefreshedTime),
            Codec.STRING.optionalFieldOf("xuid", "").forGetter(Alt::getXuid)
    ).apply(instance, Alt::new));

    private final String username;
    private final String refreshToken;
    private final String accessToken;
    private final String userUUID;
    private final long lastRefreshedTime;
    private final String xuid;

    public Alt(String username) {
        this(username, "", "", "", 0L, "");
    }

    public Alt(String username, String refreshToken, String accessToken, String userUUID, long lastRefreshedTime) {
        this(username, refreshToken, accessToken, userUUID, lastRefreshedTime, "");
    }

    public Alt(String username, String refreshToken, String accessToken, String userUUID, long lastRefreshedTime, String xuid) {
        this.username = username == null ? "" : username;
        this.refreshToken = refreshToken == null ? "" : refreshToken;
        this.accessToken = accessToken == null ? "" : accessToken;
        this.userUUID = userUUID == null ? "" : userUUID;
        this.lastRefreshedTime = lastRefreshedTime;
        this.xuid = xuid == null ? "" : xuid;
    }

    public String getUsername() {
        return username;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getUserUUID() {
        return userUUID;
    }

    public long getLastRefreshedTime() {
        return lastRefreshedTime;
    }

    public String getXuid() {
        return xuid;
    }

    public boolean isMicrosoft() {
        return !accessToken.isBlank() && !userUUID.isBlank();
    }

    public boolean canRefresh() {
        return !refreshToken.isBlank() && !userUUID.isBlank();
    }

    public boolean isExpired() {
        return isMicrosoft() && getLeftExpiringTime() <= 0L;
    }

    public long getLeftExpiringTime() {
        if (!isMicrosoft()) {
            return 0L;
        }
        return Math.max(0L, 86_400L - (System.currentTimeMillis() / 1000L - lastRefreshedTime));
    }

    public UUID getProfileId() {
        if (isMicrosoft()) {
            try {
                String id = userUUID.trim();
                if (id.length() == 32) {
                    id = id.substring(0, 8) + "-" + id.substring(8, 12) + "-" + id.substring(12, 16) + "-"
                            + id.substring(16, 20) + "-" + id.substring(20);
                }
                return UUID.fromString(id);
            } catch (Exception ignored) {
            }
        }
        return UUIDUtil.createOfflinePlayerUUID(username);
    }
}
