package dev.herebynamed.domain;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AutomaticOwnershipTest {
    @Test void growingMountainCanBeRenamedOnlyOnceEvenAfterSaving() throws Exception {
        RegionBook book=new RegionBook();
        Set<ChunkCell> peak=rectangle(30,30), ridge=rectangle(100,30), range=rectangle(200,30);
        book.automate("MOUNTAIN:TEMPERATE","赤霞峰",1,peak);
        var config=new dev.herebynamed.auto.AutoConfig();
        var candidate=new dev.herebynamed.auto.TerrainAnalysis.Candidate(dev.herebynamed.auto.TerrainKind.MOUNTAIN,ridge,0.7f,1600);
        String name=dev.herebynamed.auto.AutoNames.choose(candidate,book,config).orElseThrow();
        assertEquals("赤霞岭",name);
        book.automate("MOUNTAIN:TEMPERATE",name,1,ridge);
        assertEquals("赤霞岭",book.regions().get(0).name());
        assertTrue(book.regions().get(0).automatic().formerNames().contains("赤霞峰"));
        book=BookCodec.decode(BookCodec.encode(book));
        var expanded=new dev.herebynamed.auto.TerrainAnalysis.Candidate(dev.herebynamed.auto.TerrainKind.MOUNTAIN,range,0.7f,3200);
        assertEquals("赤霞岭",dev.herebynamed.auto.AutoNames.choose(expanded,book,config).orElseThrow());
        book.automate("MOUNTAIN:COLD","赤霞山脉",1,range);
        assertEquals("赤霞岭",book.regions().get(0).name());
        book.renameRegion(book.regions().get(0).id(),"自定山名",2);
        book.automate("MOUNTAIN:TEMPERATE","别的山脉",1,range);
        assertEquals("自定山名",book.regions().get(0).name());
    }
    private static Set<ChunkCell> rectangle(int width,int height) {
        Set<ChunkCell> cells=new HashSet<>();
        for(int x=0;x<width;x++) for(int z=0;z<height;z++) cells.add(new ChunkCell(x,z));
        return cells;
    }
    @Test void hbn2AutomaticMountainLoadsWithItsNameAndCanStillUpgradeOnce() throws Exception {
        java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
        try(var out=new java.io.DataOutputStream(new java.util.zip.GZIPOutputStream(bytes))) {
            out.writeInt(0x48424e32);out.writeInt(2);out.writeInt(2);out.writeInt(1);out.writeInt(2);out.writeInt(1);
            out.writeUTF("old");out.writeInt(1);out.writeUTF("赤霞峰");out.writeInt(1);out.writeBoolean(true);
            out.writeUTF("MOUNTAIN:TEMPERATE");out.writeLong(1);out.writeInt(0);
            out.writeInt(1);out.writeInt(0);out.writeInt(0);out.writeInt(0);
        }
        RegionBook book=BookCodec.decode(bytes.toByteArray());
        assertEquals("赤霞峰",book.regions().get(0).name());
        assertFalse(book.regions().get(0).automatic().mountainRenamed());
        book.automate("MOUNTAIN:TEMPERATE","赤霞山脉",1,rectangle(200,30));
        assertTrue(book.regions().get(0).automatic().mountainRenamed());
        assertEquals(book.state(),BookCodec.decode(BookCodec.encode(book)).state());
    }
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
