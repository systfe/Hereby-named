package dev.herebynamed.client;

import dev.herebynamed.domain.ChunkCell;
import xaero.map.MapProcessor;
import xaero.map.file.RegionDetection;
import java.util.*;

/** Lazy disk-map enumeration. No giant region list is built on the client thread. */
final class SurfaceDiscovery {
    private Enumeration<Hashtable<Integer,RegionDetection>> columns;
    private Enumeration<RegionDetection> rows;
    private final ArrayDeque<ChunkCell> loadedRegions=new ArrayDeque<>();
    private boolean current;
    private int offset,regionX,regionZ;
    SurfaceDiscovery(MapProcessor processor) {
        synchronized(processor.uiSync) {
            var dimension=processor.getMapWorld().getCurrentDimension();
            var layer=dimension==null?null:dimension.getLayeredMapRegions().getLayer(Integer.MAX_VALUE);
            columns=layer==null?Collections.emptyEnumeration():layer.getDetectedRegions().elements();
            if(dimension!=null) for(var region:new ArrayList<>(dimension.getLayeredMapRegions().getLoadedListUnsynced()))
                if(region instanceof xaero.map.region.MapRegion leaf && leaf.getCaveStart()==Integer.MAX_VALUE)
                    loadedRegions.add(new ChunkCell(leaf.getRegionX(),leaf.getRegionZ()));
        }
    }
    ChunkCell next() {
        while(!current || offset>=1024) {
            if(!loadedRegions.isEmpty()) {
                ChunkCell position=loadedRegions.removeFirst();regionX=position.x();regionZ=position.z();current=true;offset=0;break;
            }
            while(rows==null || !rows.hasMoreElements()) {
                if(!columns.hasMoreElements()) return null;
                rows=columns.nextElement().elements();
            }
            RegionDetection detection=rows.nextElement();offset=0;
            if(detection.isRemoved() || detection.isDestroyed()) { current=false;continue; }
            regionX=detection.getRegionX();regionZ=detection.getRegionZ();current=true;
        }
        int i=offset++;
        return new ChunkCell(regionX*32+(i&31),regionZ*32+(i>>5));
    }
}
