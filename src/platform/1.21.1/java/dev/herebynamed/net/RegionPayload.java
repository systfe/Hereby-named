package dev.herebynamed.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

public record RegionPayload(Wire.Fragment fragment) implements CustomPayload {
    public static final Id<RegionPayload> ID=new Id<>(Wire.CHANNEL);
    public static final PacketCodec<RegistryByteBuf,RegionPayload> CODEC=new PacketCodec<>() {
        @Override public RegionPayload decode(RegistryByteBuf buffer) { return new RegionPayload(Wire.Fragment.read(buffer)); }
        @Override public void encode(RegistryByteBuf buffer,RegionPayload payload) {
            Wire.Fragment fragment=payload.fragment();
            buffer.writeInt(fragment.id()); buffer.writeInt(fragment.index()); buffer.writeInt(fragment.count());
            buffer.writeByteArray(fragment.bytes());
        }
    };
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
