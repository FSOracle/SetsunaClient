package com.setsuna.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.setsuna.event.EventBus;
import com.setsuna.event.events.RaytraceEvent;
import com.setsuna.event.events.StrafeEvent;
import com.setsuna.module.modules.movement.Velocity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(Entity.class)
public abstract class EntityMixin {

    @WrapOperation(
            method = "getViewVector",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;calculateViewVector(FF)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 setsuna$hookViewVector(Entity instance, float pitch, float yaw, Operation<Vec3> original) {
        if (instance != Minecraft.getInstance().player) {
            return original.call(instance, pitch, yaw);
        }

        RaytraceEvent event = EventBus.INSTANCE.post(new RaytraceEvent(yaw, pitch));
        return original.call(instance, event.getPitch(), event.getYaw());
    }

    @WrapOperation(
            method = "moveRelative",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;getYRot()F"))
    private float setsuna$hookMoveRelativeYaw(Entity instance, Operation<Float> original) {
        if (instance == Minecraft.getInstance().player) {
            StrafeEvent event = EventBus.INSTANCE.post(new StrafeEvent(instance.getYRot()));
            return event.getYaw();
        }
        return original.call(instance);
    }

    @ModifyArgs(
            method = "push(Lnet/minecraft/world/entity/Entity;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;push(DDD)V"))
    private void setsuna$cancelEntityPush(Args args) {
        if ((Object) this == Minecraft.getInstance().player
                && Velocity.INSTANCE.isEnabled()
                && Velocity.INSTANCE.entityPush.get()) {
            args.set(0, 0.0);
            args.set(1, 0.0);
            args.set(2, 0.0);
        }
    }
}
