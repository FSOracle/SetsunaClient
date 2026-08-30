package tritium.ncm.music;

public enum MusicProvider {
    NETEASE("NetEase Cloud Music"),
    QQ("QQ Music");

    private final String displayName;

    MusicProvider(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
