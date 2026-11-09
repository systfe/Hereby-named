package dev.herebynamed.fabric;

import dev.herebynamed.client.ClientTransport;
import dev.herebynamed.client.HerebyNamedClient;
import dev.herebynamed.platform.PlatformHelper;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public final class HerebyNamedFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        PlatformHelper.init(new FabricPlatformHelper());
        ClientTransport.initialize(HerebyNamedClient.REGIONS::receive);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> HerebyNamedClient.reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> HerebyNamedClient.reset());
        ClientTickEvents.END_CLIENT_TICK.register(HerebyNamedClient::tick);
        PlatformHelper.get().registerTitles(HerebyNamedClient.TITLES);
        HerebyNamedClient.initialize();
    }
}
