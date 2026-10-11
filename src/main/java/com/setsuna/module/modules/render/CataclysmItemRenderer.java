package com.setsuna.module.modules.render;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.setsuna.Setsuna;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Replaces render layers only; inventory components and packets never see appearance changes. */
public final class CataclysmItemRenderer {
    private static final Identifier MANIFEST = Identifier.fromNamespaceAndPath("setsuna", "cataclysm/items/manifest.json");
    private static final Identifier MISSING = Identifier.fromNamespaceAndPath("setsuna", "__missing_cataclysm_model__");
    private static final Map<String, JsonObject> CATALOG = new HashMap<>();
    private static final Map<String, Optional<CataclysmItemAppearance>> CACHE = new HashMap<>();
    private static boolean catalogLoaded;

    private CataclysmItemRenderer() { }

    /** Called after each model/resource reload, including failed-resource negative caches. */
    public static void clearCache() {
        CATALOG.clear();
        CACHE.clear();
        catalogLoaded = false;
    }

    /** Matches the held-item rendering path, so a failed appearance never suppresses an accessory. */
    public static boolean replacesHeldItem(ItemStack stack, Entity owner) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null && minecraft.player != null && owner == minecraft.player
                && ready(stack, minecraft.getModelManager()) != null;
    }

    public static boolean append(ItemStackRenderState state, ItemStack stack, ItemDisplayContext context,
                                 Level level, ItemOwner owner, int seed, ItemModelResolver resolver,
                                 ModelManager modelManager) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || !eligible(stack, context, owner, minecraft.player)) return false;
        CataclysmItemAppearance appearance = ready(stack, modelManager);
        if (appearance == null) return false;
        // Legacy Cataclysm GUI transforms can extend beyond 16 px; preserve their full silhouette.
        if (context == ItemDisplayContext.GUI) state.setOversizedInGui(true);
        if (appearance.flat) {
            Identifier modelId = modelId(appearance.id);
            ItemStack visual = visualCopy(stack, modelId);
            modelManager.getItemModel(modelId).update(state, visual, resolver, context,
                    level instanceof ClientLevel clientLevel ? clientLevel : null, owner, seed);
            return true;
        }
        Player player = minecraft.player;
        float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        boolean using = player.isUsingItem() && player.getUseItem() == stack;
        // Render states can carry a copy of a hand stack. Identity remains preferred; the actual
        // active hand supplies the fallback only for a held perspective, never an inventory icon.
        if (!using && context != ItemDisplayContext.GUI && player.isUsingItem()) {
            boolean right = context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
            boolean main = right == (player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT);
            var hand = main ? net.minecraft.world.InteractionHand.MAIN_HAND : net.minecraft.world.InteractionHand.OFF_HAND;
            using = hand == player.getUsedItemHand() && ItemStack.isSameItemSameComponents(stack, player.getUseItem());
        }
        float pull = using ? Math.clamp(player.getTicksUsingItem(partial) / 20.0F, 0, 1) : 0;
        String variant = appearance.variant(using, pull);
        var frame = new CataclysmItemModel.Frame(player.tickCount + partial, pull, using);
        var layer = state.newLayer();
        layer.setUsesBlockLight(appearance.blockLight);
        layer.setItemTransform(appearance.transform(context, variant));
        var frameExtents = appearance.extents(frame);
        layer.setExtents(() -> frameExtents);
        layer.setFoilType(stack.hasFoil() ? ItemStackRenderState.FoilType.STANDARD : ItemStackRenderState.FoilType.NONE);
        layer.setupSpecialModel(appearance, frame);
        state.appendModelIdentityElement(appearance.id);
        state.appendModelIdentityElement(variant);
        state.appendModelIdentityElement(stack.hasFoil());
        if (appearance.animated || stack.hasFoil()) state.setAnimated();
        return true;
    }

    static boolean eligible(ItemStack stack, ItemDisplayContext context, ItemOwner owner, Player localPlayer) {
        if (localPlayer == null || stack == null || stack.isEmpty()) return false;
        if (context == ItemDisplayContext.GUI) {
            if (owner != null && owner != localPlayer) return false;
            // GUI callers often use the local player as context even for a chest or creative tab.
            // Only references actually in the player's inventory or cursor are local possessions.
            for (int slot = 0; slot < localPlayer.getInventory().getContainerSize(); slot++) {
                if (localPlayer.getInventory().getItem(slot) == stack) return allowedScope(context, owner, localPlayer, true);
            }
            return allowedScope(context, owner, localPlayer,
                    localPlayer.containerMenu != null && localPlayer.containerMenu.getCarried() == stack);
        }
        return allowedScope(context, owner, localPlayer, false);
    }

    static boolean allowedScope(ItemDisplayContext context, Object owner, Object localPlayer, boolean inventoryReference) {
        if (localPlayer == null) return false;
        if (context == ItemDisplayContext.GUI) return inventoryReference && (owner == null || owner == localPlayer);
        return owner == localPlayer && (context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND);
    }

    static ItemStack visualCopy(ItemStack original, Identifier model) {
        ItemStack result = original.copy();
        result.set(DataComponents.ITEM_MODEL, model);
        return result;
    }

    private static CataclysmItemAppearance ready(ItemStack stack, ModelManager manager) {
        if (!EquipmentDisguise.INSTANCE.isEnabled()) return null;
        String id = CataclysmItemDisguises.INSTANCE.selected(stack);
        if (id == null) return null;
        CataclysmItemAppearance appearance = get(id);
        if (appearance != null && appearance.flat) {
            ItemModel baked = manager.getItemModel(modelId(id));
            if (baked == null || baked == manager.getItemModel(MISSING)) return null;
        }
        return appearance;
    }

    private static Identifier modelId(String id) {
        return Identifier.fromNamespaceAndPath("setsuna", "cataclysm/" + id);
    }

    private static CataclysmItemAppearance get(String id) {
        if (!catalogLoaded) {
            catalogLoaded = true;
            try {
                JsonObject manifest = CataclysmItemAppearance.readJson(open(MANIFEST));
                for (JsonElement element : manifest.getAsJsonArray("items")) {
                    JsonObject item = element.getAsJsonObject();
                    CATALOG.put(item.get("id").getAsString(), item);
                }
            } catch (IOException | RuntimeException error) {
                Setsuna.LOGGER.warn("Could not load Cataclysm item appearance catalog", error);
            }
        }
        return CACHE.computeIfAbsent(id, key -> {
            try {
                JsonObject entry = CATALOG.get(key);
                return entry == null ? Optional.empty()
                        : Optional.of(new CataclysmItemAppearance(key, entry, CataclysmItemRenderer::open));
            } catch (IOException | RuntimeException error) {
                Setsuna.LOGGER.warn("Could not load Cataclysm item appearance {}", key, error);
                return Optional.empty();
            }
        }).orElse(null);
    }

    private static java.io.InputStream open(Identifier id) throws IOException {
        return Minecraft.getInstance().getResourceManager().getResource(id)
                .orElseThrow(() -> new IOException("Missing " + id)).open();
    }
}
