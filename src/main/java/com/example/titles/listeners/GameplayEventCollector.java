package com.example.titles.listeners;

import com.example.titles.api.EventCategory;
import com.example.titles.api.EventContext;
import com.example.titles.api.TitleDisplayService;
import com.example.titles.api.TitleResolverService;
import com.example.titles.api.StreakService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;

/** Collects immutable main-thread snapshots for gameplay title resolution. */
public final class GameplayEventCollector implements Listener {
    private static final long CLUTCH_COOLDOWN_MILLIS = 30_000L;
    private static final List<Material> SHENANIGAN_FOODS = List.of(
        Material.ROTTEN_FLESH, Material.SPIDER_EYE, Material.POISONOUS_POTATO,
        Material.PUFFERFISH, Material.SUSPICIOUS_STEW, Material.ENCHANTED_GOLDEN_APPLE,
        Material.CHORUS_FRUIT);
    private static final List<EntityType> BOSS_TYPES = List.of(
        EntityType.ENDER_DRAGON, EntityType.WITHER, EntityType.WARDEN, EntityType.ELDER_GUARDIAN);

    private final Plugin plugin;
    private final TitleResolverService resolver;
    private final TitleDisplayService displays;
    private final StreakService streaks;
    private final Map<UUID, Long> lastClutchMillis = new ConcurrentHashMap<>();

    public GameplayEventCollector(Plugin plugin, TitleResolverService resolver,
                                  TitleDisplayService displays, StreakService streaks) {
        this.plugin = plugin;
        this.resolver = resolver;
        this.displays = displays;
        this.streaks = streaks;
    }

    private EventContext snapshot(Player p, EventCategory cat, String subject, String extraMetadata) {
        String normalizedSubject = subject.toUpperCase(Locale.ROOT);
        int streakCount = streaks.recordAndGet(p.getUniqueId(), cat.name() + ":" + normalizedSubject);
        String biome = p.getLocation().getBlock().getBiome().key().value().toUpperCase(Locale.ROOT);
        PlayerInventory inventory = p.getInventory();
        ItemStack hand = inventory.getItemInMainHand();
        String heldItem = hand == null || hand.getType().isAir()
            ? Material.AIR.name() : hand.getType().name();
        List<String> armor = new ArrayList<>();
        for (ItemStack item : inventory.getArmorContents()) {
            if (item != null && !item.getType().isAir()) {
                armor.add(item.getType().name());
            }
        }
        String armorSummary = armor.isEmpty() ? "NONE" : String.join(",", armor);
        String metadata = "health=" + String.format(Locale.ROOT, "%.1f", p.getHealth())
            + ";y=" + p.getLocation().getBlockY()
            + ";world=" + p.getWorld().getEnvironment().name();
        if (extraMetadata != null && !extraMetadata.isBlank()) {
            metadata += ";" + extraMetadata;
        }
        return new EventContext(p.getUniqueId(), p.getName(), cat, normalizedSubject, biome,
            heldItem, armorSummary, streakCount, metadata);
    }

