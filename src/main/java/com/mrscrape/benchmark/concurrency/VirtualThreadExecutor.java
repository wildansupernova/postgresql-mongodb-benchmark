package com.mrscrape.benchmark.concurrency;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VirtualThreadExecutor {
    private static final Logger logger = LoggerFactory.getLogger(VirtualThreadExecutor.class);
    private final int maxConcurrency;
    private final ExecutorService executor;
    private final Semaphore semaphore;
    private final List<Exception> exceptions = new CopyOnWriteArrayList<>();
    private final AtomicInteger pendingTasks = new AtomicInteger(0);

    public VirtualThreadExecutor(int maxConcurrency) {
        this.maxConcurrency = maxConcurrency;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        this.semaphore = new Semaphore(maxConcurrency);
    }

    public void execute(Runnable task) {
        pendingTasks.incrementAndGet();
        executor.execute(() -> {
            try {
                semaphore.acquire();
                try {
                    task.run();
                } finally {
                    semaphore.release();
                }
            } catch (InterruptedException e) {
                logger.error("Task interrupted while waiting for semaphore", e);
                Thread.currentThread().interrupt();
                exceptions.add(e);
            } catch (Exception e) {
                logger.error("Unexpected exception in virtual thread", e);
                exceptions.add(e);
            } finally {
                pendingTasks.decrementAndGet();
            }
        });
    }

    public <T> T execute(java.util.concurrent.Callable<T> task) {
        try {
            semaphore.acquire();
            try {
                return executor.submit(task).get();
            } finally {
                semaphore.release();
            }
        } catch (InterruptedException e) {
            logger.error("Task interrupted while waiting for semaphore", e);
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (java.util.concurrent.ExecutionException e) {
            logger.error("Task execution failed", e);
            throw new RuntimeException(e);
        }
    }

    public void waitForCompletion() throws InterruptedException {
        while (pendingTasks.get() > 0) {
            Thread.sleep(10);
        }
    }

    public List<Exception> getExceptions() {
        return new ArrayList<>(exceptions);
    }

    public int getExceptionCount() {
        return exceptions.size();
    }

    public int getMaxConcurrency() {
        return maxConcurrency;
    }

    public int getActivePermits() {
        return semaphore.availablePermits();
    }

    public void shutdown() throws InterruptedException {
        executor.shutdown();
        if (!executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS)) {
            executor.shutdownNow();
            throw new InterruptedException("Executor did not terminate in time");
        }
    }
}
