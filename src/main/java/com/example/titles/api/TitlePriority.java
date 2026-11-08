package com.example.titles.api;

/**
 * Bukkit-free priority gating. A title never disappears (no auto-decay of the title itself), but the
 * <em>protection</em> it gives against replacement wears off linearly over {@code decayMinutes}: a fresh
 * priority-80 title blocks anything below 80, half-way through the window it blocks below 40, and once
 * the window has passed any new event can replace it. Without this, one high-priority title would lock
 * a player out of every ordinary death title forever.
 */
public final class TitlePriority {
    private TitlePriority() {}

    /**
     * @param priority       the stored priority of the active title (1–100)
     * @param awardedAtMillis when it was awarded; {@code <= 0} = unknown (titles from older versions),
     *                       treated as fully worn off
     * @param nowMillis      current time
     * @param decayMinutes   protection window; {@code <= 0} = never wears off (strict original behaviour)
     * @param decays         false for titles that keep full protection (admin-set)
     * @return the priority an incoming title must meet or beat (0 = anything replaces it)
     */
    public static int effective(int priority, long awardedAtMillis, long nowMillis, int decayMinutes, boolean decays) {
        if (!decays || decayMinutes <= 0) {
            return priority;
        }
        if (awardedAtMillis <= 0) {
            return 0;
        }
        long windowMillis = decayMinutes * 60_000L;
        long age = Math.max(0, nowMillis - awardedAtMillis);
        if (age >= windowMillis) {
            return 0;
        }
        double remaining = 1.0 - (double) age / windowMillis;
        return (int) Math.ceil(priority * remaining);
    }

    /** True if an incoming title of {@code incomingPriority} may replace the active one. */
    public static boolean canReplace(int incomingPriority, int effectivePriority) {
        return incomingPriority >= effectivePriority;
    }

    /** Whole minutes until protection is fully gone; 0 if already gone, -1 if it never wears off. */
    public static long minutesUntilOpen(long awardedAtMillis, long nowMillis, int decayMinutes, boolean decays) {
        if (!decays || decayMinutes <= 0) {
            return -1;
        }
        if (awardedAtMillis <= 0) {
            return 0;
        }
        long left = awardedAtMillis + decayMinutes * 60_000L - nowMillis;
        return left <= 0 ? 0 : (left + 59_999L) / 60_000L;
    }
}