    private void award(EventContext ctx) {
        resolver.resolve(ctx).whenComplete((opt, err) -> {
            if (!plugin.isEnabled()) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player p = Bukkit.getPlayer(ctx.playerId());
                if (p != null && p.isOnline() && opt != null) {
                    opt.ifPresent(title -> displays.setPlayerTitle(p, title));
                }
            });
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        DamageSource source = event.getDamageSource();
        Entity causing = source == null ? null : source.getCausingEntity();
        Entity attacker = resolveAttacker(causing);
        Player killer = attacker instanceof Player player ? player : null;
        boolean totemHeld = holdsTotem(victim);
        if (killer != null) {
            award(snapshot(victim, EventCategory.DEATH_MOB, "PLAYER",
                "killer=" + killer.getName() + (totemHeld ? ";totem_held=true" : "")));
            award(snapshot(killer, EventCategory.PVP_KILL, victim.getName(),
                "weapon=" + itemName(killer.getInventory().getItemInMainHand())));
        } else if (attacker != null && !(attacker instanceof Player)) {
            award(snapshot(victim, EventCategory.DEATH_MOB, attacker.getType().name(),
                (totemHeld ? "totem_held=true" : "")));
        } else {
            String raw = source == null ? "UNKNOWN" : source.getDamageType().key().value();
            String subject = environmentalCause(raw);
            StringBuilder extra = new StringBuilder();
            if (totemHeld) {
                extra.append("totem_held=true");
            }
            if (subject.equals("BLOCK_EXPLOSION")
                && (victim.getWorld().getEnvironment() == World.Environment.NETHER
                || victim.getWorld().getEnvironment() == World.Environment.THE_END)) {
                appendMetadata(extra, "bed_explosion=true");
            }
            award(snapshot(victim, EventCategory.DEATH_ENV, subject, extra.toString()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        double after = player.getHealth() - event.getFinalDamage();
        if (after <= 0.0 || after > 1.0) {
            return;
        }
        long now = System.currentTimeMillis();
        Long previous = lastClutchMillis.get(player.getUniqueId());
        if (previous != null && now - previous < CLUTCH_COOLDOWN_MILLIS) {
            return;
        }
        lastClutchMillis.put(player.getUniqueId(), now);
        Entity damager = resolveAttacker(event.getDamager());
        String subject = damager == null ? event.getDamager().getType().name() : damager.getType().name();
        award(snapshot(player, EventCategory.CLUTCH_SURVIVAL, subject,
            "health_after=" + String.format(Locale.ROOT, "%.1f", after)));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player || !BOSS_TYPES.contains(entity.getType())) {
            return;
        }
        Player killer = entity.getKiller();
        if (killer != null) {
            award(snapshot(killer, EventCategory.BOSS_KILL, entity.getType().name(), ""));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        Material type = event.getBlock().getType();
        if (type != Material.ANCIENT_DEBRIS && type != Material.SPAWNER
            && type != Material.DIAMOND_ORE && type != Material.DEEPSLATE_DIAMOND_ORE) {
            return;
        }
        award(snapshot(player, EventCategory.MINING, type == Material.SPAWNER ? "SPAWNER" : type.name(),
            "tool=" + itemName(player.getInventory().getItemInMainHand())));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemConsume(PlayerItemConsumeEvent event) {
        Material type = event.getItem().getType();
        if (SHENANIGAN_FOODS.contains(type)) {
            award(snapshot(event.getPlayer(), EventCategory.SHENANIGANS, type.name(), ""));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCakeInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND
            || event.getClickedBlock() == null || event.getClickedBlock().getType() != Material.CAKE
            || event.getPlayer().getFoodLevel() >= 20) {
            return;
        }
        award(snapshot(event.getPlayer(), EventCategory.SHENANIGANS, "CAKE", ""));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        streaks.reset(id);
        lastClutchMillis.remove(id);
    }

    private static Entity resolveAttacker(Entity entity) {
        if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            return shooter;
        }
        return entity;
    }

    private static boolean holdsTotem(Player player) {
        return player.getInventory().getItemInMainHand().getType() == Material.TOTEM_OF_UNDYING
            || player.getInventory().getItemInOffHand().getType() == Material.TOTEM_OF_UNDYING;
    }

    private static String itemName(ItemStack item) {
        return item == null || item.getType().isAir() ? Material.AIR.name() : item.getType().name();
    }

    private static void appendMetadata(StringBuilder metadata, String value) {
        if (!metadata.isEmpty()) {
            metadata.append(';');
        }
        metadata.append(value);
    }

    private static String environmentalCause(String raw) {
        String cause = raw.toUpperCase(Locale.ROOT);
        return switch (cause) {
            case "FALL", "STALAGMITE" -> "FALL";
            case "LAVA" -> "LAVA";
            case "IN_FIRE", "ON_FIRE", "FIRE_TICK", "CAMPFIRE" -> "FIRE";
            case "DROWN" -> "DROWNING";
            case "OUT_OF_WORLD", "OUTSIDE_BORDER" -> "VOID";
            case "IN_WALL", "CRAMMING" -> "SUFFOCATION";
            case "STARVE" -> "STARVATION";
            case "FREEZE" -> "FREEZE";
            case "LIGHTNING_BOLT" -> "LIGHTNING";
            case "CACTUS", "SWEET_BERRY_BUSH", "FLY_INTO_WALL", "WITHER" -> cause;
            case "MAGIC", "INDIRECT_MAGIC", "POISON" -> "MAGIC";
            case "EXPLOSION" -> "BLOCK_EXPLOSION";
            case "BAD_RESPAWN_POINT" -> "BLOCK_EXPLOSION";
            case "PLAYER_EXPLOSION" -> "ENTITY_EXPLOSION";
            default -> cause;
        };
    }
}
