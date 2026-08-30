package net.shopplugin.listener;

import net.shopplugin.security.RateLimiter;
import net.shopplugin.security.TransactionGuard;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Prevents unbounded growth of per-player security maps over long server
 * uptime by clearing entries when a player disconnects. Locks that are
 * still held (a transaction was mid-flight at disconnect, which shouldn't
 * happen since transactions are synchronous and short, but is handled
 * defensively) are left alone until they're naturally released.
 */
public final class PlayerCleanupListener implements Listener {

    private final TransactionGuard transactionGuard;
    private final RateLimiter rateLimiter;

    public PlayerCleanupListener(TransactionGuard transactionGuard, RateLimiter rateLimiter) {
        this.transactionGuard = transactionGuard;
        this.rateLimiter = rateLimiter;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        var id = event.getPlayer().getUniqueId();
        transactionGuard.cleanup(id);
        rateLimiter.cleanup(id);
    }
}
