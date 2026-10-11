package com.setsuna.module.modules.render;

import com.setsuna.event.Listen;
import com.setsuna.event.events.Render2DEvent;
import com.setsuna.event.events.Render3DEvent;
import com.setsuna.event.events.VanillaHudRenderEvent;
import com.setsuna.manager.FriendManager;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.render.SkijaUi;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.ButtonSetting;
import com.setsuna.setting.settings.ColorSetting;
import com.setsuna.setting.settings.DoubleSetting;
import com.setsuna.setting.settings.EnumSetting;
import com.setsuna.setting.settings.IntSetting;
import com.setsuna.util.player.HealthDetectionUtils;
import com.setsuna.util.render.Render3DUtils;
import com.setsuna.util.render.WorldToScreen;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.humbleui.skija.Canvas;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector4d;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Renders player information and player ESP overlays. */
public final class ESP extends Module {

    public enum PlayerMode {
        CSGO,
        Outline,
        Box2D,
        Box3D,
        Cylinder
    }

    private static final int ITEM_SIZE = 16;
    private static final int ITEM_GAP = 2;
    private static final int ITEM_ROW_GAP = 3;
    private static final float TAG_HEIGHT = 11.0F;
    private static final float TAG_OFFSET = 4.0F;
    private static final float EFFECT_HEIGHT = 9.0F;
    private static final float EFFECT_GAP = 1.0F;
    private static final float EFFECT_TOP_GAP = 2.0F;
    private static final float INFO_REFERENCE_HEIGHT = 36.0F;
    private static final int DARK_BORDER = 0x96000000;
    private static final int BAR_BACKGROUND = 0x64000000;
    private static final int ARMOR_COLOR = 0xFF4ECEE5;

    public static final ESP INSTANCE = new ESP();

    public final BooleanSetting players = add(new BooleanSetting("Player", true));
    public final DoubleSetting range = add(new DoubleSetting("Range", 64.0, 1.0, 256.0, 1.0));
    public final EnumSetting<PlayerMode> playerMode = add(new EnumSetting<>("Mode", PlayerMode.CSGO)
            .visibleWhen(players::get));
    public final BooleanSetting playerBox = add(new BooleanSetting("Player Box", true)
            .visibleWhen(() -> players.get() && playerMode.is(PlayerMode.CSGO)));
    public final ColorSetting playerColor = add(new ColorSetting("Player Color", new Color(128, 128, 128, 255))
            .visibleWhen(players::get));
    public final BooleanSetting box2dBackground = add(new BooleanSetting("Box2D Background", false)
            .visibleWhen(() -> players.get() && playerMode.is(PlayerMode.Box2D)));
    public final ColorSetting box2dBackgroundColor = add(new ColorSetting("Box2D Background Color",
            new Color(220, 48, 56), false)
            .visibleWhen(() -> players.get() && playerMode.is(PlayerMode.Box2D)
                    && box2dBackground.get()));
    public final IntSetting box2dBackgroundOpacity = add(new IntSetting("Box2D Background Opacity",
            72, 0, 255, 5)
            .visibleWhen(() -> players.get() && playerMode.is(PlayerMode.Box2D)
                    && box2dBackground.get()));
    public final DoubleSetting box2dCornerRadius = add(new DoubleSetting("Box2D Corner Radius",
            0.0, 0.0, 12.0, 0.5)
            .visibleWhen(() -> players.get() && playerMode.is(PlayerMode.Box2D)));
    public final ColorSetting friendColor = add(new ColorSetting("Friend Color", new Color(80, 255, 80, 255))
            .visibleWhen(players::get));
    public final BooleanSetting healthBar = add(new BooleanSetting("Health Bar", true)
            .visibleWhen(() -> players.get() && playerMode.is(PlayerMode.CSGO)));
    public final BooleanSetting armorBar = add(new BooleanSetting("Armor Bar", true)
            .visibleWhen(() -> players.get() && playerMode.is(PlayerMode.CSGO)));
    public final BooleanSetting showEquipment = add(new BooleanSetting("Show Equipment", true)
            .visibleWhen(players::get));
    public final BooleanSetting showHands = add(new BooleanSetting("Show Hands", true)
            .visibleWhen(() -> players.get() && showEquipment.get()));
    public final BooleanSetting showEffects = add(new BooleanSetting("Show Effects", true)
            .visibleWhen(players::get));
    public final DoubleSetting infoScale = add(new DoubleSetting("Info Scale", 1.0, 0.3, 3.0, 0.1)
            .visibleWhen(players::get));
    public final ColorSetting infoBackgroundColor = add(new ColorSetting("Info Background", new Color(0, 0, 0, 130))
            .visibleWhen(() -> players.get() && showEffects.get()));
    public final DoubleSetting playerOffsetX = add(new DoubleSetting("Player Offset X", 0.0, -160.0, 160.0, 0.5)
            .visibleWhen(() -> false));
    public final DoubleSetting playerOffsetY = add(new DoubleSetting("Player Offset Y", 0.0, -160.0, 160.0, 0.5)
            .visibleWhen(() -> false));
    public final DoubleSetting healthOffsetX = add(new DoubleSetting("Health Offset X", 0.0, -160.0, 160.0, 0.5)
            .visibleWhen(() -> false));
    public final DoubleSetting healthOffsetY = add(new DoubleSetting("Health Offset Y", 0.0, -160.0, 160.0, 0.5)
            .visibleWhen(() -> false));
    public final DoubleSetting armorOffsetX = add(new DoubleSetting("Armor Offset X", 0.0, -160.0, 160.0, 0.5)
            .visibleWhen(() -> false));
    public final DoubleSetting armorOffsetY = add(new DoubleSetting("Armor Offset Y", 0.0, -160.0, 160.0, 0.5)
            .visibleWhen(() -> false));
    public final DoubleSetting equipmentOffsetX = add(new DoubleSetting("Equipment Offset X", 0.0, -160.0, 160.0, 0.5)
            .visibleWhen(() -> false));
    public final DoubleSetting equipmentOffsetY = add(new DoubleSetting("Equipment Offset Y", 0.0, -160.0, 160.0, 0.5)
            .visibleWhen(() -> false));
    public final DoubleSetting effectsOffsetX = add(new DoubleSetting("Effects Offset X", 0.0, -160.0, 160.0, 0.5)
            .visibleWhen(() -> false));
    public final DoubleSetting effectsOffsetY = add(new DoubleSetting("Effects Offset Y", 0.0, -160.0, 160.0, 0.5)
            .visibleWhen(() -> false));
    public final ButtonSetting resetPosition = add(new ButtonSetting("Reset Position", this::resetPositions));
    public final BooleanSetting invisiblePlayers = add(new BooleanSetting("Invisible Players", false)
            .visibleWhen(players::get));
    public final BooleanSetting showSelf = add(new BooleanSetting("Show Self", false)
            .visibleWhen(players::get));

