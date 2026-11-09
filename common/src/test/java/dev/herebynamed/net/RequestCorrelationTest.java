package dev.herebynamed.net;

import dev.herebynamed.domain.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RequestCorrelationTest {
    @Test void acknowledgementSurvivesEditAndSnapshotEncoding() throws Exception {
        RegionEdit request = new RegionEdit("minecraft:overworld", 9, 1234, RegionEdit.Action.ACQUIRE,
                0, "", "", 0, Set.of());
        RegionEdit decoded = RegionEdit.decode(request.encode());
        assertEquals(request, decoded);
        UUID holder = UUID.randomUUID();
        Snapshot response = Snapshot.decode(new Snapshot(decoded.dimension(), 9, decoded.requestId(), holder,
                true, "", new RegionBook()).encode());
        PendingRequest pending = new PendingRequest();
        long acquisition = pending.begin(response.dimension(), true);
        // A response for another request cannot finish the active acquisition.
        assertTrue(pending.complete(response.responseTo(), response.dimension()).isEmpty());
        assertEquals(1234, response.responseTo());
        assertEquals(holder, response.holder());
        assertTrue(pending.complete(acquisition, response.dimension()).isPresent());
    }
}
