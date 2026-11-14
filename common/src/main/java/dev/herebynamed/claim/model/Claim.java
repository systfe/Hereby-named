package dev.herebynamed.claim.model;

import dev.herebynamed.domain.ChunkCell;
import java.util.*;

public record Claim(
    UUID id,
    UUID owner,
    String name,
    int color,
    Set<ChunkCell> chunks,
    Map<UUID, ClaimRole> members,
    int visitorFlags,
    int memberFlags,
    int environmentFlags,
    boolean forceLoaded,
    long createdTime
) {
    public Claim {
        chunks = Set.copyOf(chunks);
        members = Map.copyOf(members);
    }

    public static Claim create(UUID owner, String name, int color, Set<ChunkCell> chunks) {
        return new Claim(
            UUID.randomUUID(),
            owner,
            name,
            color,
            chunks,
            Map.of(),
            ClaimFlag.DEFAULT_VISITOR,
            ClaimFlag.DEFAULT_MEMBER,
            ClaimFlag.DEFAULT_ENVIRONMENT,
            false,
            System.currentTimeMillis()
        );
    }

    public Claim withChunks(Set<ChunkCell> newChunks) {
        return new Claim(id, owner, name, color, newChunks, members, visitorFlags, memberFlags, environmentFlags, forceLoaded, createdTime);
    }

    public Claim withName(String newName) {
        return new Claim(id, owner, newName, color, chunks, members, visitorFlags, memberFlags, environmentFlags, forceLoaded, createdTime);
    }

    public Claim withColor(int newColor) {
        return new Claim(id, owner, name, newColor, chunks, members, visitorFlags, memberFlags, environmentFlags, forceLoaded, createdTime);
    }

    public Claim withMember(UUID member, ClaimRole role) {
        Map<UUID, ClaimRole> next = new HashMap<>(members);
        next.put(member, role);
        return new Claim(id, owner, name, color, chunks, next, visitorFlags, memberFlags, environmentFlags, forceLoaded, createdTime);
    }

    public Claim withoutMember(UUID member) {
        Map<UUID, ClaimRole> next = new HashMap<>(members);
        next.remove(member);
        return new Claim(id, owner, name, color, chunks, next, visitorFlags, memberFlags, environmentFlags, forceLoaded, createdTime);
    }

    public Claim withVisitorFlags(int flags) {
        return new Claim(id, owner, name, color, chunks, members, flags, memberFlags, environmentFlags, forceLoaded, createdTime);
    }

    public Claim withMemberFlags(int flags) {
        return new Claim(id, owner, name, color, chunks, members, visitorFlags, flags, environmentFlags, forceLoaded, createdTime);
    }

    public Claim withEnvironmentFlags(int flags) {
        return new Claim(id, owner, name, color, chunks, members, visitorFlags, memberFlags, flags, forceLoaded, createdTime);
    }

    public Claim withForceLoaded(boolean loaded) {
        return new Claim(id, owner, name, color, chunks, members, visitorFlags, memberFlags, environmentFlags, loaded, createdTime);
    }
}
