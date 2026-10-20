package dev.herebynamed.net;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import dev.herebynamed.HerebyNamed;

public final class ServerTransport {
    @FunctionalInterface public interface Receiver { void receive(MinecraftServer server,ServerPlayerEntity player,Wire.Fragment fragment); }
    public static void initialize(Receiver receiver) {
        ServerPlayNetworking.registerGlobalReceiver(Wire.CHANNEL,(server,player,handler,buf,response) -> {
            try { Wire.Fragment fragment=Wire.Fragment.read(buf); server.execute(() -> receiver.receive(server,player,fragment)); }
            catch(RuntimeException ex) { HerebyNamed.LOG.warn("Rejected invalid region packet from {}",player.getUuid()); }
        });
    }
    public static boolean canSend(ServerPlayerEntity player) { return ServerPlayNetworking.canSend(player,Wire.CHANNEL); }
    public static void send(ServerPlayerEntity player,PacketByteBuf packet) { ServerPlayNetworking.send(player,Wire.CHANNEL,packet); }
    private ServerTransport() {}
}
