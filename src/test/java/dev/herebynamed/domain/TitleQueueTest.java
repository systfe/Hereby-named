package dev.herebynamed.domain;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TitleQueueTest {
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
}
