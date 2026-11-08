package com.example.titles.display;

import com.example.titles.api.TitleDisplayService;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/** Restores mounted title displays across player and chunk lifecycle events. */
public final class DisplayLifecycleListener implements Listener {
    private final Plugin plugin;
    private final TitleDisplayService displays;
    private final NamespacedKey displayOwnerKey;
    private final Set<UUID> refreshPending = new HashSet<>();
    private final Set<UUID> teleporting = new HashSet<>();

    public DisplayLifecycleListener(Plugin plugin, TitleDisplayService displays) {
        this.plugin = plugin;
        this.displays = displays;
        this.displayOwnerKey = new NamespacedKey(plugin, "display_owner");
    }

    private void scheduleRefresh(Player player) {
        UUID id = player.getUniqueId();
        if (!refreshPending.add(id)) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            teleporting.remove(id);
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
        Player player = event.getPlayer();
        teleporting.add(player.getUniqueId());
        displays.removeDisplay(player);
        scheduleRefresh(player);
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
    public void onToggleSneak(PlayerToggleSneakEvent event) {
        displays.updateSneakState(event.getPlayer(), event.isSneaking());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDismount(EntityDismountEvent event) {
        Entity dismounted = event.getEntity();
        Entity vehicle = event.getDismounted();
        if (isOwnedDisplay(dismounted) && vehicle instanceof Player player && player.isOnline()
            && !player.isDead() && !teleporting.contains(player.getUniqueId())) {
            scheduleRefresh(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (isOwnedDisplay(entity) && !(entity.getVehicle() instanceof Player player && player.isOnline())) {
                entity.remove();
            }
        }
    }

    private boolean isOwnedDisplay(Entity entity) {
        return entity instanceof TextDisplay
            && (displays instanceof PaperTitleDisplayManager manager
                ? manager.isOwnedDisplay(entity)
                : entity.getPersistentDataContainer().has(displayOwnerKey, PersistentDataType.STRING));
    }
}
