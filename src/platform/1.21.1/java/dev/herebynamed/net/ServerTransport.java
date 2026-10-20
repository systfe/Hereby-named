package dev.herebynamed.net;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

public final class ServerTransport {
    @FunctionalInterface public interface Receiver { void receive(MinecraftServer server,ServerPlayerEntity player,Wire.Fragment fragment); }
    public static void initialize(Receiver receiver) {
        PayloadTypeRegistry.playC2S().register(RegionPayload.ID,RegionPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(RegionPayload.ID,RegionPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(RegionPayload.ID,(payload,context) ->
                receiver.receive(context.server(),context.player(),payload.fragment()));
    }
    public static boolean canSend(ServerPlayerEntity player) { return ServerPlayNetworking.canSend(player,RegionPayload.ID); }
    public static void send(ServerPlayerEntity player,PacketByteBuf packet) {
        try { ServerPlayNetworking.send(player,new RegionPayload(Wire.Fragment.read(packet))); }
        finally { packet.release(); }
    }
    private ServerTransport() {}
}
