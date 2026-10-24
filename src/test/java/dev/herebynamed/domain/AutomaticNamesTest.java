package dev.herebynamed.domain;

import dev.herebynamed.auto.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AutomaticNamesTest {
    @TempDir Path directory;
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
