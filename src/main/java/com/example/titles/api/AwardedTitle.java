package com.example.titles.api;

import java.util.Optional;

/**
 * A resolved title. {@code priority} is in [1, 100]; a title may replace an active one only when
 * {@code incoming.priority() >= current.priority()}.
 *
 * <p>{@code comment} is an optional witty chat line about the event (LLM-generated, already sanitized
 * to safe MiniMessage). It may contain the literal token {@link #PLAYER_TOKEN}, which the chat
 * broadcaster replaces with the player's name. Null when absent.
 */
public record AwardedTitle(
    String miniMessageText,
    int priority,
    boolean fromLlm,
    String comment
) {
    public static final int MIN_PRIORITY = 1;
    public static final int MAX_PRIORITY = 100;
    /** Placeholder for the player's name inside {@link #comment()}. */
    public static final String PLAYER_TOKEN = "<player>";

    public AwardedTitle {
        if (miniMessageText == null || miniMessageText.isBlank()) {
            throw new IllegalArgumentException("miniMessageText must not be blank");
        }
        priority = Math.clamp(priority, MIN_PRIORITY, MAX_PRIORITY);
        if (comment != null && comment.isBlank()) {
            comment = null;
        }
    }

    /** A title with no chat comment. */
    public AwardedTitle(String miniMessageText, int priority, boolean fromLlm) {
        this(miniMessageText, priority, fromLlm, null);
    }

    public Optional<String> commentText() {
        return Optional.ofNullable(comment);
    }

    /** Same title/comment with a different priority. */
    public AwardedTitle withPriority(int newPriority) {
        return new AwardedTitle(miniMessageText, newPriority, fromLlm, comment);
    }

    /** Same priority/comment with different text. */
    public AwardedTitle withText(String newText) {
        return new AwardedTitle(newText, priority, fromLlm, comment);
    }
}
