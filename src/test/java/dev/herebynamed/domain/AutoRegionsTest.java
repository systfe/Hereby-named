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
    @Test void forestCreationUsesSpanRegardlessOfBiomeVariety() {
        AutoConfig config=new AutoConfig();config.unlockSpan=256;
        Map<ChunkCell,TerrainSample> map=new HashMap<>();
        for(int x=0;x<48;x++) map.put(new ChunkCell(x,0),sample("test:forest"+(x/24),TerrainKind.FOREST));
        config.thresholds.forest=new AutoConfig.Threshold(800,0);
        assertTrue(new TerrainAnalysis().analyze(map,config).candidates().isEmpty());
        for(int x=48;x<64;x++) map.put(new ChunkCell(x,0),sample("test:forest"+(x/2),TerrainKind.FOREST));
        config.thresholds.forest=new AutoConfig.Threshold(768,0);
        assertEquals(1,new TerrainAnalysis().analyze(map,config).candidates().size());
    }
    @Test void manyBiomeIdsCannotBypassMinimumForestSpan() {
        AutoConfig config = new AutoConfig();
        config.names = List.of(new AutoConfig.Name("亚速", List.of("ALL"), List.of())); config.unlockSpan = 256;
        Map<ChunkCell, TerrainSample> map = new HashMap<>();
        for (int x=0;x<40;x++) map.put(new ChunkCell(x,0), sample("test:forest"+(x/8),TerrainKind.FOREST));
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
        config.thresholds.lake=new AutoConfig.Threshold(640,2000);
        assertTrue(new TerrainAnalysis().analyze(map,config).candidates().isEmpty());
        config.thresholds.lake=new AutoConfig.Threshold(480,520);
        assertEquals(List.of(TerrainKind.LAKE),new TerrainAnalysis().analyze(map,config).candidates().stream().map(TerrainAnalysis.Candidate::kind).toList());
        map.remove(new ChunkCell(-1,12));
        assertTrue(new TerrainAnalysis().analyze(map,config).candidates().isEmpty());
    }
    @Test void aThirtyTwoBlockRiverConnectsForestAndFillsItsNarrowGap() {
        AutoConfig config=new AutoConfig(); config.unlockSpan=256;
        Map<ChunkCell,TerrainSample> map=new HashMap<>();
        for(int x=0;x<64;x++) map.put(new ChunkCell(x,0),sample("test:forest",TerrainKind.FOREST));
        map.put(new ChunkCell(30,0),sample("minecraft:river",TerrainKind.RIVER));
        map.put(new ChunkCell(31,0),sample("minecraft:river",TerrainKind.RIVER));
        var candidates=new TerrainAnalysis().analyze(map,config).candidates();
        assertEquals(1,candidates.size());
        assertEquals(64,candidates.get(0).cells().size());
        assertTrue(candidates.get(0).cells().contains(new ChunkCell(30,0)));
        assertTrue(candidates.get(0).cells().contains(new ChunkCell(31,0)));
    }

    @Test void riverBridgeIsIncludedSoTheGeneratedRegionHasNoGap() {
        AutoConfig config=new AutoConfig();config.unlockSpan=256;
        Map<ChunkCell,TerrainSample> map=new HashMap<>();
        for(int x=0;x<64;x++) map.put(new ChunkCell(x,0),sample("test:forest",TerrainKind.FOREST));
        map.put(new ChunkCell(30,0),sample("minecraft:river",TerrainKind.RIVER));
        map.put(new ChunkCell(31,0),sample("minecraft:river",TerrainKind.RIVER));

        var candidate=new TerrainAnalysis().analyze(map,config).candidates().get(0);

        assertEquals(64,candidate.cells().size());
        for(int x=0;x<64;x++) assertTrue(candidate.cells().contains(new ChunkCell(x,0)),"missing selected chunk x="+x);
    }

    @Test void anEightyBlockRiverStillFormsOneGapFreeLandRegion() {
        AutoConfig config=new AutoConfig();config.unlockSpan=256;
        config.thresholds.forest=new AutoConfig.Threshold(768,0);
        Map<ChunkCell,TerrainSample> map=new HashMap<>();
        for(int x=0;x<70;x++) map.put(new ChunkCell(x,0),sample("test:forest",TerrainKind.FOREST));
        for(int x=30;x<35;x++) map.put(new ChunkCell(x,0),sample("minecraft:river",TerrainKind.RIVER));

        var candidates=new TerrainAnalysis().analyze(map,config).candidates();

        assertEquals(1,candidates.size());
        assertEquals(70,candidates.get(0).cells().size());
        for(int x=0;x<70;x++) assertTrue(candidates.get(0).cells().contains(new ChunkCell(x,0)));
    }

    @Test void knownEnclosedBiomeHolesAreFilledButUnknownMapCellsAreNotGuessed() {
        AutoConfig config=new AutoConfig();config.unlockSpan=256;
        config.thresholds.forest=new AutoConfig.Threshold(300,0);
        config.thresholds.plain=new AutoConfig.Threshold(500,0);
        Map<ChunkCell,TerrainSample> map=new HashMap<>();
        for(int x=0;x<20;x++) for(int z=0;z<20;z++) {
            boolean edge=x==0 || z==0 || x==19 || z==19;
            map.put(new ChunkCell(x,z),sample(edge?"test:forest":"test:plains",edge?TerrainKind.FOREST:TerrainKind.PLAIN));
        }

        var filled=new TerrainAnalysis().analyze(map,config).candidates().stream()
                .filter(c -> c.kind()==TerrainKind.FOREST).findFirst().orElseThrow();
        assertEquals(400,filled.cells().size());

        map.remove(new ChunkCell(10,10));
        var conservative=new TerrainAnalysis().analyze(map,config).candidates().stream()
                .filter(c -> c.kind()==TerrainKind.FOREST).findFirst().orElseThrow();
        assertFalse(conservative.cells().contains(new ChunkCell(10,10)));
    }

    @Test void automaticPruneDeletesRegionWithoutSuppressingFutureGeneration() throws Exception {
        RegionBook book=new RegionBook();
        ChunkCell cell=new ChunkCell(0,0);
        book.automate("PLAIN","微风平原",0x123456,Set.of(cell));
        assertEquals(1,book.regions().size());
        String regionId=book.regions().get(0).id();

        RegionEdit prune=new RegionEdit("minecraft:overworld",0,RegionEdit.Action.AUTO_PRUNE,1,regionId,"微风平原",0,Set.of());
        byte[] bytes=prune.encode();
        RegionEdit decoded=RegionEdit.decode(bytes);
        assertEquals(RegionEdit.Action.AUTO_PRUNE,decoded.action());

        decoded.apply(book);
        assertTrue(book.regions().isEmpty());
        assertFalse(book.state().suppressed().contains(cell));
    }

    @Test void automaticApplyAcceptsTheNorthlandClassification() {
        RegionEdit edit=new RegionEdit("minecraft:overworld",0,RegionEdit.Action.AUTO_APPLY,1,
                "NORTHLAND:COLD","北基朗",0xabc123,Set.of(new ChunkCell(0,0)));
        RegionBook book=new RegionBook();

        assertDoesNotThrow(() -> edit.apply(book));
        assertEquals("北基朗",book.owner(1,new ChunkCell(0,0)).orElseThrow().name());
    }

    @Test void spanThresholdProducesOneCompleteRegionOfAtLeastOneThousandBlocks() {
        AutoConfig config=new AutoConfig();config.unlockSpan=256;
        config.thresholds.forest=new AutoConfig.Threshold(1000,0);
        Map<ChunkCell,TerrainSample> map=new HashMap<>();
        for(int x=0;x<80;x++) map.put(new ChunkCell(x,0),sample("test:forest"+(x/8),TerrainKind.FOREST));

        var candidates=new TerrainAnalysis().analyze(map,config).candidates();

        assertEquals(1,candidates.size());
        assertEquals(80,candidates.get(0).cells().size());
        assertTrue(candidates.get(0).span()>=1000);
        for(int x=0;x<80;x++) assertTrue(candidates.get(0).cells().contains(new ChunkCell(x,0)));
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

    @Test void frozenOceanIsolatesWhenAtLeastOneThirdOtherwiseSubsumed() {
        AutoConfig config=new AutoConfig();config.unlockSpan=256;
        config.thresholds.ocean=new AutoConfig.Threshold(256,0);
        config.thresholds.frozenOcean=new AutoConfig.Threshold(256,0);

        Map<ChunkCell,TerrainSample> map=new HashMap<>();
        for(int x=0;x<60;x++) for(int z=0;z<4;z++) map.put(new ChunkCell(x,z),sample("minecraft:ocean",TerrainKind.OCEAN));
        for(int x=60;x<100;x++) for(int z=0;z<4;z++) map.put(new ChunkCell(x,z),new TerrainSample("minecraft:frozen_ocean",TerrainKind.FROZEN_OCEAN,0,63));

        var candidates=new TerrainAnalysis().analyze(map,config).candidates();
        assertEquals(2,candidates.size());
        assertTrue(candidates.stream().anyMatch(c -> c.kind()==TerrainKind.FROZEN_OCEAN && c.cells().size()==160));
        assertTrue(candidates.stream().anyMatch(c -> c.kind()==TerrainKind.OCEAN && c.cells().size()==240));

        assertEquals(TerrainKind.OCEAN,TerrainClassifier.classify("minecraft:deep_cold_ocean",Set.of("minecraft:is_ocean"),0.5f,0,config));
        assertEquals(TerrainKind.OCEAN,TerrainClassifier.classify("minecraft:cold_ocean",Set.of("minecraft:is_ocean"),0.5f,0,config));
    }
    @Test void temperateForestDoesNotBridgeSnowAndHotForestIntoOneClimate() {
        Map<ChunkCell,TerrainSample> map=new HashMap<>();
        for(int x=0;x<210;x++) map.put(new ChunkCell(x,0),new TerrainSample("test:forest",TerrainKind.FOREST,x<70?0:x<140?0.7f:1.5f,70));
        var result=new TerrainAnalysis().analyze(map,new AutoConfig());
        assertEquals(2,result.candidates().size());
        for(var region:result.candidates()) assertFalse(region.cells().contains(new ChunkCell(0,0)) && region.cells().contains(new ChunkCell(209,0)));
    }

    @Test void legacyMinimumBiomesConfigurationIsIgnoredAndMigratedToSpanThreshold() throws Exception {
        java.nio.file.Path tempDir = java.nio.file.Files.createTempDirectory("hbn-test-config");
        try {
            String legacyJson = """
                {
                  "thresholds": {
                    "forest": {
                      "minimumBiomes": 5,
                      "minimumSpan": 0,
                      "maximumSpan": 0
                    }
                  },
                  "minimumBiomeChunks": 4
                }
                """;
            java.nio.file.Files.writeString(tempDir.resolve("automatic.json"), legacyJson);
            AutoConfig loaded = AutoConfig.load(tempDir);
            assertEquals(1000, loaded.thresholds.forest.minimumSpan());
            assertFalse(loaded.qualifies(TerrainKind.FOREST, 500));
            assertTrue(loaded.qualifies(TerrainKind.FOREST, 1000));
        } finally {
            try (var s = java.nio.file.Files.walk(tempDir)) {
                s.sorted(Comparator.reverseOrder()).forEach(p -> { try { java.nio.file.Files.deleteIfExists(p); } catch (Exception ignored) {} });
            }
        }
    }

    @Test void neckOfLessThanThreeChunksSplitsCandidateIntoIndependentComponents() {
        AutoConfig config = new AutoConfig();
        config.names = List.of(new AutoConfig.Name("亚速", List.of("ALL"), List.of()));
        config.unlockSpan = 256;
        config.thresholds.forest = new AutoConfig.Threshold(1000, 0);

        Map<ChunkCell, TerrainSample> map = new HashMap<>();
        // 区域 1：东侧超大森林 (x: 0..70, z: 0..10)，跨度 71*16 = 1136 格 >= 1000 格
        for (int x = 0; x <= 70; x++) {
            for (int z = 0; z <= 10; z++) {
                map.put(new ChunkCell(x, z), sample("test:forest", TerrainKind.FOREST));
            }
        }
        // 狭窄颈部：连接通道 (x: 71..74, z: 4..5)，长度 4 个区块，宽度仅 2 个区块 (< 3)
        for (int x = 71; x <= 74; x++) {
            map.put(new ChunkCell(x, 4), sample("test:forest", TerrainKind.FOREST));
            map.put(new ChunkCell(x, 5), sample("test:forest", TerrainKind.FOREST));
        }
        // 区域 2：西侧较小森林 (x: 75..95, z: 0..10)，跨度 21*16 = 336 格 < 1000 格
        for (int x = 75; x <= 95; x++) {
            for (int z = 0; z <= 10; z++) {
                map.put(new ChunkCell(x, z), sample("test:forest", TerrainKind.FOREST));
            }
        }

        var result = new TerrainAnalysis().analyze(map, config);
        // 如果未切除颈部，总跨度为 (95-0+1)*16 = 1536 格，会作为一个大区域生成
        // 切除狭窄颈部后，分裂为东侧主体 (跨度 1136 格 >= 1000 格) 与西侧小区分量 (跨度 336 格 < 1000 格)
        // 仅东侧达标的大主体被保留生成为 1 个候选！
        assertEquals(1, result.candidates().size());
        var candidate = result.candidates().get(0);
        assertTrue(candidate.span() >= 1000);
        assertTrue(candidate.cells().contains(new ChunkCell(0, 0)));
        assertFalse(candidate.cells().contains(new ChunkCell(90, 5)));
    }

    @Test void pruneUnderqualifiedRemovesAutomaticRegionWhenManualOccupiesMajority() {
        AutoConfig config = new AutoConfig();
        config.names = List.of(new AutoConfig.Name("亚速", List.of("ALL"), List.of()));
        config.thresholds.mountain = new AutoConfig.Threshold(1000, 0);

        RegionBook book = new RegionBook();
        // 自动创建一座山地区域：跨度 1280 格 (80 个区块)
        Set<ChunkCell> mountainCells = new HashSet<>();
        for (int x = 0; x < 80; x++) mountainCells.add(new ChunkCell(x, 0));
        book.automate("MOUNTAIN:COLD", "测试山", 0x112233, mountainCells);
        assertEquals(1, book.regions().size());

        // 玩家在图层 1 手工创建大区域，占用了其中 50 个区块 (x: 0..49)
        book.createRegion(1, "玩家山脉", 0x445566);
        String manualId = book.regions().stream().filter(r -> r.name().equals("玩家山脉")).findFirst().orElseThrow().id();
        Set<ChunkCell> playerOccupied = new HashSet<>();
        for (int x = 0; x < 50; x++) playerOccupied.add(new ChunkCell(x, 0));
        book.paint(manualId, playerOccupied, true);

        // 剩余 30 个区块 (跨度 30*16 = 480 格 < 1000 格)
        // 重新分析调用 pruneUnderqualifiedAutomatic
        boolean pruned = book.pruneUnderqualifiedAutomatic(config);
        assertTrue(pruned);
        // "测试山" 因剩余跨度不足 1000 格被完全清理，仅保留玩家的手动区域
        assertEquals(1, book.regions().size());
        assertEquals("玩家山脉", book.regions().get(0).name());
    }

    @Test void oceanCandidateTouchingPlainNeverInheritsPlainNameOrColor() {
        RegionBook book = new RegionBook();
        AutoConfig config = new AutoConfig();
        config.names = List.of(new AutoConfig.Name("亚速", List.of("ALL"), List.of()));
        Set<ChunkCell> plainCells = new HashSet<>();
        for (int x = 0; x < 50; x++) for (int z = 0; z < 50; z++) plainCells.add(new ChunkCell(x, z));
        book.automate("PLAIN:TEMPERATE", "埃兰诺平原", 0xc1bd70, plainCells);
        assertEquals(1, book.regions().size());

        Set<ChunkCell> oceanCells = new HashSet<>();
        for (int x = 45; x < 120; x++) for (int z = 0; z < 60; z++) oceanCells.add(new ChunkCell(x, z));
        var oceanCandidate = new TerrainAnalysis.Candidate(TerrainKind.OCEAN, oceanCells, 0.5f, 1200);

        String oceanName = AutoNames.choose(oceanCandidate, book, config).orElseThrow();
        assertNotEquals("埃兰诺平原", oceanName);
        assertTrue(oceanName.endsWith("洋"), "Ocean name should end with 洋: " + oceanName);

        boolean added = book.automate(AutoNames.key(oceanCandidate), oceanName, TerrainKind.OCEAN.color, oceanCells);
        assertTrue(added);

        var oceanRegion = book.regions().stream().filter(r -> r.name().equals(oceanName)).findFirst().orElseThrow();
        assertEquals("OCEAN", oceanRegion.automatic().classification());
        assertFalse(oceanRegion.automatic().formerNames().contains("埃兰诺平原"));

        long elanCount = book.regions().stream().filter(r -> r.name().equals("埃兰诺平原")).count();
        assertEquals(1, elanCount);
    }

    @Test void cuttingRegionNeverCreatesMultipleRegionsWithTheSameName() {
        RegionBook book = new RegionBook();
        Set<ChunkCell> plainCells = new HashSet<>();
        for (int x = 0; x < 100; x++) plainCells.add(new ChunkCell(x, 0));
        book.automate("PLAIN:TEMPERATE", "微风平原", 0xc1bd70, plainCells);
        assertEquals(1, book.regions().size());

        Set<ChunkCell> cutCells = new HashSet<>();
        for (int x = 40; x <= 60; x++) cutCells.add(new ChunkCell(x, 0));
        book.automate("FOREST:TEMPERATE", "青翠之森", 0x549b63, cutCells);

        long plainCount = book.regions().stream().filter(r -> r.name().equals("微风平原")).count();
        assertEquals(1, plainCount);
    }
}
