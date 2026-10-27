package com.breakdash.launcher;

import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

final class Tasks implements AutoCloseable {
    final Handler main = new Handler(Looper.getMainLooper());
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(2, 2, 15, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(24), runnable -> new Thread(runnable, "BreakDash-IO"),
            new ThreadPoolExecutor.AbortPolicy());
    Tasks() { executor.allowCoreThreadTimeOut(true); }
    Scope scope() { return new Scope(); }
    final class Scope implements AutoCloseable {
        private final List<Future<?>> jobs = new ArrayList<>();
        private volatile boolean closed;
        <T> void run(Callable<T> work, Consumer<T> success, Consumer<Exception> failure) {
            if (closed) return;
            try {
                jobs.removeIf(Future::isDone);
                jobs.add(executor.submit(() -> {
                    try {
                        if (closed) return;
                        T value = work.call();
                        main.post(() -> { if (!closed) success.accept(value); });
                    } catch (Exception error) {
                        main.post(() -> { if (!closed) failure.accept(error); });
                    }
                }));
            } catch (java.util.concurrent.RejectedExecutionException error) {
                if (!closed) failure.accept(error);
            }
        }
        void post(Runnable action) { main.post(() -> { if (!closed) action.run(); }); }
        boolean active() { return !closed; }
        @Override public void close() { closed = true; for (Future<?> job : jobs) job.cancel(true); jobs.clear(); }
    }
    @Override public void close() { executor.shutdownNow(); main.removeCallbacksAndMessages(null); }
}
