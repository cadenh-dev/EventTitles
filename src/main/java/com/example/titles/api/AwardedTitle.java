package com.example.titles.api;

/**
 * A resolved title. {@code priority} is in [1, 100]; a title may replace an active one only when
 * {@code incoming.priority() >= current.priority()}.
 */
public record AwardedTitle(
    String miniMessageText,
    int priority,
    boolean fromLlm
) {
    public static final int MIN_PRIORITY = 1;
    public static final int MAX_PRIORITY = 100;

    public AwardedTitle {
        if (miniMessageText == null || miniMessageText.isBlank()) {
            throw new IllegalArgumentException("miniMessageText must not be blank");
        }
        priority = Math.clamp(priority, MIN_PRIORITY, MAX_PRIORITY);
    }
}
