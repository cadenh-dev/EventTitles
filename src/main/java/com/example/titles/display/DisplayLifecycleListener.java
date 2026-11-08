package com.example.titles.display;

import com.example.titles.api.TitleDisplayService;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Keeps each player's floating title display in sync with player lifecycle events and purges
 * stray displays. Displays follow players via {@link PaperTitleDisplayManager}'s tick loop, so
 * nothing here ever blocks or alters a teleport.
 */
public final class DisplayLifecycleListener implements Listener {
    /** Teleports shorter than this are left to the follow loop's smooth interpolation. */
    private static final double SNAP_DISTANCE_SQUARED = 8.0 * 8.0;

    private final Plugin plugin;
    private final TitleDisplayService displays;
    private final NamespacedKey displayOwnerKey;
    private final Set<UUID> refreshPending = new HashSet<>();

    public DisplayLifecycleListener(Plugin plugin, TitleDisplayService displays) {
        this.plugin = plugin;
        this.displays = displays;
        this.displayOwnerKey = new NamespacedKey(plugin, "display_owner");
    }

    /** Rebuilds the display from PDC two ticks later (after the move/respawn has settled). */
    private void scheduleRefresh(Player player) {
        UUID id = player.getUniqueId();
        if (!refreshPending.add(id)) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                if (player.isOnline() && !player.isDead()) {
                    displays.refreshDisplay(player);
                }
            } finally {
                refreshPending.remove(id);
            }
        }, 2L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        scheduleRefresh(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        scheduleRefresh(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        boolean longJump = to == null || !from.getWorld().equals(to.getWorld())
            || from.distanceSquared(to) > SNAP_DISTANCE_SQUARED;
        if (longJump) {
            // Avoid the display visibly gliding across the map (or lingering in the old world).
            displays.removeDisplay(event.getPlayer());
            scheduleRefresh(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        displays.removeDisplay(event.getPlayer());
        scheduleRefresh(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        displays.removeDisplay(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        displays.removeDisplay(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        // Refresh reads the new game mode two ticks later: hides the title in spectator, restores it after.
        scheduleRefresh(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onToggleSneak(PlayerToggleSneakEvent event) {
        displays.updateSneakState(event.getPlayer(), event.isSneaking());
    }

    /** Purges displays left behind by a crash: owned by us but not tracked by the manager. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (isStray(entity)) {
                entity.remove();
            }
        }
    }

    private boolean isStray(Entity entity) {
        if (!(entity instanceof TextDisplay)) {
            return false;
        }
        if (displays instanceof PaperTitleDisplayManager manager) {
            return manager.isOwnedDisplay(entity) && !manager.isTracked(entity);
        }
        return entity.getPersistentDataContainer().has(displayOwnerKey, PersistentDataType.STRING);
    }
}