    private final List<PlayerDrawData> playerDrawList = new ArrayList<>();

    private record PlayerDrawData(float left, float top, float right, float bottom, int color,
                                  float healthRatio, List<ItemStack> armor, List<ItemStack> equipment,
                                  List<EffectRow> effects, float centerX, float anchorY, float infoScale) {
    }

    private record EffectRow(String text, int color) {
    }

    private ESP() {
        super("ESP", Category.RENDER);
    }

    public void resetPositions() {
        playerOffsetX.reset();
        playerOffsetY.reset();
        healthOffsetX.reset();
        healthOffsetY.reset();
        armorOffsetX.reset();
        armorOffsetY.reset();
        equipmentOffsetX.reset();
        equipmentOffsetY.reset();
        effectsOffsetX.reset();
        effectsOffsetY.reset();
    }

    @Override
    protected void onEnable() {
        playerDrawList.clear();
    }

    @Override
    protected void onDisable() {
        playerDrawList.clear();
    }

    @Listen
    private void onRender3D(Render3DEvent event) {
        playerDrawList.clear();
        if (noPlayer()) {
            return;
        }
        if (players.get()) {
            double rangeValue = range.get();
            rebuildPlayerDrawList(rangeValue * rangeValue, event.getPoseStack());
        }
    }

    @Listen
    private void onRender2D(Render2DEvent event) {
        if (playerDrawList.isEmpty()) {
            return;
        }

        Canvas canvas = event.canvas();
        for (PlayerDrawData data : playerDrawList) {
            if (playerMode.is(PlayerMode.CSGO)) {
                drawPlayerBox(canvas, data);
            } else if (playerMode.is(PlayerMode.Box2D)) {
                draw2DBox(canvas, data);
            }
            drawEffects(canvas, data);
        }
    }

