package com.setsuna;

import com.setsuna.util.DisplayNames;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Setsuna {

    public static final String MOD_ID = "setsuna";
    public static final String NAME = "Setsuna";
    public static final String VERSION = "B15";
    public static final String DEVELOPER = "FS_Oracle / ShiYi";
    public static final String CREDITS =  DEVELOPER;
    public static final Logger LOGGER = LoggerFactory.getLogger(NAME);

    private static volatile String clientName = NAME;

    private Setsuna() {
    }


    public static Minecraft mc() {
        return Minecraft.getInstance();
    }

    /** Runtime-only branding; protocol, mod id, package names and logger names stay stable. */
    public static String clientName() {
        return clientName;
    }

    public static void setClientName(String name) {
        clientName = DisplayNames.normalize(name);
    }

    public static void resetClientName() {
        clientName = NAME;
    }

    public static void refreshWindowTitle() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.updateTitle();
        }
    }
}
