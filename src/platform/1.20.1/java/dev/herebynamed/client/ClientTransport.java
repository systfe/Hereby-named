package dev.herebynamed.client;

import dev.herebynamed.HerebyNamed;
import dev.herebynamed.net.Wire;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import java.util.function.Consumer;

final class ClientTransport {
    static void initialize(Consumer<Wire.Fragment> receiver) {
        ClientPlayNetworking.registerGlobalReceiver(Wire.CHANNEL,(client,handler,buf,response) -> {
            try { Wire.Fragment fragment=Wire.Fragment.read(buf); client.execute(() -> receiver.accept(fragment)); }
            catch(RuntimeException ex) { HerebyNamed.LOG.warn("Invalid region snapshot fragment",ex); }
        });
    }
    static boolean canSend() { return ClientPlayNetworking.canSend(Wire.CHANNEL); }
    static void send(PacketByteBuf packet) { ClientPlayNetworking.send(Wire.CHANNEL,packet); }
    private ClientTransport() {}
}
