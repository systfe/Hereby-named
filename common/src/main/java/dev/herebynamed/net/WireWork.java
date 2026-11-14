package dev.herebynamed.net;

import java.util.concurrent.*;

/**
 * Snapshot (de)compression gets its own thread. It used to share the single analysis worker, which can block for
 * seconds on map I/O and delay every acknowledgement until the client gave up.
 */
public final class WireWork {
    private static final ThreadPoolExecutor WORKER = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(16), r -> { Thread t = new Thread(r, "Hereby Named sync"); t.setDaemon(true); return t; });
    @FunctionalInterface public interface Task<T> { T run() throws Exception; }
    public static <T> CompletableFuture<T> submit(Task<T> task) {
        try {
            return CompletableFuture.supplyAsync(() -> {
                try { return task.run(); } catch (Exception ex) { throw new CompletionException(ex); }
            }, WORKER);
        } catch (RejectedExecutionException ex) { return CompletableFuture.failedFuture(ex); }
    }
    public static boolean rejected(CompletableFuture<?> future) {
        if (!future.isCompletedExceptionally()) return false;
        try { future.join(); return false; }
        catch (CompletionException ex) { return ex.getCause() instanceof RejectedExecutionException; }
        catch (CancellationException ex) { return false; }
    }
    public static boolean hasCapacity() { return WORKER.getQueue().remainingCapacity() > 0; }
    private WireWork() { }
}
