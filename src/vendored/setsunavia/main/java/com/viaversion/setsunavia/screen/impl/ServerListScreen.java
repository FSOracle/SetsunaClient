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

package com.viaversion.setsunavia.screen.impl;

import com.viaversion.setsunavia.save.SaveManager;
import com.viaversion.setsunavia.screen.SetsunaViaScreen;
import com.viaversion.setsunavia.screen.impl.classic4j.BetaCraftScreen;
import com.viaversion.setsunavia.screen.impl.classic4j.ClassiCubeLoginScreen;
import com.viaversion.setsunavia.screen.impl.classic4j.ClassiCubeServerListScreen;
import com.viaversion.setsunavia.screen.impl.realms.BedrockRealmsScreen;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public final class ServerListScreen extends SetsunaViaScreen {

    public static final ServerListScreen INSTANCE = new ServerListScreen();

    public ServerListScreen() {
        super(Component.translatable("screen.setsunavia.server_list"), true);
    }

    @Override
    protected void init() {
        super.init();
        this.setupDefaultSubtitle();

        // ClassiCube
        final boolean loggedIn = SaveManager.INSTANCE.getAccountsSave().getClassicubeAccount() != null;

        final Button.Builder classiCubeBuilder = Button.builder(ClassiCubeServerListScreen.INSTANCE.getTitle(), button -> {
            if (!loggedIn) {
                ClassiCubeLoginScreen.INSTANCE.open(this);
                return;
            }
            ClassiCubeServerListScreen.INSTANCE.open(this);
        }).pos(this.width / 2 - 100, this.height / 2 - 25).size(200, 20);
        if (!loggedIn) {
            classiCubeBuilder.tooltip(Tooltip.create(Component.translatable("classicube.setsunavia.warning")));
        }
        this.addRenderableWidget(classiCubeBuilder.build());

        final Button.Builder betaCraftBuilder = Button.builder(BetaCraftScreen.INSTANCE.getTitle(), button -> {
            BetaCraftScreen.INSTANCE.open(this);
        }).pos(this.width / 2 - 100, this.height / 2 - 25 + 20 + 3).size(200, 20);
        if (BetaCraftScreen.SERVER_LIST == null) {
            betaCraftBuilder.tooltip(Tooltip.create(Component.translatable("betacraft.setsunavia.warning")));
        }
        this.addRenderableWidget(betaCraftBuilder.build());

        final Button.Builder bedrockRealmsBuilder = Button.builder(BedrockRealmsScreen.INSTANCE.getTitle(), button -> {
            BedrockRealmsScreen.INSTANCE.open(this);
        }).pos(this.width / 2 - 100, this.height / 2 - 25 + 40 + 6).size(200, 20);
        final boolean missingAccount = SaveManager.INSTANCE.getAccountsSave().getBedrockAccount() == null; // Only check for presence, later validate
        if (missingAccount) {
            bedrockRealmsBuilder.tooltip(Tooltip.create(Component.translatable("bedrock_realms.setsunavia.warning")));
        }

        final Button bedrockRealmsButton = bedrockRealmsBuilder.build();
        this.addRenderableWidget(bedrockRealmsButton);
        if (missingAccount) {
            bedrockRealmsButton.active = false;
        }
    }

}
