package com.alethicode.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.function.Function;

public final class BoundedParallel {

    private static final Logger log = LoggerFactory.getLogger(BoundedParallel.class);

    private BoundedParallel() {}

    public static int normalizeToPowerOfTwo(int value) {
        if (value <= 1) return 1;
        return Integer.highestOneBit(value);
    }

    /**
     * Parallel map with automatic 2^N backoff.
     *
     * Starts at nearest power-of-two <= maxConcurrency.
     * When >= 2 tasks fail with overload/timeout, concurrency halves and retries all.
     * When exactly 1 task fails, it retries that task sequentially (not an overload signal).
     * Minimum parallel concurrency is 2; below that falls back to fully sequential.
     */
    public static <T, R> List<R> map(List<T> items, int maxConcurrency, Function<T, R> task) {
        if (items.isEmpty()) {
            return List.of();
        }
        int concurrency = normalizeToPowerOfTwo(maxConcurrency);
        if (concurrency <= 1 || items.size() == 1) {
            return runSequential(items, task);
        }

        while (concurrency >= 2) {
            ParallelResult<R> result = runParallel(items, concurrency, task);
            if (result.allDone()) {
                return result.results();
            }
            if (result.overloadCount >= 2 || result.overloadCount > items.size() / 2) {
                int halved = concurrency >> 1;
                log.warn("Concurrency {} overloaded ({} failures), halving to {}",
                        concurrency, result.overloadCount, halved);
                concurrency = halved;
                continue;
            }
            log.info("Retrying {} individually-failed items sequentially", result.overloadCount);
            return result.retryFailed(items, task);
        }
        return runSequential(items, task);
    }

    public static <T> void forEach(List<T> items, int maxConcurrency, java.util.function.Consumer<T> task) {
        map(items, maxConcurrency, item -> {
            task.accept(item);
            return null;
        });
    }

    private static <T, R> List<R> runSequential(List<T> items, Function<T, R> task) {
        List<R> results = new ArrayList<>(items.size());
        for (T item : items) {
            results.add(task.apply(item));
        }
        return results;
    }

    @SuppressWarnings("unchecked")
    private static <T, R> ParallelResult<R> runParallel(List<T> items, int concurrency,
                                                         Function<T, R> task) {
        Object[] results = new Object[items.size()];
        boolean[] done = new boolean[items.size()];
        int overloadFailures = 0;

        Semaphore semaphore = new Semaphore(concurrency);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Object>> futures = new ArrayList<>(items.size());
            for (int i = 0; i < items.size(); i++) {
                final int idx = i;
                futures.add(executor.submit(() -> {
                    semaphore.acquire();
                    try {
                        return task.apply(items.get(idx));
                    } finally {
                        semaphore.release();
                    }
                }));
            }

            for (int i = 0; i < futures.size(); i++) {
                try {
                    results[i] = futures.get(i).get();
                    done[i] = true;
                } catch (ExecutionException executionException) {
                    Throwable cause = executionException.getCause();
                    if (cause instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted", cause);
                    }
                    if (isOverloadError(cause)) {
                        overloadFailures++;
                    } else {
                        if (cause instanceof RuntimeException rt) throw rt;
                        throw new RuntimeException("Parallel task failed", cause);
                    }
                }
            }
        } catch (RuntimeException runtimeException) {
            throw runtimeException;
        } catch (Exception exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException rt) throw rt;
            throw new RuntimeException("Parallel execution failed", exception);
        }

        return new ParallelResult<>(results, done, overloadFailures);
    }

    private static boolean isOverloadError(Throwable cause) {
        if (cause == null) return false;
        String msg = cause.getMessage();
        if (msg == null) return false;
        String lower = msg.toLowerCase(Locale.ROOT);
        return lower.contains("429")
                || lower.contains("rate limit")
                || lower.contains("too many requests")
                || lower.contains("timed out")
                || lower.contains("timeout")
                || lower.contains("overloaded")
                || lower.contains("received no bytes")
                || lower.contains("connection reset")
                || lower.contains("connection refused")
                || lower.contains("broken pipe")
                || lower.contains("stream is closed")
                || lower.contains("eof");
    }

    private record ParallelResult<R>(Object[] resultArray, boolean[] done, int overloadCount) {
        boolean allDone() {
            for (boolean d : done) if (!d) return false;
            return true;
        }

        @SuppressWarnings("unchecked")
        List<R> results() {
            List<R> list = new ArrayList<>(resultArray.length);
            for (Object r : resultArray) list.add((R) r);
            return list;
        }

        @SuppressWarnings("unchecked")
        <T> List<R> retryFailed(List<T> items, Function<T, R> task) {
            for (int i = 0; i < items.size(); i++) {
                if (!done[i]) {
                    resultArray[i] = task.apply(items.get(i));
                }
            }
            List<R> list = new ArrayList<>(resultArray.length);
            for (Object r : resultArray) list.add((R) r);
            return list;
        }
    }
}
