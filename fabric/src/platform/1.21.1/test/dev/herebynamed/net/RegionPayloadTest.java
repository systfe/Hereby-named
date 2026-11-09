package dev.herebynamed.net;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RegionPayloadTest {
    @Test void modernPayloadCodecPreservesBoundedFragments() {
        byte[] original=new byte[80_000]; new Random(21).nextBytes(original);
        Wire.Assembler assembler=new Wire.Assembler();
        List<byte[]> results=new ArrayList<>();
        Wire.send(original,packet -> {
            try {
                RegionPayload payload=new RegionPayload(Wire.Fragment.read(packet));
                RegistryFriendlyByteBuf buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
                try {
                    RegionPayload.CODEC.encode(buffer,payload);
                    assertTrue(buffer.readableBytes()<32767);
                    RegionPayload decoded=RegionPayload.CODEC.decode(buffer);
                    assertEquals(RegionPayload.TYPE,decoded.type());
                    assembler.accept(decoded.fragment()).ifPresent(results::add);
                } finally { buffer.release(); }
            } finally { packet.release(); }
        });
        assertEquals(1,results.size()); assertArrayEquals(original,results.getFirst());
    }
    @Test void modernPayloadRejectsInvalidFragmentCounts() {
        RegistryFriendlyByteBuf buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try {
            buffer.writeInt(1); buffer.writeInt(0); buffer.writeInt(0); buffer.writeByteArray(new byte[]{1});
            assertThrows(IllegalArgumentException.class,() -> RegionPayload.CODEC.decode(buffer));
        } finally { buffer.release(); }
    }
}
