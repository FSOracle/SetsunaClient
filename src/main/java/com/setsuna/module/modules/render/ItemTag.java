package com.setsuna.module.modules.render;

import com.setsuna.event.Listen;
import com.setsuna.event.events.Render2DEvent;
import com.setsuna.event.events.Render3DEvent;
import com.setsuna.event.events.VanillaHudRenderEvent;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.render.SkijaUi;
import com.setsuna.setting.settings.ColorSetting;
import com.setsuna.setting.settings.DoubleSetting;
import com.setsuna.util.player.InvHelper;
import com.setsuna.util.player.TeamColorUtils;
import com.setsuna.util.render.WorldToScreen;
import io.github.humbleui.skija.Canvas;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Labels dropped items with their name and stack count. Positions are projected
 * in the 3D pass and drawn as a 2D overlay through the shared Skija canvas.
 */
public final class ItemTag extends Module {

    public static final ItemTag INSTANCE = new ItemTag();

    private static final int ROW_HEIGHT = 20;
    private static final int PANEL_GAP = 2;
    private static final int PANEL_PADDING = 3;
    private static final int ICON_SIZE = 16;
    private static final int ICON_GAP = 4;
    private static final int COUNT_GAP = 10;
    private static final int MIN_PANEL_WIDTH = 86;
    private static final int CLUSTER_MIN_ITEMS = 3;
    private static final double CLUSTER_RADIUS_SQ = 2.25 * 2.25;
    private static final float MAX_NAME_WIDTH = 132.0F;
    private static final float TEXT_SIZE = 10.0F;
    private static final int COUNT_COLOR = 0xFFF2F4F5;
    private static final int DIAMOND_COLOR = 0xFF63D6EA;
    private static final int GOLD_COLOR = 0xFFFFC83D;
    private static final int EMERALD_COLOR = 0xFF63E38A;
    private static final int NETHERITE_COLOR = 0xFFD0A8FF;

    public final DoubleSetting range = add(new DoubleSetting("Range", 32.0, 4.0, 128.0, 1.0));
    public final ColorSetting bgColor = add(new ColorSetting("Background", new Color(10, 15, 14, 205)));
    public final ColorSetting textColor = add(new ColorSetting("Text Color", new Color(255, 255, 255, 235)));

    private final List<PanelData> panels = new ArrayList<>();

    private record DrawData(ItemStack stack, String name, int count, int nameColor) {
    }

    private record ItemProjection(int entityId, Vec3 worldPosition, float screenX,
                                  float screenY, DrawData data) {
    }

    private record PanelData(int anchorEntityId, float anchorX, float anchorY,
                             List<DrawData> rows) {
    }

    private record PanelLayout(float left, float top, float width, float height) {
    }

    private record PlacedPanel(PanelData panel, PanelLayout layout) {
    }

    private ItemTag() {
        super("Item Tag", Category.RENDER);
    }

    @Listen
    private void onRender3D(Render3DEvent event) {
        if (noPlayer()) {
            panels.clear();
            return;
        }
        panels.clear();

        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        double maxDistSq = range.get() * range.get();
        double guiScale = mc.getWindow().getGuiScale();
        InventoryVisibility inventory = InventoryVisibility.capture();
        List<ItemProjection> visibleItems = new ArrayList<>();

        for (ItemEntity entity : mc.level.getEntitiesOfClass(ItemEntity.class,
                mc.player.getBoundingBox().inflate(range.get()))) {
            if (mc.player.distanceToSqr(entity) > maxDistSq) {
                continue;
            }

            ItemStack stack = entity.getItem();
            if (!isImportant(stack) || inventory.shouldHide(stack)) {
                continue;
            }

            Vec3 pos = new Vec3(
                    entity.xOld + (entity.getX() - entity.xOld) * partialTick,
                    entity.yOld + (entity.getY() - entity.yOld) * partialTick + entity.getBbHeight() + 0.2,
                    entity.zOld + (entity.getZ() - entity.zOld) * partialTick);

            Vector3f projected = WorldToScreen.getWorldPositionToScreen(pos);
            if (projected.z > 1.0f || projected.z < 0.0f) {
                continue;
            }

            String name = fitName(stack.getHoverName().getString());
            visibleItems.add(new ItemProjection(entity.getId(), pos,
                    (float) (projected.x / guiScale), (float) (projected.y / guiScale),
                    new DrawData(stack.copy(), name, stack.getCount(), itemNameColor(stack))));
        }

        visibleItems.sort(Comparator.comparingInt(ItemProjection::entityId));
        for (List<ItemProjection> cluster : spatialClusters(visibleItems)) {
            if (cluster.size() < CLUSTER_MIN_ITEMS) {
                for (ItemProjection item : cluster) {
                    panels.add(new PanelData(item.entityId(), item.screenX(), item.screenY(),
                            List.of(item.data())));
                }
                continue;
            }

            ItemProjection anchor = cluster.getFirst();
            List<DrawData> rows = new ArrayList<>();
            for (ItemProjection item : cluster) {
                addOrMerge(rows, item.data());
            }
            rows.sort(Comparator.comparing(DrawData::name, String.CASE_INSENSITIVE_ORDER));
            panels.add(new PanelData(anchor.entityId(), anchor.screenX(), anchor.screenY(),
                    List.copyOf(rows)));
        }
        panels.sort(Comparator.comparingInt(PanelData::anchorEntityId));
    }

