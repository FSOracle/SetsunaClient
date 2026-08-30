package com.setsuna.module.modules.combat;

import com.setsuna.event.Listen;
import com.setsuna.event.events.*;
import com.setsuna.manager.FriendManager;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.setting.settings.*;
import com.setsuna.util.network.BlinkManager;
import com.setsuna.util.network.TrackedEntityPosition;
import com.setsuna.util.player.TeamColorUtils;
import com.setsuna.util.render.Render3DUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.awt.Color;
import java.util.concurrent.ThreadLocalRandom;

/** Direct Java port of LiquidBounce nextgen ModuleBacktrack. */
public final class Backtrack extends Module {
    public static final Backtrack INSTANCE = new Backtrack();
    private enum TargetMode { Attack, Range }
    private enum Esp { Box, Model, Wireframe, None }
    private final DoubleSetting rangeMin=add(new DoubleSetting("Range Min",1,0,10,.1));
    private final DoubleSetting rangeMax=add(new DoubleSetting("Range Max",3,0,10,.1));
    private final IntSetting delayMin=add(new IntSetting("Delay Min",100,0,1000,10));
    private final IntSetting delayMax=add(new IntSetting("Delay Max",150,0,1000,10));
    private final IntSetting nextDelayMin=add(new IntSetting("Next Backtrack Delay Min",0,0,2000,10));
    private final IntSetting nextDelayMax=add(new IntSetting("Next Backtrack Delay Max",10,0,2000,10));
    private final IntSetting trackingBuffer=add(new IntSetting("Tracking Buffer",500,0,2000,10));
    private final DoubleSetting chance=add(new DoubleSetting("Chance",50,0,100,1));
    private final BooleanSetting pauseOnHurt=add(new BooleanSetting("Pause On Hurt Time",false));
    private final IntSetting hurtTime=add(new IntSetting("Hurt Time",3,0,10,1).visibleWhen(pauseOnHurt::get));
    private final EnumSetting<TargetMode> targetMode=add(new EnumSetting<>("Target Mode",TargetMode.Attack));
    private final IntSetting lastAttackTime=add(new IntSetting("Last Attack Time To Work",1000,0,5000,50));
    private final EnumSetting<Esp> esp=add(new EnumSetting<>("ESP",Esp.Model));
    private final ColorSetting espColor=add(new ColorSetting("ESP Color",new Color(0,160,255,180)).visibleWhen(() -> !esp.is(Esp.None)));
    private final TrackedEntityPosition position=new TrackedEntityPosition();
    private Entity target;
    private long currentDelay=100, allowAt, trackingUntil, lastAttack;
    private int currentChance;
    private boolean shouldPause;
    private Backtrack(){super("Backtrack",Category.COMBAT);}

