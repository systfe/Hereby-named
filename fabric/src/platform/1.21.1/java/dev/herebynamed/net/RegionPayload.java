package dev.herebynamed.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record RegionPayload(Wire.Fragment fragment) implements CustomPacketPayload {
    public static final Type<RegionPayload> TYPE = new Type<>(Wire.CHANNEL);
    public static final StreamCodec<RegistryFriendlyByteBuf, RegionPayload> CODEC = StreamCodec.of(
            (buffer, payload) -> {
                Wire.Fragment fragment = payload.fragment();
                buffer.writeInt(fragment.id()); buffer.writeInt(fragment.index()); buffer.writeInt(fragment.count());
                buffer.writeByteArray(fragment.bytes());
            },
            buffer -> new RegionPayload(Wire.Fragment.read(buffer))
    );
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
