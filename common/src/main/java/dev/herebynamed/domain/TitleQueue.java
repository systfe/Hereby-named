package dev.herebynamed.domain;

import java.util.*;

public final class TitleQueue {
    public static boolean automaticCreationAround(RegionBook before,RegionBook after,ChunkCell previous,ChunkCell current) {
        return before!=null && previous!=null && after.owner(1,current).filter(r -> r.automatic()!=null).isPresent()
                && after.namesAt(previous).equals(after.namesAt(current))
                && !before.namesAt(previous).equals(after.namesAt(previous));
    }
    private String dimension = "";
    private List<String> current = List.of(), lastShown = List.of();
    private long changedAt, shownAt;
    private boolean delivered, everShown;
    public void observeWithoutTitle(String dimension,List<String> names) {
        this.dimension=dimension;current=List.copyOf(names);delivered=true;
    }

    public Optional<List<String>> update(String dimension, List<String> names, long nowMillis, boolean originalBusy) {
        return update(dimension, names, nowMillis, originalBusy, 0, 0);
    }

    /**
     * repeatCooldownMillis gates coming back to the same place; switchCooldownMillis gates moving on to a
     * different one. A dimension change always wins, so a portal never eats the title.
     */
    public Optional<List<String>> update(String dimension, List<String> names, long nowMillis, boolean originalBusy,
                                         long repeatCooldownMillis, long switchCooldownMillis) {
        boolean otherDimension = !this.dimension.equals(dimension);
        if (otherDimension || !current.equals(names)) {
            if (otherDimension) { shownAt = 0; everShown = false; lastShown = List.of(); }
            this.dimension = dimension; current = List.copyOf(names); changedAt = nowMillis; delivered = false;
        }
        if (current.isEmpty() || delivered || originalBusy || nowMillis - changedAt < 1000) return Optional.empty();
        long cooldown = current.equals(lastShown) ? repeatCooldownMillis : switchCooldownMillis;
        if (everShown && nowMillis - shownAt < cooldown) { delivered = true; return Optional.empty(); }
        delivered = true; everShown = true; lastShown = current; shownAt = nowMillis;
        return Optional.of(current);
    }
}
