package com.smartlibrary.concurrency;

import javafx.application.Platform;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The project's shared thread pool - the Java Executor Framework from the
 * Lab 2 manual (Tasks 8-10: ExecutorService, fixed pool, Callable, Future
 * and the graceful shutdown pattern) applied to the real work of the app
 * instead of a stand-alone demo.
 *
 * Every database or disk job that does not need the JavaFX Application
 * Thread runs here, so the UI thread is only ever used for painting. The
 * worker threads are purpose-named (library-db-1, library-db-2, ...) just
 * like the lab's naming convention, which makes them easy to recognise in
 * a thread dump or in the log.
 */
public final class AppExecutors {

    /** Small fixed pool: enough to run the dashboard's independent queries at once. */
    private static final int POOL_SIZE = 4;

    /** How long shutdown() waits for accepted tasks before interrupting them. */
    private static final long TERMINATION_TIMEOUT_SECONDS = 5;

    private static final ExecutorService POOL =
            Executors.newFixedThreadPool(POOL_SIZE, new LibraryThreadFactory());

    private AppExecutors() {
    }

    /**
     * Submits a value-returning task. The caller reads the outcome later
     * with {@code Future.get()} - which must never be called on the
     * JavaFX thread (it blocks until the task finishes).
     */
    public static <T> Future<T> submit(Callable<T> task) {
        try {
            return POOL.submit(task);
        } catch (RejectedExecutionException e) {
            // The app is already shutting down: hand back a Future that
            // reports the rejection instead of throwing on the caller's thread.
            CompletableFuture<T> rejected = new CompletableFuture<>();
            rejected.completeExceptionally(e);
            return rejected;
        }
    }

    /** Fire-and-forget task, for work whose result nobody needs. */
    public static void execute(Runnable task) {
        try {
            POOL.execute(task);
        } catch (RejectedExecutionException e) {
            System.err.println("Background task rejected (application is shutting down): "
                    + e.getMessage());
        }
    }

    /**
     * Jumps back onto the JavaFX Application Thread. Nodes, controls and
     * ObservableLists may only be touched from that thread, so every
     * background job ends with {@code runFx(...)}.
     */
    public static void runFx(Runnable uiUpdate) {
        if (Platform.isFxApplicationThread()) {
            uiUpdate.run();
            return;
        }
        try {
            Platform.runLater(uiUpdate);
        } catch (IllegalStateException e) {
            // The toolkit already stopped while the app was closing - the
            // update is no longer wanted, so dropping it is correct.
        }
    }

    /**
     * Graceful shutdown (Lab 2, section 10.3): stop accepting new work,
     * give the accepted tasks time to finish, then interrupt what is left.
     * Called from Main.stop() when the window is closed.
     */
    public static void shutdown() {
        POOL.shutdown();
        try {
            if (!POOL.awaitTermination(TERMINATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                POOL.shutdownNow();
                if (!POOL.awaitTermination(TERMINATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    System.err.println("Executor did not terminate.");
                }
            }
        } catch (InterruptedException e) {
            POOL.shutdownNow();
            Thread.currentThread().interrupt(); // restore the interrupt request
        }
    }

    /** Gives every pool worker a purpose-based name, as taught in the manual. */
    private static final class LibraryThreadFactory implements ThreadFactory {

        private final AtomicInteger nextNumber = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable task) {
            Thread thread = new Thread(task, "library-db-" + nextNumber.getAndIncrement());
            // Daemon so a forgotten pool can never keep the JVM alive after
            // the window closes; shutdown() still waits for accepted work.
            thread.setDaemon(true);
            return thread;
        }
    }
}
