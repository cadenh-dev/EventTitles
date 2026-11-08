package com.example.titles.display;

import com.example.titles.api.AwardedTitle;
import com.example.titles.api.TitleDisplayService;
import com.example.titles.api.TitleText;
import com.example.titles.api.TitlesSettings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import net.kyori.adventure.text.minimessage.MiniMessage;
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
    /** Client-side interpolation, in ticks, for each per-tick follow teleport. */
    private static final int FOLLOW_INTERPOLATION_TICKS = 1;
    /** Movements longer than this (blocks) are real teleports: respawn instead of gliding across. */
    private static final double SNAP_DISTANCE = 8.0;
    private static final double SNAP_DISTANCE_SQUARED = SNAP_DISTANCE * SNAP_DISTANCE;
    private static final double MOVE_EPSILON_SQUARED = 1.0e-6;

    private final Plugin plugin;
    private final Supplier<TitlesSettings> settings;
    private final NamespacedKey activeTitleKey;
    private final NamespacedKey activePriorityKey;
    private final NamespacedKey displayOwnerKey;
    private final Map<UUID, TextDisplay> displays = new HashMap<>();
    private BukkitTask followTask;

    public PaperTitleDisplayManager(Plugin plugin, Supplier<TitlesSettings> settings) {
        this.plugin = plugin;
        this.settings = settings;
        this.activeTitleKey = new NamespacedKey(plugin, "active_title");
        this.activePriorityKey = new NamespacedKey(plugin, "active_priority");
        this.displayOwnerKey = new NamespacedKey(plugin, "display_owner");
    }

    @Override
    public void setPlayerTitle(Player player, AwardedTitle title) {
        Integer current = player.getPersistentDataContainer().get(activePriorityKey, PersistentDataType.INTEGER);
        if (title.priority() < (current == null ? 0 : current)) {
            return;
        }
        String text = TitleText.truncateVisible(title.miniMessageText(), settings.get().maxTitleLength());
        player.getPersistentDataContainer().set(activeTitleKey, PersistentDataType.STRING, text);
        player.getPersistentDataContainer().set(activePriorityKey, PersistentDataType.INTEGER, title.priority());

        TextDisplay display = displays.get(player.getUniqueId());
        if (display != null && display.isValid() && display.getWorld().equals(player.getWorld())) {
            display.text(MiniMessage.miniMessage().deserialize(text));
        } else {
            refreshDisplay(player);
        }
    }

    @Override
    public void clearPlayerTitle(Player player) {
        player.getPersistentDataContainer().remove(activeTitleKey);
        player.getPersistentDataContainer().remove(activePriorityKey);
        removeDisplay(player);
    }

    @Override
    public void refreshDisplay(Player player) {
        removeDisplay(player); // always first: guarantees at most one display per player
        String text = player.getPersistentDataContainer().get(activeTitleKey, PersistentDataType.STRING);
        if (!player.isOnline() || player.isDead() || text == null
            || player.getGameMode() == GameMode.SPECTATOR) { // spectators are invisible; don't out them
            return;
        }
        String ownerId = player.getUniqueId().toString();
        TextDisplay display = player.getWorld().spawn(anchor(player), TextDisplay.class, d -> {
            d.text(MiniMessage.miniMessage().deserialize(text));
            d.setBillboard(Display.Billboard.CENTER);
            d.setSeeThrough(false);
            d.setDefaultBackground(false);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.setTransformation(new Transformation(new Vector3f(0f, 0.4f, 0f), new AxisAngle4f(),
                new Vector3f(1f, 1f, 1f), new AxisAngle4f()));
            d.setTeleportDuration(FOLLOW_INTERPOLATION_TICKS);
            d.setPersistent(false);
            d.setTextOpacity((byte) (player.isSneaking() ? 60 : 255));
            d.getPersistentDataContainer().set(displayOwnerKey, PersistentDataType.STRING, ownerId);
        });
        displays.put(player.getUniqueId(), display);
        ensureFollowTask();
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
        if (display != null && display.isValid()) {
            display.remove();
        }
        stopFollowTaskIfIdle();
    }

    @Override
    public void cleanupAll() {
        for (TextDisplay display : displays.values()) {
            if (display.isValid()) {
                display.remove();
            }
        }
        displays.clear();
        for (var world : Bukkit.getWorlds()) {
            for (TextDisplay display : world.getEntitiesByClass(TextDisplay.class)) {
                if (isOwnedDisplay(display)) {
                    display.remove();
                }
            }
        }
        stopFollowTaskIfIdle();
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
                if (display.isValid()) {
                    display.remove();
                }
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
