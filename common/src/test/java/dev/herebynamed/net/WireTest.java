package dev.herebynamed.net;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WireTest {
    @Test void largePayloadUsesBoundedFragmentsAndReassemblesExactly() {
        byte[] payload = new byte[600_000]; new Random(42).nextBytes(payload);
        List<Wire.Fragment> fragments = new ArrayList<>();
        Wire.send(payload, packet -> {
            try { assertTrue(packet.readableBytes() < 32767); fragments.add(Wire.Fragment.read(packet)); }
            finally { packet.release(); }
        });
        Wire.Assembler assembler = new Wire.Assembler();
        Optional<byte[]> result = Optional.empty();
        for(Wire.Fragment fragment:fragments) result=assembler.accept(fragment);
        assertArrayEquals(payload,result.orElseThrow());
    }
    @Test void rejectsMissingOrOutOfOrderFragments() {
        Wire.Assembler assembler = new Wire.Assembler();
        assertThrows(IllegalArgumentException.class,() -> assembler.accept(new Wire.Fragment(1,1,2,new byte[]{1})));
        assertTrue(assembler.accept(new Wire.Fragment(2,0,3,new byte[]{1})).isEmpty());
        assertThrows(IllegalArgumentException.class,() -> assembler.accept(new Wire.Fragment(2,2,3,new byte[]{1})));
    }
}
