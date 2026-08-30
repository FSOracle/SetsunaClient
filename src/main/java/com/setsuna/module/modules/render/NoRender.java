package com.setsuna.module.modules.render;

import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.setting.settings.BooleanSetting;

public final class NoRender extends Module {

    public static final NoRender INSTANCE = new NoRender();

    public final BooleanSetting potionEffects = add(new BooleanSetting("Potion Effects", true));
    public final BooleanSetting blockOverlay = add(new BooleanSetting("Block Overlay", true));
    public final BooleanSetting explosions = add(new BooleanSetting("Explosions", true));
    public final BooleanSetting totems = add(new BooleanSetting("Totems", true));
    public final BooleanSetting totemAnimation = add(new BooleanSetting("Totem Animation", true));
    public final BooleanSetting portal = add(new BooleanSetting("Portal", true));
    public final BooleanSetting fireworks = add(new BooleanSetting("Fireworks", true));
    public final BooleanSetting fireOverlay = add(new BooleanSetting("Fire Overlay", true));
    public final BooleanSetting negativeEffects = add(new BooleanSetting("Negative Effects", true));
    public final BooleanSetting potionParticles = add(new BooleanSetting("Potion Particles", true));
    public final BooleanSetting hurtCam = add(new BooleanSetting("Hurt Cam", true));
    public final BooleanSetting bobView = add(new BooleanSetting("Bob View", false));

    private NoRender() {
        super("No Render", Category.RENDER);
    }
}
