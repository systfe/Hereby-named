package dev.herebynamed.client;

import dev.herebynamed.net.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import java.util.function.Consumer;

public final class ClientTransport {
    public static void initialize(Consumer<Wire.Fragment> receiver) {
        ClientPlayNetworking.registerGlobalReceiver(RegionPayload.TYPE,(payload,context) -> receiver.accept(payload.fragment()));
    }
    public static boolean canSend() { return ClientPlayNetworking.canSend(RegionPayload.TYPE); }
    public static void send(FriendlyByteBuf packet) {
        try { ClientPlayNetworking.send(new RegionPayload(Wire.Fragment.read(packet))); }
        finally { packet.release(); }
    }
    private ClientTransport() {}
}
