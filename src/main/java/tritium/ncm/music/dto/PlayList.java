package tritium.ncm.music.dto;

import com.google.gson.JsonArray;
import com.google.gson.annotations.SerializedName;
import lombok.Data;
import tritium.ncm.RequestUtil;
import tritium.ncm.api.CloudMusicApi;
import tritium.ncm.music.CloudMusic;
import tritium.ncm.music.MusicProvider;
import tritium.ncm.music.QqMusic;
import tritium.utils.Location;
import tritium.utils.json.JsonUtils;
import tritium.utils.other.multithreading.MultiThreadingUtil;
import top.fpsmaster.music.MusicPlaylist;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 歌单对象
 */
@Data
public class PlayList {

    @SerializedName("id")
    private final long id;

    @SerializedName("name")
    private final String name;

    @SerializedName(value = "coverImgUrl", alternate = {"picUrl", "coverUrl"})
    private final String coverUrl;

    @SerializedName("trackCount")
    private final int count;

    @SerializedName(value = "playCount")
    private final long playCount;

    @SerializedName("creator")
    private final User creator;

    @SerializedName("description")
    private final String description;

    @SerializedName("subscribed")
    private final boolean subscribed;

    @SerializedName("createTime")
    private final long createTime;

    // unique fields
    public transient List<Music> musics;
    private transient boolean searchMode = false;
    public transient boolean musicsQueried = false, musicsLoaded = false;
    private transient MusicProvider provider = MusicProvider.NETEASE;
    private transient String providerId;

    public static PlayList fromQqPlaylist(MusicPlaylist source) {
        long numericId;
        try {
            numericId = Long.parseLong(source.getId());
        } catch (NumberFormatException ignored) {
            numericId = source.getId().hashCode();
        }
        PlayList playlist = new PlayList(numericId, source.getName(), source.getCoverUrl(),
                source.getTrackCount(), 0L, null, source.getDescription(), false, 0L);
        playlist.provider = MusicProvider.QQ;
        playlist.providerId = source.getId();
        return playlist;
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

    public final Location getCoverLocation() {
        return Location.of("tritium/textures/playlist/" + this.id + "/cover.png");
    }

    public List<Music> getMusics() {

        if (this.musics == null)
            this.musics = new CopyOnWriteArrayList<>();

        if (!musics.isEmpty() && (this.musicsQueried || searchMode)) {
            return this.musics;
        }

        if (!this.musicsQueried && !searchMode) {
            this.musicsQueried = true;

            MultiThreadingUtil.runAsync(this::queryMusics);
        }

        return this.musics;
    }

    public void loadMusicsWithCallback(MusicsLoadedCallback callback) {

        if (this.musics == null)
            this.musics = new CopyOnWriteArrayList<>();

        if (!musics.isEmpty() && (this.musicsQueried || searchMode)) {
            callback.onMusicsLoaded(musics);
            return;
        }

        if (!this.musicsQueried && !searchMode) {
            this.musicsQueried = true;

            MultiThreadingUtil.runAsync(() -> {
                queryMusics();
                callback.onMusicsLoaded(musics);
            });
        }
    }

    private void queryMusics() {
        if (getProvider() == MusicProvider.QQ) {
            try {
                this.musics.addAll(QqMusic.playlistTracks(getProviderId()));
                musicsLoaded = true;
            } catch (Exception error) {
                this.musicsQueried = false;
            }
            return;
        }
        RequestUtil.RequestAnswer requestAnswer;
        try {
            requestAnswer = CloudMusicApi.playlistTrackAll(id, 8);
        } catch (Exception e) {
            this.musicsQueried = false;
            e.printStackTrace();
            return;
        }

        JsonArray songs = requestAnswer.toJsonObject().getAsJsonArray("songs");
        songs.forEach(element -> this.musics.add(JsonUtils.parse(CloudMusic.normalizeSong(element.getAsJsonObject()), Music.class)));

        musicsLoaded = true;
    }

    public interface MusicsLoadedCallback {
        void onMusicsLoaded(List<Music> musics);
    }

    public void updPlayCount() {
        CloudMusicApi.playlistUpdatePlaycount(this.id);
    }

    public void addToList(long musicId) {
        CloudMusicApi.playlistTracks("add", this.id, String.valueOf(musicId));
    }

    public void removeFromList(long musicId) {
        CloudMusicApi.playlistTracks("del", this.id, String.valueOf(musicId));
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        PlayList playList = (PlayList) o;
        return getProvider() == playList.getProvider() && getProviderId().equals(playList.getProviderId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getProvider(), getProviderId());
    }
}
