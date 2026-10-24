package dev.herebynamed.auto;

import java.util.concurrent.*;

/** One bounded worker for analysis, compression and automatic persistence, including integrated servers. */
public final class AutoWork {
    private static final ThreadPoolExecutor WORKER=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(16),r -> { Thread t=new Thread(r,"Hereby Named analysis");t.setDaemon(true);return t; });
    @FunctionalInterface public interface Task<T> { T run() throws Exception; }
    public static <T> CompletableFuture<T> submit(Task<T> task) {
        try { return CompletableFuture.supplyAsync(() -> {
            try { return task.run(); } catch(Exception ex) { throw new CompletionException(ex); }
        },WORKER); } catch(RejectedExecutionException ex) { return CompletableFuture.failedFuture(ex); }
    }
    public static boolean rejected(CompletableFuture<?> future) {
        if(!future.isCompletedExceptionally()) return false;
        try { future.join();return false; }
        catch(CompletionException ex) { return ex.getCause() instanceof RejectedExecutionException; }
        catch(CancellationException ex) { return false; }
    }
    public static boolean hasCapacity() { return WORKER.getQueue().remainingCapacity()>0; }
    /** Closing cancels queued writes and waits only for an already-running write, never queued analysis. */
    public static final class Session implements AutoCloseable {
        private boolean active=true;
        public <T> CompletableFuture<T> submit(Task<T> task) {
            return AutoWork.submit(() -> {
                synchronized(this) {
                    if(!active) throw new CancellationException("World session closed");
                    return task.run();
                }
            });
        }
        @Override public synchronized void close() { active=false; }
    }
    private AutoWork() { }
}