    @Listen
    private void onVanillaHud(VanillaHudRenderEvent event) {
        if (panels.isEmpty()) {
            return;
        }

        GuiGraphicsExtractor graphics = event.graphics();
        for (PlacedPanel placed : layouts(event.width(), event.height())) {
            PanelLayout layout = placed.layout();
            int left = Math.round(layout.left());
            int top = Math.round(layout.top());
            int right = Math.round(layout.left() + layout.width());
            int bottom = Math.round(layout.top() + layout.height());
            graphics.fill(left, top, right, bottom, bgColor.argb());

            List<DrawData> rows = placed.panel().rows();
            for (int row = 0; row < rows.size(); row++) {
                DrawData data = rows.get(row);
                int rowTop = top + row * ROW_HEIGHT;
                graphics.item(data.stack(), left + PANEL_PADDING,
                        rowTop + (ROW_HEIGHT - ICON_SIZE) / 2);
            }
        }
    }

    @Listen
    private void onRender2D(Render2DEvent event) {
        if (panels.isEmpty()) {
            return;
        }
        Canvas canvas = event.canvas();
        for (PlacedPanel placed : layouts(event.width(), event.height())) {
            PanelLayout panel = placed.layout();
            List<DrawData> rows = placed.panel().rows();
            for (int row = 0; row < rows.size(); row++) {
                DrawData data = rows.get(row);
                float rowTop = panel.top() + row * ROW_HEIGHT;
                String count = "x" + data.count();
                float nameX = panel.left() + PANEL_PADDING + ICON_SIZE + ICON_GAP;
                float countWidth = SkijaUi.textWidth(count, TEXT_SIZE);
                float countX = panel.left() + panel.width() - PANEL_PADDING - countWidth;

                SkijaUi.textShadow(canvas, data.name(), nameX, rowTop, ROW_HEIGHT,
                        data.nameColor(), TEXT_SIZE);
                SkijaUi.textShadow(canvas, count, countX, rowTop, ROW_HEIGHT,
                        COUNT_COLOR, TEXT_SIZE);
            }
        }
    }

    private List<PlacedPanel> layouts(float screenWidth, float screenHeight) {
        List<PlacedPanel> placed = new ArrayList<>(panels.size());
        for (PanelData panel : panels) {
            PanelLayout desired = layout(panel, screenWidth, screenHeight);
            placed.add(new PlacedPanel(panel,
                    avoidOverlap(desired, placed, screenHeight)));
        }
        return placed;
    }

    private PanelLayout layout(PanelData panel, float screenWidth, float screenHeight) {
        float width = MIN_PANEL_WIDTH;
        for (DrawData data : panel.rows()) {
            float nameWidth = SkijaUi.textWidth(data.name(), TEXT_SIZE);
            float countWidth = SkijaUi.textWidth("x" + data.count(), TEXT_SIZE);
            float contentWidth = PANEL_PADDING + ICON_SIZE + ICON_GAP + nameWidth
                    + COUNT_GAP + countWidth + PANEL_PADDING;
            width = Math.max(width, contentWidth);
        }

        float height = panel.rows().size() * ROW_HEIGHT;
        float maxLeft = Math.max(2.0F, screenWidth - width - 2.0F);
        float left = Math.max(2.0F, Math.min(panel.anchorX() - width * 0.5F, maxLeft));
        float maxTop = Math.max(2.0F, screenHeight - height - 2.0F);
        float top = Math.max(2.0F, Math.min(panel.anchorY() - height - PANEL_GAP, maxTop));
        return new PanelLayout(left, top, width, height);
    }

