package dev.herebynamed.net;

import dev.herebynamed.domain.*;
import java.io.*;
import java.util.UUID;

public record Snapshot(String dimension, long revision, long responseTo, UUID holder, boolean authorized, String message, RegionBook book) {
    public byte[] encode() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeUTF(dimension); out.writeLong(revision); out.writeLong(responseTo); out.writeBoolean(holder != null);
            if (holder != null) { out.writeLong(holder.getMostSignificantBits()); out.writeLong(holder.getLeastSignificantBits()); }
            out.writeBoolean(authorized); out.writeUTF(message);
            byte[] bookBytes = BookCodec.encode(book); out.writeInt(bookBytes.length); out.write(bookBytes);
        }
        return bytes.toByteArray();
    }
    public static Snapshot decode(byte[] bytes) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            String dimension = in.readUTF(); long revision = in.readLong(), responseTo = in.readLong();
            UUID holder = in.readBoolean() ? new UUID(in.readLong(), in.readLong()) : null;
            boolean authorized = in.readBoolean(); String message = in.readUTF();
            int length = in.readInt();
            if (length < 0 || length > 8_000_000 || length != in.available()) throw new IOException("同步快照长度无效");
            return new Snapshot(dimension, revision, responseTo, holder, authorized, message, BookCodec.decode(in.readNBytes(length)));
        }
    }
}
