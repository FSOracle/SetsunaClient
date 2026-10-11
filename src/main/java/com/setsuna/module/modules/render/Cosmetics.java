package com.setsuna.module.modules.render;

import com.setsuna.accessor.EntityRenderStateAccessor;
import com.setsuna.cosmetic.CataclysmAccessory;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.setting.settings.EnumSetting;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;

/** Client-side accessories selected and persisted through the regular module settings. */
public final class Cosmetics extends Module {
    public static final Cosmetics INSTANCE = new Cosmetics();

    public enum Headwear { NONE, BAMBOO_DOULI }

    public enum Necklace {
        NONE(null), VITALITY_ANKH(CataclysmAccessory.VITALITY_ANKH),
        BERSERKER_SOUL_AMULET(CataclysmAccessory.BERSERKER_SOUL_AMULET);
        public final CataclysmAccessory accessory;
        Necklace(CataclysmAccessory accessory) { this.accessory = accessory; }
    }

    public enum HandAccessory {
        NONE(null), STICKY_GLOVES(CataclysmAccessory.STICKY_GLOVES),
        BLAZING_GRIPS(CataclysmAccessory.BLAZING_GRIPS), CHITIN_CLAW(CataclysmAccessory.CHITIN_CLAW);
        public final CataclysmAccessory accessory;
        HandAccessory(CataclysmAccessory accessory) { this.accessory = accessory; }
    }

    public enum WaistAccessory {
        NONE(null), BELT_OF_BEGINNER(CataclysmAccessory.BELT_OF_BEGINNER),
        BELT_OF_MONSTROSITY(CataclysmAccessory.BELT_OF_MONSTROSITY),
        SANDSTORM_IN_A_BOTTLE(CataclysmAccessory.SANDSTORM_IN_A_BOTTLE);
        public final CataclysmAccessory accessory;
        WaistAccessory(CataclysmAccessory accessory) { this.accessory = accessory; }
    }

    public enum Talisman {
        NONE(null), UNBREAKABLE_SKULL(CataclysmAccessory.UNBREAKABLE_SKULL);
        public final CataclysmAccessory accessory;
        Talisman(CataclysmAccessory accessory) { this.accessory = accessory; }
    }

    public enum Footwear {
        NONE(null), STURDY_BOOTS(CataclysmAccessory.STURDY_BOOTS);
        public final CataclysmAccessory accessory;
        Footwear(CataclysmAccessory accessory) { this.accessory = accessory; }
    }

    public final EnumSetting<Headwear> headwear = add(
            new EnumSetting<>("Headwear", Headwear.NONE));
    public final EnumSetting<Necklace> necklace = add(new EnumSetting<>("Necklace", Necklace.NONE));
    public final EnumSetting<HandAccessory> mainHandAccessory = add(
            new EnumSetting<>("Main Hand Accessory", HandAccessory.NONE));
    public final EnumSetting<HandAccessory> offHandAccessory = add(
            new EnumSetting<>("Off Hand Accessory", HandAccessory.NONE));
    public final EnumSetting<WaistAccessory> waistAccessory = add(
            new EnumSetting<>("Waist Accessory", WaistAccessory.NONE));
    public final EnumSetting<Talisman> talisman = add(new EnumSetting<>("Talisman", Talisman.NONE));
    public final EnumSetting<Footwear> footwear = add(new EnumSetting<>("Footwear", Footwear.NONE));

    private Cosmetics() {
        super("Cosmetics", Category.RENDER);
    }

    @Override
    public String getInfo() {
        for (EnumSetting<?> setting : new EnumSetting<?>[]{headwear, necklace, mainHandAccessory,
                offHandAccessory, waistAccessory, talisman, footwear}) {
            if (!setting.get().name().equals("NONE")) return setting.displayValue();
        }
        return headwear.displayValue();
    }

    public boolean wearsHeadwear(AvatarRenderState state) {
        return headwear.is(Headwear.BAMBOO_DOULI) && shouldRender(state);
    }

    public boolean shouldRender(AvatarRenderState state) {
        return isEnabled() && mc != null && mc.player != null && !state.isInvisible && !state.isSpectator
                && state instanceof EntityRenderStateAccessor accessor
                && accessor.setsuna$getEntity() == mc.player;
    }
}
