package dev.herebynamed.net;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Bounded ordered fragments keep both C2S and S2C below Minecraft's payload limits. */
public final class Wire {
    public static final Identifier CHANNEL = Identifier.tryParse("hereby_named:regions_v4");
    private static final AtomicInteger IDS = new AtomicInteger();
    private static final int PART = 24_000, MAX = 8_000_000;
    private Wire() {}
    public static void send(byte[] bytes, Consumer<PacketByteBuf> sender) {
        if (bytes.length > MAX) throw new IllegalArgumentException("同步数据过大");
        int id = IDS.incrementAndGet(), count = Math.max(1, (bytes.length + PART - 1) / PART);
        for (int i = 0; i < count; i++) {
            PacketByteBuf packet = PacketByteBufs.create();
            packet.writeInt(id); packet.writeInt(i); packet.writeInt(count);
            packet.writeByteArray(Arrays.copyOfRange(bytes, i * PART, Math.min(bytes.length, (i+1)*PART)));
            sender.accept(packet);
        }
    }
    public record Fragment(int id, int index, int count, byte[] bytes) {
        public static Fragment read(PacketByteBuf buf) {
            int id = buf.readInt(), index = buf.readInt(), count = buf.readInt();
            byte[] bytes = buf.readByteArray(PART);
            if (count < 1 || count > (MAX + PART - 1)/PART || index < 0 || index >= count || buf.isReadable())
                throw new IllegalArgumentException("无效数据分片");
            return new Fragment(id, index, count, bytes);
        }
    }
    public static final class Assembler {
        private ByteArrayOutputStream pending;
        private int id, next, count;
        private long started;
        public Optional<byte[]> accept(Fragment f) {
            if (f.index() == 0) {
                pending = new ByteArrayOutputStream(); id = f.id(); next = 0; count = f.count(); started = System.nanoTime();
            }
            if (pending == null || id != f.id() || next != f.index() || count != f.count()
                    || System.nanoTime() - started > 30_000_000_000L || pending.size() + f.bytes().length > MAX) {
                pending = null; throw new IllegalArgumentException("同步分片顺序或大小无效");
            }
            pending.writeBytes(f.bytes()); next++;
            if (next != count) return Optional.empty();
            byte[] result = pending.toByteArray(); pending = null; return Optional.of(result);
        }
    }
}
