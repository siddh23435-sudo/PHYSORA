package edu.physics.ui;

import javafx.application.Platform;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/**
 * Shared JavaFX toolkit lifecycle helper for UI tests running in the same JVM.
 * Initializes the JavaFX platform once and keeps it active across test classes
 * without calling {@link Platform#exit()} between suites.
 */
final class FxTestRuntime {
    private static final Object LOCK = new Object();
    private static boolean started;

    private FxTestRuntime() {}

    static void ensureStarted() throws Exception {
        synchronized (LOCK) {
            if (started) {
                verifyFxThreadResponsive();
                return;
            }
            CountDownLatch latch = new CountDownLatch(1);
            try {
                Platform.startup(latch::countDown);
                if (!latch.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("JavaFX toolkit failed to start within timeout");
                }
            } catch (IllegalStateException alreadyInitialized) {
                verifyFxThreadResponsive();
            }
            Platform.setImplicitExit(false);
            started = true;
        }
    }

    static <T> T runOnFxThread(Callable<T> work) throws Exception {
        ensureStarted();
        if (Platform.isFxApplicationThread()) {
            return work.call();
        }
        FutureTask<T> task = new FutureTask<>(work);
        Platform.runLater(task);
        try {
            return task.get(10, TimeUnit.SECONDS);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new RuntimeException(cause);
        }
    }

    private static void verifyFxThreadResponsive() throws InterruptedException {
        if (Platform.isFxApplicationThread()) {
            return;
        }
        CountDownLatch ping = new CountDownLatch(1);
        Platform.runLater(ping::countDown);
        if (!ping.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("JavaFX toolkit is unavailable or has already exited");
        }
    }
}

