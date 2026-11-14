package dev.herebynamed.domain;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TitleQueueTest {
    @Test void movingInsideNewlyCreatedAreaDoesNotCountAsEnteringIt() {
        RegionBook before=new RegionBook(),after=new RegionBook();
        ChunkCell previous=new ChunkCell(0,0),current=new ChunkCell(1,0),outside=new ChunkCell(-1,0);
        after.automate("FOREST:TEMPERATE","银杉林",0x888888,java.util.Set.of(previous,current));
        assertTrue(TitleQueue.automaticCreationAround(before,after,previous,current));
        assertFalse(TitleQueue.automaticCreationAround(before,after,outside,current));
        assertFalse(TitleQueue.automaticCreationAround(after,after,previous,current));
    }
    @Test void automaticCreationWhileStandingStillWaitsForARealReentry() {
        TitleQueue queue=new TitleQueue();queue.observeWithoutTitle("overworld",List.of("银杉林"));
        assertTrue(queue.update("overworld",List.of("银杉林"),5000,false).isEmpty());
        queue.update("overworld",List.of(),6000,false);queue.update("overworld",List.of("银杉林"),7000,false);
        assertEquals(List.of("银杉林"),queue.update("overworld",List.of("银杉林"),8000,false).orElseThrow());
    }
    @Test void waitsForOriginalTitlesAndOnlyShowsLatestStablePlace() {
        TitleQueue queue = new TitleQueue();
        assertTrue(queue.update("overworld", List.of("北高"), 0, true).isEmpty());
        assertTrue(queue.update("overworld", List.of("北高"), 1500, true).isEmpty());
        assertTrue(queue.update("overworld", List.of("北高", "北塔"), 1600, true).isEmpty());
        assertTrue(queue.update("overworld", List.of("北高", "北塔"), 2000, false).isEmpty());
        assertEquals(List.of("北高", "北塔"), queue.update("overworld", List.of("北高", "北塔"), 2600, false).orElseThrow());
        assertTrue(queue.update("overworld", List.of("北高", "北塔"), 4000, false).isEmpty());
        assertTrue(queue.update("overworld", List.of(), 5000, false).isEmpty());
        assertTrue(queue.update("nether", List.of("北高", "北塔"), 6000, false).isEmpty());
        assertEquals(List.of("北高", "北塔"), queue.update("nether", List.of("北高", "北塔"), 7000, false).orElseThrow());
    }
    @Test void comingBackToTheSamePlaceWaitsTheRepeatCooldown() {
        TitleQueue queue = new TitleQueue();
        queue.update("overworld", List.of("甲"), 0, false, 15000, 5000);
        assertEquals(List.of("甲"), queue.update("overworld", List.of("甲"), 1000, false, 15000, 5000).orElseThrow());
        queue.update("overworld", List.of(), 5000, false, 15000, 5000);
        assertTrue(queue.update("overworld", List.of("甲"), 8000, false, 15000, 5000).isEmpty());
        queue.update("overworld", List.of(), 14000, false, 15000, 5000);
        assertTrue(queue.update("overworld", List.of("甲"), 15000, false, 15000, 5000).isEmpty());
        assertEquals(List.of("甲"), queue.update("overworld", List.of("甲"), 17000, false, 15000, 5000).orElseThrow());
    }
    @Test void movingOnToAnotherPlaceOnlyWaitsTheSwitchCooldown() {
        TitleQueue queue = new TitleQueue();
        queue.update("overworld", List.of("甲"), 0, false, 15000, 5000);
        assertEquals(List.of("甲"), queue.update("overworld", List.of("甲"), 1000, false, 15000, 5000).orElseThrow());
        queue.update("overworld", List.of(), 2000, false, 15000, 5000);
        assertTrue(queue.update("overworld", List.of("乙"), 4000, false, 15000, 5000).isEmpty());
        assertTrue(queue.update("overworld", List.of("乙"), 5000, false, 15000, 5000).isEmpty());
        queue.update("overworld", List.of(), 6000, false, 15000, 5000);
        queue.update("overworld", List.of("乙"), 7000, false, 15000, 5000);
        assertEquals(List.of("乙"), queue.update("overworld", List.of("乙"), 8000, false, 15000, 5000).orElseThrow());
    }
    @Test void changingDimensionShowsTheTitleEvenRightAfterAnotherOne() {
        TitleQueue queue = new TitleQueue();
        queue.update("overworld", List.of("北高"), 0, false, 15000, 5000);
        assertEquals(List.of("北高"), queue.update("overworld", List.of("北高"), 1000, false, 15000, 5000).orElseThrow());
        queue.update("overworld", List.of(), 1100, false, 15000, 5000);
        assertTrue(queue.update("nether", List.of("北高"), 1200, false, 15000, 5000).isEmpty());
        assertEquals(List.of("北高"), queue.update("nether", List.of("北高"), 2200, false, 15000, 5000).orElseThrow());
    }
}
