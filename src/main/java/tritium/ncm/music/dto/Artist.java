package tritium.ncm.music.dto;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.List;
import java.util.Objects;

/**
 * @author IzumiiKonata
 * Date: 2025/11/7 22:13
 */
@Data
public class Artist {

    @SerializedName("id")
    private final long id;
    @SerializedName("name")
    private final String name;
    @SerializedName("tns")
    private final List<String> translatedName;
    @SerializedName("alias")
    private final List<String> aliasName;

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Artist artist = (Artist) o;
        return id == artist.id;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