    @Listen private void attack(AttackEvent e){ lastAttack=System.currentTimeMillis(); currentChance=ThreadLocalRandom.current().nextInt(101); if(targetMode.is(TargetMode.Attack)) processTarget(e.getTarget()); }
    @Listen private void tick(TickEvent.Pre e){
        if(noPlayer()){clear(false,true);return;}
        if(targetMode.is(TargetMode.Range)){Entity enemy=findEnemy(); if(enemy==null) clear(true,false); else processTarget(enemy);}
        boolean queued=BlinkManager.INSTANCE.has(BlinkPacketEvent.Origin.INCOMING);
        if(shouldCancel()) BlinkManager.INSTANCE.flush(s -> s.origin()==BlinkPacketEvent.Origin.INCOMING && s.timestamp()<=System.currentTimeMillis()-currentDelay);
        else if(queued){BlinkManager.INSTANCE.flush(BlinkPacketEvent.Origin.INCOMING);clear(false,false);}
        if(!BlinkManager.INSTANCE.has(BlinkPacketEvent.Origin.INCOMING)) currentDelay=sample(delayMin.get(),delayMax.get());
    }
    @Listen private void blink(BlinkPacketEvent e){
        if(e.origin()!=BlinkPacketEvent.Origin.INCOMING)return;
        Packet<?> p=e.packet(); boolean cancel=shouldCancel(), queued=BlinkManager.INSTANCE.has(BlinkPacketEvent.Origin.INCOMING);
        if(p==null){if(cancel||queued)e.setAction(BlinkPacketEvent.Action.PASS);return;}
        if(!queued&&!cancel)return;
        if(p.getClass().getSimpleName().contains("Chat")){e.setAction(BlinkPacketEvent.Action.PASS);return;}
        if(p instanceof ClientboundPlayerPositionPacket||p instanceof ClientboundDisconnectPacket){clear(true,false);return;}
        if(p instanceof ClientboundSetHealthPacket h&&h.getHealth()<=0){clear(true,false);return;}
        Entity t=target;if(t==null)return; Vec3 actual=position.handle(p,mc.level,t);
        if(actual!=null&&boxDistanceSqr(t.getBoundingBox().move(actual.subtract(t.position())),mc.player.position())<boxDistanceSqr(t.getBoundingBox(),mc.player.position())){e.setAction(BlinkPacketEvent.Action.FLUSH);return;}
        e.setAction(BlinkPacketEvent.Action.QUEUE);
    }
    @Listen private void render(Render3DEvent e){if(target==null||esp.is(Esp.None)||position.base().equals(Vec3.ZERO))return; AABB box=target.getBoundingBox().move(position.base().subtract(target.position())); Render3DUtils.drawOutlineBox(e.getPoseStack(),box,espColor.get().getRGB(),1.5f,0x00FFFFFF);}
    private void processTarget(Entity enemy){shouldPause=enemy instanceof LivingEntity l&&l.hurtTime>=hurtTime.get();if(!shouldBacktrack(enemy))return;if(enemy!=target){clear(true,false);position.setBaseFrom(enemy);}target=enemy;}
    private boolean shouldBacktrack(Entity e){double d=Math.sqrt(boxDistanceSqr(e.getBoundingBox(),mc.player.position()));boolean in=d>=rangeMin.get()&&d<=rangeMax.get();if(in)trackingUntil=System.currentTimeMillis()+trackingBuffer.get();return (in||System.currentTimeMillis()<trackingUntil)&&valid(e)&&mc.player.tickCount>10&&currentChance<chance.get()&&System.currentTimeMillis()>=allowAt&&!(pauseOnHurt.get()&&shouldPause)&&System.currentTimeMillis()-lastAttack<=lastAttackTime.get();}
    private boolean shouldCancel(){return target!=null&&target.isAlive()&&shouldBacktrack(target);}
    private void clear(boolean handle,boolean clearOnly){if(handle&&!clearOnly)BlinkManager.INSTANCE.flush(BlinkPacketEvent.Origin.INCOMING);else if(clearOnly)BlinkManager.INSTANCE.remove(BlinkPacketEvent.Origin.INCOMING);if(target!=null)allowAt=System.currentTimeMillis()+sample(nextDelayMin.get(),nextDelayMax.get());target=null;position.setBase(Vec3.ZERO);}
    private Entity findEnemy(){Entity best=null;double d=rangeMax.get();for(Entity e:mc.level.entitiesForRendering())if(valid(e)){double x=mc.player.distanceTo(e);if(x<d){d=x;best=e;}}return best;}
    private boolean valid(Entity e){if(e==mc.player||!(e instanceof LivingEntity l)||e instanceof ArmorStand||!l.isAlive())return false;if(e instanceof Player p)return !FriendManager.INSTANCE.isFriend(p)&&!TeamColorUtils.isFriendlyColor(p)&&(TeamColorUtils.isEnemyColor(p)||mc.player.canHarmPlayer(p));return !mc.player.isAlliedTo(e);}
    private static double boxDistanceSqr(AABB b,Vec3 p){double x=Math.max(b.minX-p.x,Math.max(0,p.x-b.maxX)),y=Math.max(b.minY-p.y,Math.max(0,p.y-b.maxY)),z=Math.max(b.minZ-p.z,Math.max(0,p.z-b.maxZ));return x*x+y*y+z*z;}
    private static long sample(int a,int b){int min=Math.min(a,b),max=Math.max(a,b);return min==max?min:ThreadLocalRandom.current().nextLong(min,(long)max+1);}
    @Override protected void onEnable(){currentChance=ThreadLocalRandom.current().nextInt(101);lastAttack=0;clear(false,false);}
    @Override protected void onDisable(){clear(true,false);}
    @Override public String getInfo(){return targetMode.displayValue();}
}
