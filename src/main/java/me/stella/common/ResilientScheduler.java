package me.stella.common;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ResilientScheduler implements AutoCloseable {

    private final Logger logger;
    private final ExecutorService asyncExecutor;

    public ResilientScheduler(Logger logger, int asyncThreadCount) {
        this.logger = Objects.requireNonNull(logger, "logger cannot be null");
        this.asyncExecutor = Executors.newFixedThreadPool(Math.max(1, asyncThreadCount), namedFactory("crystal-async"));
    }

    public static int computeAsyncThreads(int cores, int multiplier, int minimum) {
        int normalizedCores = Math.max(1, cores);
        int normalizedMultiplier = Math.max(1, multiplier);
        int normalizedMinimum = Math.max(1, minimum);
        return Math.max(normalizedMinimum, Math.min(4, normalizedCores * normalizedMultiplier));
    }

    public TaskHandle<Void> async(Runnable task) {
        Objects.requireNonNull(task, "task cannot be null");
        return submit(asyncExecutor, () -> {
            task.run();
            return null;
        }, "async");
    }

    public <T> TaskHandle<T> async(Callable<T> task) {
        Objects.requireNonNull(task, "task cannot be null");
        return submit(asyncExecutor, task, "async-callable");
    }

    @Override
    public void close() {
        asyncExecutor.shutdownNow();
    }

    private <T> TaskHandle<T> submit(ExecutorService executor, Callable<T> task, String taskType) {
        CompletableFuture<T> status = new CompletableFuture<>();
        final Future<?> future;

        try {
            future = executor.submit(() -> {
                try {
                    T result = task.call();
                    status.complete(result);
                    return result;
                } catch (Throwable throwable) {
                    status.completeExceptionally(throwable);
                    logger.log(Level.WARNING, "Unhandled scheduler error in " + taskType, throwable);
                    return null;
                }
            });
        } catch (Throwable throwable) {
            status.completeExceptionally(throwable);
            logger.log(Level.WARNING, "Unhandled scheduler error in " + taskType, throwable);
            return TaskHandle.completed(status);
        }

        return new DefaultTaskHandle<>(status, mayInterruptIfRunning -> {
            boolean cancelled = future.cancel(mayInterruptIfRunning);
            if (cancelled && !status.isDone()) {
                status.cancel(mayInterruptIfRunning);
            }
            return cancelled;
        });
    }

    private static ThreadFactory namedFactory(String baseName) {
        AtomicInteger sequence = new AtomicInteger(1);
        return runnable -> {
            Thread thread = new Thread(runnable, baseName + "-" + sequence.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
    }

    @FunctionalInterface
    private interface CancelAction {
        boolean cancel(boolean mayInterruptIfRunning);
    }

    private static final class DefaultTaskHandle<T> implements TaskHandle<T> {

        private final CompletableFuture<T> status;
        private final CancelAction cancelAction;

        private DefaultTaskHandle(CompletableFuture<T> status, CancelAction cancelAction) {
            this.status = status;
            this.cancelAction = cancelAction;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return cancelAction.cancel(mayInterruptIfRunning);
        }

        @Override
        public CompletableFuture<T> status() {
            return status;
        }
    }

    public interface TaskHandle<T> {

        boolean cancel(boolean mayInterruptIfRunning);

        CompletableFuture<T> status();

        default boolean cancel() {
            return cancel(false);
        }

        static <T> TaskHandle<T> completed(CompletableFuture<T> status) {
            return new DefaultTaskHandle<>(status, ignored -> false);
        }
    }
}
