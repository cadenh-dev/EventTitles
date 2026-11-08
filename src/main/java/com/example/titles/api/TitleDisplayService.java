package com.example.titles.api;

import org.bukkit.entity.Player;

/** Main-thread-only service owning the floating TextDisplay passengers and PDC title state. */
public interface TitleDisplayService {
    /**
     * Applies {@code title} if its priority is >= the active title's current protection
     * ({@link TitlePriority#effective}); otherwise ignores it. The new title's protection wears off.
     */
    default void setPlayerTitle(Player player, AwardedTitle title) {
        setPlayerTitle(player, title, true);
    }

    /** As above; {@code decays = false} keeps full protection forever (admin-set titles). */
    void setPlayerTitle(Player player, AwardedTitle title, boolean decays);

    /** Current protection of the active title (0 = anything replaces it). Main thread. */
    int effectivePriority(Player player);

    /** Whole minutes until any new title can replace the active one; 0 = now, -1 = never wears off. */
    long minutesUntilReplaceable(Player player);

    /**
     * Thread-safe (any thread, e.g. async chat): the player's active title as last seen on the main
     * thread. Empty for players with no title or who are offline.
     */
    java.util.Optional<AwardedTitle> cachedTitle(java.util.UUID playerId);

    /** Drops per-player caches when the player leaves (main thread). PDC is untouched. */
    void forgetPlayer(Player player);

    /** The player's active title as stored in PDC (text, priority and reason; no comment). */
    java.util.Optional<AwardedTitle> currentTitle(Player player);

    /** Removes the title from PDC and despawns the display (admin action, ignores priority). */
    void clearPlayerTitle(Player player);

    /** The player's pinned showcase titles (MiniMessage), possibly empty. Main thread. */
    java.util.List<String> showcase(Player player);

    /** Replaces the showcase (empty = clear), persists it, and refreshes the display. Main thread. */
    void setShowcase(Player player, java.util.List<String> titles);

    /** Ensures exactly one display exists and is mounted, rebuilt from PDC state; no-op if no title. */
    void refreshDisplay(Player player);

    void updateSneakState(Player player, boolean isSneaking);

    /** Despawns and unregisters the display entity but KEEPS the PDC title (so it can be restored). */
    void removeDisplay(Player player);

    /** Despawns every tracked display (plugin disable). */
    void cleanupAll();
}
