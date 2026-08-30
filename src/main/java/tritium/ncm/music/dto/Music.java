package tritium.ncm.music.dto;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import lombok.Data;
import tritium.ncm.api.CloudMusicApi;
import tritium.ncm.music.CloudMusic;
import tritium.ncm.music.MusicProvider;
import tritium.ncm.music.QqMusic;
import tritium.utils.Location;
import tritium.utils.Tuple;
import top.fpsmaster.music.Track;

import java.util.List;
import java.util.Objects;

@Data
public class Music {

    static final int STEREO = 8192;
    static final int INSTRUMENTAL = 131072;
    static final int DOLBY_ATMOS = 262144;
    static final int DIRTY = 1048576;
    static final long HIRES = 17179869184L;

    @SerializedName("name")
    private final String name;

    @SerializedName("mainTitle")
    private final String mainTitle;

    @SerializedName("additionalTitle")
    private final String additionalTitle;

    @SerializedName("id")
    private final long id;

    @SerializedName("ar")
    private final List<Artist> artists;

    @SerializedName("alia")
    private final List<String> aliasName;

    @SerializedName(value = "al", alternate = {"album"})
    private final Album album;

    @SerializedName("dt")
    private final long duration;

    @SerializedName("mark")
    private final long featureFlag;

    @SerializedName("publishTime")
    private final long publishTime;

    @SerializedName("tns")
    private final List<String> translatedName;

    private transient String artistsName, translatedNames;
    private transient MusicProvider provider = MusicProvider.NETEASE;
    private transient String providerId;
    private transient Track qqTrack;

    public static Music fromQqTrack(Track track) {
        long numericId = numericId(track.getId());
        Artist artist = new Artist(0L, track.getArtists(), List.of(), List.of());
        Album album = new Album(numericId, track.getAlbum(), track.getCoverUrl(), List.of());
        Music music = new Music(track.getName(), track.getName(), "", numericId,
                List.of(artist), List.of(), album, track.getDurationMs(), 0L, 0L, List.of());
        music.provider = MusicProvider.QQ;
        music.providerId = track.getId();
        music.qqTrack = track;
        return music;
    }

    public MusicProvider getProvider() {
        return provider == null ? MusicProvider.NETEASE : provider;
    }

    public String getProviderId() {
        return providerId == null || providerId.isBlank() ? String.valueOf(id) : providerId;
    }

    public String cacheKey() {
        return getProvider().name().toLowerCase() + "_" + getProviderId().replaceAll("[^A-Za-z0-9._-]", "_");
    }

    public Track qqTrack() {
        return qqTrack;
    }

    public final Location getCoverLocation() {
        return Location.of("tritium/textures/music/" + this.id + "/cover.png");
    }

    public final Location getBlurredCoverLocation() {
        return Location.of("tritium/textures/music/" + this.id + "/cover_blurred.png");
    }

    public final Location getSmallCoverLocation() {
        return Location.of("tritium/textures/music/" + this.id + "/cover_small.png");
    }

    public String getArtistsName() {
        if (this.artistsName == null) {
            this.artistsName = this.buildArtistsNames();

            if (this.artistsName.isEmpty()) {
                this.artistsName = "Unknown";
            }
        }

        return this.artistsName;
    }

    public String getTranslatedNames() {
        if (this.translatedNames == null) {
            this.translatedNames = this.buildTranslatedNames();
        }

        return this.translatedNames;
    }

    private String buildTranslatedNames() {

        if (this.translatedName == null || this.translatedName.isEmpty())
            return "";

        return String.join(", ", this.translatedName);
    }

    private String buildArtistsNames() {
        if (this.artists == null || this.artists.isEmpty()) {
            return "";
        }
        List<String> artistsList = this.artists.stream().map(Artist::getName).toList();

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < artistsList.size(); i++) {
            String artistName = artistsList.get(i);

            if (i != artistsList.size() - 1) {
                sb.append(artistName).append(", ");
            } else {
                sb.append(artistName);
            }
        }

        return sb.toString();
    }

    public String getCoverUrl(int size) {
        return this.album == null ? "" : this.album.getCoverUrl(size);
    }

    /**
     * 更新歌曲播放次数
     * 这个方法目前会触发网易云风控, 不要使用
     */
    @Deprecated
    public void updPlayCount(PlayList pl, float sec) {
//        MultiThreadingUtil.runAsync(() -> {
//            Map<String, Object> data = new HashMap<>();
//            data.put("id", this.id);
//            data.put("sourceid", pl.id);
//            data.put("time", sec);
//
//            JsonObject result = CloudMusic.api.GET("/scrobble", data).toJson();
//        });
    }

    /**
     * 获得歌曲 url
     *
     * @return 歌曲文件 url
     */
    public Tuple<String, String> getPlayUrl() {
        if (getProvider() == MusicProvider.QQ) {
            return QqMusic.playUrl(this);
        }
        JsonObject result = CloudMusicApi.songUrlV1(this.id, CloudMusic.quality.getQuality().toLowerCase()).toJsonObject();
        JsonObject music = result.get("data").getAsJsonArray().get(0).getAsJsonObject();
        if (music.get("code").getAsInt() != 200) {
            return null;
        }

        String url = music.get("url").getAsString();

        String type = music.get("type").getAsString();

        if (type.isEmpty())
            type = "mp3";

        return new Tuple<>(url, type);
    }

    public void setLike(boolean like) {
        if (getProvider() == MusicProvider.QQ) {
            return;
        }
        CloudMusicApi.like(this.id, like);
    }

    public boolean isInstrumental() {
        return (this.featureFlag & INSTRUMENTAL) != 0;
    }

    public boolean isDolbyAtmos() {
        return (this.featureFlag & DOLBY_ATMOS) != 0;
    }

    public boolean isDirty() {
        return (this.featureFlag & DIRTY) != 0;
    }

    public boolean isHiRes() {
        return (this.featureFlag & HIRES) != 0;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Music music = (Music) o;
        return getProvider() == music.getProvider() && getProviderId().equals(music.getProviderId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getProvider(), getProviderId());
    }

    private static long numericId(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            long hash = 0xcbf29ce484222325L;
            for (int i = 0; i < value.length(); i++) {
                hash ^= value.charAt(i);
                hash *= 0x100000001b3L;
            }
            return hash;
        }
    }
}
