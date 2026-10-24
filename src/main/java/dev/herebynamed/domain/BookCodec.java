package dev.herebynamed.domain;

import java.io.*;
import java.util.*;
import java.util.zip.*;

public final class BookCodec {
    private static final int LEGACY_MAGIC = 0x48424e31, MAGIC = 0x48424e32;
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
                out.writeBoolean(r.automatic()!=null);
                if(r.automatic()!=null) {
                    out.writeUTF(r.automatic().classification()); out.writeLong(r.automatic().order());
                    out.writeInt(r.automatic().formerNames().size());
                    for(String former:r.automatic().formerNames()) out.writeUTF(former);
                }
                out.writeInt(r.cells().size());
                for (ChunkCell c : r.cells().stream().sorted(Comparator.comparingInt(ChunkCell::x).thenComparingInt(ChunkCell::z)).toList()) {
                    out.writeInt(c.x()); out.writeInt(c.z());
                }
            }
            out.writeInt(book.state().suppressed().size());
            for(ChunkCell c:book.state().suppressed()) { out.writeInt(c.x());out.writeInt(c.z()); }
        }
        return bytes.toByteArray();
    }
    public static RegionBook decode(byte[] bytes) throws IOException {
        if (bytes.length > 8_000_000) throw new IOException("区域数据过大");
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(bytes)))) {
            int magic=in.readInt();
            if (magic != MAGIC && magic!=LEGACY_MAGIC) throw new IOException("未知区域存档版本");
            int highest = in.readInt();
            Set<Integer> layers = new HashSet<>();
            int count = bounded(in.readInt(), 256);
            for (int i = 0; i < count; i++) if (!layers.add(in.readInt())) throw new IOException("图层重复");
            List<RegionBook.Region> regions = new ArrayList<>();
            int total = 0, regionCount = bounded(in.readInt(), 1024);
            for (int i = 0; i < regionCount; i++) {
                String id = in.readUTF(); int layer = in.readInt(); String name = in.readUTF(); int color = in.readInt();
                if (id.length() > 80 || name.length() > 80) throw new IOException("区域字段过长");
                RegionBook.Automatic automatic=null;
                if(magic==MAGIC && in.readBoolean()) {
                    String classification=in.readUTF(); long order=in.readLong();
                    if(classification.length()>40 || order<0) throw new IOException("自动区域字段无效");
                    List<String> former=new ArrayList<>(); int formerCount=bounded(in.readInt(),128);
                    for(int j=0;j<formerCount;j++) { String value=in.readUTF();if(value.length()>80) throw new IOException("旧名称过长");former.add(value); }
                    automatic=new RegionBook.Automatic(classification,order,former);
                }
                int size = bounded(in.readInt(), 500_000 - total); total += size;
                Set<ChunkCell> cells = new HashSet<>();
                for (int j = 0; j < size; j++) if (!cells.add(new ChunkCell(in.readInt(), in.readInt()))) throw new IOException("区块重复");
                regions.add(new RegionBook.Region(id, layer, name, color, cells,automatic));
            }
            Set<ChunkCell> suppressed=new HashSet<>();
            if(magic==MAGIC) {
                int size=bounded(in.readInt(),500_000);
                for(int i=0;i<size;i++) if(!suppressed.add(new ChunkCell(in.readInt(),in.readInt()))) throw new IOException("保护区块重复");
            }
            if (in.read() != -1) throw new IOException("区域数据尾部无效");
            RegionBook book = new RegionBook(); book.replace(new RegionBook.State(highest, layers, regions,suppressed)); return book;
        } catch (IllegalArgumentException | NullPointerException ex) { throw new IOException("区域数据无效", ex); }
    }
    private static int bounded(int n, int max) throws IOException {
        if (n < 0 || n > max) throw new IOException("区域数据数量无效"); return n;
    }
}
