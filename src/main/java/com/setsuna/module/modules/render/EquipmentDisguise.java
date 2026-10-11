package com.setsuna.module.modules.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.setsuna.Setsuna;
import com.setsuna.accessor.EntityRenderStateAccessor;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.render.CataclysmRenderTypes;
import com.setsuna.render.SkijaRenderer;
import com.setsuna.render.SkijaUi;
import com.setsuna.setting.settings.EnumSetting;
import com.setsuna.ui.UiTheme;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.SamplingMode;
import io.github.humbleui.types.Rect;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Local-only armor appearance; the equipped stack and server inventory remain unchanged. */
public final class EquipmentDisguise extends Module {
    public static final EquipmentDisguise INSTANCE = new EquipmentDisguise();

    public enum HeadStyle { NONE, IGNITIUM, CURSIUM, BONE_REPTILE, MONSTROUS_HELM }
    public enum ChestStyle { NONE, IGNITIUM, IGNITIUM_ELYTRA, CURSIUM, BONE_REPTILE, BLOOM_STONE_PAULDRONS }
    public enum LegsStyle { NONE, IGNITIUM, CURSIUM }
    public enum FeetStyle { NONE, IGNITIUM, CURSIUM }

    public final EnumSetting<HeadStyle> helmet = add(new EnumSetting<>("Helmet", HeadStyle.NONE).asSlider());
    public final EnumSetting<ChestStyle> chestplate = add(new EnumSetting<>("Chestplate", ChestStyle.NONE).asSlider());
    public final EnumSetting<LegsStyle> leggings = add(new EnumSetting<>("Leggings", LegsStyle.NONE).asSlider());
    public final EnumSetting<FeetStyle> boots = add(new EnumSetting<>("Boots", FeetStyle.NONE).asSlider());

    private record ModelKey(Style style, EquipmentSlot slot) { }
    private final Map<ModelKey, Optional<CataclysmArmorMesh>> models = new HashMap<>();

    private EquipmentDisguise() {
        super("Equipment Disguise", Category.RENDER);
        CataclysmItemDisguises.INSTANCE.settings().forEach(this::add);
    }

