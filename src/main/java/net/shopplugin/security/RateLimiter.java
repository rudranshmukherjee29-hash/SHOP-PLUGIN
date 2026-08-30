package net.shopplugin.security;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple sliding-window-ish rate limiter (fixed minimum interval between
 * actions) to blunt packet-spam and macro-click exploits without adding
 * perceptible delay to normal play.
 */
public final class RateLimiter {

    private final Map<UUID, Long> lastActionMillis = new ConcurrentHashMap<>();
    private final long minIntervalMillis;

    public RateLimiter(long minIntervalMillis) {
        this.minIntervalMillis = minIntervalMillis;
    }

    /**
     * Returns true if the player is allowed to act now, and records the
     * action time if so. Returns false (and does NOT update the timestamp)
     * if the player is acting too quickly.
     */
    public boolean tryAcquire(UUID playerId) {
        long now = System.currentTimeMillis();
        long[] result = new long[1];
        boolean[] allowed = new boolean[1];
        lastActionMillis.compute(playerId, (id, last) -> {
            if (last == null || now - last >= minIntervalMillis) {
                allowed[0] = true;
                result[0] = now;
                return now;
            } else {
                allowed[0] = false;
                return last;
            }
        });
        return allowed[0];
    }

    public void cleanup(UUID playerId) {
        lastActionMillis.remove(playerId);
    }
}
