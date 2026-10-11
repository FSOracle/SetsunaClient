package com.setsuna.module.modules.render;

import com.setsuna.event.Listen;
import com.setsuna.event.events.Render2DEvent;
import com.setsuna.event.events.Render3DEvent;
import com.setsuna.event.events.VanillaHudRenderEvent;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.render.SkijaUi;
import com.setsuna.setting.settings.ColorSetting;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.DoubleSetting;
import com.setsuna.util.player.InvHelper;
import com.setsuna.util.player.TeamColorUtils;
import com.setsuna.util.legendwatch.LegendaryIcons;
import com.setsuna.util.render.WorldToScreen;
import io.github.humbleui.skija.Canvas;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
    private static final float MAX_NAME_WIDTH = 132.0F;
    private static final float TEXT_SIZE = 10.0F;
    private static final int COUNT_COLOR = 0xFFF2F4F5;
    private static final int DIAMOND_COLOR = 0xFF63D6EA;
    private static final int GOLD_COLOR = 0xFFFFC83D;
    private static final int EMERALD_COLOR = 0xFF63E38A;
    private static final int NETHERITE_COLOR = 0xFFD0A8FF;

    public final DoubleSetting range = add(new DoubleSetting("Range", 32.0, 4.0, 128.0, 1.0));
    public final DoubleSetting size = add(new DoubleSetting("Size", 1.0, 0.5, 2.0, 0.1));
    public final BooleanSetting distanceScaling = add(new BooleanSetting("Distance Scaling", false));
    public final ColorSetting bgColor = add(new ColorSetting("Background", new Color(10, 15, 14, 205)));
    public final ColorSetting textColor = add(new ColorSetting("Text Color", new Color(255, 255, 255, 235)));

    private final List<PanelData> panels = new ArrayList<>();

    private record DrawData(ItemStack stack, String name, int count, int nameColor) {
    }

    private record ItemProjection(int entityId, float screenX,
                                  float screenY, DrawData data) {
    }

    private record PanelData(int anchorEntityId, float anchorX, float anchorY,
                             float distanceScale, List<DrawData> rows) {
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
        List<ItemProjection> visibleItems = new ArrayList<>();

        for (ItemEntity entity : mc.level.getEntitiesOfClass(ItemEntity.class,
                mc.player.getBoundingBox().inflate(range.get()))) {
            if (mc.player.distanceToSqr(entity) > maxDistSq) {
                continue;
            }

            ItemStack stack = entity.getItem();
            if (!isImportant(stack)) {
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
            visibleItems.add(new ItemProjection(entity.getId(),
                    (float) (projected.x / guiScale), (float) (projected.y / guiScale),
                    new DrawData(stack.copy(), name, stack.getCount(), itemNameColor(stack))));
        }

        visibleItems.sort(Comparator.comparingInt(ItemProjection::entityId));
        // Keep one label per entity. Nearby drops must not be merged into a fake stack.
        for (ItemProjection item : visibleItems) {
            ItemEntity entity = mc.level.getEntity(item.entityId()) instanceof ItemEntity found ? found : null;
            float distanceScale = distanceScaling.get() && entity != null
                    ? distanceScale(mc.player.distanceTo(entity)) : 1.0F;
            panels.add(new PanelData(item.entityId(), item.screenX(), item.screenY(), distanceScale,
                    List.of(item.data())));
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
            float uiScale = size.get().floatValue() * placed.panel().distanceScale();
            float rowHeight = ROW_HEIGHT * uiScale;
            float iconSize = ICON_SIZE * uiScale;
            float padding = PANEL_PADDING * uiScale;
            int left = Math.round(layout.left());
            int top = Math.round(layout.top());
            int right = Math.round(layout.left() + layout.width());
            int bottom = Math.round(layout.top() + layout.height());
            graphics.fill(left, top, right, bottom, bgColor.argb());

            List<DrawData> rows = placed.panel().rows();
            for (int row = 0; row < rows.size(); row++) {
                DrawData data = rows.get(row);
                float rowTop = layout.top() + row * rowHeight;
                float iconX = layout.left() + padding;
                float iconY = rowTop + (rowHeight - iconSize) * 0.5F;
                graphics.pose().pushMatrix();
                try {
                    graphics.pose().translate(iconX, iconY).scale(uiScale, uiScale);
                    graphics.item(data.stack(), 0, 0);
                } finally {
                    graphics.pose().popMatrix();
                }
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
            float uiScale = size.get().floatValue() * placed.panel().distanceScale();
            float rowHeight = ROW_HEIGHT * uiScale;
            float padding = PANEL_PADDING * uiScale;
            float iconSize = ICON_SIZE * uiScale;
            float iconGap = ICON_GAP * uiScale;
            float textSize = TEXT_SIZE * uiScale;
            List<DrawData> rows = placed.panel().rows();
            for (int row = 0; row < rows.size(); row++) {
                DrawData data = rows.get(row);
                float rowTop = panel.top() + row * rowHeight;
                String count = "x" + data.count();
                float nameX = panel.left() + padding + iconSize + iconGap;
                float countWidth = SkijaUi.textWidth(count, textSize);
                float countX = panel.left() + panel.width() - padding - countWidth;

                SkijaUi.textShadow(canvas, data.name(), nameX, rowTop, rowHeight,
                        data.nameColor(), textSize);
                SkijaUi.textShadow(canvas, count, countX, rowTop, rowHeight,
                        COUNT_COLOR, textSize);
            }
        }
    }

    private List<PlacedPanel> layouts(float screenWidth, float screenHeight) {
        List<PlacedPanel> placed = new ArrayList<>(panels.size());
        for (PanelData panel : panels) {
            PanelLayout desired = layout(panel, screenWidth, screenHeight);
            float panelGap = PANEL_GAP * size.get().floatValue() * panel.distanceScale();
            placed.add(new PlacedPanel(panel,
                    avoidOverlap(desired, placed, screenHeight, panelGap)));
        }
        return placed;
    }

    private PanelLayout layout(PanelData panel, float screenWidth, float screenHeight) {
        float uiScale = size.get().floatValue() * panel.distanceScale();
        float textSize = TEXT_SIZE * uiScale;
        float width = MIN_PANEL_WIDTH * uiScale;
        for (DrawData data : panel.rows()) {
            float nameWidth = SkijaUi.textWidth(data.name(), textSize);
            float countWidth = SkijaUi.textWidth("x" + data.count(), textSize);
            float contentWidth = (PANEL_PADDING + ICON_SIZE + ICON_GAP) * uiScale + nameWidth
                    + COUNT_GAP * uiScale + countWidth + PANEL_PADDING * uiScale;
            width = Math.max(width, contentWidth);
        }

        float height = panel.rows().size() * ROW_HEIGHT * uiScale;
        float maxLeft = Math.max(2.0F, screenWidth - width - 2.0F);
        float left = Math.max(2.0F, Math.min(panel.anchorX() - width * 0.5F, maxLeft));
        float maxTop = Math.max(2.0F, screenHeight - height - 2.0F);
        float top = Math.max(2.0F,
                Math.min(panel.anchorY() - height - PANEL_GAP * uiScale, maxTop));
        return new PanelLayout(left, top, width, height);
    }

    static float distanceScale(double distance) {
        if (!Double.isFinite(distance) || distance <= 0.0D) {
            return 1.0F;
        }
        return Math.max(0.55F, Math.min(1.0F, (float) (10.0D / distance)));
    }

    private static PanelLayout avoidOverlap(PanelLayout desired, List<PlacedPanel> placed,
                                            float screenHeight, float panelGap) {
        if (!overlapsAny(desired, placed, panelGap)) {
            return desired;
        }

        float step = desired.height() + panelGap;
        int attempts = placed.size() + 1;
        for (int distance = 1; distance <= attempts; distance++) {
            float above = desired.top() - step * distance;
            if (above >= 2.0F) {
                PanelLayout candidate = new PanelLayout(desired.left(), above,
                        desired.width(), desired.height());
                if (!overlapsAny(candidate, placed, panelGap)) {
                    return candidate;
                }
            }

            float below = desired.top() + step * distance;
            if (below + desired.height() <= screenHeight - 2.0F) {
                PanelLayout candidate = new PanelLayout(desired.left(), below,
                        desired.width(), desired.height());
                if (!overlapsAny(candidate, placed, panelGap)) {
                    return candidate;
                }
            }
        }
        return desired;
    }

    private static boolean overlapsAny(PanelLayout candidate, List<PlacedPanel> placed,
                                       float panelGap) {
        for (PlacedPanel other : placed) {
            PanelLayout layout = other.layout();
            if (candidate.left() < layout.left() + layout.width() + panelGap
                    && candidate.left() + candidate.width() + panelGap > layout.left()
                    && candidate.top() < layout.top() + layout.height() + panelGap
                    && candidate.top() + candidate.height() + panelGap > layout.top()) {
                return true;
            }
        }
        return false;
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
        return isLegendary(stack) || isSupply(stack)
                || isEquipment(stack) || isOreOrMineral(stack);
    }

    private static boolean isLegendary(ItemStack stack) {
        return LegendaryIcons.isLegendaryName(stack.getHoverName().getString());
    }

    private static boolean isEquipment(ItemStack stack) {
        Item item = stack.getItem();
        String path = BuiltInRegistries.ITEM.getKey(item).getPath();
        return InvHelper.isArmor(stack) || InvHelper.isSword(stack) || InvHelper.isPickaxe(stack)
                || item instanceof AxeItem || item instanceof ShovelItem || item instanceof BowItem
                || item instanceof CrossbowItem || item instanceof TridentItem || item instanceof MaceItem
                || item instanceof ShieldItem || item == Items.ELYTRA
                || path.endsWith("_helmet") || path.endsWith("_chestplate")
                || path.endsWith("_leggings") || path.endsWith("_boots");
    }

    private static boolean isSupply(ItemStack stack) {
        Item item = stack.getItem();
        return item == Items.GOLDEN_APPLE
                || item == Items.ENCHANTED_GOLDEN_APPLE
                || item == Items.ENCHANTED_BOOK
                || item == Items.PLAYER_HEAD
                || item == Items.TOTEM_OF_UNDYING;
    }

    private static boolean isOreOrMineral(ItemStack stack) {
        Item item = stack.getItem();
        String path = BuiltInRegistries.ITEM.getKey(item).getPath();
        return path.endsWith("_ore") || path.equals("ancient_debris")
                || path.startsWith("raw_")
                || path.endsWith("_ingot") || path.endsWith("_nugget")
                || item == Items.DIAMOND || item == Items.EMERALD
                || item == Items.COAL || item == Items.LAPIS_LAZULI
                || item == Items.REDSTONE || item == Items.QUARTZ
                || item == Items.AMETHYST_SHARD || item == Items.NETHERITE_SCRAP;
    }
}
