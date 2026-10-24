package dev.herebynamed.domain;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AutomaticOwnershipTest {
    @Test void automaticUpdatesProtectManualCellsAndManualEditsTakeOverTheWholeRegion() throws Exception {
        RegionBook book=new RegionBook();
        ChunkCell a=new ChunkCell(0,0),b=new ChunkCell(1,0),c=new ChunkCell(2,0);
        String manual=book.createRegion(1,"北塔",0x123456); book.paint(manual,Set.of(a),true);
        assertTrue(book.automate("FOREST", "银杉林",0x549b63,Set.of(a,b)));
        var automatic=book.owner(1,b).orElseThrow();
        assertNotNull(automatic.automatic()); assertEquals("北塔",book.owner(1,a).orElseThrow().name());
        book.renameRegion(automatic.id(),"银杉古林",automatic.color());
        assertNull(book.region(automatic.id()).orElseThrow().automatic());
        book.automate("FOREST","月影森林",0x549b63,Set.of(b,c));
        assertEquals("银杉古林",book.owner(1,b).orElseThrow().name());
        assertEquals(book.state(),BookCodec.decode(BookCodec.encode(book)).state());
    }
    @Test void deletedAutomaticFootprintStaysProtectedAcrossSaveUndoAndExplicitRelease() throws Exception {
        RegionBook book=new RegionBook(); ChunkCell a=new ChunkCell(0,0),b=new ChunkCell(1,0);
        book.automate("FOREST","银杉林",0x549b63,Set.of(a));
        book.deleteRegion(book.owner(1,a).orElseThrow().id());
        assertTrue(book.undo()); assertTrue(book.state().suppressed().isEmpty()); assertTrue(book.redo());
        book=BookCodec.decode(BookCodec.encode(book));
        book.automate("FOREST","月影森林",0x549b63,Set.of(a,b));
        assertTrue(book.owner(1,a).isEmpty()); assertTrue(book.owner(1,b).isPresent());
        book.allowAutomaticAgain();
        book.automate("FOREST","月影森林",0x549b63,Set.of(a,b));
        assertTrue(book.owner(1,a).isPresent());
    }
    @Test void repeatedAnalysisIsIdempotentAndMergingKeepsOldestNameAndAliases() {
        RegionBook book=new RegionBook();ChunkCell a=new ChunkCell(0,0),b=new ChunkCell(1,0),c=new ChunkCell(2,0);
        book.automate("FOREST","银杉林",0x549b63,Set.of(a));
        book.automate("FOREST","月影森林",0x549b63,Set.of(c));
        assertFalse(book.automate("FOREST","忽略的名字",0x549b63,Set.of(a)));
        book.automate("FOREST","忽略的名字",0x549b63,Set.of(a,b,c));
        assertEquals(1,book.regions().size());assertEquals("银杉林",book.regions().get(0).name());
        assertEquals(List.of("月影森林"),book.regions().get(0).automatic().formerNames());
    }
    @Test void legacyBookRemainsManualAfterUpgrade() throws Exception {
        java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
        try(var out=new java.io.DataOutputStream(new java.util.zip.GZIPOutputStream(bytes))) {
            out.writeInt(0x48424e31);out.writeInt(2);out.writeInt(2);out.writeInt(1);out.writeInt(2);out.writeInt(1);
            out.writeUTF("old");out.writeInt(1);out.writeUTF("北塔");out.writeInt(0x123456);out.writeInt(1);out.writeInt(0);out.writeInt(0);
        }
        RegionBook book=BookCodec.decode(bytes.toByteArray());
        assertNull(book.regions().get(0).automatic());
        assertFalse(book.automate("FOREST","银杉林",0x549b63,Set.of(new ChunkCell(0,0))));
        assertEquals("北塔",book.regions().get(0).name());
    }
}
