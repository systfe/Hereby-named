package dev.herebynamed.domain;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class RegionBookTest {
    @Test void mapNamesIgnoreInvalidHoverCoordinatesAndRecoverForRealBlocks() {
        RegionBook book=new RegionBook();
        String low=book.createRegion(1,"北高",1), high=book.createRegion(2,"北塔",2);
        for(String id:java.util.List.of(low,high)) book.paint(id,Set.of(new ChunkCell(-1,4)),true);
        assertEquals(java.util.List.of(),book.namesAtBlock(Integer.MAX_VALUE,Integer.MAX_VALUE));
        assertEquals(java.util.List.of(),book.namesAtBlock(Integer.MIN_VALUE,64));
        assertEquals(java.util.List.of(),book.namesAtBlock(-1,Integer.MAX_VALUE));
        assertEquals(java.util.List.of("北高","北塔"),book.namesAtBlock(-1,64));
        assertEquals(java.util.List.of("北高","北塔"),book.namesAtBlock(-16,79));
        assertEquals(java.util.List.of(),book.namesAtBlock(-17,64));
    }
    @Test void biomeSelectionCanPreserveOtherRegionsAndUndoRemainsTransactional() {
        RegionBook book=new RegionBook();
        String target=book.createRegion(1,"高原",1), other=book.createRegion(1,"哨塔",2);
        String upper=book.createRegion(2,"城市",3);
        ChunkCell occupied=new ChunkCell(0,0), free=new ChunkCell(1,0), own=new ChunkCell(2,0);
        book.paint(other,Set.of(occupied),true); book.paint(upper,Set.of(free),true); book.paint(target,Set.of(own),true);
        Set<ChunkCell> selection=Set.of(occupied,free,own);
        assertEquals(Set.of(occupied),book.planPaint(target,selection).conflicts());
        assertEquals(Set.of(free,own),book.planPaint(target,selection).uncontested());
        book.paintPreserving(target,selection);
        assertEquals(other,book.owner(1,occupied).orElseThrow().id());
        assertEquals(target,book.owner(1,free).orElseThrow().id());
        assertEquals(upper,book.owner(2,free).orElseThrow().id());
        assertTrue(book.undo());
        assertTrue(book.owner(1,free).isEmpty());
        assertEquals(target,book.owner(1,own).orElseThrow().id());
    }
    @Test void preservingRequestRoundTripsAndRechecksOwnershipWhenApplied() throws Exception {
        RegionBook book=new RegionBook();
        String target=book.createRegion(1,"高原",1), other=book.createRegion(1,"塔",2);
        ChunkCell cell=new ChunkCell(-4,3);
        assertTrue(book.planPaint(target,Set.of(cell)).conflicts().isEmpty());
        var request=new RegionEdit("minecraft:overworld",5,17,RegionEdit.Action.ADD_PRESERVING,1,target,"",0,Set.of(cell));
        book.paint(other,Set.of(cell),true);
        var decoded=RegionEdit.decode(request.encode());
        assertEquals(request,decoded); decoded.apply(book);
        assertEquals(other,book.owner(1,cell).orElseThrow().id());
        new RegionEdit("minecraft:overworld",5,RegionEdit.Action.ADD,1,target,"",0,Set.of(cell)).apply(book);
        assertEquals(target,book.owner(1,cell).orElseThrow().id());
    }
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
