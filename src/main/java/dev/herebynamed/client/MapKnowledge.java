package dev.herebynamed.client;

import dev.herebynamed.domain.ChunkCell;
import xaero.map.MapProcessor;
import xaero.map.region.*;
import xaero.map.region.texture.LeafRegionTexture;
import java.util.*;

/** Reads only recorded surface map data; never queries or generates server terrain. */
final class MapKnowledge {
    record Sample(String biome, boolean loading) {
        static final Sample UNKNOWN = new Sample(null, false), LOADING = new Sample(null, true);
    }
    private final MapProcessor processor;
    private final Map<Long, Long> loadRequests = new HashMap<>();
    MapKnowledge(MapProcessor processor) { this.processor = processor; }
    Sample sample(ChunkCell cell, boolean load) {
        synchronized (processor.uiSync) {
            int rx = cell.x() >> 5, rz = cell.z() >> 5;
            var dimension = processor.getMapWorld().getCurrentDimension();
            if (dimension == null) return Sample.UNKNOWN;
            var manager = dimension.getLayeredMapRegions();
            var layer = manager.getLayer(Integer.MAX_VALUE);
            MapRegion region = manager.getLeaf(Integer.MAX_VALUE, rx, rz);
            if (region == null) {
                if (!load || layer == null || !layer.regionDetectionExists(rx, rz)) return Sample.UNKNOWN;
                region = processor.getLeafMapRegion(Integer.MAX_VALUE, rx, rz, true);
                if (region == null) return Sample.LOADING;
            }
            int tx = (cell.x() >> 2) & 7, tz = (cell.z() >> 2) & 7;
            LeafRegionTexture texture = region.getTexture(tx, tz);
            int px = ((cell.x() & 3) << 4) + 8, pz = ((cell.z() & 3) << 4) + 8;
            if (texture != null && texture.getHeight(px, pz) != 32767) {
                var biome = texture.getBiome(px, pz);
                if (biome != null) return new Sample(biome.getValue().toString(), false);
            }
            MapTileChunk chunk = region.getChunk(tx, tz);
            MapTile tile = chunk == null ? null : chunk.getTile(cell.x() & 3, cell.z() & 3);
            MapBlock block = tile == null ? null : tile.getBlock(8, 8);
            if (block != null && block.getBiome() != null) return new Sample(block.getBiome().getValue().toString(), false);
            if (!load || region.isLoaded()) return Sample.UNKNOWN;
            long key = ((long)rx<<32) ^ (rz & 0xffffffffL), now = System.nanoTime();
            if (now - loadRequests.getOrDefault(key, 0L) > 1_000_000_000L) {
                processor.getMapSaveLoad().requestLoad(region, "Hereby Named selection", true);
                loadRequests.put(key, now);
            }
            return Sample.LOADING;
        }
    }
}
