package dev.herebynamed.fabric;

import dev.herebynamed.net.ServerTransport;
import dev.herebynamed.platform.PlatformHelper;
import dev.herebynamed.server.HerebyNamedServer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

public final class HerebyNamedFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        PlatformHelper.init(new FabricPlatformHelper());
        ServerTransport.initialize(HerebyNamedServer::receive);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                HerebyNamedServer.onJoin(server, handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                HerebyNamedServer.onDisconnect(server, handler.player));
        ServerLifecycleEvents.SERVER_STOPPING.register(HerebyNamedServer::onStopping);
        ServerLifecycleEvents.SERVER_STOPPED.register(HerebyNamedServer::onStopped);
        ServerTickEvents.END_SERVER_TICK.register(HerebyNamedServer::onServerTick);
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) ->
                HerebyNamedServer.registerCommands(dispatcher));
    }
}
