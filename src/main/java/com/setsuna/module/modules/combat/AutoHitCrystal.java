package com.setsuna.module.modules.combat;

import com.setsuna.event.Listen;
import com.setsuna.event.events.PlayerTickEvent;
import com.setsuna.event.events.Render3DEvent;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.ColorSetting;
import com.setsuna.setting.settings.DoubleSetting;
import com.setsuna.setting.settings.IntSetting;
import com.setsuna.setting.settings.KeybindSetting;
import com.setsuna.util.client.KeybindUtils;
import com.setsuna.util.player.FindItemResult;
import com.setsuna.util.player.InvHelper;
import com.setsuna.util.player.InvUtils;
import com.setsuna.util.render.Render3DUtils;
import com.setsuna.util.world.BlockUtils;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Crosshair-driven crystal helper. While its activation key is held it builds
 * an obsidian base when needed, places a crystal, and attacks spawned crystals.
 */
public final class AutoHitCrystal extends Module {

    public static final AutoHitCrystal INSTANCE = new AutoHitCrystal();

    private final KeybindSetting activateKey = add(new KeybindSetting(
            "Activate Key", GLFW.GLFW_KEY_UNKNOWN));
    private final BooleanSetting checkPlace = add(new BooleanSetting("Check Place", false));
    private final IntSetting switchDelay = add(new IntSetting("Switch Delay", 0, 0, 20, 1));
    private final DoubleSetting switchChance = add(new DoubleSetting("Switch Chance", 100.0, 0.0, 100.0, 1.0));
    private final IntSetting placeDelay = add(new IntSetting("Place Delay", 0, 0, 20, 1));
    private final DoubleSetting placeChance = add(new DoubleSetting("Place Chance", 100.0, 0.0, 100.0, 1.0));
    private final IntSetting attackDelay = add(new IntSetting("Attack Delay", 0, 0, 20, 1));
    private final DoubleSetting attackRange = add(new DoubleSetting("Attack Range", 4.5, 1.0, 6.0, 0.1));
    private final BooleanSetting workWithTotem = add(new BooleanSetting("Work With Totem", false));
    private final BooleanSetting workWithCrystal = add(new BooleanSetting("Work With Crystal", false));
    private final BooleanSetting workWithPickaxe = add(new BooleanSetting("Work With Pickaxe", false));
    private final BooleanSetting swordSwap = add(new BooleanSetting("Sword Swap", true));
    private final BooleanSetting swingHand = add(new BooleanSetting("Swing Hand", true));
    private final BooleanSetting render = add(new BooleanSetting("Render", true));
    private final IntSetting fadeTime = add(new IntSetting("Fade Time", 500, 0, 3000, 50)
            .visibleWhen(render::get));
    private final ColorSetting sideColor = add(new ColorSetting(
            "Side Color", new Color(255, 183, 197, 100)).visibleWhen(render::get));
    private final ColorSetting lineColor = add(new ColorSetting(
            "Line Color", new Color(255, 105, 180, 255)).visibleWhen(render::get));

    private final List<RenderBox> renderBoxes = new ArrayList<>();
    private ClientLevel trackedLevel;
    private BlockPos pendingBase;
    private int switchClock;
    private int placeClock;
    private int attackClock;
    private boolean active;

    private AutoHitCrystal() {
        super("Auto Hit Crystal", Category.COMBAT);
    }

    @Override
    protected void onEnable() {
        trackedLevel = mc.level;
        resetActionState();
        renderBoxes.clear();
    }

    @Override
    protected void onDisable() {
        trackedLevel = null;
        resetActionState();
        renderBoxes.clear();
    }

