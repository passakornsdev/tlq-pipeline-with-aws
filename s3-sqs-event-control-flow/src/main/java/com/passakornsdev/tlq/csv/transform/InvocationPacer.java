package com.passakornsdev.tlq.csv.transform;

import java.util.concurrent.TimeUnit;

final class InvocationPacer {
    private static final int INVOCATIONS_PER_INTERVAL = 75;
    private static final long INVOCATION_INTERVAL_MILLIS = 2_000L;

    private int invocationsInInterval;
    private long intervalStartedAtNanos;

    void waitForNextIntervalIfNeeded() {
        if (invocationsInInterval < INVOCATIONS_PER_INTERVAL) {
            return;
        }

        try {
            long intervalNanos = TimeUnit.MILLISECONDS.toNanos(INVOCATION_INTERVAL_MILLIS);
            long remainingNanos = intervalNanos - (System.nanoTime() - intervalStartedAtNanos);
            if (remainingNanos > 0) {
                TimeUnit.NANOSECONDS.sleep(remainingNanos);
            }
            invocationsInInterval = 0;
            intervalStartedAtNanos = System.nanoTime();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while pacing Lambda invocations", exception);
        }
    }

    void recordInvocation() {
        if (invocationsInInterval == 0) {
            intervalStartedAtNanos = System.nanoTime();
        }
        invocationsInInterval++;
    }
}
