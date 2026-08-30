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

package com.viaversion.setsunavia.injection.mixin.features.item.filter_creative_tabs;

import com.viaversion.setsunavia.protocoltranslator.ProtocolTranslator;
import com.viaversion.setsunavia.settings.impl.GeneralSettings;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.flag.FeatureFlagSet;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CreativeModeTabs.class)
public abstract class MixinCreativeModeTabs {

    @Shadow
    @Nullable
    private static CreativeModeTab.@Nullable ItemDisplayParameters CACHED_PARAMETERS;

    @Shadow
    private static void buildAllTabContents(CreativeModeTab.ItemDisplayParameters displayContext) {
    }

    @Unique
    private static ProtocolVersion setsunaVia$version;

    @Unique
    private static int setsunaVia$state;

    @Inject(method = "tryRebuildTabContents", at = @At("HEAD"), cancellable = true)
    private static void trackLastVersion(FeatureFlagSet enabledFeatures, boolean operatorEnabled, HolderLookup.Provider lookup, CallbackInfoReturnable<Boolean> cir) {
        if (setsunaVia$version != ProtocolTranslator.getTargetVersion() || setsunaVia$state != GeneralSettings.INSTANCE.removeNotAvailableItemsFromCreativeTab.getIndex()) {
            setsunaVia$version = ProtocolTranslator.getTargetVersion();
            setsunaVia$state = GeneralSettings.INSTANCE.removeNotAvailableItemsFromCreativeTab.getIndex();

            CACHED_PARAMETERS = new CreativeModeTab.ItemDisplayParameters(enabledFeatures, operatorEnabled, lookup);
            buildAllTabContents(CACHED_PARAMETERS);

            cir.setReturnValue(true);
        }
    }

}
