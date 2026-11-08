package com.example.titles.api;

import java.util.Locale;
import java.util.Optional;

/** Bukkit-free view of the {@code chat:} config section (LLM commentary broadcasts). */
public record ChatSettings(
    boolean enabled,
    Audience audience,
    int nearbyRadius,
    String format,
    int cooldownSeconds
) {
    /** Who receives a commentary line. */
    public enum Audience {
        /** Everyone online. */
        SERVER,
        /** Everyone in the player's world. */
        WORLD,
        /** Players within {@code nearby_radius} blocks (same world). */
        NEARBY,
        /** Only the player the event happened to. */
        PLAYER;

        public static Optional<Audience> fromConfig(String value) {
            if (value == null) {
                return Optional.empty();
            }
            try {
                return Optional.of(valueOf(value.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }
    }

    /** Placeholder for the comment inside {@link #format()}. */
    public static final String COMMENT_TOKEN = "<comment>";
    public static final String DEFAULT_FORMAT =
        "<dark_gray>[<gold>EventTitles</gold>]</dark_gray> <gray><comment></gray>";

    public ChatSettings {
        if (audience == null) {
            audience = Audience.SERVER;
        }
        nearbyRadius = Math.max(1, nearbyRadius);
        cooldownSeconds = Math.max(0, cooldownSeconds);
        if (format == null || !format.contains(COMMENT_TOKEN)) {
            format = DEFAULT_FORMAT;
        }
    }

    public static ChatSettings defaults() {
        return new ChatSettings(true, Audience.SERVER, 64, DEFAULT_FORMAT, 15);
    }
}
