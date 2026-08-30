package com.setsuna;

import com.setsuna.integration.apollo.ApolloTeamNetworking;
import com.setsuna.runtime.FeatureRuntime;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;


public final class SetsunaClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        Setsuna.LOGGER.info("Initializing {}...", Setsuna.NAME);

        ApolloTeamNetworking.init();

        FeatureRuntime.INSTANCE.activate();

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> FeatureRuntime.INSTANCE.shutdown());
        Setsuna.LOGGER.info("{} client runtime loaded.", Setsuna.NAME);
    }
}
