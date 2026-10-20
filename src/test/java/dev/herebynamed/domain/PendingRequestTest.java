package dev.herebynamed.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PendingRequestTest {
    @Test void readSnapshotCannotCompleteAnAcquisition() {
        PendingRequest pending = new PendingRequest();
        long read = pending.nextId();
        long acquire = pending.begin("overworld", true);
        assertTrue(pending.complete(read, "overworld").isEmpty());
        assertTrue(pending.waiting());
        assertTrue(pending.complete(acquire, "nether").isEmpty());
        assertTrue(pending.complete(acquire, "overworld").orElseThrow().acquisition());
        assertFalse(pending.waiting());
    }
    @Test void timeoutReturnsLockToReleaseAndIgnoresLateSuccess() {
        PendingRequest pending = new PendingRequest();
        long old = pending.begin("overworld", true);
        var canceled = pending.cancel().orElseThrow();
        assertTrue(canceled.acquisition());
        assertEquals("overworld", canceled.dimension());
        long retry = pending.begin("overworld", true);
        assertTrue(pending.complete(old, "overworld").isEmpty());
        assertTrue(pending.waiting());
        assertEquals(retry, pending.complete(retry, "overworld").orElseThrow().id());
    }
}
