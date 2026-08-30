package tritium.utils;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.apache.commons.lang3.Validate;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.MissingResourceException;

@Getter
@EqualsAndHashCode(
        onlyExplicitlyIncluded = true,
        cacheStrategy = EqualsAndHashCode.CacheStrategy.LAZY
)
public class Location {

    @EqualsAndHashCode.Include
    protected final String resourcePath;

    protected Location(String resourcePath) {
        this.resourcePath = resourcePath;
        Validate.notNull(this.resourcePath);
    }

    public InputStream getResourceStream() {
        InputStream stream = Location.class.getResourceAsStream(resourcePath);

        if (stream == null)
            throw new MissingResourceException(resourcePath + " not found", Location.class.getName(), resourcePath);

        return stream;
    }

    public String toString() {
        return this.resourcePath;
    }

    private static final Map<String, Location> locationCache = new HashMap<>();

    public static Location of(String path) {
        if (!locationCache.containsKey(path)) {
            Location location = new Location(path);
            locationCache.put(path, location);
            return location;
        }

        return locationCache.get(path);
    }


}
