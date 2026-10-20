package dev.herebynamed.domain;

import java.util.*;
import java.util.function.Function;

public final class BiomeSelection {
    private BiomeSelection() {}
    public record Sample(String biome, boolean loading) { }
    public static final class Walker {
        private final Function<ChunkCell,Sample> reader;
        private final int limit;
        private final Set<ChunkCell> selected = new HashSet<>(), visited = new HashSet<>();
        private final Deque<ChunkCell> pending = new ArrayDeque<>();
        private String biome;
        public Walker(ChunkCell start, Function<ChunkCell,Sample> reader, int limit) {
            this.reader=reader; this.limit=limit; pending.add(start);
        }
        /** Advances one cell; false means recorded data is still loading. */
        public boolean step() {
            if(pending.isEmpty()) return true;
            ChunkCell cell=pending.peekFirst();
            if(visited.contains(cell)) { pending.removeFirst(); return true; }
            Sample sample=reader.apply(cell);
            if(sample.loading()) return false;
            pending.removeFirst(); visited.add(cell);
            if(sample.biome()==null) return true;
            if(biome==null) biome=sample.biome();
            if(!biome.equals(sample.biome())) return true;
            selected.add(cell);
            if(selected.size()>limit) throw new IllegalArgumentException("群系过大，请使用矩形分批选择（单次上限 "+limit+"）");
            for(int[] offset:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                long x=(long)cell.x()+offset[0],z=(long)cell.z()+offset[1];
                if(Math.abs(x)<=1_875_000 && Math.abs(z)<=1_875_000) pending.addLast(new ChunkCell((int)x,(int)z));
            }
            return true;
        }
        public boolean complete() { return pending.isEmpty(); }
        public int size() { return selected.size(); }
        public Set<ChunkCell> result() { return Set.copyOf(selected); }
    }
    public static Set<ChunkCell> connected(ChunkCell start, Function<ChunkCell, String> recordedBiome, int limit) {
        Walker walker=new Walker(start,c -> new Sample(recordedBiome.apply(c),false),limit);
        while(!walker.complete()) walker.step();
        return walker.result();
    }
}