    private static PanelLayout avoidOverlap(PanelLayout desired, List<PlacedPanel> placed,
                                            float screenHeight) {
        if (!overlapsAny(desired, placed)) {
            return desired;
        }

        float step = desired.height() + PANEL_GAP;
        int attempts = placed.size() + 1;
        for (int distance = 1; distance <= attempts; distance++) {
            float above = desired.top() - step * distance;
            if (above >= 2.0F) {
                PanelLayout candidate = new PanelLayout(desired.left(), above,
                        desired.width(), desired.height());
                if (!overlapsAny(candidate, placed)) {
                    return candidate;
                }
            }

            float below = desired.top() + step * distance;
            if (below + desired.height() <= screenHeight - 2.0F) {
                PanelLayout candidate = new PanelLayout(desired.left(), below,
                        desired.width(), desired.height());
                if (!overlapsAny(candidate, placed)) {
                    return candidate;
                }
            }
        }
        return desired;
    }

    private static boolean overlapsAny(PanelLayout candidate, List<PlacedPanel> placed) {
        for (PlacedPanel other : placed) {
            PanelLayout layout = other.layout();
            if (candidate.left() < layout.left() + layout.width() + PANEL_GAP
                    && candidate.left() + candidate.width() + PANEL_GAP > layout.left()
                    && candidate.top() < layout.top() + layout.height() + PANEL_GAP
                    && candidate.top() + candidate.height() + PANEL_GAP > layout.top()) {
                return true;
            }
        }
        return false;
    }

    private static List<List<ItemProjection>> spatialClusters(List<ItemProjection> items) {
        List<List<ItemProjection>> clusters = new ArrayList<>();
        boolean[] grouped = new boolean[items.size()];
        for (int start = 0; start < items.size(); start++) {
            if (grouped[start]) {
                continue;
            }

            List<ItemProjection> cluster = new ArrayList<>();
            List<Integer> pending = new ArrayList<>();
            pending.add(start);
            grouped[start] = true;
            for (int cursor = 0; cursor < pending.size(); cursor++) {
                int index = pending.get(cursor);
                ItemProjection current = items.get(index);
                cluster.add(current);
                for (int other = 0; other < items.size(); other++) {
                    if (!grouped[other]
                            && current.worldPosition().distanceToSqr(items.get(other).worldPosition())
                            <= CLUSTER_RADIUS_SQ) {
                        grouped[other] = true;
                        pending.add(other);
                    }
                }
            }
            cluster.sort(Comparator.comparingInt(ItemProjection::entityId));
            clusters.add(cluster);
        }
        return clusters;
    }

    private static void addOrMerge(List<DrawData> rows, DrawData data) {
        for (int i = 0; i < rows.size(); i++) {
            DrawData existing = rows.get(i);
            if (ItemStack.isSameItemSameComponents(existing.stack(), data.stack())) {
                rows.set(i, new DrawData(existing.stack(), existing.name(),
                        existing.count() + data.count(), existing.nameColor()));
                return;
            }
        }
        rows.add(data);
    }

    private int itemNameColor(ItemStack stack) {
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        if (!stack.has(DataComponents.CUSTOM_NAME)) {
            if (stack.is(Items.DIAMOND) || path.startsWith("diamond_")) {
                return withTextAlpha(DIAMOND_COLOR);
            }
            if (stack.is(Items.GOLD_INGOT) || stack.is(Items.GOLD_NUGGET)
                    || path.startsWith("golden_")) {
                return withTextAlpha(GOLD_COLOR);
            }
            if (stack.is(Items.EMERALD)) {
                return withTextAlpha(EMERALD_COLOR);
            }
            if (stack.is(Items.NETHERITE_INGOT) || stack.is(Items.NETHERITE_SCRAP)
                    || path.startsWith("netherite_")) {
                return withTextAlpha(NETHERITE_COLOR);
            }
        }

        Integer componentColor = TeamColorUtils.getTextColor(stack.getHoverName());
        if (componentColor != null) {
            return withTextAlpha(componentColor);
        }

        Integer rarityColor = stack.getRarity().color().getColor();
        return rarityColor == null
                ? textColor.argb()
                : withTextAlpha(rarityColor);
    }

