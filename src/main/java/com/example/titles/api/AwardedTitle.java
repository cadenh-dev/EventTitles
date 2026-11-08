package com.example.titles.api;

import java.util.Optional;

/**
 * A resolved title. {@code priority} is in [1, 100]; a title may replace an active one only when
 * {@code incoming.priority() >= current.priority()}.
 *
 * <p>{@code comment} is an optional witty chat line about the event (LLM-generated, already sanitized
 * to safe MiniMessage). It may contain the literal token {@link #PLAYER_TOKEN}, which the chat
 * broadcaster replaces with the player's name. Null when absent.
 *
 * <p>{@code reason} is an optional PLAIN-TEXT (never parsed as MiniMessage) explanation of why the
 * title was awarded, one fact per line, shown as hover text in chat. Null when absent.
 */
public record AwardedTitle(
    String miniMessageText,
    int priority,
    boolean fromLlm,
    String comment,
    String reason
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
        if (reason != null && reason.isBlank()) {
            reason = null;
        }
    }

    /** A title with no chat comment and no reason. */
    public AwardedTitle(String miniMessageText, int priority, boolean fromLlm) {
        this(miniMessageText, priority, fromLlm, null, null);
    }

    /** A title with a chat comment but no reason. */
    public AwardedTitle(String miniMessageText, int priority, boolean fromLlm, String comment) {
        this(miniMessageText, priority, fromLlm, comment, null);
    }

    public Optional<String> commentText() {
        return Optional.ofNullable(comment);
    }

    public Optional<String> reasonText() {
        return Optional.ofNullable(reason);
    }

    /** Same title with a different priority. */
    public AwardedTitle withPriority(int newPriority) {
        return new AwardedTitle(miniMessageText, newPriority, fromLlm, comment, reason);
    }

    /** Same title with different text. */
    public AwardedTitle withText(String newText) {
        return new AwardedTitle(newText, priority, fromLlm, comment, reason);
    }

    /** Same title with a different reason. */
    public AwardedTitle withReason(String newReason) {
        return new AwardedTitle(miniMessageText, priority, fromLlm, comment, newReason);
    }
}
