package tritium.ncm.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import tritium.ncm.DeviceIdGenerator;
import tritium.ncm.OptionsUtil;
import tritium.ncm.RequestUtil;
import tritium.utils.json.JsonUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author IzumiiKonata
 * Date: 2025/7/2 19:56
 */
@UtilityClass
public class CloudMusicApi {

    public RequestUtil.RequestAnswer lyricNew(long id) {

        Map<String, Object> data = new HashMap<>();

        data.put("id", id);
        data.put("cp", false);
        data.put("tv", 0);
        data.put("lv", 0);
        data.put("rv", 0);
        data.put("kv", 0);
        data.put("yv", 0);
        data.put("ytv", 0);
        data.put("yrv", 0);

        return RequestUtil.createRequest("/api/song/lyric/v1", data, OptionsUtil.createOptions());
    }

    public RequestUtil.RequestAnswer loginStatus() {

        RequestUtil.RequestAnswer request = RequestUtil.createRequest("/api/w/nuser/account/get", new HashMap<>(), OptionsUtil.createOptions("weapi"));

        JsonObject result = request.toJsonObject();

        if (request.getStatus() == 200) {

            JsonObject objResult = new JsonObject();

            objResult.addProperty("status", 200);
            objResult.add("data", request.toJsonObject());
            if (request.getCookies() != null) {
                objResult.addProperty("cookie", String.join(";", request.getCookies()));
            }

            result = objResult;
        }

        return RequestUtil.RequestAnswer.of(result, 200, request.getCookies());
    }

    @SneakyThrows
    public RequestUtil.RequestAnswer cloudSearch(String keyWord, @NonNull SearchType type) {

        Map<String, Object> data = new HashMap<>();

        data.put("s", keyWord);
        data.put("type", type.getId());
        data.put("limit", 100);
        data.put("offset", 0);
        data.put("total", true);

        return RequestUtil.createRequest("/api/cloudsearch/pc", data, OptionsUtil.createOptions());

    }

    public enum SearchType {

        Single(1),
        Album(10),
        Singer(100),
        Playlist(1000),
        User(1002),
        MV(1004),
        Lyric(1006),
        Radio(1009),
        Video(1014),
        All(1018),
        Sound(2000);

        @Getter
        private final int id;

        SearchType(int id) {
            this.id = id;
        }

    }

    public RequestUtil.RequestAnswer likeList(long uid) {

        Map<String, Object> data = new HashMap<>();
        data.put("uid", uid);

        return RequestUtil.createRequest("/api/song/like/get", data, OptionsUtil.createOptions());

    }

    public RequestUtil.RequestAnswer loginQrKey() {

        Map<String, Object> data = new HashMap<>();
        data.put("type", 3);

        RequestUtil.RequestAnswer request = RequestUtil.createRequest("/api/login/qrcode/unikey", data, OptionsUtil.createOptions());

        JsonObject obj = new JsonObject();
        obj.addProperty("status", 200);
        obj.add("data", request.toJsonObject());
        if (request.getCookies() != null) {
            obj.addProperty("cookie", String.join(";", request.getCookies()));
        }

        return RequestUtil.RequestAnswer.of(obj, 200, request.getCookies());
    }

    public RequestUtil.RequestAnswer loginQrCheck(String key) {

        Map<String, Object> data = new HashMap<>();
        data.put("key", key);
        data.put("type", 3);

        RequestUtil.RequestAnswer request = RequestUtil.createRequest("/api/login/qrcode/client/login", data, OptionsUtil.createOptions());

//        JsonObject obj = new JsonObject();
//        obj.addProperty("status", 200);
//
//        JsonObject objBody = new JsonObject();
//
//        if (request.getStatus() == 200) {
//            JsonObject jsonObject = gson.fromJson(request.toJsonString(), JsonObject.class);
//            jsonObject.addProperty("cookie", String.join(";", request.getCookies()));
//            objBody.add("body", jsonObject);
//        }
//
//        obj.add("body", objBody);
//        obj.addProperty("cookie", String.join(";", request.getCookies()));
//
//        return RequestUtil.RequestAnswer.of(obj, 200, request.getCookies());

        if (request.getCookies() == null || request.getCookies().length == 0) {
            return request;
        }

        JsonObject body = request.toJsonObject();
        body.addProperty("cookie", String.join(";", request.getCookies()));
        return RequestUtil.RequestAnswer.of(body, request.getStatus(), request.getCookies());
    }

    public RequestUtil.RequestAnswer songUrlV1(long id, String level) {

        Map<String, Object> data = new HashMap<>();
        data.put("ids", "[" + id + "]");
        data.put("level", level);
        data.put("encodeType", "flac");

        if (level.equals("sky")) {
            data.put("immerseType", "c51");
        }

        return RequestUtil.createRequest("/api/song/enhance/player/url/v1", data, OptionsUtil.createOptions());
    }

    public RequestUtil.RequestAnswer like(long id, boolean like) {

        Map<String, Object> data = new HashMap<>();
        data.put("alg", "itembased");
        data.put("trackId", id);
        data.put("like", like);
        data.put("time", 3);

        return RequestUtil.createRequest("/api/radio/like", data, OptionsUtil.createOptions("weapi"));
    }