    @Listen
    private void onVanillaHud(VanillaHudRenderEvent event) {
        if (!showEquipment.get() || playerDrawList.isEmpty()) {
            return;
        }

        GuiGraphicsExtractor graphics = event.graphics();
        for (PlayerDrawData data : playerDrawList) {
            if (data.equipment().isEmpty()) {
                continue;
            }

            int rowWidth = data.equipment().size() * ITEM_SIZE
                    + Math.max(0, data.equipment().size() - 1) * ITEM_GAP;
            int itemX = -rowWidth / 2 + Math.round(equipmentOffsetX.get().floatValue());
            int itemY = Math.round(-TAG_HEIGHT - TAG_OFFSET - ITEM_ROW_GAP - ITEM_SIZE
                    + equipmentOffsetY.get().floatValue());

            graphics.pose().pushMatrix();
            graphics.pose().translate(data.centerX(), data.anchorY());
            graphics.pose().scale(data.infoScale(), data.infoScale());
            for (ItemStack stack : data.equipment()) {
                graphics.item(stack, itemX, itemY);
                graphics.itemDecorations(mc.font, stack, itemX, itemY);
                itemX += ITEM_SIZE + ITEM_GAP;
            }
            graphics.pose().popMatrix();
        }
    }

    private void rebuildPlayerDrawList(double maxRangeSq, PoseStack poseStack) {
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        float baseInfoScale = infoScale.get().floatValue();

        for (Player target : mc.level.players()) {
            if (!isValidPlayer(target, maxRangeSq)) {
                continue;
            }

            Vec3 current = WorldToScreen.interpolate(target, partialTick);
            boolean friend = FriendManager.INSTANCE.isFriend(target);
            int boxColor = (friend ? friendColor : playerColor).argb();
            renderWorldPlayerMode(poseStack, target, current, boxColor);

            Vector4d projected = WorldToScreen.getEntityPositionsOn2D(target, partialTick);
            if (projected == null || projected.z - projected.x < 2.0 || projected.w - projected.y < 4.0) {
                continue;
            }

            float detectedHealth = HealthDetectionUtils.getHealth(target);
            float maximumHealth = Math.max(1.0F, Math.max(
                    target.getMaxHealth() + Math.max(0.0F, target.getAbsorptionAmount()), detectedHealth));
            float healthRatio = Mth.clamp(detectedHealth / maximumHealth, 0.0F, 1.0F);
            float projectedHeight = (float) (projected.w - projected.y);
            float renderInfoScale = scaledInfoSize(projectedHeight, baseInfoScale);
            float projectedCenterX = (float) ((projected.x + projected.z) * 0.5);
            float projectedTop = (float) projected.y;

            playerDrawList.add(new PlayerDrawData(
                    (float) projected.x, (float) projected.y, (float) projected.z, (float) projected.w,
                    boxColor, healthRatio, buildArmor(target), buildEquipment(target), buildEffects(target),
                    projectedCenterX, projectedTop, renderInfoScale));
        }
    }

    public static float scaledInfoSize(float boxHeight, float configuredScale) {
        return configuredScale * Mth.clamp(boxHeight / INFO_REFERENCE_HEIGHT, 0.3F, 3.0F);
    }

    private void renderWorldPlayerMode(PoseStack poseStack, Player target, Vec3 current, int boxColor) {
        double height = target.getBbHeight();
        switch (playerMode.get()) {
            case Box3D -> {
                AABB box = new AABB(current.x - 0.4, current.y, current.z - 0.4,
                        current.x + 0.4, current.y + height, current.z + 0.4);
                Render3DUtils.drawOutlineBox(poseStack, box, boxColor, 3.0F);
            }
            case Cylinder -> Render3DUtils.drawCylinder(poseStack, current, 0.5, height + 0.1,
                    boxColor, 1.0F, 18);
            default -> {
            }
        }
    }

    private void draw2DBox(Canvas canvas, PlayerDrawData data) {
        float width = data.right() - data.left();
        float height = data.bottom() - data.top();
        float left = data.left() + playerOffsetX.get().floatValue();
        float top = data.top() + playerOffsetY.get().floatValue();
        float radius = Math.min(box2dCornerRadius.get().floatValue(),
                Math.min(width, height) * 0.5F);
        if (box2dBackground.get()) {
            SkijaUi.rounded(canvas, left, top, width, height, radius,
                    withAlpha(box2dBackgroundColor.get().getRGB(), box2dBackgroundOpacity.get()));
        }
        drawRoundedOutline(canvas, left - 1.0F, top - 1.0F,
                width + 2.0F, height + 2.0F, radius + 1.0F, DARK_BORDER);
        drawRoundedOutline(canvas, left, top, width, height, radius, data.color());
        if (width > 2.0F && height > 2.0F) {
            drawRoundedOutline(canvas, left + 1.0F, top + 1.0F,
                    width - 2.0F, height - 2.0F, Math.max(0.0F, radius - 1.0F), DARK_BORDER);
        }
    }

