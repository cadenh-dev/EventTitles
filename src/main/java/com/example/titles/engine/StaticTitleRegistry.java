package com.example.titles.engine;

import com.example.titles.api.AwardedTitle;
import com.example.titles.api.EventCategory;
import com.example.titles.api.EventContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Tier 1 / Tier 3 static titles: 50 trigger rules, each with a main title plus optional variants.
 *
 * <p>Matching picks the matching rule with the highest <em>main</em> priority (ties: first declared),
 * then a random title from that rule's pool (main + variants). Each title keeps its own priority for
 * gating, while the rule's main priority decides boss short-circuiting (see {@link StaticMatch}).
 * Bukkit-free and thread-safe.
 */
public final class StaticTitleRegistry {

    /** A resolved static match: which rule fired, its main priority, and the randomly chosen title. */
    public record StaticMatch(String ruleId, int triggerPriority, AwardedTitle title) {}

    private record Variant(String title, int priority) {}

    private record Rule(String id, EventCategory category, Predicate<EventContext> matcher, List<Variant> pool) {
        Variant main() {
            return pool.getFirst();
        }

        int priority() {
            return main().priority();
        }
    }

    private final List<Rule> rules;
    /** Maps a pool size n to an index in [0, n). Default: uniform random. */
    private final IntUnaryOperator variantPicker;

    public StaticTitleRegistry() {
        this(n -> ThreadLocalRandom.current().nextInt(n));
    }

