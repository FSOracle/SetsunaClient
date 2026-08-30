package com.setsuna.integration.apollo;

import com.setsuna.module.modules.render.TeamViewer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

import java.util.concurrent.atomic.AtomicBoolean;

/** Registers the Apollo channel once, before any play connection is created. */
public final class ApolloTeamNetworking {

    private static final AtomicBoolean INITIALIZED = new AtomicBoolean();

    private ApolloTeamNetworking() {
    }

    public static void init() {
        if (!INITIALIZED.compareAndSet(false, true)) {
            return;
        }
        PayloadTypeRegistry.clientboundPlay().register(ApolloPayload.TYPE, ApolloPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(ApolloPayload.TYPE,
                (payload, context) -> TeamViewer.INSTANCE.acceptApollo(payload.data()));
        ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) -> TeamViewer.INSTANCE.clearTeam());
    }
}
