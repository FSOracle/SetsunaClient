package com.setsuna.module.modules;

import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.setting.settings.ButtonSetting;
import com.setsuna.ui.screen.AltManagerScreen;

/** Opens the account manager without maintaining an enabled state. */
public final class AltManagerModule extends Module {

    public static final AltManagerModule INSTANCE = new AltManagerModule();

    public final ButtonSetting open = add(new ButtonSetting("Open", this::openScreen));

    private AltManagerModule() {
        super("Alt Manager", Category.CLIENT);
        setToggleable(false);
    }

    @Override
    protected void onTrigger() {
        openScreen();
    }

    private void openScreen() {
        mc.setScreen(new AltManagerScreen(mc.screen));
    }
}
