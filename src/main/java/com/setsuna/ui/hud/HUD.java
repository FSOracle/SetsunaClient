package com.setsuna.ui.hud;

import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.module.modules.render.TeamViewer;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.ColorSetting;
import com.setsuna.setting.settings.EnumSetting;

import java.awt.Color;
import java.util.List;

/** Master switch and visibility controls for the internal HUD modules. */
public final class HUD extends Module {

    public static final HUD INSTANCE = new HUD();

    /** Shared accent colouring for the Watermark and Array List, mirroring Remix. */
    public enum ColorMode {
        RAINBOW,
        FADE,
        CUSTOM
    }

    public final EnumSetting<ColorMode> colorMode =
            add(new EnumSetting<>("Color Mode", ColorMode.RAINBOW));
    public final ColorSetting mainColor =
            add(new ColorSetting("Main Color", Color.getHSBColor(0.53F, 1.0F, 1.0F)));
    public final ColorSetting secondColor =
            add(new ColorSetting("Second Color", Color.getHSBColor(0.83F, 1.0F, 1.0F))
                    .visibleWhen(() -> colorMode.is(ColorMode.FADE)));
    public final BooleanSetting whiteMode = add(new BooleanSetting("White Mode", false));

    public final BooleanSetting watermark = control("Watermark", true, WatermarkHUD.INSTANCE);
    public final BooleanSetting arrayList = control("Array List", true, ModuleListHUD.INSTANCE);
    public final BooleanSetting fps = control("FPS", false, FPSHUD.INSTANCE);
    public final BooleanSetting bps = control("BPS", false, BPSHUD.INSTANCE);
    public final BooleanSetting coordinates = control("Coordinates", false, CoordinatesHUD.INSTANCE);
    public final BooleanSetting inventory = control("Inventory", false, InventoryHUD.INSTANCE);
    public final BooleanSetting keybindOverlay = control("Keybind Overlay", false, KeybindOverlayHUD.INSTANCE);
    public final BooleanSetting potion = control("Potion", false, PotionHUD.INSTANCE);
    public final BooleanSetting radar = control("Radar", false, RadarHUD.INSTANCE);
    public final BooleanSetting teamViewer = control("Team Viewer", false, TeamViewer.INSTANCE);
    public final BooleanSetting scaffoldBlocks = control("Scaffold Blocks", false, ScaffoldBlockHUD.INSTANCE);
    public final BooleanSetting scoreboard = control("Scoreboard", false, ScoreboardHUD.INSTANCE);
    public final BooleanSetting musicLyrics = control("Music Lyrics", false, MusicLyricsHUD.INSTANCE)
            .displayAs("Music HUD");
    public final BooleanSetting notifications = control("Notifications", true, Notifications.INSTANCE);

    private boolean synchronizing;

    private HUD() {
        super("HUD", Category.MISC);
        setEnabled(true);
    }

    private BooleanSetting control(String name, boolean defaultValue, EpsilonHudModule module) {
        return add(new BooleanSetting(name, defaultValue).onChange(value -> {
            if (!synchronizing && isEnabled()) {
                module.setEnabled(value);
            }
        }));
    }

    @Override
    protected void onEnable() {
        applyControls();
    }

    @Override
    protected void onDisable() {
        disableHudModules();
    }

    /**
     * Resolves duplicated state after loading either the current controller format
     * or a legacy config that stored each HUD module independently.
     */
    public void reconcileAfterConfigLoad(boolean hasControllerConfig) {
        if (hasControllerConfig) {
            if (isEnabled()) {
                applyControls();
            } else {
                disableHudModules();
            }
            return;
        }

        captureChildStates();
        if (isEnabled()) {
            applyControls();
        } else {
            setEnabled(true);
        }
    }

    /** Copies independently loaded legacy module states into the controller settings. */
    public void captureChildStates() {
        synchronizing = true;
        try {
            watermark.set(WatermarkHUD.INSTANCE.isEnabled());
            arrayList.set(ModuleListHUD.INSTANCE.isEnabled());
            fps.set(FPSHUD.INSTANCE.isEnabled());
            bps.set(BPSHUD.INSTANCE.isEnabled());
            coordinates.set(CoordinatesHUD.INSTANCE.isEnabled());
            inventory.set(InventoryHUD.INSTANCE.isEnabled());
            keybindOverlay.set(KeybindOverlayHUD.INSTANCE.isEnabled());
            potion.set(PotionHUD.INSTANCE.isEnabled());
            radar.set(RadarHUD.INSTANCE.isEnabled());
            teamViewer.set(TeamViewer.INSTANCE.isEnabled());
            scaffoldBlocks.set(ScaffoldBlockHUD.INSTANCE.isEnabled());
            scoreboard.set(ScoreboardHUD.INSTANCE.isEnabled());
            musicLyrics.set(MusicLyricsHUD.INSTANCE.isEnabled());
            notifications.set(Notifications.INSTANCE.isEnabled());
        } finally {
            synchronizing = false;
        }
    }

    public BooleanSetting componentSetting(Module module) {
        if (module == WatermarkHUD.INSTANCE) return watermark;
        if (module == ModuleListHUD.INSTANCE) return arrayList;
        if (module == FPSHUD.INSTANCE) return fps;
        if (module == BPSHUD.INSTANCE) return bps;
        if (module == CoordinatesHUD.INSTANCE) return coordinates;
        if (module == InventoryHUD.INSTANCE) return inventory;
        if (module == KeybindOverlayHUD.INSTANCE) return keybindOverlay;
        if (module == PotionHUD.INSTANCE) return potion;
        if (module == RadarHUD.INSTANCE) return radar;
        if (module == TeamViewer.INSTANCE) return teamViewer;
        if (module == ScaffoldBlockHUD.INSTANCE) return scaffoldBlocks;
        if (module == ScoreboardHUD.INSTANCE) return scoreboard;
        if (module == MusicLyricsHUD.INSTANCE) return musicLyrics;
        if (module == Notifications.INSTANCE) return notifications;
        return null;
    }

