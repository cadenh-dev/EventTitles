package com.example.titles.api;

/** Bukkit-free settings for the player title showcase. */
public record ShowcaseSettings(boolean enabled, int maxTitles) {
    public ShowcaseSettings {
        maxTitles = Math.clamp(maxTitles, 1, 5);
    }

    public static ShowcaseSettings defaults() {
        return new ShowcaseSettings(true, 5);
    }
}
