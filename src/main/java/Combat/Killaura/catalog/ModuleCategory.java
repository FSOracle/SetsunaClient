package Combat.Killaura.catalog;

public enum ModuleCategory {
    COMBAT("Combat"),
    MISC("Misc"),
    RENDER("Render"),
    MOVEMENT("Movement"),
    PLAYER("Player"),
    CLIENT("Client"),
    HUD("Hud");

    private final String displayName;

    ModuleCategory(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return this.displayName;
    }
}