    private void drawPlayerBox(Canvas canvas, PlayerDrawData data) {
        float left = data.left();
        float top = data.top();
        float width = data.right() - left;
        float height = data.bottom() - top;

        if (playerBox.get()) {
            float boxLeft = left + playerOffsetX.get().floatValue();
            float boxTop = top + playerOffsetY.get().floatValue();
            drawOutline(canvas, boxLeft - 1.0F, boxTop - 1.0F,
                    width + 2.0F, height + 2.0F, DARK_BORDER);
            drawOutline(canvas, boxLeft, boxTop, width, height, data.color());
            if (width > 2.0F && height > 2.0F) {
                drawOutline(canvas, boxLeft + 1.0F, boxTop + 1.0F,
                        width - 2.0F, height - 2.0F, DARK_BORDER);
            }
        }

        if (healthBar.get()) {
            float barX = left - 6.0F + healthOffsetX.get().floatValue();
            float barY = top + healthOffsetY.get().floatValue();
            drawBarFrame(canvas, barX, barY, 3.0F, height);
            float fillHeight = Math.max(0.0F, (height - 2.0F) * data.healthRatio());
            SkijaUi.fill(canvas, barX + 1.0F, barY + height - 1.0F - fillHeight,
                    1.0F, fillHeight, healthColor(data.healthRatio()));
            if (height > 50.0F) {
                for (int i = 1; i < 10; i++) {
                    float markerY = barY + height * i / 10.0F;
                    SkijaUi.fill(canvas, barX, markerY, 3.0F, 1.0F, DARK_BORDER);
                }
            }
        }

        if (armorBar.get()) {
            float segmentHeight = height / 4.0F;
            for (int i = 0; i < data.armor().size(); i++) {
                ItemStack stack = data.armor().get(i);
                if (stack.isEmpty()) {
                    continue;
                }
                float segmentTop = top + segmentHeight * i + armorOffsetY.get().floatValue();
                float segmentBottom = top + segmentHeight * (i + 1) + armorOffsetY.get().floatValue();
                float barHeight = Math.max(2.0F, segmentBottom - segmentTop);
                float barX = data.right() + 3.0F + armorOffsetX.get().floatValue();
                drawBarFrame(canvas, barX, segmentTop, 4.0F, barHeight);
                float durability = durability(stack);
                float fillHeight = Math.max(0.0F, (barHeight - 2.0F) * durability);
                SkijaUi.fill(canvas, barX + 1.0F, segmentBottom - 1.0F - fillHeight,
                        2.0F, fillHeight, ARMOR_COLOR);
            }
        }
    }

    private void drawEffects(Canvas canvas, PlayerDrawData data) {
        if (!showEffects.get() || data.effects().isEmpty()) {
            return;
        }

        canvas.save();
        canvas.translate(data.centerX(), data.anchorY());
        canvas.scale(data.infoScale(), data.infoScale());
        float effectY = -TAG_OFFSET + EFFECT_TOP_GAP + effectsOffsetY.get().floatValue();
        float pad = 3.0F;
        for (EffectRow effect : data.effects()) {
            float width = SkijaUi.textWidth(effect.text()) + pad * 2.0F;
            float left = -width / 2.0F + effectsOffsetX.get().floatValue();
            SkijaUi.fill(canvas, left, effectY, width, EFFECT_HEIGHT, infoBackgroundColor.argb());
            SkijaUi.text(canvas, effect.text(), left + pad, effectY, EFFECT_HEIGHT, effect.color());
            effectY += EFFECT_HEIGHT + EFFECT_GAP;
        }
        canvas.restore();
    }

    private static void drawOutline(Canvas canvas, float x, float y, float width, float height, int color) {
        SkijaUi.fill(canvas, x, y, width, 1.0F, color);
        SkijaUi.fill(canvas, x, y + height - 1.0F, width, 1.0F, color);
        if (height > 2.0F) {
            SkijaUi.fill(canvas, x, y + 1.0F, 1.0F, height - 2.0F, color);
            SkijaUi.fill(canvas, x + width - 1.0F, y + 1.0F, 1.0F, height - 2.0F, color);
        }
    }