    private int withTextAlpha(int color) {
        return (textColor.argb() & 0xFF000000) | (color & 0x00FFFFFF);
    }

    private static String fitName(String name) {
        if (SkijaUi.textWidth(name, TEXT_SIZE) <= MAX_NAME_WIDTH) {
            return name;
        }

        String suffix = "...";
        int end = name.length();
        while (end > 0 && SkijaUi.textWidth(name.substring(0, end) + suffix, TEXT_SIZE) > MAX_NAME_WIDTH) {
            end = name.offsetByCodePoints(end, -1);
        }
        return name.substring(0, end) + suffix;
    }

    static boolean isImportant(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.has(DataComponents.CUSTOM_NAME) || stack.isEnchanted() || stack.getRarity() != Rarity.COMMON) {
            return true;
        }

        Item item = stack.getItem();
        if (item instanceof AxeItem || item instanceof BowItem || item instanceof CrossbowItem
                || item instanceof TridentItem || item instanceof MaceItem || item instanceof ShieldItem
                || item instanceof PotionItem) {
            return true;
        }

        String path = BuiltInRegistries.ITEM.getKey(item).getPath();
        if (path.endsWith("_sword") || path.endsWith("_pickaxe")
                || path.endsWith("_helmet") || path.endsWith("_chestplate")
                || path.endsWith("_leggings") || path.endsWith("_boots")) {
            return true;
        }

