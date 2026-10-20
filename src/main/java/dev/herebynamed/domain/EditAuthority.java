package dev.herebynamed.domain;

import java.util.*;

public final class EditAuthority {
    private final Map<String, UUID> holders = new HashMap<>();
    public boolean acquire(String dimension, UUID player, boolean authorized) {
        if (!authorized || (holders.containsKey(dimension) && !player.equals(holders.get(dimension)))) return false;
        holders.put(dimension, player); return true;
    }
    public boolean canEdit(String dimension, UUID player, boolean authorized) {
        return authorized && player.equals(holders.get(dimension));
    }
    public UUID holder(String dimension) { return holders.get(dimension); }
    public void release(String dimension, UUID player) { holders.remove(dimension, player); }
    public Set<String> releaseAll(UUID player) {
        Set<String> released = new HashSet<>();
        holders.forEach((dimension, holder) -> { if (holder.equals(player)) released.add(dimension); });
        released.forEach(holders::remove); return released;
    }
}
