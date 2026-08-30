package com.setsuna.module.modules.player;

import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.setting.settings.ButtonSetting;
import com.setsuna.setting.settings.EnumSetting;
import com.setsuna.setting.settings.IntSetting;
import com.setsuna.ui.screen.MusicBackgroundSettings;
import com.setsuna.ui.screen.MusicScreen;

/** Opens the shared NetEase/QQ music controls without maintaining an enabled state. */
public final class NetEaseMusicModule extends Module {

    public static final NetEaseMusicModule INSTANCE = new NetEaseMusicModule();

    public enum ColorPreset {
        Midnight(0xFF65DED2, 0xFFFF6F61, 0xFF080D0E, 0xFF0D1314, 0xFF0B1011, 0xD9182021),
        RoseMint(0xFFFF6F91, 0xFF68E0C1, 0xFF120D14, 0xFF181019, 0xFF100B12, 0xD9241824),
        VioletLime(0xFFB879FF, 0xFFC8F56A, 0xFF0F0B16, 0xFF17101F, 0xFF0C0912, 0xD923192D),
        AmberAzure(0xFFF6B84A, 0xFF58A6FF, 0xFF0B1018, 0xFF101925, 0xFF080D14, 0xD91A2635);

        private final int accent;
        private final int secondary;
        private final int background;
        private final int surface;
        private final int sidebar;
        private final int card;

        ColorPreset(int accent, int secondary, int background, int surface, int sidebar, int card) {
            this.accent = accent;
            this.secondary = secondary;
            this.background = background;
            this.surface = surface;
            this.sidebar = sidebar;
            this.card = card;
        }

        public int accent() { return accent; }
        public int secondary() { return secondary; }
        public int background() { return background; }
        public int surface() { return surface; }
        public int sidebar() { return sidebar; }
        public int card() { return card; }
    }

    public final EnumSetting<ColorPreset> colorPreset = add(
            new EnumSetting<>("Color Preset", ColorPreset.Midnight));
    public final ButtonSetting importBackground = add(new ButtonSetting(
            "Import Background", MusicBackgroundSettings::importImage));
    public final ButtonSetting resetBackground = add(new ButtonSetting(
            "Reset Background", MusicBackgroundSettings::reset)
            .visibleWhen(MusicBackgroundSettings::hasImage));
    public final IntSetting backgroundOpacity = add(new IntSetting(
            "Background Opacity", 58, 10, 100, 1)
            .visibleWhen(MusicBackgroundSettings::hasImage));
    public final ButtonSetting open = add(new ButtonSetting("Open", this::openScreen));

    private NetEaseMusicModule() {
        super("Music", Category.CLIENT);
        setToggleable(false);
    }

    @Override
    public String id() {
        return "netease_music";
    }

    @Override
    protected void onTrigger() {
        openScreen();
    }

    private void openScreen() {
        mc.setScreen(new MusicScreen(mc.screen));
    }
}
