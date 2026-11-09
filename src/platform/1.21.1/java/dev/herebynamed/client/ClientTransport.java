package dev.herebynamed.client;

import dev.herebynamed.net.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import java.util.function.Consumer;

final class ClientTransport {
    static void initialize(Consumer<Wire.Fragment> receiver) {
        ClientPlayNetworking.registerGlobalReceiver(RegionPayload.TYPE,(payload,context) -> receiver.accept(payload.fragment()));
    }
    static boolean canSend() { return ClientPlayNetworking.canSend(RegionPayload.TYPE); }
    static void send(FriendlyByteBuf packet) {
        try { ClientPlayNetworking.send(new RegionPayload(Wire.Fragment.read(packet))); }
        finally { packet.release(); }
    }
    private ClientTransport() {}
}
