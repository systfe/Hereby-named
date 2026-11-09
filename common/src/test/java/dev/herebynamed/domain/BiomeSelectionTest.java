package dev.herebynamed.domain;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BiomeSelectionTest {
    @Test void incrementalFloodWaitsForRecordedDataInsteadOfTreatingItAsUnknown() {
        var loading = new java.util.concurrent.atomic.AtomicBoolean(true);
        BiomeSelection.Walker walker = new BiomeSelection.Walker(new ChunkCell(0,0), cell -> {
            if(cell.equals(new ChunkCell(0,0))) return new BiomeSelection.Sample("plains",false);
            if(cell.equals(new ChunkCell(1,0))) return new BiomeSelection.Sample(loading.get()?null:"plains",loading.get());
            return new BiomeSelection.Sample(null,false);
        },10);
        assertTrue(walker.step());
        assertFalse(walker.step());
        assertFalse(walker.complete());
        loading.set(false);
        while(!walker.complete()) assertTrue(walker.step());
        assertEquals(Set.of(new ChunkCell(0,0),new ChunkCell(1,0)),walker.result());
    }
    @Test void refusesOversizedFloodRatherThanSilentlyApplyingPartOfTheBiome() {
        assertThrows(IllegalArgumentException.class,() -> BiomeSelection.connected(new ChunkCell(0,0),c -> "plains",4));
    }
    @Test void floodStaysInRecordedEdgeConnectedBiomeAndDoesNotCrossUnknownOrDiagonalCells() {
        Map<ChunkCell, String> recorded = Map.of(
                new ChunkCell(-1, 0), "plains", new ChunkCell(0, 0), "plains",
                new ChunkCell(1, 0), "forest", new ChunkCell(1, 1), "plains",
                new ChunkCell(-3, 0), "plains");
        assertEquals(Set.of(new ChunkCell(-1, 0), new ChunkCell(0, 0)),
                BiomeSelection.connected(new ChunkCell(-1, 0), recorded::get, 100));
    }
}
