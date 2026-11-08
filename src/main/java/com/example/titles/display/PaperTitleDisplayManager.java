package com.example.titles.display;

import com.example.titles.api.AwardedTitle;
import com.example.titles.api.TitleDisplayService;
import com.example.titles.api.TitleText;
import com.example.titles.api.TitlesSettings;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import org.bukkit.util.Transformation;

/** Owns the player title PDC state and its mounted display entity. */
public final class PaperTitleDisplayManager implements TitleDisplayService {
    private final Plugin plugin;
    private final Supplier<TitlesSettings> settings;
    private final NamespacedKey activeTitleKey;
    private final NamespacedKey activePriorityKey;
    private final NamespacedKey displayOwnerKey;
    private final Map<UUID, TextDisplay> displays = new HashMap<>();

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
        if (display != null && display.isValid() && player.getPassengers().contains(display)) {
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
        String text = player.getPersistentDataContainer().get(activeTitleKey, PersistentDataType.STRING);
        if (!player.isOnline() || player.isDead() || text == null) {
            removeDisplay(player);
            return;
        }

        removeDisplay(player);
        String ownerId = player.getUniqueId().toString();
        for (Entity passenger : player.getPassengers()) {
            if (passenger instanceof TextDisplay display
                && ownerId.equals(display.getPersistentDataContainer().get(displayOwnerKey, PersistentDataType.STRING))) {
                player.removePassenger(display);
                display.remove();
            }
        }

        TextDisplay display = player.getWorld().spawn(player.getLocation(), TextDisplay.class, d -> {
            d.text(MiniMessage.miniMessage().deserialize(text));
            d.setBillboard(Display.Billboard.CENTER);
            d.setSeeThrough(false);
            d.setDefaultBackground(false);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.setTransformation(new Transformation(new Vector3f(0f, 0.4f, 0f), new AxisAngle4f(),
                new Vector3f(1f, 1f, 1f), new AxisAngle4f()));
            d.setPersistent(false);
            d.setTextOpacity((byte) (player.isSneaking() ? 60 : 255));
            d.getPersistentDataContainer().set(displayOwnerKey, PersistentDataType.STRING, ownerId);
        });
        if (!player.addPassenger(display)) {
            display.remove();
            plugin.getLogger().warning("Could not mount title display for player " + ownerId);
            return;
        }
        displays.put(player.getUniqueId(), display);
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
            player.removePassenger(display);
            display.remove();
        }
    }

    @Override
    public void cleanupAll() {
        for (TextDisplay display : displays.values()) {
            if (display.isValid()) {
                display.eject();
                display.remove();
            }
        }
        displays.clear();
        for (var world : Bukkit.getWorlds()) {
            for (TextDisplay display : world.getEntitiesByClass(TextDisplay.class)) {
                if (display.getPersistentDataContainer().has(displayOwnerKey, PersistentDataType.STRING)) {
                    display.eject();
                    display.remove();
                }
            }
        }
    }

    public boolean isOwnedDisplay(Entity entity) {
        return entity instanceof TextDisplay display
            && display.getPersistentDataContainer().has(displayOwnerKey, PersistentDataType.STRING);
    }
}