    @Override
    public String getInfo() {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.CHEST, EquipmentSlot.HEAD,
                EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            Style style = selected(slot);
            if (style != Style.NONE) return setting(slot).displayValue();
        }
        for (var option : CataclysmItemDisguises.INSTANCE.settings()) {
            if (option instanceof EnumSetting<?> choice && !choice.get().name().equals("NONE")) {
                return choice.displayValue();
            }
        }
        return helmet.displayValue();
    }

    public boolean appliesTo(HumanoidRenderState state, ItemStack stack, EquipmentSlot slot) {
        if (!isEnabled() || selected(slot) == Style.NONE || stack == null || stack.isEmpty()
                || !HumanoidArmorLayer.shouldRender(stack, slot)) return false;
        Entity entity = state instanceof EntityRenderStateAccessor accessor
                ? accessor.setsuna$getEntity() : null;
        return entity != null && entity == mc.player;
    }

    /** Entity armor layers are recreated on resource reload, including failed loads. */
    public void clearModels() {
        models.clear();
    }

    /** A selected appearance hides lower layers only after its resources load. */
    public boolean replacesArmor(HumanoidRenderState state, ItemStack stack, EquipmentSlot slot) {
        if (!appliesTo(state, stack, slot)) return false;
        CataclysmArmorMesh mesh = model(selected(slot), slot);
        return mesh != null && mesh.has(slot);
    }

    public boolean replacesLocalArmor(EquipmentSlot slot) {
        if (!isEnabled() || mc.player == null || selected(slot) == Style.NONE) return false;
        ItemStack stack = mc.player.getItemBySlot(slot);
        if (stack.isEmpty() || !HumanoidArmorLayer.shouldRender(stack, slot)) return false;
        CataclysmArmorMesh mesh = model(selected(slot), slot);
        return mesh != null && mesh.has(slot);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public boolean renderArmorDetails(HumanoidRenderState state, ItemStack stack, EquipmentSlot slot,
                                   HumanoidModel<?> model, PoseStack poseStack,
                                   SubmitNodeCollector collector, int light) {
        if (!appliesTo(state, stack, slot) || model == null) return false;
        Style style = selected(slot);
        CataclysmArmorMesh mesh = model(style, slot);
        if (mesh == null || !mesh.has(slot)) return false;
        Identifier texture = texture(style, slot);
        if (texture == null) return false;
        ((HumanoidModel) model).setupAnim(state);
        mesh.submit(slot, model, state, poseStack, collector, RenderTypes.armorCutoutNoCull(texture),
                light, stack.hasFoil());
        if (style == Style.CURSIUM) {
            mesh.submit(slot, model, state, poseStack, collector,
                    CataclysmRenderTypes.ghost(texture(style, "cursium_armor_ghost.png")),
                    light, false);
        }
        return true;
    }

    public void renderPreview(Canvas canvas, float x, float y, float width, float height) {
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET};
        float rowHeight = Math.min(42.0F, height / 4.0F);
        float rowTop = y + (height - rowHeight * slots.length) * 0.5F;
        float iconSize = Math.min(28.0F, rowHeight - 8.0F);
        for (int index = 0; index < slots.length; index++) {
            EquipmentSlot slot = slots[index];
            Style style = selected(slot);
            float top = rowTop + index * rowHeight;
            float iconX = x + 7.0F;
            float iconY = top + (rowHeight - iconSize) * 0.5F;
            Identifier icon = icon(style, slot);
            if (icon != null) drawImage(canvas, icon, iconX, iconY, iconSize, iconSize);
            else SkijaUi.rounded(canvas, iconX, iconY, iconSize, iconSize, 3.0F, 0x45343B3A);
            float textX = iconX + iconSize + 7.0F;
            float maxWidth = Math.max(18.0F, width - (textX - x) - 4.0F);
            EnumSetting<?> choice = setting(slot);
            String label = choice.displayValue();
            float fontSize = label.length() > 18 ? 5.6F : 6.3F;
            while (fontSize > 4.5F && SkijaUi.textWidth(label, fontSize) > maxWidth) {
                fontSize -= 0.3F;
            }
            SkijaUi.text(canvas, choice.displayName(), textX, top + 6.0F, 12.0F,
                    UiTheme.TEXT_MUTED, 6.2F);
            SkijaUi.text(canvas, label, textX, top + 18.0F, 10.0F,
                    style == Style.NONE ? UiTheme.TEXT_FAINT : UiTheme.accent(), fontSize);
        }
    }

    private static void drawImage(Canvas canvas, Identifier id, float x, float y, float w, float h) {
        try (SkijaRenderer.BorrowedImage borrowed = SkijaRenderer.borrowTexture(id)) {
            if (borrowed == null) return;
            canvas.drawImageRect(borrowed.image(),
                    Rect.makeWH(borrowed.image().getWidth(), borrowed.image().getHeight()),
                    Rect.makeXYWH(x, y, w, h), SamplingMode.DEFAULT, null, true);
        }
    }

    private CataclysmArmorMesh model(Style style, EquipmentSlot slot) {
        String name = style.model;
        if (name == null) return null;
        return models.computeIfAbsent(new ModelKey(style, slot), ignored -> {
            Identifier texture = texture(style, slot);
            if (texture == null || mc.getResourceManager().getResource(texture).isEmpty()
                    || style == Style.CURSIUM && mc.getResourceManager().getResource(
                    texture(style, "cursium_armor_ghost.png")).isEmpty()) {
                Setsuna.LOGGER.warn("Missing armor appearance texture for {} {}", style, slot);
                return Optional.empty();
            }
            Identifier id = Identifier.fromNamespaceAndPath("setsuna",
                    "cataclysm/armor/" + style.folder + "/" + name
                            + (slot == EquipmentSlot.LEGS ? "_legs" : "") + ".mesh.json");
            return mc.getResourceManager().getResource(id).flatMap(resource -> {
                try {
                    return Optional.of(CataclysmArmorMesh.parse(resource.open()));
                } catch (IOException | RuntimeException error) {
                    Setsuna.LOGGER.warn("Could not load armor appearance {}", id, error);
                    return Optional.empty();
                }
            });
        }).orElse(null);
    }

    Style selected(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> Style.valueOf(helmet.get().name());
            case CHEST -> Style.valueOf(chestplate.get().name());
            case LEGS -> Style.valueOf(leggings.get().name());
            case FEET -> Style.valueOf(boots.get().name());
            default -> Style.NONE;
        };
    }

    private EnumSetting<?> setting(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> helmet;
            case CHEST -> chestplate;
            case LEGS -> leggings;
            case FEET -> boots;
            default -> helmet;
        };
    }

    static Identifier texture(Style style, EquipmentSlot slot) {
        String name = slot == EquipmentSlot.LEGS
                ? style.legTexture : style.texture;
        return name == null ? null : texture(style, name);
    }

    private static Identifier texture(Style style, String name) {
        return Identifier.fromNamespaceAndPath("setsuna", "cataclysm/armor/" + style.folder + "/" + name);
    }

    private static Identifier icon(Style style, EquipmentSlot slot) {
        String name = switch (style) {
            case IGNITIUM -> "item_ignitium_" + piece(slot) + ".png";
            case IGNITIUM_ELYTRA -> "item_ignitium_elytra_chestplate.png";
            case CURSIUM -> "cursium_" + piece(slot) + ".png";
            case BONE_REPTILE -> "bone_reptile_" + piece(slot) + ".png";
            case MONSTROUS_HELM -> "item_monstrous_helm.png";
            case BLOOM_STONE_PAULDRONS -> "item_bloom_stone_pauldrons.png";
            case NONE -> null;
        };
        return name == null ? null : texture(style, name);
    }

    private static String piece(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> "helmet";
            case CHEST -> "chestplate";
            case LEGS -> "leggings";
            case FEET -> "boots";
            default -> "";
        };
    }

    enum Style {
        NONE("", null, null, null),
        IGNITIUM("ignitium", "ignitium_armor", "armor_ignitium_armor.png",
                "armor_ignitium_armor_legs.png"),
        IGNITIUM_ELYTRA("ignitium", "ignitium_elytra", "armor_ignitium_elytra_chestplate.png",
                null),
        CURSIUM("cursium", "cursium_armor", "cursium_armor.png",
                "cursium_armor_legs.png"),
        BONE_REPTILE("bone_reptile", "bone_reptile_armor", "bone_reptile_armor.png",
                null),
        MONSTROUS_HELM("monstrous_helm", "monstrous_helm", "armor_monstrous_helm.png",
                null),
        BLOOM_STONE_PAULDRONS("bloom_stone_pauldrons", "bloom_stone_pauldrons",
                "armor_bloom_stone_pauldrons.png", null);

        final String folder, model, texture, legTexture;

        Style(String folder, String model, String texture, String legTexture) {
            this.folder = folder;
            this.model = model;
            this.texture = texture;
            this.legTexture = legTexture;
        }
    }
}