    @Listen
    private void onTick(PlayerTickEvent.Pre event) {
        if (noPlayer() || mc.gameMode == null) {
            trackedLevel = null;
            resetActionState();
            renderBoxes.clear();
            return;
        }
        if (trackedLevel != mc.level) {
            trackedLevel = mc.level;
            resetActionState();
            renderBoxes.clear();
        }

        decrementClocks();
        if (mc.screen != null || !KeybindUtils.isPressed(activateKey.get())) {
            resetActionState();
            return;
        }
        if (!active && !canStartWith(mc.player.getMainHandItem())) {
            return;
        }
        active = true;

        EndCrystal crystal = crystalToAttack();
        if (crystal != null) {
            if (attackClock <= 0) {
                attackCrystal(crystal);
                attackClock = attackDelay.get();
            }
            return;
        }

        if (pendingBase != null && handlePendingBase()) {
            return;
        }

        HitResult crosshair = mc.hitResult;
        if (!(crosshair instanceof BlockHitResult blockHit)
                || crosshair.getType() != HitResult.Type.BLOCK) {
            return;
        }

        BlockPos clicked = blockHit.getBlockPos();
        BlockPos adjacent = clicked.relative(blockHit.getDirection());
        if (isCrystalBase(clicked)) {
            pendingBase = clicked;
            handlePendingBase();
            return;
        }
        if (isCrystalBase(adjacent)) {
            pendingBase = adjacent;
            handlePendingBase();
            return;
        }

        BlockState clickedState = mc.level.getBlockState(clicked);
        if (clickedState.is(Blocks.RESPAWN_ANCHOR)
                && clickedState.getValue(RespawnAnchorBlock.CHARGE) > 0) {
            return;
        }
        if (checkPlace.get() && !BlockUtils.canPlaceAt(adjacent)) {
            return;
        }
        placeObsidian(blockHit, adjacent);
    }

