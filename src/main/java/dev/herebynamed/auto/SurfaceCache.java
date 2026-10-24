package dev.herebynamed.auto;

import dev.herebynamed.domain.ChunkCell;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Bounded, disposable observations, separate from the authoritative named-region book. */
public final class SurfaceCache {
    private static final int MAGIC=0x48425331;
    private SurfaceCache() { }
    public static Map<ChunkCell,TerrainSample> load(Path path,int maximum) throws IOException {
        Map<ChunkCell,TerrainSample> result=new HashMap<>();
        if(!Files.exists(path)) return result;
        if(Files.size(path)>20_000_000) throw new IOException("地表缓存过大");
        try(DataInputStream in=new DataInputStream(new GZIPInputStream(Files.newInputStream(path)))) {
            if(in.readInt()!=MAGIC) throw new IOException("未知地表缓存格式");
            int size=in.readInt();if(size<0 || size>500_000) throw new IOException("缓存数量无效");
            for(int i=0;i<size;i++) {
                ChunkCell cell=new ChunkCell(in.readInt(),in.readInt());String biome=in.readUTF();
                int kind=in.readUnsignedByte();float temperature=in.readFloat();int height=in.readInt();
                if(biome.length()>256 || kind>=TerrainKind.values().length || !Float.isFinite(temperature)) throw new IOException("缓存条目无效");
                if(i<maximum) result.put(cell,new TerrainSample(biome,TerrainKind.values()[kind],temperature,height));
            }
            if(in.read()!=-1) throw new IOException("地表缓存尾部无效");
        }
        return result;
    }
    public static void save(Path path,Map<ChunkCell,TerrainSample> samples) throws IOException {
        Files.createDirectories(path.getParent());Path temporary=path.resolveSibling(path.getFileName()+".tmp");
        try(DataOutputStream out=new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(temporary)))) {
            out.writeInt(MAGIC);out.writeInt(samples.size());
            for(var e:samples.entrySet()) {
                out.writeInt(e.getKey().x());out.writeInt(e.getKey().z());out.writeUTF(e.getValue().biome());
                out.writeByte(e.getValue().kind().ordinal());out.writeFloat(e.getValue().temperature());out.writeInt(e.getValue().height());
            }
        }
        Files.move(temporary,path,StandardCopyOption.REPLACE_EXISTING);
    }
}
