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

    @Test void popularModdedBiomesClassifyIntoAppropriateSurfaceKindsAndExcludeCaves() {
        AutoConfig config=new AutoConfig();
        // Caves / underground remain OTHER
        assertEquals(TerrainKind.OTHER,TerrainClassifier.classify("terralith:amethyst_caves",Set.of(),0.8f,0,config));
        assertEquals(TerrainKind.OTHER,TerrainClassifier.classify("galosphere:crystal_canyons",Set.of(),0.5f,0,config));
        assertEquals(TerrainKind.OTHER,TerrainClassifier.classify("yungscavebiomes:frosted_caves",Set.of(),0.5f,0,config));
        assertEquals(TerrainKind.OTHER,TerrainClassifier.classify("minecraft:dripstone_caves",Set.of(),0.8f,0,config));
        assertEquals(TerrainKind.OTHER,TerrainClassifier.classify("biomesoplenty:glowing_grotto",Set.of(),0.5f,0,config));

        // Mountains / Cliffs / Spires / Slopes / Volcanoes
        assertEquals(TerrainKind.MOUNTAIN,TerrainClassifier.classify("terralith:white_cliffs",Set.of(),0.4f,0,config));
        assertEquals(TerrainKind.MOUNTAIN,TerrainClassifier.classify("terralith:bryce_canyon",Set.of(),2.0f,0,config));
        assertEquals(TerrainKind.MOUNTAIN,TerrainClassifier.classify("terralith:stony_spires",Set.of(),0.7f,0,config));
        assertEquals(TerrainKind.MOUNTAIN,TerrainClassifier.classify("biomesoplenty:jade_cliffs",Set.of(),0.8f,0,config));
        assertEquals(TerrainKind.MOUNTAIN,TerrainClassifier.classify("biomesoplenty:volcano",Set.of(),0.95f,0,config));
        assertEquals(TerrainKind.MOUNTAIN,TerrainClassifier.classify("regions_unexplored:pine_slopes",Set.of(),0.4f,0,config));
        assertEquals(TerrainKind.MOUNTAIN,TerrainClassifier.classify("natures_spirit:white_cliffs",Set.of(),0.95f,0,config));

        // Forests / Glades / Orchards / Gardens / Shields
        assertEquals(TerrainKind.FOREST,TerrainClassifier.classify("minecraft:pale_garden",Set.of(),0.7f,0,config));
        assertEquals(TerrainKind.FOREST,TerrainClassifier.classify("biomesoplenty:aspen_glade",Set.of(),0.4f,0,config));
        assertEquals(TerrainKind.FOREST,TerrainClassifier.classify("biomeswevegone:enchanted_tangle",Set.of(),0.8f,0,config));
        assertEquals(TerrainKind.FOREST,TerrainClassifier.classify("terralith:shield",Set.of(),0.4f,0,config));
        assertEquals(TerrainKind.FOREST,TerrainClassifier.classify("plaincraft_erdtree_sanctuary:erdtree_sanctuary",Set.of(),0.65f,0,config));

        // Plains / Valleys / Basins / Swamps / Wetlands / Beaches
        assertEquals(TerrainKind.PLAIN,TerrainClassifier.classify("minecraft:swamp",Set.of(),0.8f,0,config));
        assertEquals(TerrainKind.PLAIN,TerrainClassifier.classify("biomesoplenty:bayou",Set.of(),0.95f,0,config));
        assertEquals(TerrainKind.PLAIN,TerrainClassifier.classify("biomeswevegone:cypress_swamplands",Set.of(),0.8f,0,config));
        assertEquals(TerrainKind.PLAIN,TerrainClassifier.classify("terralith:lush_valley",Set.of(),0.4f,0,config));
        assertEquals(TerrainKind.PLAIN,TerrainClassifier.classify("biomesoplenty:origin_valley",Set.of(),0.6f,0,config));
        assertEquals(TerrainKind.PLAIN,TerrainClassifier.classify("regions_unexplored:ancient_delta",Set.of(),1.1f,0,config));
        assertEquals(TerrainKind.PLAIN,TerrainClassifier.classify("terralith:gravel_beach",Set.of(),0.8f,0,config));

        // Deserts / Dunes / Sands / Oasis
        assertEquals(TerrainKind.DESERT,TerrainClassifier.classify("terralith:sandstone_valley",Set.of(),2.0f,0,config));
        assertEquals(TerrainKind.DESERT,TerrainClassifier.classify("terralith:ancient_sands",Set.of(),2.0f,0,config));
        assertEquals(TerrainKind.DESERT,TerrainClassifier.classify("terralith:red_oasis",Set.of(),2.0f,0,config));
        assertEquals(TerrainKind.DESERT,TerrainClassifier.classify("biomesoplenty:dryland",Set.of(),0.85f,0,config));
        assertEquals(TerrainKind.DESERT,TerrainClassifier.classify("mysticsbiomes:lush_oasis",Set.of(),1.7f,0,config));

        // Water / Lakes / Lagoons / Reefs
        assertEquals(TerrainKind.LAKE,TerrainClassifier.classify("mysticsbiomes:lagoon",Set.of(),1.3f,0,config));
        assertEquals(TerrainKind.LAKE,TerrainClassifier.classify("biomesoplenty:hot_springs",Set.of(),0.17f,0,config));
        assertEquals(TerrainKind.OCEAN,TerrainClassifier.classify("regions_unexplored:rocky_reef",Set.of(),0.8f,0,config));
        assertEquals(TerrainKind.OCEAN,TerrainClassifier.classify("regions_unexplored:hyacinth_deeps",Set.of(),0.5f,0,config));
    }
    @Test void disposableSurfaceCachePreservesNegativeCoordinatesAndHonorsMemoryLimit() throws Exception {
        Map<ChunkCell,TerrainSample> samples=new LinkedHashMap<>();
        samples.put(new ChunkCell(-12,36),new TerrainSample("test:forest",TerrainKind.FOREST,0.7f,2400));
        samples.put(new ChunkCell(3,-9),new TerrainSample("test:cold",TerrainKind.FROZEN_OCEAN,0,TerrainSample.UNKNOWN_HEIGHT));
        Path path=directory.resolve("surface.gz");SurfaceCache.save(path,samples);
        assertEquals(samples,SurfaceCache.load(path,10));assertEquals(1,SurfaceCache.load(path,1).size());
    }

    @Test void northlandTakesColdNonMountainNonOceanBiomesAfterExplicitRules() {
        AutoConfig config=new AutoConfig();
        assertEquals(TerrainKind.NORTHLAND,TerrainClassifier.classify("test:pine_forest",Set.of("c:is_forest"),0,0,config));
        assertEquals(TerrainKind.NORTHLAND,TerrainClassifier.classify("test:frozen_lake",Set.of(),0,0,config));
        assertEquals(TerrainKind.NORTHLAND,TerrainClassifier.classify("test:river",Set.of("minecraft:is_river"),0,0,config));
        assertEquals(TerrainKind.MOUNTAIN,TerrainClassifier.classify("test:mountain",Set.of(),0,0,config));
        assertEquals(TerrainKind.FROZEN_OCEAN,TerrainClassifier.classify("test:frozen_ocean",Set.of("minecraft:is_ocean"),0,0,config));
        config.biomeRules=List.of(new AutoConfig.BiomeRule("test:pine_forest",TerrainKind.FOREST));
        assertEquals(TerrainKind.FOREST,TerrainClassifier.classify("test:pine_forest",Set.of(),0,0,config));
    }

    @Test void coldForestAndPlainSamplesAnalyzeAsOneNorthlandCandidate() {
        AutoConfig config=new AutoConfig();config.unlockSpan=256;
        config.thresholds.northland=new AutoConfig.Threshold(256,0);
        Map<ChunkCell,TerrainSample> samples=new HashMap<>();
        for(int x=0;x<20;x++) samples.put(new ChunkCell(x,0),new TerrainSample("test:cold_"+(x/4),
                x<10?TerrainKind.FOREST:TerrainKind.PLAIN,0,70));
        var result=new TerrainAnalysis().analyze(samples,config);
        assertEquals(1,result.candidates().size());assertEquals(TerrainKind.NORTHLAND,result.candidates().get(0).kind());
        assertEquals(20,result.candidates().get(0).cells().size());
    }

    @Test void anExplicitColdBiomeRuleKeepsItsRequestedCategoryInsteadOfNorthland() {
        AutoConfig config=new AutoConfig();config.unlockSpan=256;
        config.biomeRules=List.of(new AutoConfig.BiomeRule("test:cold_woods",TerrainKind.FOREST));
        config.thresholds.forest=new AutoConfig.Threshold(256,0);
        Map<ChunkCell,TerrainSample> samples=new HashMap<>();
        for(int x=0;x<20;x++) samples.put(new ChunkCell(x,0),new TerrainSample("test:cold_woods",TerrainKind.NORTHLAND,0,70));
        var result=new TerrainAnalysis().analyze(samples,config);
        assertEquals(List.of(TerrainKind.FOREST),result.candidates().stream().map(TerrainAnalysis.Candidate::kind).toList());
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

    @Test void tinyPatchIsAbsorbedIntoEnclosingHostPatch() {
        AutoConfig config=new AutoConfig();config.unlockSpan=256;
        config.thresholds.forest=new AutoConfig.Threshold(256,0);
        Map<ChunkCell,TerrainSample> samples=new HashMap<>();
        // 20x20 grid of forest, with a 1-chunk swamp right in the center (10,10)
        for(int x=0;x<20;x++) for(int z=0;z<20;z++) {
            if(x==10 && z==10) {
                samples.put(new ChunkCell(x,z),new TerrainSample("minecraft:swamp",TerrainKind.PLAIN,0.7f,64));
            } else {
                samples.put(new ChunkCell(x,z),new TerrainSample("minecraft:forest",TerrainKind.FOREST,0.7f,64));
            }
        }
        var result=new TerrainAnalysis().analyze(samples,config);
        assertEquals(1,result.candidates().size());
        assertEquals(TerrainKind.FOREST,result.candidates().get(0).kind());
        // Center swamp chunk was absorbed into the forest candidate!
        assertTrue(result.candidates().get(0).cells().contains(new ChunkCell(10,10)));
    }

    @Test void biomeOverridesTakePrecedenceOverRulesAndDefaults() {
        AutoConfig config=new AutoConfig();
        config.biomeOverrides.put("minecraft:plains","MOUNTAIN");
        assertEquals(TerrainKind.MOUNTAIN,TerrainClassifier.classify("minecraft:plains",Set.of(),0.8f,0,config));
    }
}
