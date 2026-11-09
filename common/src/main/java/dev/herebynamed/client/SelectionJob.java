package dev.herebynamed.client;

import dev.herebynamed.domain.*;
import java.util.*;

/** Incremental selection keeps large flood fills and recorded-map loading off the render hot path. */
final class SelectionJob {
    final String region;
    final boolean add;
    private final BiomeSelection.Walker flood;
    private final MapKnowledge knowledge;
    private final Deque<ChunkCell> pending = new ArrayDeque<>();
    private final Set<ChunkCell> visited = new HashSet<>(), selected = new HashSet<>();
    private long waitingSince;
    SelectionJob(String region, boolean add, MapKnowledge knowledge, ChunkCell start, ChunkCell end, boolean flood) {
        this.region = region; this.add = add; this.knowledge = knowledge;
        this.flood = flood ? new BiomeSelection.Walker(start, c -> {
            MapKnowledge.Sample sample=knowledge.sample(c,true);
            return new BiomeSelection.Sample(sample.biome(),sample.loading());
        },65_536) : null;
        if (!flood) {
            int x1 = Math.min(start.x(), end.x()), x2 = Math.max(start.x(), end.x());
            int z1 = Math.min(start.z(), end.z()), z2 = Math.max(start.z(), end.z());
            if (((long)x2-x1+1)*((long)z2-z1+1) > 65_536) throw new IllegalArgumentException("矩形过大，单次最多 65536 个区块");
            for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++) pending.add(new ChunkCell(x,z));
        }
    }
    boolean advance() {
        long until = System.nanoTime()+3_000_000L;
        if(flood!=null) {
            for(int budget=0;budget<512 && !flood.complete() && System.nanoTime()<until;budget++) {
                if(!flood.step()) { checkWait(); return false; }
                waitingSince=0;
            }
            return flood.complete();
        }
        for (int budget = 0; budget < 512 && !pending.isEmpty() && System.nanoTime() < until; budget++) {
            ChunkCell cell = pending.peekFirst();
            if (visited.contains(cell)) { pending.removeFirst(); continue; }
            MapKnowledge.Sample sample = knowledge.sample(cell, true);
            if (sample.loading()) {
                checkWait();
                return false;
            }
            waitingSince = 0; pending.removeFirst(); visited.add(cell);
            if (sample.biome() == null) continue;
            selected.add(cell);
            if (selected.size() > 65_536) throw new IllegalArgumentException("群系过大，请用矩形分批选择");
        }
        return pending.isEmpty();
    }
    private void checkWait() {
        if(waitingSince==0) waitingSince=System.nanoTime();
        if(System.nanoTime()-waitingSince>30_000_000_000L) throw new IllegalArgumentException("读取已记录地图超时，本次选择已取消");
    }
    Set<ChunkCell> result() { return flood==null?Set.copyOf(selected):flood.result(); }
    int size() { return flood==null?selected.size():flood.size(); }
    boolean biomeSelection() { return flood!=null; }
}