    /** Ordered child list used by the HUD settings group and editor. */
    public List<EpsilonHudModule> components() {
        return List.of(
                WatermarkHUD.INSTANCE,
                ModuleListHUD.INSTANCE,
                FPSHUD.INSTANCE,
                BPSHUD.INSTANCE,
                CoordinatesHUD.INSTANCE,
                InventoryHUD.INSTANCE,
                KeybindOverlayHUD.INSTANCE,
                PotionHUD.INSTANCE,
                RadarHUD.INSTANCE,
                TeamViewer.INSTANCE,
                ScaffoldBlockHUD.INSTANCE,
                ScoreboardHUD.INSTANCE,
                MusicLyricsHUD.INSTANCE,
                Notifications.INSTANCE
        );
    }

    public void setComponentEnabled(EpsilonHudModule module, boolean enabled) {
        BooleanSetting setting = componentSetting(module);
        if (setting == null) return;
        setting.set(enabled);
        if (enabled && !isEnabled()) {
            setEnabled(true);
        }
    }

    private void applyControls() {
        WatermarkHUD.INSTANCE.setEnabled(watermark.get());
        ModuleListHUD.INSTANCE.setEnabled(arrayList.get());
        FPSHUD.INSTANCE.setEnabled(fps.get());
        BPSHUD.INSTANCE.setEnabled(bps.get());
        CoordinatesHUD.INSTANCE.setEnabled(coordinates.get());
        InventoryHUD.INSTANCE.setEnabled(inventory.get());
        KeybindOverlayHUD.INSTANCE.setEnabled(keybindOverlay.get());
        PotionHUD.INSTANCE.setEnabled(potion.get());
        RadarHUD.INSTANCE.setEnabled(radar.get());
        TeamViewer.INSTANCE.setEnabled(teamViewer.get());
        ScaffoldBlockHUD.INSTANCE.setEnabled(scaffoldBlocks.get());
        ScoreboardHUD.INSTANCE.setEnabled(scoreboard.get());
        MusicLyricsHUD.INSTANCE.setEnabled(musicLyrics.get());
        Notifications.INSTANCE.setEnabled(notifications.get());
    }

    private static void disableHudModules() {
        WatermarkHUD.INSTANCE.setEnabled(false);
        ModuleListHUD.INSTANCE.setEnabled(false);
        FPSHUD.INSTANCE.setEnabled(false);
        BPSHUD.INSTANCE.setEnabled(false);
        CoordinatesHUD.INSTANCE.setEnabled(false);
        InventoryHUD.INSTANCE.setEnabled(false);
        KeybindOverlayHUD.INSTANCE.setEnabled(false);
        PotionHUD.INSTANCE.setEnabled(false);
        RadarHUD.INSTANCE.setEnabled(false);
        TeamViewer.INSTANCE.setEnabled(false);
        ScaffoldBlockHUD.INSTANCE.setEnabled(false);
        ScoreboardHUD.INSTANCE.setEnabled(false);
        MusicLyricsHUD.INSTANCE.setEnabled(false);
        Notifications.INSTANCE.setEnabled(false);
    }

    // --- shared HUD accent colouring (ported 1:1 from Remix ColorUtil) ---------

    public int getColor() {
        return getColor(0);
    }

    public int getColor(int counter) {
        return getColor(counter, 255);
    }

    /** Accent for row {@code counter}, honouring the selected colour mode. */
    public int getColor(int counter, int alpha) {
        return switch (colorMode.get()) {
            case RAINBOW -> rainbow(counter, alpha);
            case FADE -> fade(counter, alpha);
            case CUSTOM -> custom(alpha);
        };
    }

    private int custom(int alpha) {
        return (clampAlpha(alpha) << 24) | (mainColor.argb() & 0xFFFFFF);
    }

    private int rainbow(int counter, int alpha) {
        float[] hsb = Color.RGBtoHSB(mainColor.get().getRed(), mainColor.get().getGreen(),
                mainColor.get().getBlue(), null);
        double state = Math.ceil(System.currentTimeMillis() - counter * 110L) / 11.0 % 360.0;
        int rgb = Color.HSBtoRGB((float) (state / 360.0), hsb[1], hsb[2]);
        return (clampAlpha(alpha) << 24) | (rgb & 0xFFFFFF);
    }

    private int fade(int counter, int alpha) {
        int first = mainColor.argb();
        int second = secondColor.argb();
        long period = 2000L;
        long now = System.currentTimeMillis() - counter * 110L;
        boolean firstPhase = Math.floorMod(now, period * 2L) < period;
        int start = firstPhase ? first : second;
        int end = firstPhase ? second : first;
        float ratio = (float) Math.floorMod(now, period) / period;
        int blended = interpolate(start, end, ratio);
        return (clampAlpha(alpha) << 24) | (blended & 0xFFFFFF);
    }

    private static int interpolate(int start, int end, float ratio) {
        float inverse = 1.0F - ratio;
        int r = (int) ((start >> 16 & 0xFF) * inverse + (end >> 16 & 0xFF) * ratio);
        int g = (int) ((start >> 8 & 0xFF) * inverse + (end >> 8 & 0xFF) * ratio);
        int b = (int) ((start & 0xFF) * inverse + (end & 0xFF) * ratio);
        return (r << 16) | (g << 8) | b;
    }

    private static int clampAlpha(int alpha) {
        return Math.max(0, Math.min(255, alpha));
    }
}
