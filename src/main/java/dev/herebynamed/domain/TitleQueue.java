package dev.herebynamed.domain;

import java.util.*;

public final class TitleQueue {
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
