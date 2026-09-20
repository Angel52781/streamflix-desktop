package dev.streamflix.desktop;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

public final class PlaybackRecoveryTest {
    public static void main(String[] args) throws Exception {
        testRetriesOneTransientFailure();
        testPermanentFailureIsNotRetried();
        testIllegalArgumentIsNotRetried();
        System.out.println("PlaybackRecoveryTest OK");
    }

    private static void testRetriesOneTransientFailure() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        String result = PlaybackRecovery.run(() -> {
            if (attempts.incrementAndGet() == 1) throw new IOException("temporary timeout");
            return "ok";
        });
        require("ok".equals(result), "transient retry returns result");
        require(attempts.get() == 2, "transient failure retried exactly once");
    }

    private static void testPermanentFailureIsNotRetried() {
        AtomicInteger attempts = new AtomicInteger();
        try {
            PlaybackRecovery.run(() -> {
                attempts.incrementAndGet();
                throw new IOException("access denied");
            });
            throw new AssertionError("Expected permanent failure");
        } catch (IOException expected) {
            require(attempts.get() == 1, "permanent IO failure not retried");
        } catch (Exception unexpected) {
            throw new AssertionError(unexpected);
        }
    }

    private static void testIllegalArgumentIsNotRetried() {
        AtomicInteger attempts = new AtomicInteger();
        try {
            PlaybackRecovery.run(() -> {
                attempts.incrementAndGet();
                throw new IllegalArgumentException("invalid source");
            });
            throw new AssertionError("Expected invalid-source failure");
        } catch (IllegalArgumentException expected) {
            require(attempts.get() == 1, "invalid source not retried");
        } catch (Exception unexpected) {
            throw new AssertionError(unexpected);
        }
    }

    private static void require(boolean value, String name) {
        if (!value) throw new AssertionError("Failed: " + name);
    }
}