package dev.herebynamed.domain;

import dev.herebynamed.auto.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AutoRegionsTest {
    @Test void distantIslandsDoNotUnlockButOneContinuousTwoThousandBlockMapDoes() {
        Map<ChunkCell, TerrainSample> map = new HashMap<>();
        map.put(new ChunkCell(0, 0), sample("minecraft:plains", TerrainKind.PLAIN));
        map.put(new ChunkCell(200, 0), sample("minecraft:plains", TerrainKind.PLAIN));
        assertFalse(new TerrainAnalysis().analyze(map, new AutoConfig()).unlocked());
        for (int x = 0; x < 125; x++) map.put(new ChunkCell(x, 0), sample("minecraft:plains", TerrainKind.PLAIN));
        assertTrue(new TerrainAnalysis().analyze(map, new AutoConfig()).unlocked());
    }
    private static TerrainSample sample(String biome, TerrainKind kind) {
        return new TerrainSample(biome, kind, 0.7f, 70);
    }
    @Test void threeRealBiomeIdsCanCreateAShortForestButTinyFragmentsCannot() {
        AutoConfig config = new AutoConfig(); config.unlockSpan = 256;
        Map<ChunkCell, TerrainSample> map = new HashMap<>();
        for (int x=0;x<24;x++) map.put(new ChunkCell(x,0), sample("test:forest"+(x/8),TerrainKind.FOREST));
        var result = new TerrainAnalysis().analyze(map,config);
        assertEquals(1,result.candidates().size());
        assertEquals(TerrainKind.FOREST,result.candidates().get(0).kind());
        map.put(new ChunkCell(8,0),sample("test:speck",TerrainKind.FOREST));
        for(int x=9;x<16;x++) map.put(new ChunkCell(x,0),sample("test:forest0",TerrainKind.FOREST));
        assertTrue(new TerrainAnalysis().analyze(map,config).candidates().isEmpty());
    }
    @Test void lakeNeedsKnownLandAllAroundAndAnUnknownGapPreventsNaming() {
        AutoConfig config=new AutoConfig(); config.unlockSpan=256;
        Map<ChunkCell,TerrainSample> map=new HashMap<>();
        for(int x=-1;x<=32;x++) for(int z=-1;z<=32;z++)
            map.put(new ChunkCell(x,z),sample("minecraft:plains",TerrainKind.PLAIN));
        for(int x=0;x<32;x++) for(int z=0;z<32;z++)
            map.put(new ChunkCell(x,z),sample("minecraft:ocean",TerrainKind.OCEAN));
        assertEquals(List.of(TerrainKind.LAKE),new TerrainAnalysis().analyze(map,config).candidates().stream().map(TerrainAnalysis.Candidate::kind).toList());
        map.remove(new ChunkCell(-1,12));
        assertTrue(new TerrainAnalysis().analyze(map,config).candidates().isEmpty());
    }
    @Test void aThirtyTwoBlockRiverConnectsForestWithoutOwningTheRiver() {
        AutoConfig config=new AutoConfig(); config.unlockSpan=256;
        Map<ChunkCell,TerrainSample> map=new HashMap<>();
        for(int x=0;x<64;x++) map.put(new ChunkCell(x,0),sample("test:forest",TerrainKind.FOREST));
        map.put(new ChunkCell(30,0),sample("minecraft:river",TerrainKind.RIVER));
        map.put(new ChunkCell(31,0),sample("minecraft:river",TerrainKind.RIVER));
        var candidates=new TerrainAnalysis().analyze(map,config).candidates();
        assertEquals(1,candidates.size());
        assertEquals(62,candidates.get(0).cells().size());
        assertFalse(candidates.get(0).cells().contains(new ChunkCell(30,0)));
    }
    @Test void smallConnectedFrozenOceanMergesByActualAreaNotItsBoundingBox() {
        Map<ChunkCell,TerrainSample> map=new HashMap<>();
        for(int x=0;x<125;x++) for(int z=0;z<4;z++) map.put(new ChunkCell(x,z),sample("minecraft:ocean",TerrainKind.OCEAN));
        for(int x=-10;x<0;x++) for(int z=0;z<4;z++) map.put(new ChunkCell(x,z),new TerrainSample("minecraft:frozen_ocean",TerrainKind.FROZEN_OCEAN,0,63));
        var candidates=new TerrainAnalysis().analyze(map,new AutoConfig()).candidates();
        assertEquals(1,candidates.size());
        assertEquals(TerrainKind.OCEAN,candidates.get(0).kind());
        assertEquals(540,candidates.get(0).cells().size());
    }
    @Test void temperateForestDoesNotBridgeSnowAndHotForestIntoOneClimate() {
        Map<ChunkCell,TerrainSample> map=new HashMap<>();
        for(int x=0;x<210;x++) map.put(new ChunkCell(x,0),new TerrainSample("test:forest",TerrainKind.FOREST,x<70?0:x<140?0.7f:1.5f,70));
        var result=new TerrainAnalysis().analyze(map,new AutoConfig());
        assertEquals(2,result.candidates().size());
        for(var region:result.candidates()) assertFalse(region.cells().contains(new ChunkCell(0,0)) && region.cells().contains(new ChunkCell(209,0)));
    }
}
