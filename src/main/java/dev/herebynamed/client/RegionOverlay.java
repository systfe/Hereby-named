package dev.herebynamed.client;

import dev.herebynamed.HerebyNamed;
import dev.herebynamed.domain.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Samples recorded map data with a tick budget; pure geometry is built off the UI thread. */
final class RegionOverlay {
    private static final RegionMesh EMPTY=new RegionMesh(List.of(),List.of(),List.of());
    private final MapKnowledge knowledge;
    private final Set<ChunkCell> recorded=new HashSet<>();
    private final Deque<ChunkCell> unknown=new ArrayDeque<>();
    private List<RegionBook.Region> regions=List.of();
    private String dimension="";
    private Integer layer;
    private RegionMesh mesh=EMPTY;
    private long epoch,nextBuild,nextScan;
    private int remaining;
    private boolean dirty;
    private CompletableFuture<Built> building;
    private record Built(long epoch,RegionMesh mesh) {}
    RegionOverlay(MapKnowledge knowledge) { this.knowledge=knowledge; }
    RegionMesh mesh() { return mesh; }
    void update(String dimension,List<RegionBook.Region> regions,Integer layer) {
        if(this.dimension.equals(dimension) && this.regions==regions && Objects.equals(this.layer,layer)) return;
        if(!this.dimension.equals(dimension)) recorded.clear();
        this.dimension=dimension; this.regions=regions; this.layer=layer;
        epoch++; mesh=EMPTY; dirty=true; nextBuild=nextScan=0;
        Set<ChunkCell> pending=new HashSet<>();
        for(var r:regions) if(layer==null || r.layer()==layer) pending.addAll(r.cells());
        pending.removeAll(recorded); unknown.clear(); unknown.addAll(pending); remaining=unknown.size();
    }
    void tick() {
        long now=System.nanoTime();
        if(building!=null && building.isDone()) {
            try { Built result=building.join(); if(result.epoch==epoch) mesh=result.mesh; }
            catch(RuntimeException ex) { HerebyNamed.LOG.warn("Cannot build region display geometry",ex); }
            building=null;
        }
        if(now>=nextScan && !unknown.isEmpty()) {
            if(remaining==0) remaining=unknown.size();
            long until=now+2_000_000L;
            for(int budget=0;budget<512 && remaining>0 && System.nanoTime()<until;budget++) {
                ChunkCell cell=unknown.removeFirst(); remaining--;
                if(knowledge.sample(cell,false).biome()!=null) { recorded.add(cell); dirty=true; }
                else unknown.addLast(cell);
            }
            if(remaining==0) nextScan=now+1_000_000_000L;
        }
        if(dirty && building==null && now>=nextBuild) {
            Set<ChunkCell> visible=Set.copyOf(recorded);
            List<RegionBook.Region> source=regions;
            Integer shown=layer; long version=epoch;
            building=CompletableFuture.supplyAsync(() -> {
                List<RegionBook.Region> filtered=new ArrayList<>();
                for(var r:source) if(shown==null || r.layer()==shown) {
                    Set<ChunkCell> cells=new HashSet<>(r.cells()); cells.retainAll(visible);
                    filtered.add(new RegionBook.Region(r.id(),r.layer(),r.name(),r.color(),cells));
                }
                return new Built(version,RegionMesh.build(filtered,shown));
            });
            dirty=false; nextBuild=now+750_000_000L;
        }
    }
}
