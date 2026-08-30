/*
 * This file is part of SetsunaVia - https://github.com/ViaVersion/SetsunaVia
 * Copyright (C) 2021-2026 the original authors
 *                         - Florian Reuth <git@florianreuth.de>
 *                         - RK_01/RaphiMC
 * Copyright (C) 2023-2026 ViaVersion and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.viaversion.setsunavia.injection.mixin.features.networking.level_loading;

import com.viaversion.setsunavia.injection.access.networking.downloading_terrain.ILevelLoadingScreen;
import com.viaversion.setsunavia.protocoltranslator.ProtocolTranslator;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelLoadingScreen.class)
public abstract class MixinLevelLoadingScreen extends Screen implements ILevelLoadingScreen {

    @Unique
    private long setsunaVia$loadStartTime;

    @Unique
    private int setsunaVia$tickCounter;

    @Unique
    private boolean setsunaVia$ready;

    @Unique
    private boolean setsunaVia$closeOnNextTick = false;

    public MixinLevelLoadingScreen(Component title) {
        super(title);
        this.setsunaVia$loadStartTime = Util.getMillis();
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void modifyCloseCondition(CallbackInfo ci) {
        if (Minecraft.getInstance() != null && Minecraft.getInstance().isLocalServer()) {
            // When joining the singleplayer, we set the target version to the native version when the integrated server is started
            // However this is already to late and the screen was already opened (and ticked), causing NPEs due to the network handler being null
            return;
        }

        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_20_2)) {
            ci.cancel();

            if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_18)) {
                if (this.setsunaVia$ready) {
                    this.onClose();
                }

                if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_12_1)) {
                    this.setsunaVia$tickCounter++;
                    if (this.setsunaVia$tickCounter % 20 == 0) {
                        this.minecraft.getConnection().send(new ServerboundKeepAlivePacket(0));
                    }
                }
            } else {
                if (System.currentTimeMillis() > this.setsunaVia$loadStartTime + 30000L) {
                    this.onClose();
                } else {
                    if (this.setsunaVia$closeOnNextTick) {
                        if (this.minecraft.player == null) return;

                        final BlockPos blockPos = this.minecraft.player.blockPosition();
                        final boolean isOutOfHeightLimit = this.minecraft.level != null && this.minecraft.level.isOutsideBuildHeight(blockPos.getY());
                        if (isOutOfHeightLimit || this.minecraft.levelRenderer.isSectionCompiledAndVisible(blockPos) || this.minecraft.player.isSpectator() || !this.minecraft.player.isAlive()) {
                            this.onClose();
                        }
                    } else {
                        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_19_1)) {
                            this.setsunaVia$closeOnNextTick = this.setsunaVia$ready || System.currentTimeMillis() > this.setsunaVia$loadStartTime + 2000;
                        } else {
                            this.setsunaVia$closeOnNextTick = this.setsunaVia$ready;
                        }
                    }
                }
            }
        }
    }

    @Override
    public void setsunaVia$setReady() {
        this.setsunaVia$ready = true;
    }

}
