package com.setsuna.module.modules.combat;

import com.setsuna.event.Listen;
import com.setsuna.event.events.BlinkPacketEvent;
import com.setsuna.event.events.TickEvent;
import com.setsuna.manager.FriendManager;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.DoubleSetting;
import com.setsuna.setting.settings.EnumSetting;
import com.setsuna.setting.settings.IntSetting;
import com.setsuna.util.network.BlinkManager;
import com.setsuna.util.player.TeamColorUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.concurrent.ThreadLocalRandom;

/** Direct Java port of LiquidBounce nextgen ModuleFakeLag. */
public final class FakeLag extends Module {
    public static final FakeLag INSTANCE = new FakeLag();
    private enum Mode { Constant, Dynamic }
    private final DoubleSetting rangeMin = add(new DoubleSetting("Range Min", 2, 0, 10, .1));
    private final DoubleSetting rangeMax = add(new DoubleSetting("Range Max", 5, 0, 10, .1));
    private final IntSetting delayMin = add(new IntSetting("Delay Min", 300, 0, 1000, 10));
    private final IntSetting delayMax = add(new IntSetting("Delay Max", 600, 0, 1000, 10));
    private final IntSetting recoilTime = add(new IntSetting("Recoil Time", 250, 0, 1000, 10));
    private final EnumSetting<Mode> mode = add(new EnumSetting<>("Mode", Mode.Dynamic));
    private final BooleanSetting entityInteract = add(new BooleanSetting("Entity Interact", true));
    private final BooleanSetting blockInteract = add(new BooleanSetting("Block Interact", true));
    private final BooleanSetting action = add(new BooleanSetting("Action", true));
    private long nextDelay = 300, recoilUntil;
    private boolean enemyNearby;
    private FakeLag() { super("FakeLag", Category.COMBAT); }

    @Listen private void tick(TickEvent.Pre e) { enemyNearby = findEnemy() != null; }
    @Listen private void blink(BlinkPacketEvent e) {
        if (e.origin() != BlinkPacketEvent.Origin.OUTGOING || noPlayer() || mc.player.isDeadOrDying()
                || mc.player.isInWater() || mc.screen != null || System.currentTimeMillis() < recoilUntil) return;
        if (BlinkManager.INSTANCE.isAboveTime(nextDelay)) { nextDelay = sample(delayMin.get(), delayMax.get()); return; }
        Packet<?> p = e.packet();
        if (p != null && flushOn(p)) { recoilUntil = System.currentTimeMillis() + recoilTime.get(); return; }
        if (p instanceof ClientboundPlayerPositionPacket || p instanceof ServerboundResourcePackPacket) { recoilUntil = System.currentTimeMillis() + recoilTime.get(); return; }
        if (p instanceof ClientboundSetEntityMotionPacket x && x.id() == mc.player.getId() && !x.movement().equals(Vec3.ZERO)) { recoilUntil = System.currentTimeMillis() + recoilTime.get(); return; }
        if (p instanceof ClientboundExplodePacket x && x.playerKnockback().filter(v -> !v.equals(Vec3.ZERO)).isPresent()) { recoilUntil = System.currentTimeMillis() + recoilTime.get(); return; }
        if (p instanceof ClientboundSetHealthPacket) { recoilUntil = System.currentTimeMillis() + recoilTime.get(); return; }
        if (mc.player.isUsingItem() && isConsumable()) return;
        if (mode.is(Mode.Constant)) { e.setAction(BlinkPacketEvent.Action.QUEUE); return; }
        if (!enemyNearby) return;
        Vec3 server = BlinkManager.INSTANCE.firstOutgoingPosition();
        if (server == null) { e.setAction(BlinkPacketEvent.Action.QUEUE); return; }
        AABB serverBox = mc.player.getBoundingBox().move(server.subtract(mc.player.position()));
        double sd = Double.MAX_VALUE, cd = Double.MAX_VALUE; boolean intersects = false, found = false;
        for (Entity entity : mc.level.entitiesForRendering()) if (valid(entity) && entity.position().distanceTo(server) <= rangeMax.get()) {
            found = true; intersects |= entity.getBoundingBox().intersects(serverBox);
            sd = Math.min(sd, entity.position().distanceTo(server)); cd = Math.min(cd, entity.position().distanceTo(mc.player.position()));
        }
        if (found && sd >= cd && !intersects) e.setAction(BlinkPacketEvent.Action.QUEUE);
    }
    private boolean flushOn(Packet<?> p) {
        return entityInteract.get() && (p instanceof ServerboundInteractPacket || p instanceof ServerboundSwingPacket)
                || blockInteract.get() && (p instanceof ServerboundUseItemOnPacket || p instanceof ServerboundSignUpdatePacket)
                || action.get() && p instanceof ServerboundPlayerActionPacket;
    }
    private Entity findEnemy() { Entity best = null; double d = rangeMax.get(); for (Entity e : mc.level.entitiesForRendering()) if (valid(e)) { double x = mc.player.distanceTo(e); if (x < d && x >= rangeMin.get()) { d=x; best=e; } } return best; }
    private boolean valid(Entity e) { if (e == mc.player || !(e instanceof LivingEntity l) || e instanceof ArmorStand || !l.isAlive()) return false; if (e instanceof Player p) return !FriendManager.INSTANCE.isFriend(p) && !TeamColorUtils.isFriendlyColor(p) && (TeamColorUtils.isEnemyColor(p) || mc.player.canHarmPlayer(p)); return !mc.player.isAlliedTo(e); }
    private boolean isConsumable() { var s=mc.player.getUseItem(); return s.getComponents().has(DataComponents.FOOD) || s.getComponents().has(DataComponents.CONSUMABLE) || s.getUseAnimation()==ItemUseAnimation.EAT || s.getUseAnimation()==ItemUseAnimation.DRINK; }
    private static long sample(int a,int b) { int min=Math.min(a,b),max=Math.max(a,b); return min==max?min:ThreadLocalRandom.current().nextLong(min,(long)max+1); }
    @Override protected void onEnable() { nextDelay=sample(delayMin.get(),delayMax.get()); recoilUntil=0; }
    @Override protected void onDisable() { BlinkManager.INSTANCE.flush(BlinkPacketEvent.Origin.OUTGOING); enemyNearby=false; }
    @Override public String getInfo() { return mode.displayValue(); }
}