        return item == Items.DIAMOND
                || item == Items.EMERALD
                || item == Items.IRON_INGOT
                || item == Items.GOLD_INGOT
                || item == Items.GOLD_NUGGET
                || item == Items.PLAYER_HEAD
                || item == Items.NETHERITE_INGOT
                || item == Items.NETHERITE_SCRAP
                || item == Items.ANCIENT_DEBRIS
                || item == Items.GOLDEN_APPLE
                || item == Items.ENCHANTED_GOLDEN_APPLE
                || item == Items.TOTEM_OF_UNDYING
                || item == Items.ELYTRA
                || item == Items.ENDER_PEARL
                || item == Items.EXPERIENCE_BOTTLE
                || item == Items.ENCHANTED_BOOK
                || item == Items.NETHER_STAR
                || item == Items.WATER_BUCKET
                || item == Items.LAVA_BUCKET
                || item == Items.FIRE_CHARGE
                || item == Items.TNT;
    }

    private static boolean isUhcStack(ItemStack stack) {
        Item item = stack.getItem();
        return InvHelper.isGoldenHead(stack)
                || item == Items.ENCHANTED_BOOK
                || item == Items.GOLD_INGOT
                || item == Items.GOLD_NUGGET
                || item == Items.GOLDEN_APPLE
                || item == Items.ENCHANTED_GOLDEN_APPLE
                || item == Items.DIAMOND
                || item == Items.EMERALD
                || item == Items.IRON_INGOT;
    }

    private static final class InventoryVisibility {
        private final List<ItemStack> items;
        private final Map<Item, Integer> itemCounts;
        private final EnumMap<EquipmentSlot, Float> bestArmorScores;
        private final float bestSwordDamage;
        private final float bestPickaxeScore;
        private final float bestAxeScore;
        private final float bestShovelScore;
        private final float bestCrossbowScore;
        private final float bestPunchBowScore;
        private final float bestPowerBowScore;

        private InventoryVisibility(List<ItemStack> items, Map<Item, Integer> itemCounts,
                                    EnumMap<EquipmentSlot, Float> bestArmorScores,
                                    float bestSwordDamage, float bestPickaxeScore,
                                    float bestAxeScore, float bestShovelScore,
                                    float bestCrossbowScore, float bestPunchBowScore,
                                    float bestPowerBowScore) {
            this.items = items;
            this.itemCounts = itemCounts;
            this.bestArmorScores = bestArmorScores;
            this.bestSwordDamage = bestSwordDamage;
            this.bestPickaxeScore = bestPickaxeScore;
            this.bestAxeScore = bestAxeScore;
            this.bestShovelScore = bestShovelScore;
            this.bestCrossbowScore = bestCrossbowScore;
            this.bestPunchBowScore = bestPunchBowScore;
            this.bestPowerBowScore = bestPowerBowScore;
        }

        static InventoryVisibility capture() {
            List<ItemStack> items = InvHelper.getAllItems();
            Map<Item, Integer> itemCounts = new HashMap<>();
            EnumMap<EquipmentSlot, Float> bestArmorScores = new EnumMap<>(EquipmentSlot.class);
            float bestSwordDamage = 0.0F;
            float bestPickaxeScore = 0.0F;
            float bestAxeScore = 0.0F;
            float bestShovelScore = 0.0F;
            float bestCrossbowScore = 0.0F;
            float bestPunchBowScore = 0.0F;
            float bestPowerBowScore = 0.0F;

            for (ItemStack stack : items) {
                if (stack.isEmpty()) {
                    continue;
                }

                Item item = stack.getItem();
                itemCounts.merge(item, stack.getCount(), Integer::sum);

                if (InvHelper.isArmor(stack)) {
                    EquipmentSlot slot = InvHelper.getArmorSlot(stack);
                    if (slot != null) {
                        bestArmorScores.merge(slot, InvHelper.getProtection(stack), Math::max);
                    }
                }
                if (InvHelper.isSword(stack)) {
                    bestSwordDamage = Math.max(bestSwordDamage, InvHelper.getSwordDamage(stack));
                }
                if (InvHelper.isPickaxe(stack)) {
                    bestPickaxeScore = Math.max(bestPickaxeScore, InvHelper.getToolScore(stack));
                }
                if (item instanceof AxeItem && !InvHelper.isSharpnessAxe(stack)) {
                    bestAxeScore = Math.max(bestAxeScore, InvHelper.getToolScore(stack));
                }
                if (item instanceof ShovelItem) {
                    bestShovelScore = Math.max(bestShovelScore, InvHelper.getToolScore(stack));
                }
                if (item instanceof CrossbowItem) {
                    bestCrossbowScore = Math.max(bestCrossbowScore, InvHelper.getCrossbowScore(stack));
                }
                if (item instanceof BowItem && InvHelper.isPunchBow(stack)) {
                    bestPunchBowScore = Math.max(bestPunchBowScore, InvHelper.getPunchBowScore(stack));
                }
                if (item instanceof BowItem && InvHelper.isPowerBow(stack)) {
                    bestPowerBowScore = Math.max(bestPowerBowScore, InvHelper.getPowerBowScore(stack));
                }
            }

            return new InventoryVisibility(items, itemCounts, bestArmorScores, bestSwordDamage,
                    bestPickaxeScore, bestAxeScore, bestShovelScore, bestCrossbowScore,
                    bestPunchBowScore, bestPowerBowScore);
        }

        boolean shouldHide(ItemStack stack) {
            if (stack.isEmpty() || isUhcStack(stack)) {
                return false;
            }

            for (ItemStack carried : items) {
                if (!carried.isEmpty() && ItemStack.isSameItemSameComponents(carried, stack)) {
                    return true;
                }
            }

            if (InvHelper.isArmor(stack)) {
                EquipmentSlot slot = InvHelper.getArmorSlot(stack);
                return bestArmorScores.getOrDefault(slot, 0.0F) >= InvHelper.getProtection(stack);
            }
            if (InvHelper.isSword(stack)) {
                return bestSwordDamage >= InvHelper.getSwordDamage(stack);
            }
            if (InvHelper.isPickaxe(stack)) {
                return bestPickaxeScore >= InvHelper.getToolScore(stack);
            }
            if (stack.getItem() instanceof AxeItem && !InvHelper.isSharpnessAxe(stack)) {
                return bestAxeScore >= InvHelper.getToolScore(stack);
            }
            if (stack.getItem() instanceof ShovelItem) {
                return bestShovelScore >= InvHelper.getToolScore(stack);
            }
            if (stack.getItem() instanceof CrossbowItem) {
                return bestCrossbowScore >= InvHelper.getCrossbowScore(stack);
            }
            if (stack.getItem() instanceof BowItem && InvHelper.isPunchBow(stack)) {
                return bestPunchBowScore >= InvHelper.getPunchBowScore(stack);
            }
            if (stack.getItem() instanceof BowItem && InvHelper.isPowerBow(stack)) {
                return bestPowerBowScore >= InvHelper.getPowerBowScore(stack);
            }
            if (stack.getItem() instanceof BowItem) {
                return itemCounts.containsKey(Items.BOW);
            }

            return itemCounts.containsKey(stack.getItem());
        }
    }
}
