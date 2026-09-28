package com.smartlibrary.concurrency;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * One background worker that runs screen refreshes strictly in the order
 * they were requested - this is Lab 2 Task 7's producer/consumer box used
 * for real work:
 *
 *   - the JavaFX Application Thread is the producer ({@link #request})
 *   - the named thread "library-refresh-worker" is the consumer
 *   - the job queue is the shared resource, guarded by a single monitor
 *   - the consumer sleeps in a {@code while} loop (never {@code if}) until
 *     a producer calls {@code notifyAll()}
 *
 * Why a queue instead of just the pool: a refresh requested later must
 * never be overwritten by one requested earlier, otherwise a screen could
 * briefly show rows from before a change. A single FIFO worker guarantees
 * that ordering, while {@link AppExecutors} runs the dashboard's
 * independent queries in parallel.
 */
public final class RefreshQueue {

    /** The one monitor both sides synchronise on. */
    private static final Object LOCK = new Object();

    /** Shared mutable state - only ever touched while holding LOCK. */
    private static final Queue<Runnable> pending = new ArrayDeque<>();
    private static boolean stopped = false;

    private static final Thread WORKER;

    static {
        WORKER = new Thread(RefreshQueue::consume, "library-refresh-worker");
        WORKER.setDaemon(true);
        WORKER.start();
    }

    private RefreshQueue() {
    }

    /** Producer side: queue a refresh job and wake the consumer. */
    public static void request(Runnable refresh) {
        synchronized (LOCK) {
            if (stopped) {
                return;
            }
            pending.add(refresh);
            LOCK.notifyAll();
        }
    }

    /** Consumer side: one thread, jobs taken in the order they arrived. */
    private static void consume() {
        while (true) {
            Runnable refresh;

            synchronized (LOCK) {
                while (pending.isEmpty() && !stopped) {
                    try {
                        LOCK.wait(); // WAITING until a producer calls notifyAll()
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt(); // restore the request
                        return;
                    }
                }
                if (stopped) {
                    return;
                }
                refresh = pending.poll();
            }

            try {
                refresh.run(); // the monitor is released while the job runs
            } catch (Throwable t) {
                // One failing screen must not kill the worker for every screen after it.
                t.printStackTrace();
            }
        }
    }

    /**
     * Stops the consumer (called from Main.stop()). Jobs still queued are
     * dropped: the window is gone, so nobody is waiting for their result.
     */
    public static void shutdown() {
        synchronized (LOCK) {
            stopped = true;
            pending.clear();
            LOCK.notifyAll();
        }
    }
}
