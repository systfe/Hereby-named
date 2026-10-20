package dev.herebynamed.domain;

import java.io.*;
import java.util.*;
import java.util.zip.*;

public final class BookCodec {
    private static final int MAGIC = 0x48424e31;
    private BookCodec() {}
    public static byte[] encode(RegionBook book) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(bytes))) {
            out.writeInt(MAGIC); out.writeInt(book.state().highestLayer());
            out.writeInt(book.state().layers().size());
            for (int layer : new TreeSet<>(book.state().layers())) out.writeInt(layer);
            out.writeInt(book.regions().size());
            for (RegionBook.Region r : book.regions()) {
                out.writeUTF(r.id()); out.writeInt(r.layer()); out.writeUTF(r.name()); out.writeInt(r.color());
                out.writeInt(r.cells().size());
                for (ChunkCell c : r.cells().stream().sorted(Comparator.comparingInt(ChunkCell::x).thenComparingInt(ChunkCell::z)).toList()) {
                    out.writeInt(c.x()); out.writeInt(c.z());
                }
            }
        }
        return bytes.toByteArray();
    }
    public static RegionBook decode(byte[] bytes) throws IOException {
        if (bytes.length > 8_000_000) throw new IOException("区域数据过大");
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(bytes)))) {
            if (in.readInt() != MAGIC) throw new IOException("未知区域存档版本");
            int highest = in.readInt();
            Set<Integer> layers = new HashSet<>();
            int count = bounded(in.readInt(), 256);
            for (int i = 0; i < count; i++) if (!layers.add(in.readInt())) throw new IOException("图层重复");
            List<RegionBook.Region> regions = new ArrayList<>();
            int total = 0, regionCount = bounded(in.readInt(), 1024);
            for (int i = 0; i < regionCount; i++) {
                String id = in.readUTF(); int layer = in.readInt(); String name = in.readUTF(); int color = in.readInt();
                if (id.length() > 80 || name.length() > 80) throw new IOException("区域字段过长");
                int size = bounded(in.readInt(), 500_000 - total); total += size;
                Set<ChunkCell> cells = new HashSet<>();
                for (int j = 0; j < size; j++) if (!cells.add(new ChunkCell(in.readInt(), in.readInt()))) throw new IOException("区块重复");
                regions.add(new RegionBook.Region(id, layer, name, color, cells));
            }
            if (in.read() != -1) throw new IOException("区域数据尾部无效");
            RegionBook book = new RegionBook(); book.replace(new RegionBook.State(highest, layers, regions)); return book;
        } catch (IllegalArgumentException | NullPointerException ex) { throw new IOException("区域数据无效", ex); }
    }
    private static int bounded(int n, int max) throws IOException {
        if (n < 0 || n > max) throw new IOException("区域数据数量无效"); return n;
    }
}
