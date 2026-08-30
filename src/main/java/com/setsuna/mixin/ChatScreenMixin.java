package com.setsuna.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.setsuna.command.CommandManager;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Intercepts dot commands at the chat-screen send call, before signing. */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {

    @Shadow
    protected EditBox input;

    @WrapOperation(
            method = "handleChatInput(Ljava/lang/String;Z)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;sendChat(Ljava/lang/String;)V"))
    private void setsuna$handleClientCommand(ClientPacketListener listener, String message,
                                            Operation<Void> original) {
        if (!CommandManager.INSTANCE.handle(message)) {
            original.call(listener, message);
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void setsuna$completeClientCommand(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (event.key() != GLFW.GLFW_KEY_TAB) {
            return;
        }
        String current = input.getValue();
        String completed = CommandManager.INSTANCE.complete(current, input.getCursorPosition());
        if (completed == null || completed.equals(current)) {
            return;
        }
        input.setValue(completed);
        input.setCursorPosition(completed.length());
        cir.setReturnValue(true);
    }
}
