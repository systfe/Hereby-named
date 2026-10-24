package dev.herebynamed.domain;

import dev.herebynamed.auto.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TerrainCompatibilityTest {
    @TempDir Path directory;
    @Test void modTagsAndUntaggedFlowerNamesWorkAndPlayerOverridesWin() {
        AutoConfig config=new AutoConfig();
        assertEquals(TerrainKind.FLOWER,TerrainClassifier.classify("mysticsbiomes:lavender_meadow",Set.of(),0.7f,0,config));
        assertEquals(TerrainKind.MOUNTAIN,TerrainClassifier.classify("test:wooded_place",Set.of("c:is_mountain/slope","minecraft:is_forest"),0.7f,0,config));
        assertEquals(TerrainKind.FROZEN_OCEAN,TerrainClassifier.classify("test:blue_water",Set.of("minecraft:is_ocean"),0,0,config));
        assertEquals(TerrainKind.OTHER,TerrainClassifier.classify("test:unknown",Set.of(),0.7f,0,config));
        config.biomeRules=List.of(new AutoConfig.BiomeRule("test:unknown",TerrainKind.FOREST));
        assertEquals(TerrainKind.FOREST,TerrainClassifier.classify("test:unknown",Set.of(),0.7f,0,config));
    }
    @Test void disposableSurfaceCachePreservesNegativeCoordinatesAndHonorsMemoryLimit() throws Exception {
        Map<ChunkCell,TerrainSample> samples=new LinkedHashMap<>();
        samples.put(new ChunkCell(-12,36),new TerrainSample("test:forest",TerrainKind.FOREST,0.7f,2400));
        samples.put(new ChunkCell(3,-9),new TerrainSample("test:cold",TerrainKind.FROZEN_OCEAN,0,TerrainSample.UNKNOWN_HEIGHT));
        Path path=directory.resolve("surface.gz");SurfaceCache.save(path,samples);
        assertEquals(samples,SurfaceCache.load(path,10));assertEquals(1,SurfaceCache.load(path,1).size());
    }
    @Test void denseAndScatteredMapsStayBoundedAndKeepNaturalBoundaries() {
        Map<ChunkCell,TerrainSample> map=new HashMap<>();
        for(int x=0;x<256;x++) for(int z=0;z<256;z++) map.put(new ChunkCell(x,z),new TerrainSample("test:forest",TerrainKind.FOREST,0.7f,80));
        long start=System.nanoTime();var result=new TerrainAnalysis().analyze(map,new AutoConfig());
        System.out.println("65536-chunk analysis ms="+(System.nanoTime()-start)/1_000_000.0);
        assertEquals(1,result.candidates().size());assertEquals(65536,result.candidates().get(0).cells().size());
        map.clear();for(int x=0;x<20_000;x++) map.put(new ChunkCell(x*3,x*3),new TerrainSample("test:forest",TerrainKind.FOREST,0.7f,80));
        assertTimeout(java.time.Duration.ofSeconds(10),() -> assertFalse(new TerrainAnalysis().analyze(map,new AutoConfig()).unlocked()));
    }
}
