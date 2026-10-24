package dev.herebynamed.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import dev.herebynamed.auto.AutoWork;
import static org.junit.jupiter.api.Assertions.*;

class RegionStoreTest {
    @TempDir Path directory;
    @Test void closedSessionCannotOverwriteDataAfterWorldReopens() throws Exception {
        CountDownLatch started=new CountDownLatch(1),release=new CountDownLatch(1);
        var blocker=AutoWork.submit(() -> { started.countDown();release.await();return true; });
        assertTrue(started.await(5,TimeUnit.SECONDS));
        AutoWork.Session session=new AutoWork.Session();
        RegionStore store=new RegionStore(directory);
        var stale=session.submit(() -> { store.save("minecraft:overworld",new RegionBook());return true; });
        session.close();
        RegionBook reopened=new RegionBook();reopened.createRegion(1,"新区域",0x888888);
        store.save("minecraft:overworld",reopened);
        release.countDown();blocker.get(5,TimeUnit.SECONDS);
        assertThrows(Exception.class,() -> stale.get(5,TimeUnit.SECONDS));
        assertEquals(reopened.state(),store.load("minecraft:overworld").state());
    }
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
    @Test void longCustomDimensionFitsWindowsFileNameLimit() throws Exception {
        RegionStore store = new RegionStore(directory);
        String dimension = "custom:" + "a".repeat(240);
        RegionBook book = new RegionBook();
        book.createRegion(1, "高原", 0x123456);
        store.save(dimension, book);
        store.save(dimension, book); // Backup and temporary names must also fit.
        assertEquals(book.state(), store.load(dimension).state());
        assertTrue(store.load(dimension + "b").regions().isEmpty());
        try (var files = Files.list(directory)) {
            assertTrue(files.allMatch(file -> file.getFileName().toString().length() <= 255));
        }
    }
}
