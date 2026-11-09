package dev.herebynamed.net;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import dev.herebynamed.HerebyNamed;

public final class ServerTransport {
    @FunctionalInterface public interface Receiver { void receive(MinecraftServer server,ServerPlayer player,Wire.Fragment fragment); }
    public static void initialize(Receiver receiver) {
        ServerPlayNetworking.registerGlobalReceiver(Wire.CHANNEL,(server,player,handler,buf,response) -> {
            try { Wire.Fragment fragment=Wire.Fragment.read(buf); server.execute(() -> receiver.receive(server,player,fragment)); }
            catch(RuntimeException ex) { HerebyNamed.LOG.warn("Rejected invalid region packet from {}",player.getUUID()); }
        });
    }
    public static boolean canSend(ServerPlayer player) { return ServerPlayNetworking.canSend(player,Wire.CHANNEL); }
    public static void send(ServerPlayer player,FriendlyByteBuf packet) { ServerPlayNetworking.send(player,Wire.CHANNEL,packet); }
    private ServerTransport() {}
}