    @Listen
    private void onRender(Render3DEvent event) {
        if (!render.get() || renderBoxes.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        int duration = Math.max(1, fadeTime.get());
        renderBoxes.removeIf(box -> now - box.startedAt() > duration);
        for (RenderBox box : renderBoxes) {
            float progress = Mth.clamp((now - box.startedAt()) / (float) duration, 0.0F, 1.0F);
            float alpha = 1.0F - progress;
            Render3DUtils.drawFilledBox(box.box(), withAlpha(box.sideColor(), alpha));
            Render3DUtils.drawOutlineBox(
                    event.getPoseStack(), box.box(), withAlpha(box.lineColor(), alpha));
        }
    }

    private boolean handlePendingBase() {
        if (pendingBase == null) {
            return false;
        }
        if (!withinBlockReach(pendingBase)) {
            pendingBase = null;
            return false;
        }

        BlockState state = mc.level.getBlockState(pendingBase);
        if (!isCrystalBase(state)) {
            if (!state.canBeReplaced()) {
                pendingBase = null;
            }
            return true;
        }
        if (!crystalSpaceClear(pendingBase) || placeClock > 0) {
            return true;
        }
        if (!mc.player.isHolding(Items.END_CRYSTAL)) {
            if (swordSwap.get() && InvHelper.isSword(mc.player.getMainHandItem())) {
                selectFromHotbar(Items.END_CRYSTAL);
            } else if (switchClock <= 0 && chance(switchChance.get())) {
                if (selectFromHotbar(Items.END_CRYSTAL)) {
                    switchClock = switchDelay.get();
                }
            }
            return true;
        }
        if (chance(placeChance.get()) && placeCrystal(pendingBase)) {
            placeClock = placeDelay.get();
        }
        return true;
    }

    private void placeObsidian(BlockHitResult hit, BlockPos placementPosition) {
        if (placeClock > 0 || !BlockUtils.canPlaceAt(placementPosition)) {
            return;
        }
        if (!mc.player.isHolding(Items.OBSIDIAN)) {
            if (switchClock <= 0 && chance(switchChance.get()) && selectFromHotbar(Items.OBSIDIAN)) {
                switchClock = switchDelay.get();
            }
            return;
        }
        if (!chance(placeChance.get())) {
            return;
        }

        InteractionHand hand = handHolding(Items.OBSIDIAN);
        InteractionResult result = mc.gameMode.useItemOn(mc.player, hand, hit);
        if (!result.consumesAction()) {
            return;
        }
        swing(hand);
        pendingBase = placementPosition;
        placeClock = placeDelay.get();
        renderBoxes.add(new RenderBox(
                new AABB(placementPosition),
                lineColor.get(),
                sideColor.get(),
                System.currentTimeMillis()));
    }

    private boolean placeCrystal(BlockPos base) {
        InteractionHand hand = handHolding(Items.END_CRYSTAL);
        Vec3 hitPosition = Vec3.atCenterOf(base).add(0.0, 0.5, 0.0);
        BlockHitResult hit = new BlockHitResult(hitPosition, Direction.UP, base, false);
        InteractionResult result = mc.gameMode.useItemOn(mc.player, hand, hit);
        if (!result.consumesAction()) {
            return false;
        }
        swing(hand);
        return true;
    }

    private EndCrystal crystalToAttack() {
        if (mc.hitResult instanceof EntityHitResult entityHit
                && entityHit.getEntity() instanceof EndCrystal crystal
                && crystal.isAlive()
                && mc.player.distanceToSqr(crystal) <= attackRange.get() * attackRange.get()) {
            return crystal;
        }

        AABB searchBox = mc.player.getBoundingBox().inflate(attackRange.get());
        return mc.level.getEntities(
                        (Entity) null,
                        searchBox,
                        entity -> entity instanceof EndCrystal && entity.isAlive())
                .stream()
                .map(EndCrystal.class::cast)
                .min(Comparator.comparingDouble(mc.player::distanceToSqr))
                .orElse(null);
    }

    private void attackCrystal(EndCrystal crystal) {
        mc.gameMode.attack(mc.player, crystal);
        swing(InteractionHand.MAIN_HAND);
    }

    private boolean selectFromHotbar(Item item) {
        FindItemResult result = InvUtils.findInHotbar(item);
        if (!result.found()) {
            return false;
        }
        if (result.slot() != 40) {
            InvUtils.swap(result.slot(), false);
        }
        return true;
    }

    private InteractionHand handHolding(Item item) {
        return mc.player.getOffhandItem().is(item)
                ? InteractionHand.OFF_HAND
                : InteractionHand.MAIN_HAND;
    }

    private boolean canStartWith(ItemStack stack) {
        return InvHelper.isSword(stack)
                || workWithTotem.get() && stack.is(Items.TOTEM_OF_UNDYING)
                || workWithCrystal.get() && stack.is(Items.END_CRYSTAL)
                || workWithPickaxe.get() && InvHelper.isPickaxe(stack);
    }

    private boolean crystalSpaceClear(BlockPos base) {
        BlockPos above = base.above();
        if (!mc.level.getBlockState(above).canBeReplaced()) {
            return false;
        }
        AABB box = new AABB(above).expandTowards(0.0, 1.0, 0.0);
        return mc.level.getEntities((Entity) null, box, Entity::isAlive).isEmpty();
    }

    private boolean withinBlockReach(BlockPos position) {
        double reach = mc.player.blockInteractionRange();
        return mc.player.getEyePosition().distanceToSqr(position.getCenter()) <= reach * reach;
    }

    private boolean isCrystalBase(BlockPos position) {
        return isCrystalBase(mc.level.getBlockState(position));
    }

    private static boolean isCrystalBase(BlockState state) {
        return state.is(Blocks.OBSIDIAN) || state.is(Blocks.BEDROCK);
    }

    private void swing(InteractionHand hand) {
        if (swingHand.get()) {
            mc.player.swing(hand);
        } else if (mc.getConnection() != null) {
            mc.getConnection().send(new ServerboundSwingPacket(hand));
        }
    }

    private static boolean chance(double percent) {
        return percent >= 100.0
                || percent > 0.0 && ThreadLocalRandom.current().nextDouble(100.0) < percent;
    }

    private static Color withAlpha(Color color, float multiplier) {
        int alpha = Mth.clamp(Math.round(color.getAlpha() * multiplier), 0, 255);
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    private void decrementClocks() {
        if (switchClock > 0) {
            switchClock--;
        }
        if (placeClock > 0) {
            placeClock--;
        }
        if (attackClock > 0) {
            attackClock--;
        }
    }

    private void resetActionState() {
        switchClock = 0;
        placeClock = 0;
        attackClock = 0;
        pendingBase = null;
        active = false;
    }

    private record RenderBox(
            AABB box,
            Color lineColor,
            Color sideColor,
            long startedAt) {
    }
}
