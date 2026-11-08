package com.example.titles.display;

import com.example.titles.api.AwardedTitle;
import com.example.titles.api.ShowcaseLines;
import com.example.titles.api.TabListSettings;
import com.example.titles.api.TitleDisplayService;
import com.example.titles.api.TitlePriority;
import com.example.titles.api.TitleHistory;
import com.example.titles.api.TitleText;
import com.example.titles.api.TitlesSettings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Owns the player title PDC state and its floating display entity.
 *
 * <p>The display is deliberately NOT mounted with {@code player.addPassenger}: Paper refuses
 * cross-world {@code Player#teleport} calls for players that have passengers, before
 * {@code PlayerTeleportEvent} fires, which would silently break other plugins' teleports. Instead an
 * unmounted {@link TextDisplay} is moved to the player's head every tick, and the client smooths the
 * movement ({@link Display#setTeleportDuration}).
 */
public final class PaperTitleDisplayManager implements TitleDisplayService {
    /** Movements longer than this (blocks) are real teleports: respawn instead of gliding across. */
    private static final double SNAP_DISTANCE = 8.0;
    private static final double SNAP_DISTANCE_SQUARED = SNAP_DISTANCE * SNAP_DISTANCE;
    private static final double MOVE_EPSILON_SQUARED = 1.0e-6;

    private final Plugin plugin;
    private final Supplier<TitlesSettings> settings;
    private final NamespacedKey activeTitleKey;
    private final NamespacedKey activePriorityKey;
    private final NamespacedKey activeReasonKey;
    private final NamespacedKey activeSinceKey;
    private final NamespacedKey activeNoDecayKey;
    private final NamespacedKey displayOwnerKey;
    private final NamespacedKey showcaseKey;
    private final Map<UUID, TextDisplay> displays = new HashMap<>();
    /** Players whose tab-list name this plugin has set (so only those are reset). */
    private final Set<UUID> tabNamed = new HashSet<>();
    /** Thread-safe mirror of each online player's PDC title, for async readers (chat rendering). */
    private final Map<UUID, AwardedTitle> titleCache = new ConcurrentHashMap<>();
    private BukkitTask followTask;
    private final TitleHistory history;

    public PaperTitleDisplayManager(Plugin plugin, Supplier<TitlesSettings> settings) {
        this(plugin, settings, null);
    }

    /** {@code history} may be null (history disabled). */
    public PaperTitleDisplayManager(Plugin plugin, Supplier<TitlesSettings> settings, TitleHistory history) {
        this.plugin = plugin;
        this.settings = settings;
        this.history = history;
        this.activeTitleKey = new NamespacedKey(plugin, "active_title");
        this.activePriorityKey = new NamespacedKey(plugin, "active_priority");
        this.activeReasonKey = new NamespacedKey(plugin, "active_reason");
        this.activeSinceKey = new NamespacedKey(plugin, "active_since");
        this.activeNoDecayKey = new NamespacedKey(plugin, "active_no_decay");
        this.displayOwnerKey = new NamespacedKey(plugin, "display_owner");
        this.showcaseKey = new NamespacedKey(plugin, "showcase");
    }

    @Override
    public void setPlayerTitle(Player player, AwardedTitle title, boolean decays) {
        // Protection of the current title wears off over settings.protection_decay_minutes.
        if (!TitlePriority.canReplace(title.priority(), effectivePriority(player))) {
            return;
        }
        String text = TitleText.truncateVisible(title.miniMessageText(), settings.get().maxTitleLength());
        player.getPersistentDataContainer().set(activeTitleKey, PersistentDataType.STRING, text);
        player.getPersistentDataContainer().set(activePriorityKey, PersistentDataType.INTEGER, title.priority());
        player.getPersistentDataContainer().set(activeSinceKey, PersistentDataType.LONG, System.currentTimeMillis());
        if (decays) {
            player.getPersistentDataContainer().remove(activeNoDecayKey);
        } else {
            player.getPersistentDataContainer().set(activeNoDecayKey, PersistentDataType.BYTE, (byte) 1);
        }
        if (title.reason() != null) {
            player.getPersistentDataContainer().set(activeReasonKey, PersistentDataType.STRING, title.reason());
        } else {
            player.getPersistentDataContainer().remove(activeReasonKey);
        }
        titleCache.put(player.getUniqueId(), new AwardedTitle(text, title.priority(), title.fromLlm(), null,
            title.reason()));
        if (history != null) {
            history.record(player.getUniqueId(), new TitleHistory.Entry(text, title.priority(),
                TitleHistory.sourceOf(title), title.reason(), System.currentTimeMillis()));
        }

        TextDisplay display = displays.get(player.getUniqueId());
        if (display != null && display.isValid() && display.getWorld().equals(player.getWorld())) {
            display.text(displayText(player, text));
            updateTabName(player, text);
        } else {
            refreshDisplay(player); // also updates the tab name
        }
        if (settings.get().announceToSelf()) {
            // The owner usually can't see their own title (show_to_self: false), so tell them in chat.
            player.sendMessage(MiniMessage.miniMessage().deserialize(
                "<gray>New title: <title> <dark_gray>(hover for why)</dark_gray>",
                Placeholder.component("title", TitleHover.titleWithReason(text, title.reason()))));
        }
    }

    @Override
    public int effectivePriority(Player player) {
        var pdc = player.getPersistentDataContainer();
        Integer priority = pdc.get(activePriorityKey, PersistentDataType.INTEGER);
        if (priority == null || !pdc.has(activeTitleKey, PersistentDataType.STRING)) {
            return 0;
        }
        Long since = pdc.get(activeSinceKey, PersistentDataType.LONG);
        return TitlePriority.effective(priority, since == null ? 0 : since, System.currentTimeMillis(),
            settings.get().protectionDecayMinutes(), !pdc.has(activeNoDecayKey, PersistentDataType.BYTE));
    }

    @Override
    public long minutesUntilReplaceable(Player player) {
        var pdc = player.getPersistentDataContainer();
        if (!pdc.has(activeTitleKey, PersistentDataType.STRING)) {
            return 0;
        }
        Long since = pdc.get(activeSinceKey, PersistentDataType.LONG);
        return TitlePriority.minutesUntilOpen(since == null ? 0 : since, System.currentTimeMillis(),
            settings.get().protectionDecayMinutes(), !pdc.has(activeNoDecayKey, PersistentDataType.BYTE));
    }

    @Override
    public Optional<AwardedTitle> cachedTitle(UUID playerId) {
        return Optional.ofNullable(titleCache.get(playerId));
    }

    @Override
    public void forgetPlayer(Player player) {
        titleCache.remove(player.getUniqueId());
        tabNamed.remove(player.getUniqueId());
    }

    @Override
    public Optional<AwardedTitle> currentTitle(Player player) {
        var pdc = player.getPersistentDataContainer();
        String text = pdc.get(activeTitleKey, PersistentDataType.STRING);
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        Integer priority = pdc.get(activePriorityKey, PersistentDataType.INTEGER);
        return Optional.of(new AwardedTitle(text, priority == null ? AwardedTitle.MIN_PRIORITY : priority, false,
            null, pdc.get(activeReasonKey, PersistentDataType.STRING)));
    }

    @Override
    public void clearPlayerTitle(Player player) {
        player.getPersistentDataContainer().remove(activeTitleKey);
        player.getPersistentDataContainer().remove(activePriorityKey);
        player.getPersistentDataContainer().remove(activeReasonKey);
        player.getPersistentDataContainer().remove(activeSinceKey);
        player.getPersistentDataContainer().remove(activeNoDecayKey);
        titleCache.remove(player.getUniqueId());
        removeDisplay(player);
        updateTabName(player, null);
    }

    @Override
    public List<String> showcase(Player player) {
        String json = player.getPersistentDataContainer().get(showcaseKey, PersistentDataType.STRING);
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonArray()) {
                return List.of();
            }
            List<String> titles = new ArrayList<>();
            for (JsonElement value : parsed.getAsJsonArray()) {
                if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                    titles.add(value.getAsString());
                }
            }
            return List.copyOf(titles);
        } catch (RuntimeException malformed) {
            return List.of();
        }
    }

    @Override
    public void setShowcase(Player player, List<String> titles) {
        List<String> capped = titles == null ? List.of() : titles.stream().filter(java.util.Objects::nonNull)
            .limit(5).map(title -> TitleText.truncateVisible(title, settings.get().maxTitleLength())).toList();
        if (capped.isEmpty()) {
            player.getPersistentDataContainer().remove(showcaseKey);
        } else {
            player.getPersistentDataContainer().set(showcaseKey, PersistentDataType.STRING,
                new Gson().toJson(capped));
        }
        refreshDisplay(player);
    }

    @Override
    public void refreshDisplay(Player player) {
        removeDisplay(player); // always first: guarantees at most one display per player
        String text = player.getPersistentDataContainer().get(activeTitleKey, PersistentDataType.STRING);
        if (player.isOnline()) {
            updateTabName(player, text); // tab list keeps the title even while dead or spectating
            currentTitle(player).ifPresentOrElse(t -> titleCache.put(player.getUniqueId(), t),
                () -> titleCache.remove(player.getUniqueId()));
        }
        // Pins only count while the showcase feature is on; otherwise behave exactly as before.
        List<String> pinned = settings.get().showcase().enabled() ? showcase(player) : List.of();
        if (!player.isOnline() || player.isDead() || (text == null && pinned.isEmpty())
            || player.getGameMode() == GameMode.SPECTATOR) { // spectators are invisible; don't out them
            return;
        }
        String ownerId = player.getUniqueId().toString();
        TitlesSettings config = settings.get();
        float offsetY = (float) config.displayOffsetY();
        TextDisplay display = player.getWorld().spawn(anchor(player), TextDisplay.class, d -> {
            d.text(displayText(player, text));
            d.setBillboard(Display.Billboard.CENTER);
            d.setSeeThrough(false);
            d.setDefaultBackground(false);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.setTransformation(new Transformation(new Vector3f(0f, offsetY, 0f), new AxisAngle4f(),
                new Vector3f(1f, 1f, 1f), new AxisAngle4f()));
            d.setTeleportDuration(config.displayInterpolationTicks()); // match remote-player smoothing
            d.setPersistent(false);
            d.setTextOpacity((byte) (player.isSneaking() ? 60 : 255));
            d.getPersistentDataContainer().set(displayOwnerKey, PersistentDataType.STRING, ownerId);
        });
        if (!config.showToSelf()) {
            // Like the vanilla nametag: the owner never sees their own title (any camera mode).
            player.hideEntity(plugin, display);
        }
        displays.put(player.getUniqueId(), display);
        ensureFollowTask();
    }

    private Component displayText(Player player, String activeTitleText) {
        boolean enabled = settings.get().showcase().enabled();
        List<String> lines = ShowcaseLines.compose(activeTitleText, enabled ? showcase(player) : List.of(),
            enabled ? settings.get().showcase().maxTitles() : 1);
        return Component.join(JoinConfiguration.separator(Component.newline()), lines.stream()
            .map(MiniMessage.miniMessage()::deserialize).toList());
    }

    @Override
    public void updateSneakState(Player player, boolean sneaking) {
        TextDisplay display = displays.get(player.getUniqueId());
        if (display != null && display.isValid()) {
            display.setTextOpacity((byte) (sneaking ? 60 : 255));
        }
    }

    @Override
    public void removeDisplay(Player player) {
        TextDisplay display = displays.remove(player.getUniqueId());
        if (display != null) {
            despawn(player, display);
        }
        stopFollowTaskIfIdle();
    }

    /**
     * Removes the entity, then clears the owner's hide-entry for it. Paper keeps hidden-entity
     * entries per UUID until shown again; since every respawn is a new UUID, skipping this would
     * grow that set for the rest of the session. Showing an already-removed entity sends nothing.
     */
    private void despawn(Player owner, TextDisplay display) {
        if (display.isValid()) {
            display.remove();
        }
        if (owner != null && !owner.canSee(display)) {
            owner.showEntity(plugin, display);
        }
    }

    @Override
    public void cleanupAll() {
        for (var entry : displays.entrySet()) {
            despawn(Bukkit.getPlayer(entry.getKey()), entry.getValue());
        }
        displays.clear();
        for (var world : Bukkit.getWorlds()) {
            for (TextDisplay display : world.getEntitiesByClass(TextDisplay.class)) {
                if (isOwnedDisplay(display)) {
                    display.remove();
                }
            }
        }
        // Hand tab-list names back to vanilla (or other plugins) on disable/reload.
        for (UUID id : Set.copyOf(tabNamed)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                player.playerListName(null);
            }
        }
        tabNamed.clear();
        titleCache.clear();
        stopFollowTaskIfIdle();
    }

    /**
     * Shows the title next to the player's name in the tab list. {@code titleText == null} or a
     * disabled {@code tab_list} restores the default name, but only if this plugin set it, so other
     * plugins' tab-list names are never clobbered.
     */
    private void updateTabName(Player player, String titleText) {
        TabListSettings tab = settings.get().tabList();
        if (!tab.enabled() || titleText == null) {
            if (tabNamed.remove(player.getUniqueId())) {
                player.playerListName(null);
            }
            return;
        }
        try {
            Component title = MiniMessage.miniMessage().deserialize(
                TitleText.truncateVisible(titleText, tab.maxTitleLength()));
            player.playerListName(MiniMessage.miniMessage().deserialize(tab.format(),
                Placeholder.component("title", title), Placeholder.unparsed("player", player.getName())));
            tabNamed.add(player.getUniqueId());
        } catch (RuntimeException malformed) {
            plugin.getLogger().warning("Invalid tab_list.format; leaving the tab name unchanged.");
        }
    }

    /** True for any TextDisplay created by this plugin (tracked or stray). */
    public boolean isOwnedDisplay(Entity entity) {
        return entity instanceof TextDisplay display
            && display.getPersistentDataContainer().has(displayOwnerKey, PersistentDataType.STRING);
    }

    /** True if {@code entity} is a display this manager currently tracks for an online player. */
    public boolean isTracked(Entity entity) {
        return entity instanceof TextDisplay display && displays.containsValue(display);
    }

    /** Where the display sits: top of the player's current hitbox (shrinks when sneaking/swimming). */
    private static Location anchor(Player player) {
        return player.getLocation().add(0.0, player.getHeight(), 0.0);
    }

    private void tick() {
        List<Player> respawn = new ArrayList<>();
        var it = displays.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            TextDisplay display = entry.getValue();
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline() || player.isDead()) {
                despawn(player, display);
                it.remove();
                continue;
            }
            Location target = anchor(player);
            if (!display.isValid() || !display.getWorld().equals(target.getWorld())
                || display.getLocation().distanceSquared(target) > SNAP_DISTANCE_SQUARED) {
                respawn.add(player); // cross-world move, long teleport, or entity lost: rebuild in place
                continue;
            }
            if (display.getLocation().distanceSquared(target) > MOVE_EPSILON_SQUARED) {
                display.teleport(target);
            }
        }
        for (Player player : respawn) {
            refreshDisplay(player);
        }
        stopFollowTaskIfIdle();
    }

    private void ensureFollowTask() {
        if (followTask == null && plugin.isEnabled()) {
            followTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        }
    }

    private void stopFollowTaskIfIdle() {
        if (followTask != null && displays.isEmpty()) {
            followTask.cancel();
            followTask = null;
        }
    }
}
