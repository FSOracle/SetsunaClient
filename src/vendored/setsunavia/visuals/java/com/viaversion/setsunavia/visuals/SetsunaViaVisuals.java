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

package com.viaversion.setsunavia.visuals;

import com.viaversion.setsunavia.api.SetsunaViaBase;
import com.viaversion.setsunavia.api.entrypoint.SetsunaViaLoadEntrypoint;
import com.viaversion.setsunavia.api.events.LoadingCycleCallback;
import com.viaversion.setsunavia.visuals.features.classic.creative_menu.GridItemSelectionScreen;
import com.viaversion.setsunavia.visuals.features.force_unicode_font.UnicodeFontFix1_12_2;
import com.viaversion.setsunavia.visuals.settings.VisualSettings;
import net.minecraft.client.Minecraft;
import net.raphimc.vialegacy.api.LegacyProtocolVersion;

public class SetsunaViaVisuals implements SetsunaViaLoadEntrypoint {

    public static final SetsunaViaVisuals INSTANCE = new SetsunaViaVisuals();

    @Override
    public void onPlatformLoad(SetsunaViaBase platform) {
        UnicodeFontFix1_12_2.init();

        platform.registerLoadingCycleCallback(cycle -> {
            if (cycle == LoadingCycleCallback.LoadingCycle.POST_SETTINGS_LOAD) {
                platform.addSettingGroup(VisualSettings.INSTANCE);
            }
        });

        platform.registerOnChangeProtocolVersionCallback((oldVersion, newVersion) -> Minecraft.getInstance().execute(() -> {
            if (newVersion.olderThanOrEqualTo(LegacyProtocolVersion.c0_28toc0_30)) {
                GridItemSelectionScreen.INSTANCE.itemGrid = null;
            }
        }));
    }

}
