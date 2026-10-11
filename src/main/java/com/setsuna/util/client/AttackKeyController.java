package com.setsuna.util.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/** Serializes synthetic attack-key presses shared by combat modules. */
public final class AttackKeyController {

    private static final Minecraft mc = Minecraft.getInstance();

    private static Object owner;
    private static boolean syntheticDown;

    private AttackKeyController() {
    }

    public static synchronized boolean requestSyntheticAttack(Object requester) {
        if (requester == null || mc.options == null) {
            return false;
        }
        if (syntheticDown && owner != requester) {
            return false;
        }
        if (!KeybindUtils.click(mc.options.keyAttack)) {
            return false;
        }

        owner = requester;
        syntheticDown = true;
        mc.options.keyAttack.setDown(true);
        return true;
    }

    public static synchronized void releaseAttackKey(Object requester) {
        if (!syntheticDown || owner != requester) {
            return;
        }
        syntheticDown = false;
        owner = null;
        if (mc.options != null) {
            mc.options.keyAttack.setDown(false);
        }
    }

    public static synchronized boolean modifyAttackKeyState(boolean original, KeyMapping mapping) {
        return original || syntheticDown && mc.options != null && mapping == mc.options.keyAttack;
    }
}
