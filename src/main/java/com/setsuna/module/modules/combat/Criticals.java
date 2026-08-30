package com.setsuna.module.modules.combat;

import com.setsuna.event.Listen;
import com.setsuna.event.events.AttackEvent;
import com.setsuna.event.events.KeyboardInputEvent;
import com.setsuna.event.events.PlayerTickEvent;
import com.setsuna.mixin.LocalPlayerAccessor;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.DoubleSetting;
import com.setsuna.setting.settings.EnumSetting;
import com.setsuna.util.network.PacketUtils;
import com.setsuna.util.player.SkipTickUtility;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Packet and Heypixel critical-hit modes shared by both local KillAura modules. */
public final class Criticals extends Module {

    public static final Criticals INSTANCE = new Criticals();

    public enum Mode {
        Packet,
        NCP,
        UpdatedNCP,
        OldNCP,
        Heypixel,
        Hoplite
    }

    private final EnumSetting<Mode> mode = add(new EnumSetting<>("Mode", Mode.Packet)
            .onChange(this::onModeChanged));
    private final BooleanSetting groundOnly = add(new BooleanSetting("Ground Only", false)
            .visibleWhen(() -> !mode.is(Mode.Heypixel) && !mode.is(Mode.Hoplite)));
    private final DoubleSetting criticalRange = add(new DoubleSetting("Critical Range", 3.0, 1.0, 3.2, 0.1)
            .visibleWhen(() -> mode.is(Mode.Heypixel)));
    private final BooleanSetting autoJump = add(new BooleanSetting("Auto Jump", true)
            .visibleWhen(() -> mode.is(Mode.Heypixel)));
    private final BooleanSetting skipTicks = add(new BooleanSetting("Skip Ticks", true)
            .visibleWhen(() -> mode.is(Mode.Heypixel)));

    private boolean armedSkipTick;

    private Criticals() {
        super("Criticals", Category.COMBAT);
    }

    @Override
    public String getInfo() {
        return mode.displayValue();
    }

    @Override
    protected void onDisable() {
        armedSkipTick = false;
        SkipTickUtility.reset();
    }

    private void onModeChanged(Mode ignored) {
        armedSkipTick = false;
        SkipTickUtility.reset();
    }

    @Listen
    private void onMoveInput(KeyboardInputEvent event) {
        if (!mode.is(Mode.Heypixel) || !autoJump.get() || noPlayer()) {
            return;
        }
        if (event.isJump() || mc.options.keyJump.isDown() || hasHeadBlock() || !hasActiveAura()) {
            return;
        }

        LivingEntity target = getAuraTarget();
        if (target != null && mc.player.onGround() && mc.player.distanceTo(target) <= criticalRange.get()) {
            event.setJump(true);
        }
    }

    @Listen
    private void onPlayerTick(PlayerTickEvent.Pre event) {
        if (noPlayer() || mc.player.onGround()) {
            armedSkipTick = false;
        }
    }

    @Listen
    private void onAttack(AttackEvent event) {
        if (noPlayer()) {
            return;
        }

        if (mode.is(Mode.Hoplite)) {
            doHopliteCritical(event.getTarget());
            return;
        }
        if (event.getAttacker() != mc.player) {
            return;
        }
        if (mode.is(Mode.Heypixel)) {
            doHeypixelCritical(event.getTarget());
        } else if (mode.is(Mode.Packet)) {
            doPacketCritical(event.getTarget());
        } else {
            doNcpCritical(event.getTarget());
        }
    }

    private void doHopliteCritical(Entity target) {
        if (!shouldSpawnHopliteCritical(mc.level != null && target != null, mc.player.onGround())) {
            return;
        }
        mc.level.addParticle(
                ParticleTypes.CRIT,
                target.getX(),
                target.getY() + target.getBbHeight() * 0.5D,
                target.getZ(),
                0.0D,
                0.0D,
                0.0D);
    }

    static boolean shouldSpawnHopliteCritical(boolean contextAvailable, boolean playerOnGround) {
        return contextAvailable && !playerOnGround;
    }

    private void doNcpCritical(Entity target) {
        if (target instanceof EndCrystal || !(target instanceof LivingEntity living) || !living.isAlive()) {
            return;
        }
        if (!isCriticalHitAvailable()
                || (groundOnly.get() && !mc.player.onGround() && !mc.player.getAbilities().flying)
                || mc.getConnection() == null) {
            return;
        }

        switch (mode.get()) {
            case UpdatedNCP -> {
                sendOffset(0.000000271875D);
                sendOffset(0.0D);
            }
            case NCP -> {
                sendOffset(0.0625D);
                sendOffset(0.0D);
            }
            case OldNCP -> {
                sendOffset(0.00001058293536D);
                sendOffset(0.00000916580235D);
                sendOffset(0.00000010371854D);
            }
            default -> {
                return;
            }
        }

        mc.level.addParticle(
                ParticleTypes.CRIT,
                target.getX(),
                target.getY() + target.getBbHeight() * 0.5D,
                target.getZ(),
                0.0D,
                0.0D,
                0.0D);
    }

