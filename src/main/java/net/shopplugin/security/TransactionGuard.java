package net.shopplugin.security;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Ensures a single player can never have two overlapping shop transactions
 * in flight at once, regardless of how many click/packet events fire.
 *
 * This is the primary defense against duplication via fast-clicking,
 * double-clicking, drag exploits, and packet spam: every transaction
 * (buy or sell) must acquire this lock for the player's UUID before
 * touching balance or inventory, and must release it in a finally block.
 *
 * Locks use tryLock (non-blocking): if a player already has a transaction
 * in progress, a second concurrent attempt is rejected immediately rather
 * than queued, so the main thread is never blocked waiting on itself.
 */
public final class TransactionGuard {

    private final Map<UUID, ReentrantLock> locks = new ConcurrentHashMap<>();

    /**
     * Attempts to acquire the lock for a player. Returns true if acquired.
     * Caller MUST call {@link #release(UUID)} exactly once if this returns true,
     * ideally in a try/finally block.
     */
    public boolean tryAcquire(UUID playerId) {
        ReentrantLock lock = locks.computeIfAbsent(playerId, id -> new ReentrantLock());
        return lock.tryLock();
    }

    public void release(UUID playerId) {
        ReentrantLock lock = locks.get(playerId);
        if (lock != null && lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    /**
     * Removes the lock entry entirely. Safe to call on player disconnect
     * to avoid unbounded map growth over server uptime. Only removes the
     * entry if it is not currently held, to avoid clearing an in-flight lock.
     */
    public void cleanup(UUID playerId) {
        locks.computeIfPresent(playerId, (id, lock) -> lock.isLocked() ? lock : null);
    }

    public boolean isLocked(UUID playerId) {
        ReentrantLock lock = locks.get(playerId);
        return lock != null && lock.isLocked();
    }
}
