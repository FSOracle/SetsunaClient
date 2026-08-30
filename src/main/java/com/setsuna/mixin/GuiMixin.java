package com.setsuna.mixin;

import com.setsuna.module.modules.render.NoRender;
import com.setsuna.module.modules.render.DeltaForceStyle;
import com.setsuna.ui.hud.ScoreboardHUD;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class GuiMixin {

    @Unique
    private boolean setsuna$deltaHotbarPosePushed;

    @Unique
    private boolean setsuna$scoreboardPosePushed;

    @Inject(method = "displayScoreboardSidebar", at = @At("HEAD"))
    private void setsuna$positionScoreboard(GuiGraphicsExtractor graphics,
                                            net.minecraft.world.scores.Objective objective,
                                            CallbackInfo ci) {
        setsuna$scoreboardPosePushed = ScoreboardHUD.INSTANCE.positionVanilla(graphics, objective);
    }

    @ModifyArg(
            method = "displayScoreboardSidebar",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"),
            index = 4)
    private int setsuna$removeScoreboardBackground(int color) {
        return setsuna$scoreboardPosePushed && ScoreboardHUD.INSTANCE.shouldRemoveBackground()
                ? 0x00000000 : color;
    }

    @Inject(method = "displayScoreboardSidebar", at = @At("RETURN"))
    private void setsuna$restoreScoreboardPose(GuiGraphicsExtractor graphics,
                                               net.minecraft.world.scores.Objective objective,
                                               CallbackInfo ci) {
        if (!setsuna$scoreboardPosePushed) return;
        graphics.pose().popMatrix();
        setsuna$scoreboardPosePushed = false;
    }

    @Inject(method = "extractHotbarAndDecorations", at = @At("HEAD"), cancellable = true)
    private void setsuna$hideDeltaForceHotbar(GuiGraphicsExtractor graphics,
                                            DeltaTracker deltaTracker, CallbackInfo ci) {
        setsuna$deltaHotbarPosePushed = false;
        DeltaForceStyle deltaForce = DeltaForceStyle.INSTANCE;
        if (!deltaForce.isEnabled()) return;
        float progress = deltaForce.transitionProgress();
        if (progress >= 0.999F) {
            ci.cancel();
            return;
        }
        graphics.pose().pushMatrix();
        graphics.pose().translate(0.0F, deltaForce.vanillaHotbarOffset());
        setsuna$deltaHotbarPosePushed = true;
    }

    @Inject(method = "extractHotbarAndDecorations", at = @At("RETURN"))
    private void setsuna$restoreDeltaForceHotbarPose(GuiGraphicsExtractor graphics,
                                                     DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!setsuna$deltaHotbarPosePushed) return;
        graphics.pose().popMatrix();
        setsuna$deltaHotbarPosePushed = false;
    }

    @Inject(method = "extractPlayerHealth", at = @At("HEAD"), cancellable = true)
    private void setsuna$hideDeltaForceHealth(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (DeltaForceStyle.INSTANCE.isEnabled()) ci.cancel();
    }

    @Inject(method = "extractFood", at = @At("HEAD"), cancellable = true)
    private void setsuna$hideDeltaForceFood(GuiGraphicsExtractor graphics,
                                           net.minecraft.world.entity.player.Player player,
                                           int y, int unused, CallbackInfo ci) {
        if (DeltaForceStyle.INSTANCE.isEnabled()) ci.cancel();
    }

    @Inject(method = "extractEffects", at = @At("HEAD"), cancellable = true)
    private void setsuna$hidePotionEffects(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        NoRender noRender = NoRender.INSTANCE;
        if (noRender.isEnabled() && noRender.potionEffects.get()) {
            ci.cancel();
        }
    }
}
