package dev.deepdive.spring.async;

import java.time.Duration;

final class WorkSleeper {

    private WorkSleeper() {
    }

    static void sleep(Duration delay) {
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Work was interrupted.", exception);
        }
    }
}
