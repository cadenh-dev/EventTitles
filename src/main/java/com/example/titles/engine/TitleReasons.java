package com.example.titles.engine;

import com.example.titles.api.AwardedTitle;
import com.example.titles.api.EventContext;
import com.example.titles.api.LlmProvider;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds the plain-text "why did I get this title" explanation shown on hover. Bukkit-free and
 * deterministic: everything is derived from the {@link EventContext} snapshot and the rule/LLM that
 * produced the title, so it costs no extra LLM tokens.
 */
public final class TitleReasons {
    private TitleReasons() {}

    /** Reason for a title chosen from a static rule. */
    public static String forStatic(EventContext ctx, String trigger, AwardedTitle title) {
        return join("Why: " + trigger, "Event: " + describeEvent(ctx),
            "Source: Static title · Priority " + title.priority());
    }

    /** Reason for an LLM-written title. {@code staticTrigger} may be null when no static rule matched. */
    public static String forLlm(EventContext ctx, LlmProvider provider, String model, String staticTrigger,
                                AwardedTitle title) {
        String why = staticTrigger != null
            ? "Why: " + staticTrigger + " (custom title by " + providerName(provider) + ")"
            : "Why: " + providerName(provider) + " wrote this one about what just happened";
        return join(why, "Event: " + describeEvent(ctx),
            "Source: " + providerName(provider) + " (" + model + ") · Priority " + title.priority());
    }

    /** Reason for the generic fallback title. */
    public static String forDefault(EventContext ctx, AwardedTitle title) {
        return join("Why: Nothing more specific fit this event", "Event: " + describeEvent(ctx),
            "Source: Default title · Priority " + title.priority());
    }

    /** One-line human description of the event, e.g. "Killed by Creeper · Deep Dark · 3 in a row". */
    public static String describeEvent(EventContext ctx) {
        Map<String, String> meta = metadata(ctx.extraMetadata());
        String subject = humanize(ctx.primarySubject());
        String what = switch (ctx.category()) {
            case DEATH_MOB -> "PLAYER".equalsIgnoreCase(ctx.primarySubject()) && meta.containsKey("killer")
                ? "Killed by " + meta.get("killer") : "Killed by " + subject;
            case DEATH_ENV -> "Died to " + subject.toLowerCase(Locale.ROOT);
            case BOSS_KILL -> "Killed the " + subject;
            case PVP_KILL -> "Killed " + ctx.primarySubject(); // victim name, keep exact casing
            case CLUTCH_SURVIVAL -> "Survived a hit from " + subject
                + (meta.containsKey("health_after") ? " on " + meta.get("health_after") + " HP" : "");
            case MINING -> "Mined " + subject;
            case SHENANIGANS -> "ADMIN_TEST".equalsIgnoreCase(ctx.primarySubject()) ? "Admin LLM test" : "Ate " + subject;
        };
        List<String> parts = new ArrayList<>();
        parts.add(what);
        if (ctx.biome() != null && !ctx.biome().isBlank()) {
            parts.add(humanize(ctx.biome()));
        }
        if (ctx.streakCount() > 1) {
            parts.add(ctx.streakCount() + " in a row");
        }
        if ("NONE".equalsIgnoreCase(ctx.armorSummary())) {
            parts.add("no armor");
        }
        return String.join(" · ", parts);
    }

    /** "ENDER_DRAGON" → "Ender Dragon". */
    static String humanize(String id) {
        if (id == null || id.isBlank()) {
            return "something";
        }
        StringBuilder out = new StringBuilder();
        for (String word : id.toLowerCase(Locale.ROOT).split("_")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }

    private static String providerName(LlmProvider provider) {
        return switch (provider) {
            case DEEPSEEK -> "DeepSeek";
            case GROK -> "Grok";
        };
    }

    private static String join(String... lines) {
        return String.join("\n", lines);
    }

    private static Map<String, String> metadata(String raw) {
        Map<String, String> values = new HashMap<>();
        if (raw == null) {
            return values;
        }
        for (String part : raw.split(";")) {
            int i = part.indexOf('=');
            if (i > 0) {
                values.put(part.substring(0, i).trim(), part.substring(i + 1).trim());
            }
        }
        return values;
    }
}
