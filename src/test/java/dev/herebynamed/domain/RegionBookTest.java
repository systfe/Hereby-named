package dev.herebynamed.domain;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class RegionBookTest {
    @Test void deletedLayersKeepTheirNumberReservedEvenAfterUndo() {
        RegionBook book = new RegionBook();
        assertEquals(3, book.addLayer());
        String tower = book.createRegion(3, "北塔", 0xff0000);
        book.deleteLayer(3);
        assertTrue(book.region(tower).isEmpty());
        assertTrue(book.undo());
        assertTrue(book.region(tower).isPresent());
        book.deleteLayer(3);
        assertEquals(4, book.addLayer());
    }

    @Test void namesUseOnlyExtremeLayersAndErasingAnotherRegionDoesNothing() {
        RegionBook book = new RegionBook();
        String a = book.createRegion(1, "北高", 1);
        String b = book.createRegion(2, "北城", 2);
        int layer = book.addLayer();
        String c = book.createRegion(layer, "北塔", 3);
        String empty = book.createRegion(1, "空地", 4);
        ChunkCell cell = new ChunkCell(0, 0);
        for (String id : java.util.List.of(a,b,c)) book.paint(id, Set.of(cell), true);
        book.paint(empty, Set.of(cell), false);
        assertEquals(java.util.List.of("北高", "北塔"), book.namesAt(cell));
        assertEquals(3, book.visibleAt(cell, null).orElseThrow().color());
        assertEquals("北城", book.visibleAt(cell, 2).orElseThrow().name());
    }

    @Test void sameLayerTransfersOwnershipAndUndoRestoresItWithoutChangingOtherLayers() {
        RegionBook book = new RegionBook();
        String plateau = book.createRegion(1, "北之高原", 0x55aa55);
        String tower = book.createRegion(1, "北塔", 0xaa5555);
        String town = book.createRegion(2, "北城", 0x5555aa);
        ChunkCell cell = new ChunkCell(-1, 4);
        book.paint(plateau, Set.of(cell), true);
        book.paint(town, Set.of(cell), true);
        book.paint(tower, Set.of(cell), true);
        assertEquals(tower, book.owner(1, cell).orElseThrow().id());
        assertEquals(town, book.owner(2, cell).orElseThrow().id());
        assertTrue(book.undo());
        assertEquals(plateau, book.owner(1, cell).orElseThrow().id());
        assertEquals(town, book.owner(2, cell).orElseThrow().id());
    }
}
