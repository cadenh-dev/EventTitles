package com.example.titles.api;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Bukkit-free per-player log of titles a player actually received (newest first). */
public interface TitleHistory {
    /**
     * One awarded title. {@code title} is MiniMessage (already sanitized/truncated); {@code reason} is the
     * plain-text hover reason (may be null); {@code source} is a short label such as "AI", "Static", "Admin".
     */
    record Entry(String title, int priority, String source, String reason, long awardedAtMillis) {}

    /** Appends asynchronously; never blocks the caller and never throws. */
    void record(UUID playerId, Entry entry);

    /** Loads a player's history, newest first; completes with an empty list on any error. */
    CompletableFuture<List<Entry>> load(UUID playerId);

    /** Short source label for an awarded title. */
    static String sourceOf(AwardedTitle title) {
        if (title.fromLlm()) {
            return "AI";
        }
        String reason = title.reason();
        return reason != null && reason.contains("Source: Admin") ? "Admin" : "Static";
    }
}
