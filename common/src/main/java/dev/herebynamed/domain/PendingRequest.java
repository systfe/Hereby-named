package dev.herebynamed.domain;

import java.util.Optional;

/** Correlates an edit acknowledgement independently of unsolicited/read snapshots. */
public final class PendingRequest {
    public record Request(long id, String dimension, boolean acquisition) {}
    private long sequence;
    private Request current;
    public long nextId() { return ++sequence; }
    public long begin(String dimension, boolean acquisition) {
        current = new Request(nextId(), dimension, acquisition);
        return current.id();
    }
    public boolean waiting() { return current != null; }
    public Optional<Request> complete(long id, String dimension) {
        if (current == null || current.id() != id || !current.dimension().equals(dimension)) return Optional.empty();
        return cancel();
    }
    public Optional<Request> cancel() {
        Request previous = current;
        current = null;
        return Optional.ofNullable(previous);
    }
}