    public RequestUtil.RequestAnswer playlistTrackAll(long id, int s) {

        Map<String, Object> data = new HashMap<>();
        data.put("id", id);
        data.put("n", 100000);
        data.put("s", s);

        int limit = 1000;
        int offset = 0;

        RequestUtil.RequestAnswer v6Detail = RequestUtil.createRequest("/api/v6/playlist/detail", data, OptionsUtil.createOptions());

        JsonObject v6Obj = v6Detail.toJsonObject();
        List<Long> ids = new ArrayList<>();

        JsonObject playlist = v6Obj.getAsJsonObject("playlist");
        JsonArray trackIds = playlist.getAsJsonArray("trackIds");

        for (JsonElement trackId : trackIds) {
            ids.add(trackId.getAsJsonObject().get("id").getAsLong());
        }

        List<String> collected = ids.stream().map(pId -> "{\"id\":" + pId + "}").collect(Collectors.toList());

        Map<String, Object> dataV3 = new HashMap<>();
        dataV3.put("c", "[" + String.join(",", collected) + "]");

        return RequestUtil.createRequest("/api/v3/song/detail", dataV3, OptionsUtil.createOptions());
    }

    public RequestUtil.RequestAnswer playlistUpdatePlaycount(long id) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", id);

        return RequestUtil.createRequest("/api/playlist/update/playcount", data, OptionsUtil.createOptions());
    }

    /**
     * 收藏单曲到歌单 从歌单删除歌曲
     * @param operation
     * @param trackId
     * @param musics 用英文逗号分割的音乐 Id
     * @return
     */
    public RequestUtil.RequestAnswer playlistTracks(String operation, long trackId, String musics) {
        String[] split = musics.split(",");
        Map<String, Object> data = new HashMap<>();
        data.put("op", operation);
        data.put("pid", trackId);
        data.put("trackIds", JsonUtils.toJsonString(split));
        data.put("imme", "true");

        RequestUtil.RequestAnswer request = RequestUtil.createRequest("/api/playlist/manipulate/tracks", data, OptionsUtil.createOptions());

        if (request.getStatus() == 512) {
            Map<String, Object> data2 = new HashMap<>();
            data2.put("op", operation);
            data2.put("pid", trackId);
            List<String> list = new ArrayList<>();
            list.addAll(Arrays.asList(split));
            list.addAll(Arrays.asList(split));
            data2.put("trackIds", JsonUtils.toJsonString(list.toArray(new String[0])));
            data2.put("imme", "true");
            return RequestUtil.createRequest("/api/playlist/manipulate/tracks", data2, OptionsUtil.createOptions());
        } else {
            JsonObject obj = new JsonObject();
            obj.addProperty("status", 200);
            obj.add("body", request.toJsonObject());

            return RequestUtil.RequestAnswer.of(obj, 200, request.getCookies());
        }
    }

    public RequestUtil.RequestAnswer userPlaylist(long uid, int limit, int offset) {
        Map<String, Object> data = new HashMap<>();
        data.put("uid", uid);
        data.put("limit", limit);
        data.put("offset", offset);
        data.put("includeVideo", true);

        return RequestUtil.createRequest("/api/user/playlist", data, OptionsUtil.createOptions("weapi"));
    }

    private final String ID_XOR_KEY_1 = "3go8&$8*3*3h0k(2)2";

    private String ncmDllEncodeId(String someId) {
        StringBuilder xoredString = new StringBuilder();

        for (int i = 0; i < someId.length(); i++) {
            char charCode = (char) (someId.charAt(i) ^
                    ID_XOR_KEY_1.charAt(i % ID_XOR_KEY_1.length()));
            xoredString.append(charCode);
        }

        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] hashBytes = md5.digest(xoredString.toString().getBytes(StandardCharsets.UTF_8));

            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not available", e);
        }
    }

    @SneakyThrows
    public RequestUtil.RequestAnswer registerAnonimous() {
        String deviceId = DeviceIdGenerator.generate();
        RequestUtil.globalDeviceId = deviceId;

        System.out.println("Device ID: " + deviceId);

        String encodedId = Base64.getEncoder().encodeToString(
                (deviceId + " " + ncmDllEncodeId(deviceId)).getBytes(StandardCharsets.UTF_8)
        );

        Map<String, Object> data = new HashMap<>();
        data.put("username", encodedId);

        RequestUtil.RequestAnswer request = RequestUtil.createRequest("/api/register/anonimous", data, OptionsUtil.createOptions("weapi"));
//        System.out.println(request);
        return request;
    }

    public RequestUtil.RequestAnswer songDetail(long id) {
        return songDetail(Collections.singletonList(id));
    }

    public RequestUtil.RequestAnswer songDetail(List<Long> ids) {

        Map<String, Object> data = new HashMap<>();

        StringBuilder sb = new StringBuilder();

        for (Long id : ids) {
            if (!sb.isEmpty())
                sb.append(",");
            sb.append("{\"id\":").append(id).append("}");
        }

        data.put("c", "[" + sb + "]");

        return RequestUtil.createRequest("/api/v3/song/detail", data, OptionsUtil.createOptions());
    }

    /**
     * 每日推荐歌单接口
     */
    public RequestUtil.RequestAnswer recommendResource() {
        return RequestUtil.createRequest("/api/v1/discovery/recommend/resource", null, OptionsUtil.createOptions("weapi"));
    }

    /**
     * 每日推荐歌曲
     */
    public RequestUtil.RequestAnswer recommendSongs() {
        return RequestUtil.createRequest("/api/v3/discovery/recommend/songs", null, OptionsUtil.createOptions("weapi"));
    }

}