    private void sendOffset(double yOffset) {
        PacketUtils.sendSilently(new ServerboundMovePlayerPacket.Pos(
                mc.player.getX(),
                mc.player.getY() + yOffset,
                mc.player.getZ(),
                false,
                mc.player.horizontalCollision));
    }

    private void doPacketCritical(Entity target) {
        if (!(target instanceof LivingEntity living) || !living.isAlive()) {
            return;
        }
        if (!isCriticalHitAvailable() || (groundOnly.get() && !mc.player.onGround())) {
            return;
        }
        if (mc.level == null || mc.getConnection() == null) {
            return;
        }

        AABB raisedBox = mc.player.getBoundingBox().move(0.0, 0.0625, 0.0);
        if (mc.level.getBlockCollisions(mc.player, raisedBox).iterator().hasNext()) {
            return;
        }

        Vec3 position = mc.player.position();
        boolean onGround = mc.player.onGround();
        try {
            mc.player.setPos(position.x, position.y + 0.0625, position.z);
            mc.player.setOnGround(false);
            ((LocalPlayerAccessor) (Object) mc.player).setsuna$sendPosition();

            mc.player.setPos(position.x, position.y + 0.00125, position.z);
            mc.player.setOnGround(false);
            ((LocalPlayerAccessor) (Object) mc.player).setsuna$sendPosition();
        } finally {
            mc.player.setPos(position);
            mc.player.setOnGround(onGround);
        }
    }

    private void doHeypixelCritical(Entity target) {
        if (!(target instanceof LivingEntity living)
                || !hasActiveAura()
                || cantCrit(living)) {
            resetHeypixelState();
            return;
        }

        if (mc.player.getDeltaMovement().y < 0.0
                && !mc.player.onGround()
                && mc.player.distanceTo(living) <= criticalRange.get()) {
            if (skipTicks.get() && !armedSkipTick) {
                SkipTickUtility.addSkipTicks(1);
                armedSkipTick = true;
            }
            cancelSprint();
        } else {
            resetHeypixelState();
        }
    }

    private void resetHeypixelState() {
        armedSkipTick = false;
    }

    private boolean cancelSprint() {
        if (noPlayer()) {
            return false;
        }

        boolean wasSprinting = mc.player.isSprinting() || mc.options.keySprint.isDown();
        if (!wasSprinting) {
            return false;
        }

        mc.options.keySprint.setDown(false);
        mc.player.setSprinting(false);
        if (mc.getConnection() != null) {
            mc.getConnection().send(new ServerboundPlayerCommandPacket(
                    mc.player,
                    ServerboundPlayerCommandPacket.Action.STOP_SPRINTING
            ));
        }
        return true;
    }

    private boolean cantCrit(Entity target) {
        if (!(target instanceof LivingEntity living)) {
            return true;
        }
        return !isCriticalHitAvailable()
                || mc.player.onClimbable()
                || mc.player.isInWater()
                || mc.player.isInLava()
                || mc.player.isPassenger()
                || hasHeadBlock()
                || living.hurtTime > 10
                || living.getHealth() <= 0.0F;
    }

    private boolean isCriticalHitAvailable() {
        return !mc.player.isInWater()
                && !mc.player.isInLava()
                && !mc.player.onClimbable()
                && !mc.player.hasEffect(MobEffects.BLINDNESS)
                && !mc.player.isPassenger()
                && !mc.player.isFallFlying()
                && !mc.player.isNoGravity()
                && !mc.player.getAbilities().flying;
    }

    private boolean hasHeadBlock() {
        if (mc.level == null || mc.player == null) {
            return false;
        }
        AABB box = mc.player.getBoundingBox().move(0.0, 0.5, 0.0);
        return mc.level.getBlockCollisions(mc.player, box).iterator().hasNext();
    }

    private boolean hasActiveAura() {
        return KillAura.INSTANCE.isEnabled() || KillAuraPlus.INSTANCE.isEnabled();
    }

    private LivingEntity getAuraTarget() {
        LivingEntity primary = KillAura.INSTANCE.isEnabled() ? KillAura.INSTANCE.target : null;
        LivingEntity secondary = KillAuraPlus.INSTANCE.target();
        if (primary == null) {
            return secondary;
        }
        if (secondary == null) {
            return primary;
        }
        return mc.player.distanceTo(primary) <= mc.player.distanceTo(secondary) ? primary : secondary;
    }
}
