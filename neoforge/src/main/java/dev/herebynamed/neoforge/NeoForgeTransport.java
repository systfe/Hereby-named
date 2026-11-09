package dev.herebynamed.neoforge;

import dev.herebynamed.client.HerebyNamedClient;
import dev.herebynamed.net.RegionPayload;
import dev.herebynamed.net.Wire;
import dev.herebynamed.server.HerebyNamedServer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class NeoForgeTransport {
    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("hereby_named").versioned("1");
        registrar.playBidirectional(
                RegionPayload.TYPE,
                RegionPayload.CODEC,
                (payload, context) -> {
                    context.enqueueWork(() -> {
                        if (context.flow().isClientbound()) {
                            HerebyNamedClient.REGIONS.receive(payload.fragment());
                        } else if (context.player() instanceof ServerPlayer player) {
                            HerebyNamedServer.receive(player.getServer(), player, payload.fragment());
                        }
                    });
                }
        );
    }

    public static boolean canSendToServer() {
        return Minecraft.getInstance().getConnection() != null;
    }

    public static void sendToServer(FriendlyByteBuf packet) {
        try {
            PacketDistributor.sendToServer(new RegionPayload(Wire.Fragment.read(packet)));
        } finally {
            packet.release();
        }
    }

    public static boolean canSendToPlayer(ServerPlayer player) {
        return player != null && !player.hasDisconnected();
    }

    public static void sendToPlayer(ServerPlayer player, FriendlyByteBuf packet) {
        try {
            PacketDistributor.sendToPlayer(player, new RegionPayload(Wire.Fragment.read(packet)));
        } finally {
            packet.release();
        }
    }

    private NeoForgeTransport() {}
}
