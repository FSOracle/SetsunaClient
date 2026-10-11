package com.setsuna.module.modules.render;

import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.render.SkijaRenderer;
import com.setsuna.render.SkijaUi;
import com.setsuna.setting.settings.EnumSetting;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.SamplingMode;
import io.github.humbleui.types.Rect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.item.equipment.trim.TrimMaterials;
import net.minecraft.world.item.equipment.trim.TrimPattern;
import net.minecraft.world.item.equipment.trim.TrimPatterns;

import java.util.Optional;

/** Locally replaces the local player's visible armor trim and material. */
public final class ArmorTrimOverride extends Module {

    public static final ArmorTrimOverride INSTANCE = new ArmorTrimOverride();

    public enum Pattern {
        NONE,
        SENTRY,
        DUNE,
        COAST,
        WILD,
        WARD,
        EYE,
        VEX,
        TIDE,
        SNOUT,
        RIB,
        SPIRE,
        WAYFINDER,
        SHAPER,
        SILENCE,
        RAISER,
        HOST,
        FLOW,
        BOLT
    }

    public enum Material {
        QUARTZ,
        IRON,
        NETHERITE,
        REDSTONE,
        COPPER,
        GOLD,
        EMERALD,
        DIAMOND,
        LAPIS,
        AMETHYST,
        RESIN
    }

    public final EnumSetting<Pattern> helmetPattern = add(new EnumSetting<>("Helmet Pattern", Pattern.SENTRY)
            .asSlider().legacyName("Pattern"));
    public final EnumSetting<Material> helmetMaterial = add(new EnumSetting<>("Helmet Material", Material.DIAMOND)
            .asSlider().legacyName("Material").visibleWhen(() -> !helmetPattern.is(Pattern.NONE)));
    public final EnumSetting<Pattern> chestplatePattern = add(new EnumSetting<>("Chestplate Pattern", Pattern.SENTRY)
            .asSlider().legacyName("Pattern"));
    public final EnumSetting<Material> chestplateMaterial = add(new EnumSetting<>("Chestplate Material", Material.DIAMOND)
            .asSlider().legacyName("Material").visibleWhen(() -> !chestplatePattern.is(Pattern.NONE)));
    public final EnumSetting<Pattern> leggingsPattern = add(new EnumSetting<>("Leggings Pattern", Pattern.SENTRY)
            .asSlider().legacyName("Pattern"));
    public final EnumSetting<Material> leggingsMaterial = add(new EnumSetting<>("Leggings Material", Material.DIAMOND)
            .asSlider().legacyName("Material").visibleWhen(() -> !leggingsPattern.is(Pattern.NONE)));
    public final EnumSetting<Pattern> bootsPattern = add(new EnumSetting<>("Boots Pattern", Pattern.SENTRY)
            .asSlider().legacyName("Pattern"));
    public final EnumSetting<Material> bootsMaterial = add(new EnumSetting<>("Boots Material", Material.DIAMOND)
            .asSlider().legacyName("Material").visibleWhen(() -> !bootsPattern.is(Pattern.NONE)));

    private ArmorTrimOverride() {
        super("Armor Trim", Category.RENDER);
    }

    @Override
    public String getInfo() {
        return chestplatePattern.displayValue();
    }

    public ItemStack applyToLocalPlayer(Player player, ItemStack stack, EquipmentSlot slot) {
        if (!isEnabled() || player == null || player != Minecraft.getInstance().player
                || stack == null || stack.isEmpty()
                || !HumanoidArmorLayer.shouldRender(stack, slot)
                || pattern(slot) == Pattern.NONE) {
            return stack;
        }
        Optional<ArmorTrim> trim = resolveTrim(player.registryAccess(), slot);
        if (trim.isEmpty()) {
            return stack;
        }
        ItemStack copy = stack.copy();
        copy.set(DataComponents.TRIM, trim.get());
        return copy;
    }

    public Optional<ArmorTrim> resolveTrim(RegistryAccess access, EquipmentSlot slot) {
        Pattern selectedPattern = pattern(slot);
        if (access == null || selectedPattern == Pattern.NONE) {
            return Optional.empty();
        }
        Optional<Holder.Reference<TrimPattern>> patternHolder = lookup(access, Registries.TRIM_PATTERN, patternKey(selectedPattern));
        Optional<Holder.Reference<TrimMaterial>> materialHolder = lookup(access, Registries.TRIM_MATERIAL, materialKey(material(slot)));
        if (patternHolder.isEmpty() || materialHolder.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ArmorTrim(materialHolder.get(), patternHolder.get()));
    }

    public int previewColor() {
        return previewColor(material(EquipmentSlot.CHEST));
    }