    /** For tests: {@code n -> 0} always picks the main title. */
    public StaticTitleRegistry(IntUnaryOperator variantPicker) {
        this.variantPicker = variantPicker;
        List<Rule> r = new ArrayList<>();

        // ---- BOSS_KILL -------------------------------------------------------------------------
        add(r, "dragon_slayer", EventCategory.BOSS_KILL, c -> subject(c, "ENDER_DRAGON"),
            v("<gradient:#7b2ff7:#f107a3><bold>Dragonslayer</bold></gradient>", 100),
            v("<gradient:#f107a3:#7b2ff7><bold>Dragon Down</bold></gradient>", 98),
            v("<light_purple><bold>Scale Collector</bold></light_purple>", 96));
        add(r, "withered", EventCategory.BOSS_KILL, c -> subject(c, "WITHER"),
            v("<gradient:#555555:#aaaaaa><bold>Withered</bold></gradient>", 95),
            v("<gradient:#aaaaaa:#555555><bold>Wither Wipe</bold></gradient>", 94),
            v("<dark_gray><bold>Skull Collector</bold></dark_gray>", 92));
        add(r, "warden_whisperer", EventCategory.BOSS_KILL, c -> subject(c, "WARDEN"),
            v("<dark_aqua><bold>Warden Whisperer</bold></dark_aqua>", 90),
            v("<gradient:#003b46:#07575b><bold>Deep Dark Dominator</bold></gradient>", 88),
            v("<aqua><bold>Sonic Boom Survivor</bold></aqua>", 86));
        add(r, "elder_ender", EventCategory.BOSS_KILL, c -> subject(c, "ELDER_GUARDIAN"),
            v("<aqua>Elder Ender</aqua>", 85),
            v("<aqua>Guardian Grinder</aqua>", 83),
            v("<dark_aqua>Temple Terror</dark_aqua>", 81));
        add(r, "boss_breeze", EventCategory.BOSS_KILL, c -> subject(c, "BREEZE"),
            v("<gradient:#a8edea:#fed6e3>Breeze Tamer</gradient>", 76),
            v("<gradient:#fed6e3:#a8edea>Wind Walker</gradient>", 78),
            v("<white>Breeze Breaker</white>", 74));

        // ---- DEATH_MOB -------------------------------------------------------------------------
        add(r, "creeper_consecutive", EventCategory.DEATH_MOB, c -> subject(c, "CREEPER") && c.streakCount() >= 3,
            v("<gradient:#b7f8db:#50a7c2><bold>Creeper Magnet</bold></gradient>", 80),
            v("<green><bold>Sssss… Again</bold></green>", 78),
            v("<gradient:#50a7c2:#b7f8db><bold>Boom Loop</bold></gradient>", 72));
        add(r, "creeper_hugger", EventCategory.DEATH_MOB, c -> subject(c, "CREEPER"),
            v("<green>Creeper Hugger</green>", 30),
            v("<green>Green Hug</green>", 28));
        add(r, "zombie_snack", EventCategory.DEATH_MOB, c -> subject(c, "ZOMBIE"),
            v("<dark_green>Zombie Snack</dark_green>", 25),
            v("<dark_green>Zombie Chow</dark_green>", 23));
        add(r, "skeleton_target", EventCategory.DEATH_MOB, c -> subject(c, "SKELETON"),
            v("<gray>Arrow Magnet</gray>", 25),
            v("<white>Bone Zone</white>", 22));
        add(r, "spider_supper", EventCategory.DEATH_MOB, c -> subject(c, "SPIDER"),
            v("<dark_purple>Webbed Wonder</dark_purple>", 26),
            v("<dark_purple>Spider’s Web</dark_purple>", 24));
        add(r, "enderman_gaze", EventCategory.DEATH_MOB, c -> subject(c, "ENDERMAN"),
            v("<dark_purple>Gaze Collector</dark_purple>", 32),
            v("<light_purple>Enderman’s Stare</light_purple>", 31));
        add(r, "brute_force", EventCategory.DEATH_MOB, c -> subject(c, "PIGLIN_BRUTE"),
            v("<gold>Brute Force</gold>", 38),
            v("<gold>Brute’s Beating</gold>", 40));
        add(r, "guardian_gulp", EventCategory.DEATH_MOB, c -> subject(c, "ELDER_GUARDIAN"),
            v("<aqua>Ocean's Snack</aqua>", 45),
            v("<aqua>Elder’s Appetite</aqua>", 42));
        add(r, "deep_dark_victim", EventCategory.DEATH_MOB, c -> c.biome().equalsIgnoreCase("DEEP_DARK"),
            v("<dark_gray>Darkness Dweller</dark_gray>", 50),
            v("<dark_aqua>Deep Dark Dinner</dark_aqua>", 55),
            v("<dark_gray>Sculk Snack</dark_gray>", 48));
        add(r, "mob_naked", EventCategory.DEATH_MOB, c -> c.armorSummary().equalsIgnoreCase("NONE"),
            v("<yellow>Fashionably Fragile</yellow>", 29),
            v("<yellow>Naked & Afraid</yellow>", 27));
        add(r, "mob_totem", EventCategory.DEATH_MOB, c -> c.heldItem().equalsIgnoreCase("TOTEM_OF_UNDYING"),
            v("<gold>Totem Tease</gold>", 33),
            v("<gold>Totem Fail</gold>", 35));
        add(r, "mob_outplayed", EventCategory.DEATH_MOB, c -> subject(c, "PLAYER"),
            v("<red>Thoroughly Outplayed</red>", 34),
            v("<red>Outplayed</red>", 36));

        // ---- DEATH_ENV -------------------------------------------------------------------------
        add(r, "fall_from_grace", EventCategory.DEATH_ENV, c -> subject(c, "FALL"),
            v("<gold>Gravity's Favorite</gold>", 20),
            v("<gold>Gravity Wins</gold>", 20));
        add(r, "void_vacation", EventCategory.DEATH_ENV, c -> subject(c, "VOID"),
            v("<dark_purple>Void Vacation</dark_purple>", 25),
            v("<dark_purple>Voided</dark_purple>", 25));
        add(r, "lava_lounge", EventCategory.DEATH_ENV, c -> subject(c, "LAVA"),
            v("<red>Lava Lounge</red>", 22),
            v("<red>Lava Bath</red>", 22));
        add(r, "fire_starter", EventCategory.DEATH_ENV, c -> subject(c, "FIRE"),
            v("<gold>Well Done</gold>", 18),
            v("<gold>Crispy</gold>", 18));
        add(r, "drowned", EventCategory.DEATH_ENV, c -> subject(c, "DROWNING"),
            v("<blue>Bubble Breaker</blue>", 19),
            v("<blue>Waterlogged</blue>", 19));
        add(r, "suffocated", EventCategory.DEATH_ENV, c -> subject(c, "SUFFOCATION"),
            v("<gray>Wall Hugger</gray>", 17),
            v("<gray>Choked</gray>", 17));
        add(r, "starving", EventCategory.DEATH_ENV, c -> subject(c, "STARVATION"),
            v("<yellow>Snack Seeker</yellow>", 16),
            v("<yellow>Hangry</yellow>", 16));
        add(r, "frozen_solid", EventCategory.DEATH_ENV, c -> subject(c, "FREEZE"),
            v("<aqua>Ice Cold</aqua>", 21),
            v("<aqua>Frozen Assets</aqua>", 21));
        add(r, "lightning_rod", EventCategory.DEATH_ENV, c -> subject(c, "LIGHTNING"),
            v("<yellow>Storm Magnet</yellow>", 23),
            v("<yellow>Thunderstruck</yellow>", 23));
        add(r, "cactus_kissed", EventCategory.DEATH_ENV, c -> subject(c, "CACTUS"),
            v("<green>Prickly Finish</green>", 15),
            v("<green>Cactus Kiss</green>", 15));
        add(r, "berry_bush", EventCategory.DEATH_ENV, c -> subject(c, "SWEET_BERRY_BUSH"),
            v("<dark_green>Berry Bruised</dark_green>", 14),
            v("<dark_red>Berry Bad</dark_red>", 14));
        add(r, "wall_flight", EventCategory.DEATH_ENV, c -> subject(c, "FLY_INTO_WALL"),
            v("<gray>Wall Pilot</gray>", 24),
            v("<gray>Wall Check</gray>", 24));
        add(r, "entity_boom", EventCategory.DEATH_ENV, c -> subject(c, "ENTITY_EXPLOSION"),
            v("<red>Blast Radius</red>", 26),
            v("<red><bold>Kaboom</bold></red>", 27));

        // ---- PVP_KILL --------------------------------------------------------------------------
        add(r, "pvp_streak", EventCategory.PVP_KILL, c -> c.streakCount() >= 5,
            v("<gradient:#f6d365:#fda085><bold>Unstoppable</bold></gradient>", 75),
            v("<gradient:#ff416c:#ff4b2b><bold>Streak Demon</bold></gradient>", 72));
        add(r, "pvp_sword", EventCategory.PVP_KILL, c -> c.heldItem().endsWith("SWORD"),
            v("<red>Blade Runner</red>", 48),
            v("<white>Sword Saint</white>", 50));
        add(r, "pvp_axe", EventCategory.PVP_KILL, c -> c.heldItem().endsWith("AXE"),
            v("<gold>Axe Marksman</gold>", 46),
            v("<dark_red>Axe Murderer</dark_red>", 47));
        add(r, "pvp_naked", EventCategory.PVP_KILL, c -> c.armorSummary().equalsIgnoreCase("NONE"),
            v("<yellow>Brave Threads</yellow>", 43),
            v("<yellow>Naked Aggression</yellow>", 44));
        add(r, "pvp_deepdark", EventCategory.PVP_KILL, c -> c.biome().equalsIgnoreCase("DEEP_DARK"),
            v("<dark_aqua>Dark Duelist</dark_aqua>", 52),
            v("<dark_aqua>Deep Dark Duel</dark_aqua>", 55));

        // ---- CLUTCH_SURVIVAL -------------------------------------------------------------------
        add(r, "clutch_heart", EventCategory.CLUTCH_SURVIVAL, c -> number(c, "health_after", 2) <= 1,
            v("<gradient:#ff512f:#dd2476><bold>Heartbreaker</bold></gradient>", 70),
            v("<red><bold>One Heart Wonder</bold></red>", 68));
        add(r, "clutch_void", EventCategory.CLUTCH_SURVIVAL, c -> subject(c, "VOID"),
            v("<light_purple>Void Dodger</light_purple>", 62),
            v("<light_purple>Void Escape</light_purple>", 61));
        add(r, "clutch_lava", EventCategory.CLUTCH_SURVIVAL, c -> subject(c, "LAVA"),
            v("<gold>Hot Foot</gold>", 58),
            v("<gold>Lava Legs</gold>", 57));
        add(r, "clutch_water", EventCategory.CLUTCH_SURVIVAL, c -> c.heldItem().contains("WATER_BUCKET"),
            v("<blue>Bucket Legend</blue>", 65),
            v("<blue>Bucket Clutch</blue>", 64));
        add(r, "clutch_totem", EventCategory.CLUTCH_SURVIVAL, c -> c.heldItem().equalsIgnoreCase("TOTEM_OF_UNDYING"),
            v("<gold>Not Today</gold>", 60),
            v("<gold>Totem Time</gold>", 59));

        // ---- MINING ----------------------------------------------------------------------------
        add(r, "ancient_debris", EventCategory.MINING, c -> subject(c, "ANCIENT_DEBRIS"),
            v("<gradient:#f7971e:#ffd200><bold>Ancient Prospector</bold></gradient>", 40),
            v("<gradient:#4a3b3b:#9e7b6f><bold>Netherite Dreams</bold></gradient>", 42));
        add(r, "diamond_digger", EventCategory.MINING,
            c -> subject(c, "DIAMOND_ORE") || subject(c, "DEEPSLATE_DIAMOND_ORE"),
            v("<aqua>Diamond Digger</aqua>", 35),
            v("<aqua><bold>Diamond Hands</bold></aqua>", 38));
        add(r, "spawner_smasher", EventCategory.MINING, c -> subject(c, "SPAWNER"),
            v("<dark_purple>Spawner Smasher</dark_purple>", 35),
            v("<dark_purple>Spawner Hunter</dark_purple>", 36));
        add(r, "mining_low_y", EventCategory.MINING, c -> number(c, "y", 999) <= -50,
            v("<dark_gray>Deep Delver</dark_gray>", 32),
            v("<dark_gray>Bedrock Bound</dark_gray>", 33));

        // ---- SHENANIGANS -----------------------------------------------------------------------
        add(r, "rotten_feast", EventCategory.SHENANIGANS, c -> subject(c, "ROTTEN_FLESH"),
            v("<green>Rotten Gourmet</green>", 10),
            v("<dark_green>Rotten Choice</dark_green>", 12));
        add(r, "suspicious_snack", EventCategory.SHENANIGANS, c -> subject(c, "SUSPICIOUS_STEW"),
            v("<light_purple>Stew Ponderer</light_purple>", 18),
            v("<light_purple>Mystery Stew</light_purple>", 19));
        add(r, "potato_pro", EventCategory.SHENANIGANS, c -> subject(c, "POISONOUS_POTATO"),
            v("<green>Potato Risk-Taker</green>", 20),
            v("<green>Potato Roulette</green>", 22));
        add(r, "puffer_puffer", EventCategory.SHENANIGANS, c -> subject(c, "PUFFERFISH"),
            v("<yellow>Pointy Palate</yellow>", 19),
            v("<yellow>Puffer Mistake</yellow>", 20));
        add(r, "cake_connoisseur", EventCategory.SHENANIGANS, c -> subject(c, "CAKE"),
            v("<gold>Cake Connoisseur</gold>", 16),
            v("<light_purple>Cake Day</light_purple>", 17));
        add(r, "enchanted_appetite", EventCategory.SHENANIGANS, c -> subject(c, "ENCHANTED_GOLDEN_APPLE"),
            v("<gradient:#fceabb:#f8b500>Golden Appetite</gradient>", 24),
            v("<gradient:#f8b500:#fceabb><bold>God Apple</bold></gradient>", 26));

        rules = List.copyOf(r);
    }

