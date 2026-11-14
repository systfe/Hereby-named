package dev.herebynamed.claim;

import dev.herebynamed.claim.model.Claim;
import dev.herebynamed.domain.ChunkCell;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ClaimManager {
    private final Map<String, ConcurrentHashMap<Long, Claim>> spatialIndex = new ConcurrentHashMap<>();
    private final Map<UUID, Claim> claimsById = new ConcurrentHashMap<>();
    private final Map<String, Set<UUID>> dimensionClaims = new ConcurrentHashMap<>();

    public static long toChunkKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    public Claim getClaimAt(String dimension, ChunkCell cell) {
        ConcurrentHashMap<Long, Claim> dimMap = spatialIndex.get(dimension);
        if (dimMap == null) return null;
        return dimMap.get(toChunkKey(cell.x(), cell.z()));
    }

    public Claim getClaim(UUID id) {
        return claimsById.get(id);
    }

    public Collection<Claim> allClaims(String dimension) {
        Set<UUID> ids = dimensionClaims.get(dimension);
        if (ids == null) return List.of();
        List<Claim> list = new ArrayList<>(ids.size());
        for (UUID id : ids) {
            Claim c = claimsById.get(id);
            if (c != null) list.add(c);
        }
        return list;
    }

    public Set<String> getDimensions() {
        return Collections.unmodifiableSet(dimensionClaims.keySet());
    }

    public synchronized boolean registerClaim(String dimension, Claim claim) {
        ConcurrentHashMap<Long, Claim> dimMap = spatialIndex.computeIfAbsent(dimension, k -> new ConcurrentHashMap<>());
        // Pre-check for spatial conflicts
        for (ChunkCell c : claim.chunks()) {
            Claim existing = dimMap.get(toChunkKey(c.x(), c.z()));
            if (existing != null && !existing.id().equals(claim.id())) {
                return false;
            }
        }

        // If replacing an existing claim with updated chunks, unindex old chunks first
        Claim old = claimsById.get(claim.id());
        if (old != null) {
            for (ChunkCell c : old.chunks()) {
                if (!claim.chunks().contains(c)) {
                    dimMap.remove(toChunkKey(c.x(), c.z()), old);
                }
            }
        }

        for (ChunkCell c : claim.chunks()) {
            dimMap.put(toChunkKey(c.x(), c.z()), claim);
        }
        claimsById.put(claim.id(), claim);
        dimensionClaims.computeIfAbsent(dimension, k -> ConcurrentHashMap.newKeySet()).add(claim.id());
        return true;
    }

    public synchronized void evictClaim(Claim claim, String dimension) {
        claimsById.remove(claim.id());
        Set<UUID> ids = dimensionClaims.get(dimension);
        if (ids != null) ids.remove(claim.id());

        ConcurrentHashMap<Long, Claim> dimMap = spatialIndex.get(dimension);
        if (dimMap != null) {
            for (ChunkCell c : claim.chunks()) {
                dimMap.remove(toChunkKey(c.x(), c.z()), claim);
            }
        }
    }

    public List<Claim> getClaimsByOwner(UUID owner) {
        List<Claim> list = new ArrayList<>();
        for (Claim c : claimsById.values()) {
            if (c.owner().equals(owner)) list.add(c);
        }
        return list;
    }

    public String resolvePlayerName(MinecraftServer server, UUID id) {
        if (server != null) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) return player.getScoreboardName();
            var profile = server.getProfileCache();
            if (profile != null) {
                var entry = profile.get(id);
                if (entry.isPresent()) return entry.get().getName();
            }
        }
        return id.toString().substring(0, 8);
    }
}
