package com.example.titles.api;

/** Bukkit-free view of the {@code tab_list:} config section (titles in the player list). */
public record TabListSettings(
    boolean enabled,
    String format,
    int maxTitleLength
) {
    /** Placeholders available in {@link #format()}. */
    public static final String TITLE_TOKEN = "<title>";
    public static final String PLAYER_TOKEN = "<player>";
    public static final String DEFAULT_FORMAT = "<white><player></white> <dark_gray>|</dark_gray> <title>";

    public TabListSettings {
        if (format == null || !format.contains(TITLE_TOKEN) || !format.contains(PLAYER_TOKEN)) {
            format = DEFAULT_FORMAT;
        }
    }

    public static TabListSettings defaults() {
        return new TabListSettings(true, DEFAULT_FORMAT, 24);
    }
}
