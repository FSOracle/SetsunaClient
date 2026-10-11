package com.setsuna.module.modules.misc;

import com.setsuna.event.Listen;
import com.setsuna.event.events.PacketEvent;
import com.setsuna.event.events.PlayerTickEvent;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.module.modules.player.ChestStealer;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.util.network.PacketUtils;
import com.setsuna.util.player.ChatUtils;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Input;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Minecraft 26.1.2 adapter for Nilore's packet disabler collection. */
public final class Disabler extends Module {

    public static final Disabler INSTANCE = new Disabler();

    private static final double EPSILON = 1.0E-10D;
    private static final double[] PERFECT_ROTATION_STEPS = {
            0.0D, 5.625D, 11.25D, 16.875D, 22.5D, 28.125D, 33.75D,
            39.375D, 45.0D, 50.625D, 56.25D, 61.875D, 67.5D,
            73.125D, 78.75D, 84.375D, 90.0D
    };

    private final BooleanSetting badPacketsA = add(new BooleanSetting("Grim Bad PacketsA", true));
    private final BooleanSetting grimBadPacketsF = add(new BooleanSetting("Grim Bad PacketsF", false));
    private final BooleanSetting grimBadPacketsG = add(new BooleanSetting("Grim Bad PacketsG", false));
    private final BooleanSetting grimBadPacketsD = add(new BooleanSetting("Grim Bad PacketsD", false));
    private final BooleanSetting grimBadPacketY = add(new BooleanSetting("Grim Bad PacketY", false));
    private final BooleanSetting grimDuplicateRotPlace = add(new BooleanSetting("Grim Duplicate RotPlace", true));
    private final BooleanSetting acaFastSwitch = add(new BooleanSetting("ACA Fast Switch", true));
    private final BooleanSetting acaInventoryFrequency = add(new BooleanSetting("ACA Inventory Frequency", false));
    private final BooleanSetting acaAimStep = add(new BooleanSetting("ACA Aim Step", true));
    private final BooleanSetting acaPerfectRotation = add(new BooleanSetting("ACA Perfect Rotation", true));
    private final BooleanSetting acaInventoryMultiInteraction = add(new BooleanSetting("ACA Inv MultiInteraction", false));
    private final BooleanSetting themisBlink = add(new BooleanSetting("Themis Blink", true));
    private final BooleanSetting onlyRemoteServer = add(new BooleanSetting("Only Remote Server", false));
    private final BooleanSetting logging = add(new BooleanSetting("Logging", false));

    private final ConcurrentLinkedQueue<PacketData> acaInventoryPackets = new ConcurrentLinkedQueue<>();
    private final Random random = new Random();

    private int lastSentSlot = -1;
    private long inventoryOpenTime;
    private boolean inventoryOpen;
    private ServerboundContainerClosePacket storedClosePacket;
    private long inventoryCloseReleaseAt;
    private long themisBlinkLastSend = System.currentTimeMillis();
    private int themisBlinkCount;
    private float lastYaw;
    private float lastPitch;
    private float currentYaw;
    private float currentPitch;
    private float yawDiff;
    private float pitchDiff;
    private float lastPlacedYawDiff;
    private float lastPlacedPitchDiff;
    private boolean rotated;
    private boolean acaInventoryPass;
    private boolean lastSprinting;
    private boolean lastSneaking;
    private Input lastPlayerInput;

    private record PacketData(Packet<?> packet, long timestamp) {
    }

    private Disabler() {
        super("Disabler", Category.MISC);
    }

    @Override
    protected void onEnable() {
        resetState();
    }

    @Override
    protected void onDisable() {
        resetState();
    }

