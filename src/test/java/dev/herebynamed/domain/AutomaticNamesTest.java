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
            assertTrue(AutoNames.choose(candidate,new RegionBook(),config).orElseThrow().matches("瑟兰(之森|之林|森|林)"));
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
        draft.thresholds.forest=new AutoConfig.Threshold(7,1800,0);
        draft.names=new ArrayList<>(List.of(new AutoConfig.Name("风信洋",List.of("OCEAN"),"ANY")));
        draft.roots=new ArrayList<>(List.of(new AutoConfig.Name("瑟兰",List.of("FOREST"),"TEMPERATE")));
        draft.saveAll(directory);
        assertEquals(5,original.thresholds.forest.minimumBiomes());
        AutoConfig loaded=AutoConfig.load(directory);
        assertEquals(draft.thresholds.forest,loaded.thresholds.forest);
        assertEquals(draft.names,loaded.names);assertEquals(draft.roots,loaded.roots);
        draft.names.add(new AutoConfig.Name("无效2",List.of("ALL"),"ANY"));
        assertThrows(IllegalArgumentException.class,() -> draft.saveAll(directory));
        assertEquals(loaded.names,AutoConfig.load(directory).names);
    }
    @Test void existingPartialConfigurationGainsEditableDefaultsWithoutLosingCustomValues() throws Exception {
        java.nio.file.Files.writeString(directory.resolve("automatic.json"),
                "{\"enabled\":false,\"unlockSpan\":3000,\"thresholds\":{\"forest\":{\"minimumBiomes\":7}}}");
        AutoConfig config=AutoConfig.load(directory);
        assertFalse(config.enabled);assertEquals(3000,config.unlockSpan);
        assertEquals(new AutoConfig.Threshold(7,1000,0),config.thresholds.forest);
        assertEquals(5,config.thresholds.plain.minimumBiomes());
        String saved=java.nio.file.Files.readString(directory.resolve("automatic.json"));
        assertTrue(saved.contains("\"frozenOcean\""));
        assertTrue(saved.contains("\"maximumSpan\""));
        config.thresholds.lake=new AutoConfig.Threshold(0,2000,500);
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
    @Test void whitelistFiltersClimateAndNeverAddsNumbersWhenNoHonestAlternativeExists() {
        AutoConfig config=new AutoConfig();
        config.names=List.of(new AutoConfig.Name("银霜山",List.of("MOUNTAIN"),"COLD"),
                new AutoConfig.Name("炎阳山",List.of("MOUNTAIN"),"HOT"));
        config.roots=List.of();
        Set<ChunkCell> cells=Set.of(new ChunkCell(0,0));
        var candidate=new TerrainAnalysis.Candidate(TerrainKind.MOUNTAIN,cells,0,1024);
        RegionBook book=new RegionBook();
        assertEquals("银霜山",AutoNames.choose(candidate,book,config).orElseThrow());
        String id=book.createRegion(2,"银霜山",0x888888);book.paint(id,cells,true);
        assertTrue(AutoNames.choose(candidate,book,config).isEmpty());
    }
    @Test void shippedWhitelistsLoadAndConfigurationRoundTripsWithoutOverwritingNames() throws Exception {
        AutoConfig config=AutoConfig.load(directory);assertFalse(config.names.isEmpty());assertFalse(config.roots.isEmpty());
        var names=config.names;
        config.enabled=false;config.save(directory);
        AutoConfig loaded=AutoConfig.load(directory);
        assertFalse(loaded.enabled);assertEquals(names,loaded.names);
        loaded.names=List.of(new AutoConfig.Name("银霜山2",List.of("MOUNTAIN"),"COLD"));
        assertThrows(IllegalArgumentException.class,loaded::validate);
    }
}
