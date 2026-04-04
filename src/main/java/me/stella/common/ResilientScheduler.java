package me.stella.common;

import oshi.SystemInfo;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ResilientScheduler implements AutoCloseable {

    private final Logger logger;
    private final ExecutorService asyncExecutor;
    private final ExecutorService virtualExecutor;
    private final ScheduledExecutorService timingExecutor;
    private final AtomicLong lastHeartbeatMillis = new AtomicLong(System.currentTimeMillis());

    public ResilientScheduler(Logger logger, int asyncThreadCount) {
        this.logger = Objects.requireNonNull(logger, "logger cannot be null");

        int safeAsyncThreads = Math.max(1, asyncThreadCount);
        this.asyncExecutor = Executors.newFixedThreadPool(safeAsyncThreads, namedFactory("dweller-async"));
        this.virtualExecutor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("dweller-virtual-", 0).factory());

        ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(
                Math.max(2, Math.min(4, safeAsyncThreads)),
                namedFactory("dweller-timer")
        );
        scheduler.setRemoveOnCancelPolicy(true);
        this.timingExecutor = scheduler;

        // Dedicated heartbeat to detect timer stalls.
        this.timingExecutor.scheduleAtFixedRate(
                () -> lastHeartbeatMillis.set(System.currentTimeMillis()),
                0,
                1,
                TimeUnit.SECONDS
        );
    }

    public static int detectCpuCores() {
        try {
            return Math.max(1, new SystemInfo().getHardware().getProcessor().getLogicalProcessorCount());
        } catch (Exception ignored) {
            return Math.max(1, Runtime.getRuntime().availableProcessors());
        }
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

    public TaskHandle<Void> virtual(Runnable task) {
        Objects.requireNonNull(task, "task cannot be null");
        return submit(virtualExecutor, () -> {
            task.run();
            return null;
        }, "virtual");
    }

    public <T> TaskHandle<T> virtual(Callable<T> task) {
        Objects.requireNonNull(task, "task cannot be null");
        return submit(virtualExecutor, task, "virtual-callable");
    }

    public TaskHandle<Void> scheduleAsync(Duration delay, Runnable task) {
        Objects.requireNonNull(task, "task cannot be null");
        return schedule(delay, () -> {
            task.run();
            return null;
        }, asyncExecutor, "async-delayed");
    }

    public <T> TaskHandle<T> scheduleAsync(Duration delay, Callable<T> task) {
        return schedule(delay, task, asyncExecutor, "async-delayed-callable");
    }

    public TaskHandle<Void> scheduleVirtual(Duration delay, Runnable task) {
        Objects.requireNonNull(task, "task cannot be null");
        return schedule(delay, () -> {
            task.run();
            return null;
        }, virtualExecutor, "virtual-delayed");
    }

    public <T> TaskHandle<T> scheduleVirtual(Duration delay, Callable<T> task) {
        return schedule(delay, task, virtualExecutor, "virtual-delayed-callable");
    }

    public TaskHandle<Void> scheduleAsyncAtFixedRate(Duration initialDelay, Duration period, Runnable task) {
        return scheduleAtFixedRate(initialDelay, period, task, asyncExecutor, "async-periodic");
    }

    public TaskHandle<Void> scheduleVirtualAtFixedRate(Duration initialDelay, Duration period, Runnable task) {
        return scheduleAtFixedRate(initialDelay, period, task, virtualExecutor, "virtual-periodic");
    }

    public long millisSinceLastHeartbeat() {
        return Math.max(0L, System.currentTimeMillis() - lastHeartbeatMillis.get());
    }

    @Override
    public void close() {
        timingExecutor.shutdownNow();
        virtualExecutor.shutdownNow();
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

    private <T> TaskHandle<T> schedule(Duration delay,
                                       Callable<T> task,
                                       ExecutorService executor,
                                       String taskType) {
        Objects.requireNonNull(delay, "delay cannot be null");
        Objects.requireNonNull(task, "task cannot be null");

        CompletableFuture<T> status = new CompletableFuture<>();
        AtomicReference<Future<?>> delegateFutureRef = new AtomicReference<>();
        long delayMillis = Math.max(0L, delay.toMillis());

        final ScheduledFuture<?> timerFuture;
        try {
            timerFuture = timingExecutor.schedule(() -> {
                if (status.isCancelled()) {
                    return;
                }
                try {
                    Future<?> delegateFuture = executor.submit(() -> {
                        try {
                            T result = task.call();
                            status.complete(result);
                        } catch (Throwable throwable) {
                            status.completeExceptionally(throwable);
                            logger.log(Level.WARNING, "Unhandled scheduler error in " + taskType, throwable);
                        }
                    });
                    delegateFutureRef.set(delegateFuture);
                } catch (Throwable throwable) {
                    status.completeExceptionally(throwable);
                    logger.log(Level.WARNING, "Unhandled scheduler error in " + taskType, throwable);
                }
            }, delayMillis, TimeUnit.MILLISECONDS);
        } catch (Throwable throwable) {
            status.completeExceptionally(throwable);
            logger.log(Level.WARNING, "Unhandled scheduler error in " + taskType, throwable);
            return TaskHandle.completed(status);
        }

        return new DefaultTaskHandle<>(status, mayInterruptIfRunning -> {
            boolean cancelled = timerFuture.cancel(mayInterruptIfRunning);
            Future<?> delegateFuture = delegateFutureRef.get();
            if (delegateFuture != null) {
                cancelled |= delegateFuture.cancel(mayInterruptIfRunning);
            }
            if (cancelled && !status.isDone()) {
                status.cancel(mayInterruptIfRunning);
            }
            return cancelled;
        });
    }

    private TaskHandle<Void> scheduleAtFixedRate(Duration initialDelay,
                                                 Duration period,
                                                 Runnable task,
                                                 ExecutorService executor,
                                                 String taskType) {
        Objects.requireNonNull(initialDelay, "initialDelay cannot be null");
        Objects.requireNonNull(period, "period cannot be null");
        Objects.requireNonNull(task, "task cannot be null");

        CompletableFuture<Void> status = new CompletableFuture<>();
        long delayMillis = Math.max(0L, initialDelay.toMillis());
        long periodMillis = Math.max(1L, period.toMillis());

        ScheduledFuture<?> future = timingExecutor.scheduleAtFixedRate(
                () -> executor.execute(() -> {
                    try {
                        task.run();
                    } catch (Throwable throwable) {
                        status.completeExceptionally(throwable);
                        logger.log(Level.WARNING, "Unhandled scheduler error in " + taskType, throwable);
                    }
                }),
                delayMillis,
                periodMillis,
                TimeUnit.MILLISECONDS
        );

        return new DefaultTaskHandle<>(status, mayInterruptIfRunning -> {
            boolean cancelled = future.cancel(mayInterruptIfRunning);
            if (cancelled && !status.isDone()) {
                status.complete(null);
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
        TaskHandle<Void> NO_OP = completed(CompletableFuture.completedFuture(null));

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

