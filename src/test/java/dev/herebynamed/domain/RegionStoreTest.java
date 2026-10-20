package dev.herebynamed.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RegionStoreTest {
    @TempDir Path directory;
    @Test void savesNamesColorsCellsAndLayerCounterWithoutMixingDimensions() throws Exception {
        RegionStore store = new RegionStore(directory);
        RegionBook book = new RegionBook();
        String id = book.createRegion(2, "北之高原", 0x123456);
        book.paint(id, Set.of(new ChunkCell(-42, 87)), true);
        book.addLayer(); book.deleteLayer(3);
        store.save("minecraft:overworld", book);
        RegionBook restored = store.load("minecraft:overworld");
        assertEquals(book.state(), restored.state());
        assertEquals(4, restored.addLayer());
        assertTrue(store.load("minecraft:the_nether").regions().isEmpty());
        assertFalse(restored.undo() && restored.undo());
    }
    @Test void corruptFileIsReportedInsteadOfSilentlyReplacingSavedRegions() throws Exception {
        RegionStore store = new RegionStore(directory);
        store.save("minecraft:overworld", new RegionBook());
        Path data;
        try (var files = Files.list(directory)) { data = files.filter(p -> p.toString().endsWith(".dat")).findFirst().orElseThrow(); }
        Files.write(data, new byte[]{1, 2, 3});
        assertThrows(java.io.IOException.class, () -> store.load("minecraft:overworld"));
    }
}
