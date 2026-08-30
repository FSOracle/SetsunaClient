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

package com.viaversion.setsunavia.injection.mixin.features.networking.legacy_chat_signature;

import com.mojang.authlib.yggdrasil.response.KeyPairResponse;
import com.viaversion.setsunavia.injection.access.networking.legacy_chat_signature.IProfilePublicKey_Data;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = KeyPairResponse.class, remap = false)
public abstract class MixinKeyPairResponse implements IProfilePublicKey_Data {

    @Unique
    private byte[] setsunaVia$legacyKeySignature;

    @Override
    public byte[] setsunavia$getLegacyPublicKeySignature() {
        return this.setsunaVia$legacyKeySignature;
    }

    @Override
    public void setsunavia$setLegacyPublicKeySignature(byte[] signature) {
        this.setsunaVia$legacyKeySignature = signature;
    }

}
