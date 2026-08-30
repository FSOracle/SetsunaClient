package tritium.ncm.music;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import top.fpsmaster.music.MusicPlaylist;
import top.fpsmaster.music.MusicSource;
import top.fpsmaster.music.Track;
import tritium.ncm.music.dto.Album;
import tritium.ncm.music.dto.Artist;
import tritium.ncm.music.dto.Music;
import tritium.ncm.music.dto.PlayList;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QqMusicMappingTest {

    @Test
    void mapsQqTrackIntoExistingMusicModel() {
        Track track = new Track(MusicSource.QQ, "12345", "song-mid", "Song", "Artist A / Artist B",
                "Album", 183_000L, "https://example.invalid/cover.jpg", true);

        Music music = Music.fromQqTrack(track);

        assertEquals(MusicProvider.QQ, music.getProvider());
        assertEquals("12345", music.getProviderId());
        assertEquals("Song", music.getName());
        assertEquals("Artist A / Artist B", music.getArtistsName());
        assertEquals("Album", music.getAlbum().getName());
        assertEquals(183_000L, music.getDuration());
        assertEquals(track, music.qqTrack());
        assertTrue(music.cacheKey().startsWith("qq_"));
    }

    @Test
    void isolatesIdsAcrossProviders() {
        Track track = new Track(MusicSource.QQ, "7", "qq-mid", "QQ Song", "QQ Artist",
                "QQ Album", 1L, null, false);
        Music qq = Music.fromQqTrack(track);
        Music netease = new Music("NetEase Song", "NetEase Song", "", 7L,
                List.of(new Artist(1L, "NetEase Artist", List.of(), List.of())), List.of(),
                new Album(1L, "NetEase Album", null, List.of()), 1L, 0L, 0L, List.of());

        assertNotEquals(netease, qq);
        assertNotEquals(netease.cacheKey(), qq.cacheKey());
    }

    @Test
    void mapsQqPlaylistWithProviderIdentity() {
        MusicPlaylist source = new MusicPlaylist(MusicSource.QQ, "9988", "Playlist",
                "https://example.invalid/list.jpg", 42, "Description");

        PlayList playlist = PlayList.fromQqPlaylist(source);

        assertEquals(MusicProvider.QQ, playlist.getProvider());
        assertEquals("9988", playlist.getProviderId());
        assertEquals(42, playlist.getCount());
        assertEquals("Description", playlist.getDescription());
        assertTrue(playlist.cacheKey().startsWith("qq_"));
    }

    @Test
    void parsesLegacySearchFallbackResponse() {
        String json = """
                {"data":{"song":{"list":[{
                  "songid":123,"songmid":"mid-1","songname":"Fallback Song",
                  "albumname":"Fallback Album","albummid":"album-mid","interval":201,
                  "singer":[{"name":"Artist A"},{"name":"Artist B"}],
                  "pay":{"payplay":1}
                }]}}}
                """;

        List<Track> tracks = QqMusic.parseLegacySearch(JsonParser.parseString(json).getAsJsonObject());

        assertEquals(1, tracks.size());
        Track track = tracks.getFirst();
        assertEquals(MusicSource.QQ, track.getSource());
        assertEquals("123", track.getId());
        assertEquals("mid-1", track.getMid());
        assertEquals("Artist A / Artist B", track.getArtists());
        assertEquals(201_000L, track.getDurationMs());
        assertTrue(track.getVip());
        assertTrue(track.getCoverUrl().contains("album-mid"));
    }
}