    private static void drawRoundedOutline(Canvas canvas, float x, float y, float width,
                                           float height, float radius, int color) {
        SkijaUi.outline(canvas, x, y, width, height,
                Math.min(radius, Math.min(width, height) * 0.5F), 1.0F, color);
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    private static void drawBarFrame(Canvas canvas, float x, float y, float width, float height) {
        SkijaUi.fill(canvas, x, y, width, height, DARK_BORDER);
        if (width > 2.0F && height > 2.0F) {
            SkijaUi.fill(canvas, x + 1.0F, y + 1.0F, width - 2.0F, height - 2.0F, BAR_BACKGROUND);
        }
    }

    private List<ItemStack> buildArmor(Player player) {
        return List.of(
                player.getItemBySlot(EquipmentSlot.HEAD).copy(),
                player.getItemBySlot(EquipmentSlot.CHEST).copy(),
                player.getItemBySlot(EquipmentSlot.LEGS).copy(),
                player.getItemBySlot(EquipmentSlot.FEET).copy());
    }

    private List<ItemStack> buildEquipment(Player player) {
        if (!showEquipment.get()) {
            return List.of();
        }

        List<ItemStack> items = new ArrayList<>();
        if (showHands.get()) {
            appendItem(items, player.getOffhandItem());
        }
        appendItem(items, player.getItemBySlot(EquipmentSlot.HEAD));
        appendItem(items, player.getItemBySlot(EquipmentSlot.CHEST));
        appendItem(items, player.getItemBySlot(EquipmentSlot.LEGS));
        appendItem(items, player.getItemBySlot(EquipmentSlot.FEET));
        if (showHands.get()) {
            appendItem(items, player.getMainHandItem());
        }
        return List.copyOf(items);
    }

    private List<EffectRow> buildEffects(Player player) {
        if (!showEffects.get()) {
            return List.of();
        }

        List<EffectRow> effects = new ArrayList<>();
        for (MobEffectInstance instance : player.getActiveEffects()) {
            Holder<MobEffect> holder = instance.getEffect();
            String name = holder.value().getDisplayName().getString() + roman(instance.getAmplifier() + 1);
            String duration = instance.isInfiniteDuration() ? "Infinite" : formatDuration(instance.getDuration());
            int effectColor = 0xFF000000 | (holder.value().getColor() & 0x00FFFFFF);
            effects.add(new EffectRow(name + " " + duration, effectColor));
        }
        effects.sort(Comparator.comparing(EffectRow::text, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(effects);
    }

    public boolean shouldRenderOutline(Entity entity) {
        return isEnabled() && players.get() && playerMode.is(PlayerMode.Outline)
                && entity instanceof Player player
                && !noPlayer()
                && isValidPlayer(player, range.get() * range.get());
    }

    public int getPlayerColor(Player player) {
        return (FriendManager.INSTANCE.isFriend(player) ? friendColor : playerColor).argb();
    }

    private boolean isValidPlayer(Player target, double maxRangeSq) {
        if (!target.isAlive() || target.isSpectator()) {
            return false;
        }
        if (target.isInvisible() && !invisiblePlayers.get()) {
            return false;
        }
        if (target == mc.player) {
            return showSelf.get() && !mc.options.getCameraType().isFirstPerson();
        }
        return mc.player.distanceToSqr(target) <= maxRangeSq;
    }

    private static void appendItem(List<ItemStack> items, ItemStack stack) {
        if (!stack.isEmpty()) {
            items.add(stack.copy());
        }
    }

    private static float durability(ItemStack stack) {
        if (!stack.isDamageableItem() || stack.getMaxDamage() <= 0) {
            return 1.0F;
        }
        return Mth.clamp(1.0F - (float) stack.getDamageValue() / stack.getMaxDamage(), 0.0F, 1.0F);
    }

    private static int healthColor(float health) {
        float red = health < 0.5F ? 1.0F : (1.0F - health) * 2.0F;
        float green = health > 0.5F ? 1.0F : health * 2.0F;
        return new Color(red, green, 0.0F, 1.0F).getRGB();
    }

    private static String formatDuration(int ticks) {
        int seconds = Math.max(0, ticks / 20);
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    private static String roman(int level) {
        if (level <= 1) {
            return "";
        }
        return " " + switch (level) {
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> Integer.toString(level);
        };
    }
}