    @Listen
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!canProcess()) {
            return;
        }

        Packet<?> packet = event.getPacket();
        if (packet instanceof ClientboundLoginPacket) {
            resetState();
            return;
        }
        if (!isPlayableState()) {
            resetState();
            return;
        }

        releaseStoredClosePacket();

        if (packet instanceof ClientboundOpenScreenPacket) {
            inventoryOpenTime = System.currentTimeMillis();
            inventoryOpen = true;
            log("Inventory opened at: " + inventoryOpenTime);
        }

        if (acaInventoryMultiInteraction.get()
                && !acaInventoryPass
                && ChestStealer.INSTANCE.isEnabled()
                && packet instanceof ClientboundKeepAlivePacket) {
            acaInventoryPackets.add(new PacketData(packet, System.currentTimeMillis()));
            event.setCancelled(true);
            log("InvMultiInteraction: Stored keep-alive");
        }
    }

    @Listen
    private void onPacketSend(PacketEvent.Send event) {
        if (!canProcess()) {
            return;
        }
        if (!isPlayableState()) {
            resetState();
            return;
        }

        releaseStoredClosePacket();
        Packet<?> packet = event.getPacket();

        if (packet instanceof ServerboundSetCarriedItemPacket carriedItemPacket) {
            int slot = carriedItemPacket.getSlot();
            if (badPacketsA.get() && slot == lastSentSlot && slot != -1) {
                event.setCancelled(true);
                log("BadPacketsA: Cancelled duplicate slot packet: " + slot);
                return;
            }
            if (acaFastSwitch.get() && lastSentSlot != -1 && slot != lastSentSlot) {
                sendIntermediateSlots(lastSentSlot, slot);
            }
            lastSentSlot = slot;
            log("Processed slot switch: " + slot);
        }

        if (grimBadPacketY.get() && packet instanceof ServerboundSetCarriedItemPacket slotPacket) {
            int slot = slotPacket.getSlot();
            if (slot < 0 || slot > 8) {
                int clamped = clampHotbarSlot(slot);
                packet = new ServerboundSetCarriedItemPacket(clamped);
                event.setPacket(packet);
                lastSentSlot = clamped;
                log("Grim BadPacketY: Clamped invalid slot from " + slot + " to " + clamped);
            }
        }

        if (grimBadPacketsD.get()
                && packet instanceof ServerboundMovePlayerPacket movePacket
                && movePacket.hasRotation()) {
            float pitch = movePacket.getXRot(mc.player.getXRot());
            if (Math.abs(pitch) > 90.0F) {
                event.setCancelled(true);
                log("Grim BadPacketsD: Cancelled packet with invalid pitch: " + pitch);
                return;
            }
        }

        if (acaInventoryFrequency.get() && packet instanceof ServerboundContainerClosePacket closePacket) {
            if (inventoryOpen) {
                long now = System.currentTimeMillis();
                long openDuration = now - inventoryOpenTime;
                if (openDuration <= 150L) {
                    event.setCancelled(true);
                    storedClosePacket = closePacket;
                    inventoryCloseReleaseAt = now + 151L - openDuration;
                    inventoryOpen = false;
                    log("InventoryFrequency: Storing close packet for " + (151L - openDuration) + "ms");
                    return;
                }
                inventoryOpen = false;
                log("InventoryFrequency: Allowed close packet after " + openDuration + "ms");
            }
        }

        if (themisBlink.get()) {
            long now = System.currentTimeMillis();
            if (now - themisBlinkLastSend > 200L) {
                if (themisBlinkCount == 0) {
                    PacketUtils.sendSilently(new ServerboundPongPacket(0));
                }
                themisBlinkLastSend = now;
                themisBlinkCount = 0;
            }
            if (packet instanceof ServerboundMovePlayerPacket.StatusOnly
                    || packet instanceof ServerboundPongPacket) {
                themisBlinkCount++;
            }
        }

        if (grimDuplicateRotPlace.get()) {
            if (packet instanceof ServerboundMovePlayerPacket movePacket && movePacket.hasRotation()) {
                float previousYaw = currentYaw;
                float previousPitch = currentPitch;
                currentYaw = movePacket.getYRot(mc.player.getYRot());
                currentPitch = movePacket.getXRot(mc.player.getXRot());
                yawDiff = Math.abs(currentYaw - previousYaw);
                pitchDiff = Math.abs(currentPitch - previousPitch);
                rotated = true;

                float adjustedYaw = currentYaw;
                float adjustedPitch = currentPitch;
                float yawDelta = Math.abs(yawDiff - lastPlacedYawDiff);
                if (yawDiff > 2.0F && yawDelta < 1.0E-4F) {
                    adjustedYaw -= randomJitter();
                    log("DuplicateRotPlace: Modified yaw from " + currentYaw + " to " + adjustedYaw);
                }
                float pitchDelta = Math.abs(pitchDiff - lastPlacedPitchDiff);
                if (pitchDiff > 2.0F && pitchDelta < 1.0E-4F) {
                    adjustedPitch = Mth.clamp(adjustedPitch - randomJitter(), -90.0F, 90.0F);
                    log("DuplicateRotPlace: Modified pitch from " + currentPitch + " to " + adjustedPitch);
                }
                if (adjustedYaw != currentYaw || adjustedPitch != currentPitch) {
                    packet = withRotation(movePacket, adjustedYaw, adjustedPitch);
                    event.setPacket(packet);
                }
            } else if (packet instanceof ServerboundUseItemOnPacket && rotated) {
                lastPlacedYawDiff = yawDiff;
                lastPlacedPitchDiff = pitchDiff;
                rotated = false;
            }
        }

        if ((acaAimStep.get() || acaPerfectRotation.get())
                && packet instanceof ServerboundMovePlayerPacket movePacket
                && movePacket.hasRotation()) {
            float yaw = movePacket.getYRot(mc.player.getYRot());
            float pitch = movePacket.getXRot(mc.player.getXRot());
            boolean modified = false;

            if (acaAimStep.get() && isAimStepRotation(lastYaw, lastPitch, yaw, pitch)) {
                float[] stepped = applyAimStep(lastYaw, lastPitch, yaw, pitch, random);
                yaw = stepped[0];
                pitch = stepped[1];
                modified = true;
            }
            if (acaPerfectRotation.get()) {
                float[] imperfect = applyPerfectRotation(lastYaw, lastPitch, yaw, pitch, random);
                if (imperfect[0] != yaw || imperfect[1] != pitch) {
                    yaw = imperfect[0];
                    pitch = imperfect[1];
                    modified = true;
                    log("PerfectRotation: Modified rotation");
                }
            }
            if (modified) {
                pitch = Mth.clamp(pitch, -90.0F, 90.0F);
                packet = withRotation(movePacket, yaw, pitch);
                event.setPacket(packet);
            }
            lastYaw = yaw;
            lastPitch = pitch;
        }

        if (grimBadPacketsF.get() && packet instanceof ServerboundPlayerCommandPacket commandPacket) {
            if (commandPacket.getAction() == ServerboundPlayerCommandPacket.Action.START_SPRINTING) {
                if (lastSprinting) {
                    event.setCancelled(true);
                    log("BadPacketsF: Cancelled duplicate START_SPRINTING");
                }
                lastSprinting = true;
            } else if (commandPacket.getAction() == ServerboundPlayerCommandPacket.Action.STOP_SPRINTING) {
                if (!lastSprinting) {
                    event.setCancelled(true);
                    log("BadPacketsF: Cancelled duplicate STOP_SPRINTING");
                }
                lastSprinting = false;
            }
        }

        if (grimBadPacketsG.get() && packet instanceof ServerboundPlayerInputPacket inputPacket) {
            Input input = inputPacket.input();
            if (lastPlayerInput != null && input.equals(lastPlayerInput) && input.shift() == lastSneaking) {
                event.setCancelled(true);
                log("BadPacketsG: Cancelled duplicate "
                        + (input.shift() ? "PRESS_SHIFT_KEY" : "RELEASE_SHIFT_KEY"));
                return;
            }
            lastSneaking = input.shift();
            lastPlayerInput = input;
        }
    }

    @Listen
    private void onTick(PlayerTickEvent.Pre event) {
        releaseStoredClosePacket();
        if (!acaInventoryMultiInteraction.get() || noPlayer() || !ChestStealer.INSTANCE.isEnabled()) {
            return;
        }

        long now = System.currentTimeMillis();
        while (!acaInventoryPackets.isEmpty()
                && now - acaInventoryPackets.peek().timestamp() >= 2500L) {
            PacketData data = acaInventoryPackets.poll();
            if (data != null) {
                processAcaPacket(data.packet());
            }
        }
    }

    private boolean canProcess() {
        return mc.player != null && (!mc.isSingleplayer() || !onlyRemoteServer.get());
    }

    private boolean isPlayableState() {
        return mc.player != null
                && !mc.player.isSpectator()
                && mc.player.isAlive()
                && !mc.player.isDeadOrDying()
                && !(mc.screen instanceof ProgressScreen);
    }

    private void releaseStoredClosePacket() {
        if (storedClosePacket == null || System.currentTimeMillis() < inventoryCloseReleaseAt) {
            return;
        }
        PacketUtils.sendSilently(storedClosePacket);
        storedClosePacket = null;
        inventoryCloseReleaseAt = 0L;
        log("InventoryFrequency: Released stored close packet");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void processAcaPacket(Packet<?> packet) {
        if (mc.getConnection() == null) {
            return;
        }
        acaInventoryPass = true;
        try {
            ((Packet) packet).handle((PacketListener) mc.getConnection());
        } catch (RuntimeException ignored) {
            // Nilore deliberately ignores stale buffered keep-alives.
        } finally {
            acaInventoryPass = false;
        }
    }

    private void resetState() {
        lastSentSlot = -1;
        inventoryOpenTime = 0L;
        inventoryOpen = false;
        storedClosePacket = null;
        inventoryCloseReleaseAt = 0L;
        lastYaw = 0.0F;
        lastPitch = 0.0F;
        currentYaw = 0.0F;
        currentPitch = 0.0F;
        yawDiff = 0.0F;
        pitchDiff = 0.0F;
        lastPlacedYawDiff = 0.0F;
        lastPlacedPitchDiff = 0.0F;
        rotated = false;
        acaInventoryPass = false;
        acaInventoryPackets.clear();
        lastSprinting = false;
        lastSneaking = false;
        lastPlayerInput = null;
    }

    private float randomJitter() {
        return 0.001F + random.nextFloat() * 0.009F;
    }

    private void sendIntermediateSlots(int fromSlot, int toSlot) {
        for (int slot : intermediateSlots(fromSlot, toSlot)) {
            PacketUtils.sendSilently(new ServerboundSetCarriedItemPacket(slot));
            log("Sent intermediate slot: " + slot);
        }
    }

    private Packet<?> withRotation(ServerboundMovePlayerPacket packet, float yaw, float pitch) {
        if (packet.hasPosition()) {
            return new ServerboundMovePlayerPacket.PosRot(
                    packet.getX(mc.player.getX()),
                    packet.getY(mc.player.getY()),
                    packet.getZ(mc.player.getZ()),
                    yaw,
                    pitch,
                    packet.isOnGround(),
                    packet.horizontalCollision());
        }
        return new ServerboundMovePlayerPacket.Rot(
                yaw, pitch, packet.isOnGround(), packet.horizontalCollision());
    }

    private void log(String message) {
        if (logging.get()) {
            ChatUtils.addChatMessage("[Disabler] " + message);
        }
    }

    static int clampHotbarSlot(int slot) {
        return Math.max(0, Math.min(8, slot));
    }

    static List<Integer> intermediateSlots(int fromSlot, int toSlot) {
        List<Integer> slots = new ArrayList<>();
        int distance = Math.abs(fromSlot - toSlot);
        if (distance <= 1 || isWrapAroundSlot(fromSlot, toSlot)) {
            return slots;
        }
        int step = fromSlot > toSlot ? -1 : 1;
        for (int slot = fromSlot + step; slot != toSlot; slot += step) {
            if (slot >= 0 && slot <= 8) {
                slots.add(slot);
            }
        }
        return slots;
    }

    static boolean isAimStepRotation(float lastYaw, float lastPitch, float yaw, float pitch) {
        if (lastYaw == 0.0F && lastPitch == 0.0F) {
            return false;
        }
        double yawDelta = Math.abs(Mth.wrapDegrees(yaw - lastYaw));
        double pitchDelta = Math.abs(pitch - lastPitch);
        return yawDelta < 1.0E-5D && pitchDelta > 1.0D
                || pitchDelta < 1.0E-5D && yawDelta > 1.0D;
    }

    static float[] applyAimStep(float lastYaw, float lastPitch, float yaw, float pitch, Random random) {
        double yawDelta = Math.abs(Mth.wrapDegrees(yaw - lastYaw));
        double pitchDelta = Math.abs(pitch - lastPitch);
        float newYaw = yaw;
        float newPitch = pitch;
        if (yawDelta < 1.0E-5D && pitchDelta > 1.0D) {
            newYaw = lastYaw + (float) (random.nextGaussian() * 0.001D);
        }
        if (pitchDelta < 1.0E-5D && yawDelta > 1.0D) {
            newPitch = lastPitch + (float) (random.nextGaussian() * 0.001D);
        }
        return new float[]{newYaw, newPitch};
    }

    static float[] applyPerfectRotation(
            float lastYaw, float lastPitch, float yaw, float pitch, Random random) {
        if (lastYaw == 0.0F && lastPitch == 0.0F) {
            return new float[]{yaw, pitch};
        }
        double yawDelta = Math.abs(Mth.wrapDegrees(yaw - lastYaw));
        double pitchDelta = Math.abs(pitch - lastPitch);
        float newYaw = yaw;
        float newPitch = pitch;
        if (!isNearZeroOrMultiple(yawDelta) && isKnownRotationStep(yawDelta)) {
            newYaw += (float) (random.nextGaussian() * 0.005D);
        }
        if (!isNearZeroOrMultiple(pitchDelta) && isKnownRotationStep(pitchDelta)) {
            newPitch += (float) (random.nextGaussian() * 0.005D);
        }
        return new float[]{newYaw, newPitch};
    }

    private static boolean isNearZeroOrMultiple(double value) {
        return Math.abs(value) <= EPSILON || isMultipleOf(360.0D, value);
    }

    private static boolean isKnownRotationStep(double value) {
        if (!Double.isFinite(value)) {
            return false;
        }
        for (double step : PERFECT_ROTATION_STEPS) {
            if (isMultipleOf(step, value)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isMultipleOf(double base, double value) {
        if (base == 0.0D) {
            return Math.abs(value) <= EPSILON;
        }
        double ratio = value / base;
        return Math.abs(ratio - Math.round(ratio)) <= EPSILON;
    }

    private static boolean isWrapAroundSlot(int fromSlot, int toSlot) {
        return fromSlot == 0 && toSlot == 8 || fromSlot == 8 && toSlot == 0;
    }
}