    /** Best matching rule with a randomly picked title from its pool. */
    public Optional<StaticMatch> findMatch(EventContext ctx) {
        return bestRule(ctx).map(rule -> {
            List<Variant> pool = rule.pool();
            int index = pool.size() == 1 ? 0 : Math.floorMod(variantPicker.applyAsInt(pool.size()), pool.size());
            Variant chosen = pool.get(index);
            return new StaticMatch(rule.id(), rule.priority(),
                new AwardedTitle(chosen.title(), chosen.priority(), false));
        });
    }

    /** Randomized static title (see {@link #findMatch}). */
    public Optional<AwardedTitle> findStaticMatch(EventContext ctx) {
        return findMatch(ctx).map(StaticMatch::title);
    }

    /** Deterministic main title of the best matching rule. */
    public Optional<AwardedTitle> findMainMatch(EventContext ctx) {
        return bestRule(ctx).map(rule -> new AwardedTitle(rule.main().title(), rule.priority(), false));
    }

    /** Number of trigger rules (each may hold several title variants). */
    public int size() {
        return rules.size();
    }

    /** Total number of titles across all rule pools. */
    public int titleCount() {
        return rules.stream().mapToInt(rule -> rule.pool().size()).sum();
    }

    private Optional<Rule> bestRule(EventContext ctx) {
        Rule best = null;
        for (Rule rule : rules) {
            if (rule.category() == ctx.category() && rule.matcher().test(ctx)
                && (best == null || rule.priority() > best.priority())) {
                best = rule;
            }
        }
        return Optional.ofNullable(best);
    }

