package com.example.titles.api;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.minimessage.MiniMessage;

/**
 * Shared, Bukkit-free MiniMessage title helpers (AGENTS.md section 4). Uses Adventure only, never org.bukkit.
 */
public final class TitleText {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Pattern TAG = Pattern.compile("<[^<>]+>");
    /** Tags that never need a matching close tag. */
    private static final Set<String> SELF_CLOSING = Set.of(
        "br", "newline", "reset", "key", "keybind", "lang", "tr", "translate", "lang_or", "tr_or",
        "translate_or", "selector", "sel", "score", "nbt", "data", "sprite", "head", "pr", "pre");

    private TitleText() {}

    /** Visible (rendered) length: {@code MiniMessage.miniMessage().stripTags(text).length()}. */
    public static int visibleLength(String miniMessage) {
        return MM.stripTags(miniMessage).length();
    }

    /** Visible text with all MiniMessage tags removed. */
    public static String stripTags(String miniMessage) {
        return MM.stripTags(miniMessage);
    }

    /**
     * Truncates to at most {@code maxVisible} visible characters, preserving tags and appending close
     * tags for any still-open tags. {@code maxVisible <= 0} means unlimited (returns input unchanged).
     */
    public static String truncateVisible(String miniMessage, int maxVisible) {
        if (miniMessage == null || maxVisible <= 0 || visibleLength(miniMessage) <= maxVisible) {
            return miniMessage;
        }
        StringBuilder out = new StringBuilder(miniMessage.length());
        Deque<String> open = new ArrayDeque<>();
        Matcher m = TAG.matcher(miniMessage);
        int visible = 0;
        int i = 0;
        while (i < miniMessage.length() && visible < maxVisible) {
            char c = miniMessage.charAt(i);
            if (c == '\\' && i + 1 < miniMessage.length()
                && (miniMessage.charAt(i + 1) == '<' || miniMessage.charAt(i + 1) == '\\')) {
                out.append(c).append(miniMessage.charAt(i + 1));
                visible++;
                i += 2;
                continue;
            }
            if (c == '<' && m.find(i) && m.start() == i && MM.stripTags(m.group()).isEmpty()) {
                String tag = m.group();
                out.append(tag);
                trackTag(tag, open);
                i = m.end();
                continue;
            }
            out.append(c);
            visible++;
            i++;
        }
        while (!open.isEmpty()) {
            out.append("</").append(open.pop()).append('>');
        }
        return out.toString();
    }

    private static void trackTag(String tag, Deque<String> open) {
        String body = tag.substring(1, tag.length() - 1);
        if (body.endsWith("/")) {
            return; // explicit self-closing <tag/>
        }
        if (body.startsWith("/")) {
            String name = tagName(body.substring(1));
            open.removeFirstOccurrence(name);
            return;
        }
        if (body.startsWith("!")) {
            return; // negated decoration; leave unclosed (MiniMessage auto-closes at end)
        }
        String name = tagName(body);
        if (name.equals("reset")) {
            open.clear();
        } else if (!SELF_CLOSING.contains(name)) {
            open.push(name);
        }
    }

    private static String tagName(String body) {
        int colon = body.indexOf(':');
        return (colon >= 0 ? body.substring(0, colon) : body).trim().toLowerCase(Locale.ROOT);
    }
}
