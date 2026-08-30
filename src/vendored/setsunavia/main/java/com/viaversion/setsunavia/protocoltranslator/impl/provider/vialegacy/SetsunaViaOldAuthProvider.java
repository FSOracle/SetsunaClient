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

package com.viaversion.setsunavia.protocoltranslator.impl.provider.vialegacy;

import com.viaversion.setsunavia.SetsunaViaImpl;
import com.viaversion.setsunavia.protocoltranslator.ProtocolTranslator;
import com.viaversion.setsunavia.settings.impl.AuthenticationSettings;
import com.viaversion.setsunavia.util.ChatUtil;
import com.viaversion.viaversion.api.connection.UserConnection;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.raphimc.vialegacy.protocol.release.r1_2_4_5tor1_3_1_2.provider.OldAuthProvider;

public final class SetsunaViaOldAuthProvider extends OldAuthProvider {

    @Override
    public void sendAuthRequest(UserConnection connection, String serverId) {
        if (!AuthenticationSettings.INSTANCE.verifySessionForOnlineModeServers.getValue()) {
            return;
        }

        try {
            final Minecraft client = Minecraft.getInstance();
            client.services().sessionService().joinServer(client.getUser().getProfileId(), client.getUser().getAccessToken(), serverId);
        } catch (Exception e) {
            connection.getChannel().attr(ProtocolTranslator.CLIENT_CONNECTION_ATTRIBUTE_KEY).get().disconnect(ChatUtil.prefixText(Component.translatable("betacraft.setsunavia.failed_to_verify_session")));
            SetsunaViaImpl.INSTANCE.getLogger().error("Error occurred while calling join server to verify session", e);
        }
    }

}
