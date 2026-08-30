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

package com.viaversion.setsunavia.injection.mixin.core.integration;

import com.llamalad7.mixinextras.sugar.Local;
import com.viaversion.setsunavia.injection.access.core.IServerData;
import com.viaversion.setsunavia.save.impl.SettingsSave;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerData.class)
public abstract class MixinServerData implements IServerData {

    @Shadow
    public String name;

    @Unique
    private ProtocolVersion setsunaVia$forcedVersion = null;

    @Unique
    private boolean setsunaVia$passedDirectConnectScreen;

    @Unique
    private ProtocolVersion setsunaVia$translatingVersion;

    @Inject(method = "write", at = @At("TAIL"))
    private void saveForcedVersion(CallbackInfoReturnable<CompoundTag> cir, @Local CompoundTag nbtCompound) {
        if (setsunaVia$forcedVersion != null) {
            nbtCompound.putString("setsunavia_forcedversion", setsunaVia$forcedVersion.getName());
        }
    }

    @Inject(method = "read", at = @At("TAIL"))
    private static void loadForcedVersion(CompoundTag root, CallbackInfoReturnable<ServerData> cir, @Local ServerData serverInfo) {
        if (root.contains("setsunavia_forcedversion")) {
            final ProtocolVersion version = SettingsSave.protocolVersionByName(root.getStringOr("setsunavia_forcedversion", null));
            if (version != null) {
                ((IServerData) serverInfo).setsunaVia$forceVersion(version);
            }
        }
    }

    @Inject(method = "copyNameIconFrom", at = @At("RETURN"))
    private void syncForcedVersion(ServerData serverInfo, CallbackInfo ci) {
        setsunaVia$forceVersion(((IServerData) serverInfo).setsunaVia$forcedVersion());
    }

    @Override
    public ProtocolVersion setsunaVia$forcedVersion() {
        return setsunaVia$forcedVersion;
    }

    @Override
    public void setsunaVia$forceVersion(ProtocolVersion version) {
        setsunaVia$forcedVersion = version;
    }

    @Override
    public boolean setsunaVia$passedDirectConnectScreen() {
        return setsunaVia$passedDirectConnectScreen;
    }

    @Override
    public void setsunaVia$passDirectConnectScreen(boolean state) {
        setsunaVia$passedDirectConnectScreen = state;
    }

    @Override
    public ProtocolVersion setsunaVia$translatingVersion() {
        return setsunaVia$translatingVersion;
    }

    @Override
    public void setsunaVia$setTranslatingVersion(ProtocolVersion version) {
        setsunaVia$translatingVersion = version;
    }

}
