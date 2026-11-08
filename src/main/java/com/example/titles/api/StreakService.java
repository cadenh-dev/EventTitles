package com.example.titles.api;

import java.util.UUID;

/** Thread-safe rolling-window streak counter. Bukkit-free. */
public interface StreakService {
    /**
     * Records one occurrence of {@code streakKey} for the player and returns the consecutive count
     * (>= 1) including this one. If the previous occurrence of the same key was longer ago than the
     * streak window, or a different key was recorded since, the count restarts at 1.
     */
    int recordAndGet(UUID playerId, String streakKey);

    /** Clears all streak state for the player (e.g. on quit). */
    void reset(UUID playerId);
}
