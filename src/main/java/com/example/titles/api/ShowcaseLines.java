package com.example.titles.api;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Bukkit-free composition of the active and pinned title lines. */
public final class ShowcaseLines {
    private ShowcaseLines() {}

    public static List<String> compose(String activeTitle, List<String> pinned, int maxLines) {
        int limit = Math.max(1, maxLines);
        List<String> lines = new ArrayList<>(limit);
        Set<String> visibleTitles = new HashSet<>();
        add(activeTitle, lines, visibleTitles, limit);
        if (pinned != null) {
            for (String title : pinned) {
                if (lines.size() >= limit) {
                    break;
                }
                add(title, lines, visibleTitles, limit);
            }
        }
        return List.copyOf(lines);
    }

    private static void add(String title, List<String> lines, Set<String> visibleTitles, int limit) {
        if (title == null || title.isBlank() || lines.size() >= limit) {
            return;
        }
        String visible = TitleText.stripTags(title).trim().toLowerCase(Locale.ROOT);
        if (visible.isBlank() || !visibleTitles.add(visible)) {
            return;
        }
        lines.add(title);
    }
}
