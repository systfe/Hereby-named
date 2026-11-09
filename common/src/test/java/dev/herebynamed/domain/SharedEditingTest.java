package dev.herebynamed.domain;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SharedEditingTest {
    @Test void automaticUpdatesRequireAuthorizationAndYieldToAnyManualEditor() {
        EditAuthority authority=new EditAuthority();java.util.UUID player=java.util.UUID.randomUUID();
        assertFalse(authority.canAutomate("minecraft:overworld",false));
        assertFalse(authority.canAutomate("minecraft:the_nether",true));
        assertTrue(authority.canAutomate("minecraft:overworld",true));
        authority.acquire("minecraft:overworld",player,true);
        assertFalse(authority.canAutomate("minecraft:overworld",true));
        authority.release("minecraft:overworld",player);
        assertTrue(authority.canAutomate("minecraft:overworld",true));
    }
    @Test void onlyAuthorizedPlayerCanHoldDimensionAndDisconnectReleasesAllTheirLocks() {
        EditAuthority authority = new EditAuthority();
        UUID alice = UUID.randomUUID(), bob = UUID.randomUUID();
        assertFalse(authority.acquire("overworld", alice, false));
        assertTrue(authority.acquire("overworld", alice, true));
        assertFalse(authority.acquire("overworld", bob, true));
        assertTrue(authority.acquire("nether", bob, true));
        assertFalse(authority.canEdit("overworld", alice, false));
        authority.releaseAll(alice);
        assertTrue(authority.acquire("overworld", bob, true));
        assertTrue(authority.canEdit("nether", bob, true));
    }
}
