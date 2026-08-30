package com.setsuna;

import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Setsuna {

    public static final String MOD_ID = "setsuna";
    public static final String NAME = "Setsuna";
    public static final String VERSION = "open";
    public static final String DEVELOPER = "FS_Oracle / ShiYi";
    public static final String CREDITS =  DEVELOPER;
    public static final Logger LOGGER = LoggerFactory.getLogger(NAME);

    private Setsuna() {
    }


    public static Minecraft mc() {
        return Minecraft.getInstance();
    }
}
