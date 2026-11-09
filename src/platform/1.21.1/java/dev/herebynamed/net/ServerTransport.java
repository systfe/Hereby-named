package dev.herebynamed.net;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class ServerTransport {
    @FunctionalInterface public interface Receiver { void receive(MinecraftServer server,ServerPlayer player,Wire.Fragment fragment); }
    public static void initialize(Receiver receiver) {
        PayloadTypeRegistry.playC2S().register(RegionPayload.TYPE,RegionPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(RegionPayload.TYPE,RegionPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(RegionPayload.TYPE,(payload,context) ->
                receiver.receive(context.server(),context.player(),payload.fragment()));
    }
    public static boolean canSend(ServerPlayer player) { return ServerPlayNetworking.canSend(player,RegionPayload.TYPE); }
    public static void send(ServerPlayer player,FriendlyByteBuf packet) {
        try { ServerPlayNetworking.send(player,new RegionPayload(Wire.Fragment.read(packet))); }
        finally { packet.release(); }
    }
    private ServerTransport() {}
}
