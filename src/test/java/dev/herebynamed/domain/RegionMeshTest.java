package dev.herebynamed.domain;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RegionMeshTest {
    @Test void viewportContainsOnlyNearbyGeometryDespiteManyRemoteFragments() {
        Set<ChunkCell> cells=new HashSet<>();
        for(int x=0;x<128;x++) for(int z=0;z<128;z++) cells.add(new ChunkCell(x*4,z*4));
        var mesh=RegionMesh.build(List.of(new RegionBook.Region("islands",1,"群岛",1,cells)),null);
        var visible=mesh.inView(-0.25,-0.25,1.25,1.25);
        assertEquals(1,visible.fills().size()); assertEquals(4,visible.edges().size());
        assertTrue(mesh.inView(-50,-50,-40,-40).fills().isEmpty());
    }
    @Test void largeRectangleHasFourOuterEdgesAndOneContinuousFill() {
        Set<ChunkCell> cells=new HashSet<>();
        for(int x=0;x<256;x++) for(int z=0;z<256;z++) cells.add(new ChunkCell(x,z));
        var region=new RegionBook.Region("plateau",1,"北之高原",0x55aa55,cells);
        var mesh=RegionMesh.build(List.of(region),null);
        assertEquals(4,mesh.edges().size());
        assertEquals(1,mesh.fills().size());
        assertEquals(65_536,mesh.fills().stream().mapToInt(f -> (f.x2()-f.x1())*(f.z2()-f.z1())).sum());
        assertEquals(128,mesh.labels().get(0).x(),0.51);
        assertEquals(128,mesh.labels().get(0).z(),0.51);
    }
    @Test void holesStayEmptyAndLabelAnchorBelongsToTheRegion() {
        Set<ChunkCell> ring=new HashSet<>();
        for(int x=-1;x<=1;x++) for(int z=-1;z<=1;z++) if(x!=0 || z!=0) ring.add(new ChunkCell(x,z));
        var mesh=RegionMesh.build(List.of(new RegionBook.Region("ring",1,"环城",1,ring)),null);
        assertEquals(8,mesh.edges().size());
        assertEquals(8,mesh.fills().stream().mapToInt(f -> (f.x2()-f.x1())*(f.z2()-f.z1())).sum());
        assertTrue(mesh.fills().stream().noneMatch(f -> f.x1()<=0 && f.x2()>0 && f.z1()<=0 && f.z2()>0));
        var label=mesh.labels().get(0);
        assertTrue(ring.contains(new ChunkCell((int)Math.floor(label.x()),(int)Math.floor(label.z()))));
    }
    @Test void adjacentRegionsKeepTheirBorderEvenWithTheSameColorAndLayersRemainFiltered() {
        var left=new RegionBook.Region("left",1,"西区",1,Set.of(new ChunkCell(0,0),new ChunkCell(0,1)));
        var right=new RegionBook.Region("right",1,"东区",1,Set.of(new ChunkCell(1,0),new ChunkCell(1,1)));
        var upper=new RegionBook.Region("upper",2,"城",2,Set.of(new ChunkCell(0,0),new ChunkCell(0,1),new ChunkCell(1,0),new ChunkCell(1,1)));
        var mesh=RegionMesh.build(List.of(left,right),null);
        assertEquals(8,mesh.edges().size()); assertEquals(2,mesh.labels().size());
        assertTrue(mesh.edges().stream().anyMatch(e -> e.x1()==1 && e.x2()==1 && e.z1()==0 && e.z2()==2));
        var all=RegionMesh.build(List.of(left,right,upper),null);
        assertEquals(1,all.fills().size()); assertEquals(4,all.edges().size());
        assertEquals(List.of("城"),all.labels().stream().map(RegionMesh.Label::name).toList());
        var filtered=RegionMesh.build(List.of(left,right,upper),1);
        assertEquals(2,filtered.labels().size());
    }
    @Test void titleGrowsWithRegionAreaAndZoomAndRemainsBoundedByViewport() {
        var small=RegionMesh.build(List.of(new RegionBook.Region("s",1,"北区",1,Set.of(new ChunkCell(0,0)))),null).labels().get(0);
        Set<ChunkCell> cells=new HashSet<>();
        for(int x=0;x<8;x++) for(int z=0;z<8;z++) cells.add(new ChunkCell(x,z));
        var large=RegionMesh.build(List.of(new RegionBook.Region("l",1,"北区",1,cells)),null).labels().get(0);
        assertTrue(large.textScale(30,4,400)>small.textScale(30,4,400));
        assertTrue(large.textScale(30,8,400)>large.textScale(30,4,400));
        assertTrue(large.textScale(30,800,100)*30<=80);
    }
}
