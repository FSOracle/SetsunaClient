package com.setsuna.mixin;

import com.setsuna.event.EventBus;
import com.setsuna.event.events.CharInputEvent;
import com.setsuna.event.events.KeyInputEvent;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fires a {@link KeyInputEvent} for every raw key state change before vanilla
 * handling; cancelling the event swallows the key for this frame.
 */
@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void setsuna$keyPress(long handle, int action, KeyEvent event, CallbackInfo ci) {
        KeyInputEvent posted = EventBus.INSTANCE.post(
                new KeyInputEvent(event.key(), event.scancode(), action, event.modifiers()));
        if (posted.isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void setsuna$charTyped(long handle, CharacterEvent character, CallbackInfo ci) {
        CharInputEvent posted = EventBus.INSTANCE.post(new CharInputEvent(
                character.codepoint(),
                character.codepointAsString(),
                character.isAllowedChatCharacter()));
        if (posted.isCancelled()) {
            ci.cancel();
        }
    }
}
