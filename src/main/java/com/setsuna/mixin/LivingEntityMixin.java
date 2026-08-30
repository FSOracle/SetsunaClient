package com.setsuna.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.setsuna.event.EventBus;
import com.setsuna.event.events.FallFlyingEvent;
import com.setsuna.event.events.GroundJumpEvent;
import com.setsuna.event.events.JumpEvent;
import com.setsuna.event.events.RotationAnimationEvent;
import com.setsuna.module.modules.movement.NoJumpDelay;
import com.setsuna.module.modules.movement.Scaffold;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {

    @Inject(method = "jumpFromGround", at = @At("HEAD"))
    private void setsuna$beforeGroundJump(CallbackInfo ci) {
        if ((Object) this == Minecraft.getInstance().player) {
            EventBus.INSTANCE.post(new GroundJumpEvent.Before());
        }
    }

    @Inject(method = "jumpFromGround", at = @At("TAIL"))
    private void setsuna$afterGroundJump(CallbackInfo ci) {
        if ((Object) this == Minecraft.getInstance().player) {
            EventBus.INSTANCE.post(new GroundJumpEvent.After());
        }
    }

    @ModifyReturnValue(method = "getMainHandItem", at = @At("RETURN"))
    private ItemStack setsuna$scaffoldSilentMainHand(ItemStack original) {
        if ((Object) this == Minecraft.getInstance().player) {
            return Scaffold.INSTANCE.modifyMainHandStack(original);
        }
        return original;
    }

    @WrapOperation(
            method = "tickHeadTurn",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;getYRot()F"))
    private float setsuna$modifyHeadYaw(LivingEntity entity, Operation<Float> original) {
        if (entity == Minecraft.getInstance().player) {
            RotationAnimationEvent event = EventBus.INSTANCE.post(new RotationAnimationEvent(
                    entity.getYRot(), 0.0f, 0.0f, 0.0f));
            return event.getYaw();
        }
        return original.call(entity);
    }

    @ModifyExpressionValue(
            method = "jumpFromGround",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getYRot()F"))
    private float setsuna$modifyJumpYaw(float original) {
        if ((Object) this == Minecraft.getInstance().player) {
            JumpEvent event = EventBus.INSTANCE.post(new JumpEvent(original));
            return event.getYaw();
        }
        return original;
    }

    @WrapOperation(
            method = "aiStep",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/entity/LivingEntity;noJumpDelay:I",
                    opcode = Opcodes.PUTFIELD,
                    ordinal = 1))
    private void setsuna$removeJumpDelay(LivingEntity instance, int value, Operation<Void> original) {
        if (instance == Minecraft.getInstance().player && NoJumpDelay.INSTANCE.isEnabled()) {
            original.call(instance, 0);
            return;
        }
        original.call(instance, value);
    }

    @ModifyExpressionValue(
            method = "updateFallFlyingMovement",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getLookAngle()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 setsuna$modifyFallFlyingLookAngle(Vec3 original) {
        if ((Object) this == Minecraft.getInstance().player) {
            FallFlyingEvent event = EventBus.INSTANCE.post(new FallFlyingEvent(
                    Minecraft.getInstance().player.getYRot(), Minecraft.getInstance().player.getXRot()));
            return Minecraft.getInstance().player.calculateViewVector(event.getPitch(), event.getYaw());
        }
        return original;
    }

    @ModifyExpressionValue(
            method = "updateFallFlyingMovement",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getXRot()F"))
    private float setsuna$modifyFallFlyingPitch(float original) {
        if ((Object) this == Minecraft.getInstance().player) {
            FallFlyingEvent event = EventBus.INSTANCE.post(new FallFlyingEvent(
                    Minecraft.getInstance().player.getYRot(), original));
            return event.getPitch();
        }
        return original;
    }
}
