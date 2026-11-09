package dev.herebynamed.forge;

import dev.herebynamed.net.Wire;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class ForgeTransport {
    private static final String PROTOCOL = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("hereby_named", "network"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals
    );

    @FunctionalInterface
    public interface ServerReceiver {
        void receive(net.minecraft.server.MinecraftServer server, ServerPlayer player, Wire.Fragment fragment);
    }

    public static void initialize(Consumer<Wire.Fragment> clientReceiver, ServerReceiver serverReceiver) {
        CHANNEL.registerMessage(0, Wire.Fragment.class,
                (msg, buf) -> {
                    buf.writeInt(msg.id());
                    buf.writeInt(msg.index());
                    buf.writeInt(msg.count());
                    buf.writeByteArray(msg.bytes());
                },
                Wire.Fragment::read,
                (msg, ctxSupplier) -> {
                    NetworkEvent.Context ctx = ctxSupplier.get();
                    ctx.enqueueWork(() -> {
                        if (ctx.getDirection().getReceptionSide().isClient()) {
                            clientReceiver.accept(msg);
                        } else {
                            ServerPlayer player = ctx.getSender();
                            if (player != null) {
                                serverReceiver.receive(player.getServer(), player, msg);
                            }
                        }
                    });
                    ctx.setPacketHandled(true);
                }
        );
    }

    public static boolean canSendToServer() {
        return Minecraft.getInstance().getConnection() != null;
    }

    public static void sendToServer(FriendlyByteBuf packet) {
        try {
            CHANNEL.sendToServer(Wire.Fragment.read(packet));
        } finally {
            packet.release();
        }
    }

    public static boolean canSendToPlayer(ServerPlayer player) {
        return player != null && !player.hasDisconnected();
    }

    public static void sendToPlayer(ServerPlayer player, FriendlyByteBuf packet) {
        try {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), Wire.Fragment.read(packet));
        } finally {
            packet.release();
        }
    }

    private ForgeTransport() {}
}
