package com.setsuna.module.modules.combat;

import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.DoubleSetting;
import net.minecraft.world.entity.Entity;

/** Extends local-player entity reach and optionally block interaction reach. */
public final class Reach extends Module {

    public static final Reach INSTANCE = new Reach();

    private final DoubleSetting range = add(new DoubleSetting("Range", 3.5D, 3.0D, 6.0D, 0.1D));
    private final BooleanSetting blocks = add(new BooleanSetting("Blocks", false));

    private Reach() {
        super("Reach", Category.COMBAT);
    }

    public double modifyEntityRange(double original, Entity player) {
        return appliesTo(player) ? Math.max(original, range.get()) : original;
    }

    public double modifyBlockRange(double original, Entity player) {
        return appliesTo(player) && blocks.get() ? Math.max(original, range.get()) : original;
    }

    public float modifyAttackRange(float original, Entity attacker) {
        return appliesTo(attacker) ? Math.max(original, range.get().floatValue()) : original;
    }

    private boolean appliesTo(Entity entity) {
        return isEnabled() && entity == mc.player;
    }

    @Override
    public String getInfo() {
        return String.format("%.1f", range.get());
    }
}
