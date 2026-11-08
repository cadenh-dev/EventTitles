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

    /** Style-only tags an LLM may use; anything else (click, hover, insert, selector, nbt...) is dropped. */
    private static final Set<String> SAFE_TAGS = buildSafeTags();

    private TitleText() {}

    private static Set<String> buildSafeTags() {
        Set<String> tags = new java.util.HashSet<>(net.kyori.adventure.text.format.NamedTextColor.NAMES.keys());
        tags.addAll(Set.of("grey", "dark_grey", "color", "colour", "c", "gradient", "rainbow", "transition",
            "bold", "b", "italic", "i", "em", "underlined", "u", "strikethrough", "st", "obfuscated", "obf",
            "reset", "shadow"));
        return Set.copyOf(tags);
    }

    /**
     * Removes every MiniMessage tag that is not a pure style tag (named/hex colours, gradient, rainbow,
     * transition, decorations, reset, shadow), so untrusted LLM output can never inject click/hover
     * actions, selectors, NBT lookups, translations or keybinds. Visible text is kept untouched and
     * escaped {@code \<} sequences are preserved. When {@code allowPlayerToken} is true the
     * {@link AwardedTitle#PLAYER_TOKEN} placeholder is kept as well.
     */
    public static String sanitizeLlm(String miniMessage, boolean allowPlayerToken) {
        if (miniMessage == null) {
            return null;
        }
        Matcher m = TAG.matcher(miniMessage);
        StringBuilder out = new StringBuilder(miniMessage.length());
        int last = 0;
        while (m.find()) {
            out.append(miniMessage, last, m.start());
            String tag = m.group();
            boolean escaped = m.start() > 0 && miniMessage.charAt(m.start() - 1) == '\\';
            if (escaped || isSafeTag(tag, allowPlayerToken)) {
                out.append(tag);
            }
            last = m.end();
        }
        out.append(miniMessage, last, miniMessage.length());
        return out.toString();
    }

    private static boolean isSafeTag(String tag, boolean allowPlayerToken) {
        String body = tag.substring(1, tag.length() - 1).trim();
        if (body.startsWith("/")) {
            body = body.substring(1);
        }
        if (body.startsWith("!")) {
            body = body.substring(1);
        }
        if (body.startsWith("#")) {
            return body.matches("#[0-9a-fA-F]{6}");
        }
        String name = tagName(body);
        if (allowPlayerToken && name.equals("player") && body.equalsIgnoreCase("player")) {
            return true;
        }
        return SAFE_TAGS.contains(name);
    }

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
