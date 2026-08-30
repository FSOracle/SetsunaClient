package com.setsuna.module.modules.render;

import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.ui.hud.HudEditorScreen;

/** Opens the drag-and-drop HUD layout editor. */
public final class HudEditorModule extends Module {

    public static final HudEditorModule INSTANCE = new HudEditorModule();

    private HudEditorModule() {
        super("HUD Editor", Category.CLIENT);
        setToggleable(false);
    }

    @Override
    protected void onTrigger() {
        mc.setScreen(new HudEditorScreen());
    }
}
