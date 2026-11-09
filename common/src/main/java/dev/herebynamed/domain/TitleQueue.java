package dev.herebynamed.domain;

import java.util.*;

public final class TitleQueue {
    public static boolean automaticCreationAround(RegionBook before,RegionBook after,ChunkCell previous,ChunkCell current) {
        return before!=null && previous!=null && after.owner(1,current).filter(r -> r.automatic()!=null).isPresent()
                && after.namesAt(previous).equals(after.namesAt(current))
                && !before.namesAt(previous).equals(after.namesAt(previous));
    }
    private String dimension = "";
    private List<String> current = List.of();
    private long changedAt;
    private boolean delivered;
    public void observeWithoutTitle(String dimension,List<String> names) {
        this.dimension=dimension;current=List.copyOf(names);delivered=true;
    }

    public Optional<List<String>> update(String dimension, List<String> names, long nowMillis, boolean originalBusy) {
        if (!this.dimension.equals(dimension) || !current.equals(names)) {
            this.dimension = dimension; current = List.copyOf(names); changedAt = nowMillis; delivered = false;
        }
        if (current.isEmpty() || delivered || originalBusy || nowMillis - changedAt < 1000) return Optional.empty();
        delivered = true; return Optional.of(current);
    }
}
