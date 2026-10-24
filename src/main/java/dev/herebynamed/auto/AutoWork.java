package dev.herebynamed.auto;

import java.util.concurrent.*;

/** One bounded worker for analysis, compression and automatic persistence, including integrated servers. */
public final class AutoWork {
    private static final ExecutorService WORKER=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(16),r -> { Thread t=new Thread(r,"Hereby Named analysis");t.setDaemon(true);return t; });
    @FunctionalInterface public interface Task<T> { T run() throws Exception; }
    public static <T> CompletableFuture<T> submit(Task<T> task) {
        try { return CompletableFuture.supplyAsync(() -> {
            try { return task.run(); } catch(Exception ex) { throw new CompletionException(ex); }
        },WORKER); } catch(RejectedExecutionException ex) { return CompletableFuture.failedFuture(ex); }
    }
    private AutoWork() { }
}
