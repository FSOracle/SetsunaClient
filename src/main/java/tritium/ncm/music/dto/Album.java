package tritium.ncm.music.dto;

import com.google.gson.annotations.SerializedName;
import lombok.Data;
import tritium.utils.Location;

import java.util.List;
import java.util.Objects;

/**
 * @author IzumiiKonata
 * Date: 2025/11/7 22:16
 */
@Data
public class Album {

    @SerializedName("id")
    private final long id;
    @SerializedName("name")
    private final String name;
    @SerializedName(value = "picUrl", alternate = {"coverUrl", "coverImgUrl", "blurPicUrl"})
    private final String picUrl;
    @SerializedName("tns")
    private final List<String> translatedName;

    public final Location getCoverLocation() {
        return Location.of("tritium/textures/album/" + this.id + "/cover.png");
    }

    public String getCoverUrl(int size) {
        String url = cleanUrl(this.picUrl);
        if (url.isEmpty()) {
            return "";
        }
        if (url.contains("param=")) {
            return url;
        }
        return url + (url.contains("?") ? "&" : "?") + "param=" + size + "y" + size;
    }

    private static String cleanUrl(String url) {
        if (url == null || url.isBlank() || "null".equalsIgnoreCase(url)) {
            return "";
        }
        String clean = url.trim();
        if (clean.startsWith("//")) {
            return "https:" + clean;
        }
        return clean;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Album album = (Album) o;
        return id == album.id;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
