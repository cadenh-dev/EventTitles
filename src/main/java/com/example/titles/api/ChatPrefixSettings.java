package com.example.titles.api;

import java.util.Locale;
import java.util.Optional;

/** Bukkit-free view of the {@code chat_prefix:} config section (titles on players' own chat messages). */
public record ChatPrefixSettings(
    boolean enabled,
    Mode mode,
    String format,
    int maxTitleLength
) {
    public enum Mode {
        /**
         * Replace the whole chat line with {@link #format()} ({@code <title>}, {@code <player>},
         * {@code <message>}). Players with no title keep the normal chat format.
         */
        REPLACE,
        /**
         * Put {@link #format()} ({@code <title>} only) in front of whatever line the server or another
         * chat plugin already renders. Use this alongside EssentialsChat, LuckPerms chat formats, etc.
         */
        PREFIX;

        public static Optional<Mode> fromConfig(String value) {
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

    public static final String TITLE_TOKEN = "<title>";
    public static final String PLAYER_TOKEN = "<player>";
    public static final String MESSAGE_TOKEN = "<message>";
    public static final String DEFAULT_REPLACE_FORMAT =
        "<title> <white><player></white><dark_gray>:</dark_gray> <message>";
    public static final String DEFAULT_PREFIX_FORMAT = "<dark_gray>[</dark_gray><title><dark_gray>]</dark_gray> ";

    public ChatPrefixSettings {
        if (mode == null) {
            mode = Mode.REPLACE;
        }
        if (!isValidFormat(mode, format)) {
            format = mode == Mode.REPLACE ? DEFAULT_REPLACE_FORMAT : DEFAULT_PREFIX_FORMAT;
        }
    }

    /**
     * REPLACE needs title, player and message. PREFIX needs title and must not contain player/message
     * (the existing line already has them, so they would print twice or as literal text).
     */
    public static boolean isValidFormat(Mode mode, String format) {
        if (format == null || !format.contains(TITLE_TOKEN)) {
            return false;
        }
        boolean hasRest = format.contains(PLAYER_TOKEN) || format.contains(MESSAGE_TOKEN);
        return mode == Mode.REPLACE
            ? format.contains(PLAYER_TOKEN) && format.contains(MESSAGE_TOKEN)
            : !hasRest;
    }

    public static ChatPrefixSettings defaults() {
        return new ChatPrefixSettings(true, Mode.REPLACE, DEFAULT_REPLACE_FORMAT, 24);
    }
}