    private int previewColor(Material material) {
        return switch (material) {
            case QUARTZ -> 0xFFE7E4DB;
            case IRON -> 0xFFC0C0C0;
            case NETHERITE -> 0xFF4A3B38;
            case REDSTONE -> 0xFFE02020;
            case COPPER -> 0xFFC56B4A;
            case GOLD -> 0xFFE8C44A;
            case EMERALD -> 0xFF2ECF6A;
            case DIAMOND -> 0xFF4FD6E6;
            case LAPIS -> 0xFF2E5FD6;
            case AMETHYST -> 0xFF9B63D6;
            case RESIN -> 0xFFE08A3A;
        };
    }

    public void renderPreview(Canvas canvas, float x, float y, float width, float height) {
        ItemStack[] armor = previewArmor();
        EquipmentSlot[] slots = {
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
        };
        float rowHeight = Math.min(42.0F, height / armor.length);
        float itemSize = Math.min(30.0F, rowHeight - 7.0F);
        float rowTop = y + (height - rowHeight * armor.length) * 0.5F;
        for (int index = 0; index < armor.length; index++) {
            float itemX = x + 5.0F;
            float itemY = rowTop + index * rowHeight + (rowHeight - itemSize) * 0.5F;
            drawArmorSprite(canvas, armor[index], itemX, itemY, itemSize);
            ArmorTrim trim = armor[index].get(DataComponents.TRIM);
            if (trim != null) {
                drawItemTrim(canvas, slots[index], trim, itemX, itemY, itemSize);
                drawPattern(canvas, slots[index], trim, x + width - 33.0F,
                        rowTop + index * rowHeight + 7.0F, 26.0F, rowHeight - 13.0F);
            }
            float labelX = itemX + itemSize + 4.0F;
            float labelY = rowTop + index * rowHeight + 7.0F;
            SkijaUi.text(canvas, slots[index].name(), labelX, labelY, 11.0F, 0xFFE6ECEA, 6.1F);
            SkijaUi.text(canvas, pattern(slots[index]).name(), labelX, labelY + 11.0F,
                    10.0F, trim == null ? 0xFF8B9691 : previewColor(material(slots[index])), 5.6F);
        }
    }

    private ItemStack[] previewArmor() {
        EquipmentSlot[] slots = {
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
        };
        ItemStack[] armor = {
                new ItemStack(Items.DIAMOND_HELMET),
                new ItemStack(Items.DIAMOND_CHESTPLATE),
                new ItemStack(Items.DIAMOND_LEGGINGS),
                new ItemStack(Items.DIAMOND_BOOTS)
        };
        Minecraft minecraft = Minecraft.getInstance();
        RegistryAccess access = minecraft.player == null ? null : minecraft.player.registryAccess();
        for (int index = 0; index < armor.length; index++) {
            if (access == null || pattern(slots[index]) == Pattern.NONE) continue;
            Optional<ArmorTrim> trim = resolveTrim(access, slots[index]);
            if (trim.isPresent()) armor[index].set(DataComponents.TRIM, trim.get());
        }
        return armor;
    }

    private static void drawArmorSprite(Canvas canvas, ItemStack stack, float x, float y, float size) {
        if (stack == null || stack.isEmpty()) return;
        var state = new net.minecraft.client.renderer.item.ItemStackRenderState();
        Minecraft.getInstance().getItemModelResolver().updateForTopItem(
                state, stack, net.minecraft.world.item.ItemDisplayContext.GUI,
                Minecraft.getInstance().level, null, 0);
        var material = state.pickParticleMaterial(net.minecraft.util.RandomSource.create());
        if (material == null) return;
        drawSprite(canvas, material.sprite(), 0, 0, 1, 1, x, y, size, size);
    }

    private static void drawItemTrim(Canvas canvas, EquipmentSlot slot, ArmorTrim trim,
                                     float x, float y, float size) {
        String piece = switch (slot) {
            case HEAD -> "helmet";
            case CHEST -> "chestplate";
            case LEGS -> "leggings";
            case FEET -> "boots";
            default -> null;
        };
        if (piece == null) return;
        String suffix = trim.material().value().assets().assetId(EquipmentAssets.DIAMOND).suffix();
        var texture = net.minecraft.resources.Identifier.fromNamespaceAndPath(
                "minecraft", "trims/items/" + piece + "_trim_" + suffix);
        TextureAtlasSprite sprite = Minecraft.getInstance().getAtlasManager().get(
                new SpriteId(TextureAtlas.LOCATION_ITEMS, texture));
        drawSprite(canvas, sprite, 0, 0, 1, 1, x, y, size, size);
    }

