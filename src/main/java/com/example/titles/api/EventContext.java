package com.example.titles.api;

import java.util.UUID;

/**
 * Immutable snapshot of main-thread Bukkit state, safe to hand to async workers.
 *
 * <p>All string fields are plain, non-null values. Conventions (shared by the listener that builds
 * contexts and the registry/LLM that consume them):
 * <ul>
 *   <li>{@code primarySubject}: upper-case Bukkit enum-style identifier of the thing involved, e.g.
 *       {@code "CREEPER"}, {@code "ENDER_DRAGON"}, {@code "FALL"}, {@code "LAVA"}, {@code "ANCIENT_DEBRIS"},
 *       {@code "ROTTEN_FLESH"}, or a victim player name for {@link EventCategory#PVP_KILL}.</li>
 *   <li>{@code biome}: upper-case biome key path, e.g. {@code "DEEP_DARK"}.</li>
 *   <li>{@code heldItem}: upper-case material, {@code "AIR"} when empty-handed.</li>
 *   <li>{@code armorSummary}: comma-separated worn materials, or {@code "NONE"}.</li>
 *   <li>{@code streakCount}: count (>= 1) of this category+subject within the streak window, including this one.</li>
 *   <li>{@code extraMetadata}: free-form {@code key=value;key=value} details (e.g. {@code "health=0.5;y=-58"}), may be empty.</li>
 * </ul>
 */
public record EventContext(
    UUID playerId,
    String playerName,
    EventCategory category,
    String primarySubject,
    String biome,
    String heldItem,
    String armorSummary,
    int streakCount,
    String extraMetadata
) {}
