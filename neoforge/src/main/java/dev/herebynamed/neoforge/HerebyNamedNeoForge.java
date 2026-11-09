package dev.herebynamed.neoforge;

import dev.herebynamed.platform.PlatformHelper;
import dev.herebynamed.server.HerebyNamedServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod("hereby_named")
public final class HerebyNamedNeoForge {
    public HerebyNamedNeoForge(IEventBus modBus) {
        PlatformHelper.init(new NeoForgePlatformHelper());

        modBus.addListener(NeoForgeTransport::registerPayloadHandlers);

        NeoForge.EVENT_BUS.addListener(this::onCommandsRegister);
        NeoForge.EVENT_BUS.addListener(this::onPlayerJoin);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLeave);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
        NeoForge.EVENT_BUS.addListener(this::onServerStopped);
        NeoForge.EVENT_BUS.addListener(this::onServerTick);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            HerebyNamedNeoForgeClient.init(modBus);
        }
    }

    private void onCommandsRegister(RegisterCommandsEvent event) {
        HerebyNamedServer.registerCommands(event.getDispatcher());
    }

    private void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            HerebyNamedServer.onJoin(player.getServer(), player);
        }
    }

    private void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            HerebyNamedServer.onDisconnect(player.getServer(), player);
        }
    }

    private void onServerStopping(ServerStoppingEvent event) {
        HerebyNamedServer.onStopping(event.getServer());
    }

    private void onServerStopped(ServerStoppedEvent event) {
        HerebyNamedServer.onStopped(event.getServer());
    }

    private void onServerTick(ServerTickEvent.Post event) {
        HerebyNamedServer.onServerTick(event.getServer());
    }
}
