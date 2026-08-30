package com.setsuna.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.multiplayer.prediction.PredictiveAction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exposes vanilla's local block-breaking progress and retry delay. */
@Mixin(MultiPlayerGameMode.class)
public interface MultiPlayerGameModeAccessor {

    @Invoker("startPrediction")
    void setsuna$startPrediction(ClientLevel level, PredictiveAction action);

    @Accessor("destroyDelay")
    void setsuna$setDestroyDelay(int value);

    @Accessor("destroyProgress")
    float setsuna$getDestroyProgress();

    @Accessor("destroyProgress")
    void setsuna$setDestroyProgress(float value);
}