    private static void drawPattern(Canvas canvas, EquipmentSlot slot, ArmorTrim trim,
                                    float x, float y, float width, float height) {
        var layer = slot == EquipmentSlot.LEGS
                ? EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS
                : EquipmentClientInfo.LayerType.HUMANOID;
        var spriteId = trim.layerAssetId(layer.trimAssetPrefix(), EquipmentAssets.DIAMOND);
        TextureAtlasSprite sprite = Minecraft.getInstance().getAtlasManager().get(
                new SpriteId(Sheets.ARMOR_TRIMS_SHEET, spriteId));
        if (slot == EquipmentSlot.HEAD) {
            drawSprite(canvas, sprite, 8F / 64F, 8F / 32F, 8F / 64F, 8F / 32F,
                    x, y, width, height);
        } else if (slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET) {
            drawSprite(canvas, sprite, 4F / 64F, 20F / 32F, 4F / 64F, 12F / 32F,
                    x, y, width, height);
        } else {
            drawSprite(canvas, sprite, 20F / 64F, 20F / 32F, 8F / 64F, 12F / 32F,
                    x, y, width, height);
        }
    }

    private static void drawSprite(Canvas canvas, TextureAtlasSprite sprite,
                                   float u, float v, float uSize, float vSize,
                                   float x, float y, float width, float height) {
        if (sprite == null) return;
        try (SkijaRenderer.BorrowedImage borrowed = SkijaRenderer.borrowTexture(sprite.atlasLocation())) {
            if (borrowed == null) return;
            float sourceX = (sprite.getU0() + (sprite.getU1() - sprite.getU0()) * u)
                    * borrowed.image().getWidth();
            float sourceY = (sprite.getV0() + (sprite.getV1() - sprite.getV0()) * v)
                    * borrowed.image().getHeight();
            float sourceWidth = (sprite.getU1() - sprite.getU0()) * uSize
                    * borrowed.image().getWidth();
            float sourceHeight = (sprite.getV1() - sprite.getV0()) * vSize
                    * borrowed.image().getHeight();
            canvas.drawImageRect(borrowed.image(),
                    Rect.makeXYWH(sourceX, sourceY, sourceWidth, sourceHeight),
                    Rect.makeXYWH(x, y, width, height), SamplingMode.DEFAULT, null, true);
        }
    }

    private Pattern pattern(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> helmetPattern.get();
            case CHEST -> chestplatePattern.get();
            case LEGS -> leggingsPattern.get();
            case FEET -> bootsPattern.get();
            default -> Pattern.NONE;
        };
    }

    private Material material(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> helmetMaterial.get();
            case CHEST -> chestplateMaterial.get();
            case LEGS -> leggingsMaterial.get();
            case FEET -> bootsMaterial.get();
            default -> Material.DIAMOND;
        };
    }

    private ResourceKey<TrimPattern> patternKey(Pattern pattern) {
        return switch (pattern) {
            case NONE -> TrimPatterns.SENTRY;
            case SENTRY -> TrimPatterns.SENTRY;
            case DUNE -> TrimPatterns.DUNE;
            case COAST -> TrimPatterns.COAST;
            case WILD -> TrimPatterns.WILD;
            case WARD -> TrimPatterns.WARD;
            case EYE -> TrimPatterns.EYE;
            case VEX -> TrimPatterns.VEX;
            case TIDE -> TrimPatterns.TIDE;
            case SNOUT -> TrimPatterns.SNOUT;
            case RIB -> TrimPatterns.RIB;
            case SPIRE -> TrimPatterns.SPIRE;
            case WAYFINDER -> TrimPatterns.WAYFINDER;
            case SHAPER -> TrimPatterns.SHAPER;
            case SILENCE -> TrimPatterns.SILENCE;
            case RAISER -> TrimPatterns.RAISER;
            case HOST -> TrimPatterns.HOST;
            case FLOW -> TrimPatterns.FLOW;
            case BOLT -> TrimPatterns.BOLT;
        };
    }

    private ResourceKey<TrimMaterial> materialKey(Material material) {
        return switch (material) {
            case QUARTZ -> TrimMaterials.QUARTZ;
            case IRON -> TrimMaterials.IRON;
            case NETHERITE -> TrimMaterials.NETHERITE;
            case REDSTONE -> TrimMaterials.REDSTONE;
            case COPPER -> TrimMaterials.COPPER;
            case GOLD -> TrimMaterials.GOLD;
            case EMERALD -> TrimMaterials.EMERALD;
            case DIAMOND -> TrimMaterials.DIAMOND;
            case LAPIS -> TrimMaterials.LAPIS;
            case AMETHYST -> TrimMaterials.AMETHYST;
            case RESIN -> TrimMaterials.RESIN;
        };
    }

    private static <T> Optional<Holder.Reference<T>> lookup(
            RegistryAccess access,
            ResourceKey<Registry<T>> registry,
            ResourceKey<T> key
    ) {
        return access.lookup(registry).flatMap(values -> values.get(key));
    }
}
