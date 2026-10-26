package dev.herebynamed.domain;

import dev.herebynamed.auto.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AutomaticNamesTest {
    @TempDir Path directory;
    @Test void rootGeneratedForestsUseForestOrWoodlandStyleNames() {
        AutoConfig config=new AutoConfig();
        config.roots=List.of(new AutoConfig.Name("瑟兰",List.of("FOREST"),"ANY"));
        for(int x=0;x<8;x++) {
            var candidate=new TerrainAnalysis.Candidate(TerrainKind.FOREST,Set.of(new ChunkCell(x,0)),0.7f,1024);
            assertTrue(AutoNames.choose(candidate,new RegionBook(),config).orElseThrow().matches("瑟兰(森林|林)"));
        }
    }
    @Test void plainsUsePlainOrFieldSuffixes() {
        AutoConfig config=new AutoConfig();
        config.roots=List.of(new AutoConfig.Name("艾伦",List.of("PLAIN"),"ANY"));
        for(int x=0;x<8;x++) {
            var candidate=new TerrainAnalysis.Candidate(TerrainKind.PLAIN,Set.of(new ChunkCell(x,0)),0.7f,1024);
            assertTrue(AutoNames.choose(candidate,new RegionBook(),config).orElseThrow().matches("艾伦(平原|之原)"));
        }
    }
    @Test void mountainExpansionCanChangeItsStemWhenTheGroupedNameIsAlreadyUsed() {
        AutoConfig config=new AutoConfig();config.roots=List.of(new AutoConfig.Name("瑟兰",List.of("MOUNTAIN"),"ANY"));
        RegionBook book=new RegionBook();book.automate("MOUNTAIN:TEMPERATE","赤霞峰",1,Set.of(new ChunkCell(0,0)));
        book.createRegion(2,"赤霞岭",2);
        Set<ChunkCell> cells=new HashSet<>();for(int x=0;x<100;x++) cells.add(new ChunkCell(x,0));
        var candidate=new TerrainAnalysis.Candidate(TerrainKind.MOUNTAIN,cells,0.7f,1600);
        assertEquals("瑟兰岭",AutoNames.choose(candidate,book,config).orElseThrow());
    }
    @Test void settingsDraftSavesNamesRootsAndThresholdsWithoutMutatingOriginal() throws Exception {
        AutoConfig original=AutoConfig.load(directory),draft=original.copy();
        draft.thresholds.forest=new AutoConfig.Threshold(1800,0);
        draft.names=new ArrayList<>(List.of(new AutoConfig.Name("风信洋",List.of("OCEAN"),"ANY")));
        draft.roots=new ArrayList<>(List.of(new AutoConfig.Name("瑟兰",List.of("FOREST"),"TEMPERATE")));
        draft.saveAll(directory);
        assertEquals(1000,original.thresholds.forest.minimumSpan());
        AutoConfig loaded=AutoConfig.load(directory);
        assertEquals(draft.thresholds.forest,loaded.thresholds.forest);
        assertEquals(draft.names,loaded.names);assertEquals(draft.roots,loaded.roots);
        draft.names.add(new AutoConfig.Name("无效2",List.of("ALL"),"ANY"));
        assertThrows(IllegalArgumentException.class,() -> draft.saveAll(directory));
        assertEquals(loaded.names,AutoConfig.load(directory).names);
    }
    @Test void existingPartialConfigurationGainsEditableDefaultsWithoutLosingCustomValues() throws Exception {
        java.nio.file.Files.writeString(directory.resolve("automatic.json"),
                "{\"enabled\":false,\"unlockSpan\":3000,\"minimumBiomeChunks\":9,\"thresholds\":{\"forest\":{\"minimumBiomes\":7}}}");
        AutoConfig config=AutoConfig.load(directory);
        assertFalse(config.enabled);assertEquals(3000,config.unlockSpan);
        assertEquals(new AutoConfig.Threshold(1000,0),config.thresholds.forest);
        assertEquals(new AutoConfig.Threshold(1000,0),config.thresholds.plain);
        String saved=java.nio.file.Files.readString(directory.resolve("automatic.json"));
        assertTrue(saved.contains("\"frozenOcean\""));
        assertTrue(saved.contains("\"maximumSpan\""));
        assertFalse(saved.contains("minimumBiomes"));assertFalse(saved.contains("minimumBiomeChunks"));
        assertFalse(saved.contains("minimumBiomes"));assertFalse(saved.contains("minimumBiomeChunks"));
        config.thresholds.lake=new AutoConfig.Threshold(2000,500);
        assertThrows(IllegalArgumentException.class,config::validate);
    }
    @Test void completeNamesAreUsedBeforeRootCombinations() {
        AutoConfig config=new AutoConfig();
        config.names=List.of(new AutoConfig.Name("银霜山",List.of("MOUNTAIN"),"COLD"));
        config.roots=List.of(new AutoConfig.Name("白岚",List.of("MOUNTAIN"),"COLD"));
        for(int x=0;x<8;x++) {
            var candidate=new TerrainAnalysis.Candidate(TerrainKind.MOUNTAIN,Set.of(new ChunkCell(x,0)),0,1024);
            assertEquals("银霜山",AutoNames.choose(candidate,new RegionBook(),config).orElseThrow());
        }
    }
    @Test void whitelistFiltersClimateAndOnlyRepeatsAfterUniqueNamesAreExhausted() {
        AutoConfig config=new AutoConfig();
        config.names=List.of(new AutoConfig.Name("银霜山",List.of("MOUNTAIN"),"COLD"),
                new AutoConfig.Name("炎阳山",List.of("MOUNTAIN"),"HOT"));
        config.roots=List.of();
        Set<ChunkCell> cells=Set.of(new ChunkCell(0,0));
        var candidate=new TerrainAnalysis.Candidate(TerrainKind.MOUNTAIN,cells,0,1024);
        RegionBook book=new RegionBook();
        assertEquals("银霜山",AutoNames.choose(candidate,book,config).orElseThrow());
        String id=book.createRegion(2,"银霜山",0x888888);book.paint(id,cells,true);
        assertEquals("银霜山",AutoNames.choose(candidate,book,config).orElseThrow());
    }
    @Test void shippedWhitelistsLoadAndConfigurationRoundTripsWithoutOverwritingNames() throws Exception {
        AutoConfig config=AutoConfig.load(directory);assertFalse(config.names.isEmpty());assertFalse(config.roots.isEmpty());
        Set<String> restricted=config.names.stream().filter(n -> !n.kinds().isEmpty()).map(AutoConfig.Name::text).collect(java.util.stream.Collectors.toSet());
        assertTrue(restricted.containsAll(Set.of("峨邙","岷峨","岱北","穆朗","嵩湘","梓浦","寒松","赤岩","霜火")));
        assertTrue(config.names.stream().anyMatch(n -> n.text().equals("亚速") && n.kinds().isEmpty() && n.climates().isEmpty()));
        var names=config.names;
        config.enabled=false;config.save(directory);
        AutoConfig loaded=AutoConfig.load(directory);
        assertFalse(loaded.enabled);assertEquals(names,loaded.names);
        loaded.names=List.of(new AutoConfig.Name("银霜山2",List.of("MOUNTAIN"),"COLD"));
        assertThrows(IllegalArgumentException.class,loaded::validate);
    }

    @Test void oldVersion030UnmodifiedAssetsAutomaticallyUpgradeToLatest() throws Exception {
        java.nio.file.Path legacyDir = directory.resolve("legacy_test");
        java.nio.file.Files.createDirectories(legacyDir);
        // Write mock 0.3.0 names with 125 entries ending in 梓浦
        List<AutoConfig.Name> mockOldNames = new ArrayList<>();
        for(int i=0;i<124;i++) mockOldNames.add(new AutoConfig.Name("老名"+(char)('a'+(i%26))+(char)('a'+(i/26)),List.of(),List.of()));
        mockOldNames.add(new AutoConfig.Name("梓浦",List.of(),List.of()));
        java.nio.file.Files.writeString(legacyDir.resolve("names.json"), new com.google.gson.Gson().toJson(mockOldNames));

        // Write mock 0.3.0 roots with 157 entries containing 霜风 and 汜拉诺
        List<AutoConfig.Name> mockOldRoots = new ArrayList<>();
        for(int i=0;i<155;i++) mockOldRoots.add(new AutoConfig.Name("词根"+(char)('a'+(i%26))+(char)('a'+(i/26)),List.of(),List.of()));
        mockOldRoots.add(new AutoConfig.Name("霜风",List.of(),List.of()));
        mockOldRoots.add(new AutoConfig.Name("汜拉诺",List.of(),List.of()));
        java.nio.file.Files.writeString(legacyDir.resolve("roots.json"), new com.google.gson.Gson().toJson(mockOldRoots));

        AutoConfig loaded = AutoConfig.load(legacyDir);
        // Assert that they got automatically upgraded to latest 246 names and clean roots!
        assertEquals(246, loaded.names.size());
        assertTrue(loaded.names.stream().anyMatch(n -> n.text().equals("寒松")));
        assertTrue(loaded.names.stream().anyMatch(n -> n.text().equals("赤岩")));
        assertFalse(loaded.roots.stream().anyMatch(n -> n.text().equals("霜风")));
    }

    @Test void defaultNamesGainTerrainSuffixAndAreUsedBeforeUniqueRootCompositions() {
        AutoConfig config=new AutoConfig();
        config.names=List.of(new AutoConfig.Name("亚速",List.of("ALL"),List.of()));
        config.roots=List.of(new AutoConfig.Name("拉诺",List.of("ALL"),List.of()));
        var candidate=new TerrainAnalysis.Candidate(TerrainKind.OCEAN,Set.of(new ChunkCell(0,0)),0.7f,256);
        RegionBook book=new RegionBook();
        assertEquals("亚速洋",AutoNames.choose(candidate,book,config).orElseThrow());
        book.createRegion(2,"亚速洋",1);
        assertTrue(AutoNames.choose(candidate,book,config).orElseThrow().startsWith("拉诺"));
    }

    @Test void defaultNamesWithExistingTerrainSuffixDoNotDuplicateIt() {
        AutoConfig config=new AutoConfig();
        config.names=List.of(new AutoConfig.Name("北邙山",List.of("MOUNTAIN"),List.of()));
        config.roots=List.of();
        Set<ChunkCell> ridge=new HashSet<>();for(int x=0;x<100;x++) ridge.add(new ChunkCell(x,0));
        var candidate=new TerrainAnalysis.Candidate(TerrainKind.MOUNTAIN,ridge,0.7f,1600);
        assertEquals("北邙岭",AutoNames.choose(candidate,new RegionBook(),config).orElseThrow());
    }

    @Test void reanalysisAddsTheNewSuffixToAnExistingBareDefaultName() {
        AutoConfig config=new AutoConfig();
        config.names=List.of(new AutoConfig.Name("亚速",List.of("ALL"),List.of()));
        config.roots=List.of();
        Set<ChunkCell> cells=new HashSet<>();for(int x=0;x<100;x++) cells.add(new ChunkCell(x,0));
        RegionBook book=new RegionBook();book.automate("OCEAN:TEMPERATE","亚速",0x438ac7,cells);
        var candidate=new TerrainAnalysis.Candidate(TerrainKind.OCEAN,cells,0.7f,1600);

        String updated=AutoNames.choose(candidate,book,config).orElseThrow();
        book.automate(AutoNames.key(candidate),updated,0x438ac7,cells);

        assertEquals("亚速洋",book.owner(1,new ChunkCell(0,0)).orElseThrow().name());
    }

    @Test void existingBareMountainDefaultNameGetsItsMountainSuffix() {
        AutoConfig config=new AutoConfig();config.names=List.of(new AutoConfig.Name("北邙",List.of("MOUNTAIN"),List.of()));
        Set<ChunkCell> cells=Set.of(new ChunkCell(0,0));RegionBook book=new RegionBook();
        book.automate("MOUNTAIN:TEMPERATE","北邙",0xa8a7b0,cells);
        var candidate=new TerrainAnalysis.Candidate(TerrainKind.MOUNTAIN,cells,0.7f,16);
        assertEquals("北邙山",AutoNames.choose(candidate,book,config).orElseThrow());
    }

    @Test void rootCompositionsUseOnlyCompatibleClimateAndOneToThreeRoots() {
        AutoConfig config=new AutoConfig();config.names=List.of();
        config.roots=List.of(new AutoConfig.Name("汜",List.of("ALL"),List.of("COLD","HOT")),
                new AutoConfig.Name("拉诺",List.of("ALL"),List.of()),
                new AutoConfig.Name("峨",List.of("MOUNTAIN"),List.of("COLD","HOT")));
        var temperateOcean=new TerrainAnalysis.Candidate(TerrainKind.OCEAN,Set.of(new ChunkCell(0,0)),0.7f,256);
        String name=AutoNames.choose(temperateOcean,new RegionBook(),config).orElseThrow();
        assertTrue(name.endsWith("洋"));
        assertTrue(name.startsWith("拉诺"));
        assertFalse(name.contains("汜"));
        assertFalse(name.contains("峨"));

        config.names=List.of();config.roots=List.of(new AutoConfig.Name("汜",List.of(),List.of("COLD","HOT")));
        var coldOcean=new TerrainAnalysis.Candidate(TerrainKind.OCEAN,Set.of(new ChunkCell(1,0)),-0.2f,256);
        var hotOcean=new TerrainAnalysis.Candidate(TerrainKind.OCEAN,Set.of(new ChunkCell(2,0)),1.5f,256);
        assertTrue(AutoNames.choose(coldOcean,new RegionBook(),config).orElseThrow().startsWith("汜"));
        assertTrue(AutoNames.choose(hotOcean,new RegionBook(),config).orElseThrow().startsWith("汜"));
        assertTrue(AutoNames.choose(temperateOcean,new RegionBook(),config).isEmpty());
    }

    @Test void generatedNamesContainNoMoreThanThreeCompatibleDistinctRoots() {
        AutoConfig config=new AutoConfig();config.names=List.of();
        config.roots=List.of(new AutoConfig.Name("甲",List.of(),List.of()),new AutoConfig.Name("乙",List.of(),List.of()),new AutoConfig.Name("丙",List.of(),List.of()));
        var candidate=new TerrainAnalysis.Candidate(TerrainKind.FOREST,Set.of(new ChunkCell(0,0)),0.7f,256);
        String name=AutoNames.choose(candidate,new RegionBook(),config).orElseThrow();
        assertTrue(name.matches("(?:甲|乙|丙){1,3}(?:森林|林)"));
    }

    @Test void duplicateNamesAreAllowedOnlyAfterEveryUniqueDefaultAndRootNameIsUsed() {
        AutoConfig config=new AutoConfig();
        config.names=List.of(new AutoConfig.Name("亚速",List.of("ALL"),List.of()));
        config.roots=List.of(new AutoConfig.Name("拉诺",List.of("ALL"),List.of()));
        Set<ChunkCell> cell=Set.of(new ChunkCell(0,0));
        var candidate=new TerrainAnalysis.Candidate(TerrainKind.OCEAN,cell,0.7f,256);
        RegionBook book=new RegionBook();
        for(String name:List.of("亚速洋","拉诺洋")) {
            String id=book.createRegion(2,name,1);book.paint(id,cell,true);cell=Set.of(new ChunkCell(cell.iterator().next().x()+2,0));
        }
        assertEquals("亚速洋",AutoNames.choose(candidate,book,config).orElseThrow());
    }

    @Test void oldSingleClimateRootConfigMigratesToMultiClimateFormatAndAddsNorthlandThreshold() throws Exception {
        java.nio.file.Files.writeString(directory.resolve("roots.json"),
                "[{\"text\":\"乞\",\"kinds\":[\"ALL\"],\"climate\":\"COLD\"}]");
        AutoConfig config=AutoConfig.load(directory);
        assertEquals(List.of("COLD"),config.roots.get(0).climates());
        assertEquals(new AutoConfig.Threshold(1500,0),config.thresholds.northland);
        config.saveAll(directory);
        String migrated=java.nio.file.Files.readString(directory.resolve("roots.json"));
        assertTrue(migrated.contains("\"climates\""));assertFalse(migrated.contains("\"climate\""));
        assertEquals(List.of("COLD"),AutoConfig.load(directory).roots.get(0).climates());
    }

    @Test void legacyCustomNamesAndRootsMigrateWithoutLosingEntries() throws Exception {
        java.nio.file.Files.writeString(directory.resolve("names.json"),
                "[{\"text\":\"自定义名\",\"kinds\":[\"ALL\"],\"climate\":\"ANY\"}]");
        java.nio.file.Files.writeString(directory.resolve("roots.json"),
                "[{\"text\":\"自定义根\",\"kinds\":[\"MOUNTAIN\"],\"climate\":\"COLD\"}]");

        AutoConfig migrated=AutoConfig.load(directory);

        assertEquals(List.of(new AutoConfig.Name("自定义名",List.of("ALL"),List.of())),migrated.names);
        assertEquals(List.of(new AutoConfig.Name("自定义根",List.of("MOUNTAIN"),List.of("COLD"))),migrated.roots);
        assertTrue(java.nio.file.Files.readString(directory.resolve("names.json")).contains("\"climates\""));
        assertTrue(java.nio.file.Files.readString(directory.resolve("roots.json")).contains("\"climates\""));
    }
}

