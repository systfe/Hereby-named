package dev.herebynamed.client;

import dev.herebynamed.net.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import java.util.function.Consumer;

final class ClientTransport {
    static void initialize(Consumer<Wire.Fragment> receiver) {
        ClientPlayNetworking.registerGlobalReceiver(RegionPayload.ID,(payload,context) -> receiver.accept(payload.fragment()));
    }
    static boolean canSend() { return ClientPlayNetworking.canSend(RegionPayload.ID); }
    static void send(PacketByteBuf packet) {
        try { ClientPlayNetworking.send(new RegionPayload(Wire.Fragment.read(packet))); }
        finally { packet.release(); }
    }
    private ClientTransport() {}
}
