package dev.herebynamed.domain;

import dev.herebynamed.claim.ClaimManager;
import dev.herebynamed.claim.ClaimPermissionEvaluator;
import dev.herebynamed.claim.model.Claim;
import dev.herebynamed.claim.model.ClaimFlag;
import dev.herebynamed.claim.model.ClaimRole;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ClaimTest {
    @Test void claimRolesHaveCorrectHierarchy() {
        assertTrue(ClaimRole.OWNER.isAtLeast(ClaimRole.ADMIN));
        assertTrue(ClaimRole.ADMIN.isAtLeast(ClaimRole.MEMBER));
        assertTrue(ClaimRole.MEMBER.isAtLeast(ClaimRole.VISITOR));
        assertFalse(ClaimRole.VISITOR.isAtLeast(ClaimRole.MEMBER));
        assertFalse(ClaimRole.MEMBER.isAtLeast(ClaimRole.ADMIN));
    }

    @Test void claimFlagsParseCorrectly() {
        assertEquals(ClaimFlag.BREAK, ClaimFlag.parseFlag("break"));
        assertEquals(ClaimFlag.PLACE, ClaimFlag.parseFlag("place"));
        assertEquals(ClaimFlag.INTERACT, ClaimFlag.parseFlag("interact"));
        assertEquals(ClaimFlag.CONTAINER, ClaimFlag.parseFlag("container"));
        assertEquals(ClaimFlag.PVP, ClaimFlag.parseFlag("pvp"));
        assertEquals(ClaimFlag.ANIMAL_PROTECT, ClaimFlag.parseFlag("animal"));
        assertEquals(ClaimFlag.EXPLOSION_PROOF, ClaimFlag.parseFlag("explosion"));
        assertEquals(ClaimFlag.FIRE_SPREAD, ClaimFlag.parseFlag("fire"));
        assertEquals(ClaimFlag.ENTER, ClaimFlag.parseFlag("enter"));
        assertEquals(0, ClaimFlag.parseFlag("unknown_flag"));
    }

    @Test void claimManagerSpatialIndexingAndEviction() {
        ClaimManager mgr = new ClaimManager();
        UUID owner = UUID.randomUUID();
        ChunkCell c1 = new ChunkCell(10, 20);
        ChunkCell c2 = new ChunkCell(11, 20);
        Claim claim = Claim.create(owner, "领地A", 0x112233, Set.of(c1, c2));

        String dim = "minecraft:overworld";
        assertTrue(mgr.registerClaim(dim, claim));
        assertEquals(claim, mgr.getClaimAt(dim, c1));
        assertEquals(claim, mgr.getClaimAt(dim, c2));
        assertNull(mgr.getClaimAt(dim, new ChunkCell(12, 20)));
        assertNull(mgr.getClaimAt("minecraft:the_nether", c1));

        // Attempting to register another claim on occupied chunk fails
        Claim conflict = Claim.create(UUID.randomUUID(), "领地B", 0x445566, Set.of(c1));
        assertFalse(mgr.registerClaim(dim, conflict));

        // Eviction clears spatial index
        mgr.evictClaim(claim, dim);
        assertNull(mgr.getClaimAt(dim, c1));
        assertNull(mgr.getClaimAt(dim, c2));
        assertEquals(0, mgr.allClaims(dim).size());
    }

    @Test void claimPermissionEvaluation() {
        ClaimManager mgr = new ClaimManager();
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();

        ChunkCell cell = new ChunkCell(5, 5);
        Claim claim = Claim.create(owner, "圣域", 0x00ff00, Set.of(cell))
                .withMember(member, ClaimRole.MEMBER)
                .withVisitorFlags(ClaimFlag.ENTER) // Visitors can only enter
                .withMemberFlags(ClaimFlag.BREAK | ClaimFlag.PLACE | ClaimFlag.CONTAINER | ClaimFlag.ENTER)
                .withEnvironmentFlags(ClaimFlag.EXPLOSION_PROOF | ClaimFlag.FIRE_SPREAD);

        String dim = "minecraft:overworld";
        mgr.registerClaim(dim, claim);

        // Environment protection tests
        assertTrue((claim.environmentFlags() & ClaimFlag.EXPLOSION_PROOF) != 0);
        assertTrue((claim.environmentFlags() & ClaimFlag.FIRE_SPREAD) != 0);

        // Member rights
        assertTrue((claim.memberFlags() & ClaimFlag.BREAK) != 0);
        assertTrue((claim.memberFlags() & ClaimFlag.PLACE) != 0);
        assertTrue((claim.memberFlags() & ClaimFlag.CONTAINER) != 0);
        assertFalse((claim.memberFlags() & ClaimFlag.PVP) != 0);

        // Visitor rights
        assertTrue((claim.visitorFlags() & ClaimFlag.ENTER) != 0);
        assertFalse((claim.visitorFlags() & ClaimFlag.BREAK) != 0);
        assertFalse((claim.visitorFlags() & ClaimFlag.CONTAINER) != 0);

        // Disabling ENTER flag on claim blocks strangers
        Claim privateClaim = claim.withVisitorFlags(0);
        assertFalse((privateClaim.visitorFlags() & ClaimFlag.ENTER) != 0);
    }

    @Test void claimStoreSerializationAndDeserialization() throws Exception {
        java.nio.file.Path tempDir = java.nio.file.Files.createTempDirectory("claims_test");
        try {
            dev.herebynamed.claim.ClaimStore store = new dev.herebynamed.claim.ClaimStore(tempDir);
            ClaimManager mgr = new ClaimManager();
            UUID owner = UUID.randomUUID();
            UUID member = UUID.randomUUID();
            ChunkCell cell = new ChunkCell(42, -99);
            Claim original = Claim.create(owner, "帝国领地", 0x334455, Set.of(cell))
                    .withMember(member, ClaimRole.ADMIN)
                    .withForceLoaded(true);

            String dim = "minecraft:overworld";
            mgr.registerClaim(dim, original);

            store.save(mgr, Set.of(dim));

            // Reload into empty manager
            ClaimManager reloadedMgr = new ClaimManager();
            store.load(reloadedMgr);

            Claim reloaded = reloadedMgr.getClaimAt(dim, cell);
            assertNotNull(reloaded);
            assertEquals(original.id(), reloaded.id());
            assertEquals(original.owner(), reloaded.owner());
            assertEquals(original.name(), reloaded.name());
            assertEquals(original.color(), reloaded.color());
            assertEquals(original.chunks(), reloaded.chunks());
            assertEquals(original.members(), reloaded.members());
            assertTrue(reloaded.forceLoaded());

            // Test economy config load / save
            dev.herebynamed.claim.model.ClaimEconomyConfig config = store.loadConfig();
            assertNotNull(config);
            assertFalse(config.claimCost().enabled());
            assertEquals("minecraft:emerald", config.claimCost().item());
            assertEquals(1, config.claimCost().amountPerChunk());
            assertEquals(100, config.limits().maxClaimChunksPerPlayer());
        } finally {
            // Clean up
            try (var stream = java.nio.file.Files.walk(tempDir)) {
                stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try { java.nio.file.Files.deleteIfExists(p); } catch (Exception ignored) {}
                });
            }
        }
    }
}