    private static Variant v(String title, int priority) {
        return new Variant(title, priority);
    }

    private static void add(List<Rule> rules, String id, EventCategory category,
                            Predicate<EventContext> matcher, Variant... pool) {
        rules.add(new Rule(id, category, matcher, List.of(pool)));
    }

    private static boolean subject(EventContext c, String value) {
        return c.primarySubject().equalsIgnoreCase(value);
    }

    private static double number(EventContext c, String key, double fallback) {
        try {
            return Double.parseDouble(metadata(c).getOrDefault(key, Double.toString(fallback)));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static Map<String, String> metadata(EventContext c) {
        Map<String, String> values = new HashMap<>();
        for (String part : c.extraMetadata().split(";")) {
            int i = part.indexOf('=');
            if (i > 0) {
                values.put(part.substring(0, i).trim(), part.substring(i + 1).trim());
            }
        }
        return values;
    }

    // ---- test hooks ----------------------------------------------------------------------------
    List<String> ruleIdsForTest() {
        return rules.stream().map(Rule::id).toList();
    }

    List<Integer> prioritiesForTest() {
        return rules.stream().map(Rule::priority).toList();
    }

    /** Every title in every pool. */
    List<String> titlesForTest() {
        return rules.stream().flatMap(rule -> rule.pool().stream()).map(Variant::title).toList();
    }

    Map<String, List<String>> poolsForTest() {
        return rules.stream().collect(Collectors.toMap(Rule::id,
            rule -> rule.pool().stream().map(Variant::title).toList()));
    }

    Map<EventCategory, Long> categoryCountsForTest() {
        return rules.stream().collect(Collectors.groupingBy(Rule::category, Collectors.counting()));
    }
}
