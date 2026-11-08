package com.example.titles.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.titles.api.AwardedTitle;
import com.example.titles.api.EventCategory;
import com.example.titles.api.EventContext;
import com.example.titles.api.TitleText;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StaticTitleRegistryTest {
    private final StaticTitleRegistry registry = new StaticTitleRegistry();

    private static EventContext ctx(EventCategory category, String subject, int streak) {
        return new EventContext(UUID.randomUUID(), "tester", category, subject, "PLAINS", "AIR", "IRON_HELMET",
            streak, "");
    }

    @Test
    void fiftyRulesWithUniqueTitlesAcrossEveryCategory() {
        assertEquals(50, registry.size());
        assertEquals(50, registry.ruleIdsForTest().stream().distinct().count());
        assertEquals(107, registry.titleCount());
        List<String> titles = registry.titlesForTest();
        assertEquals(titles.size(), titles.stream().distinct().count(), "duplicate title text");
        for (EventCategory c : EventCategory.values()) {
            assertTrue(registry.categoryCountsForTest().getOrDefault(c, 0L) > 0, c.name());
        }
        for (String title : titles) {
            assertFalse(TitleText.stripTags(title).isBlank(), title);
            assertFalse(title.contains("\u00a7"), title);
        }
    }

    @Test
    void requiredMainPriorities() {
        assertEquals(100, main(EventCategory.BOSS_KILL, "ENDER_DRAGON", 1).priority());
        assertEquals(95, main(EventCategory.BOSS_KILL, "WITHER", 1).priority());
        assertEquals(90, main(EventCategory.BOSS_KILL, "WARDEN", 1).priority());
        assertEquals(80, main(EventCategory.DEATH_MOB, "CREEPER", 3).priority());
        assertEquals(30, main(EventCategory.DEATH_MOB, "CREEPER", 1).priority());
        assertEquals(20, main(EventCategory.DEATH_ENV, "FALL", 1).priority());
        assertEquals(10, main(EventCategory.SHENANIGANS, "ROTTEN_FLESH", 1).priority());
    }

    @Test
    void mainTitleStaysFirstInEveryPool() {
        assertEquals("<gradient:#7b2ff7:#f107a3><bold>Dragonslayer</bold></gradient>",
            main(EventCategory.BOSS_KILL, "ENDER_DRAGON", 1).miniMessageText());
        StaticTitleRegistry first = new StaticTitleRegistry(n -> 0);
        assertEquals(main(EventCategory.DEATH_MOB, "CREEPER", 1),
            first.findStaticMatch(ctx(EventCategory.DEATH_MOB, "CREEPER", 1)).orElseThrow());
    }

    @Test
    void variantsCarryTheirOwnPriorityButKeepTriggerPriority() {
        StaticTitleRegistry second = new StaticTitleRegistry(n -> 1);
        StaticTitleRegistry.StaticMatch warden = second.findMatch(ctx(EventCategory.BOSS_KILL, "WARDEN", 1)).orElseThrow();
        assertEquals("warden_whisperer", warden.ruleId());
        assertEquals(90, warden.triggerPriority());
        assertEquals(88, warden.title().priority());
        assertEquals("Deep Dark Dominator", TitleText.stripTags(warden.title().miniMessageText()));
    }

    @Test
    void randomSelectionCoversWholePool() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            seen.add(TitleText.stripTags(registry.findStaticMatch(ctx(EventCategory.BOSS_KILL, "ENDER_DRAGON", 1))
                .orElseThrow().miniMessageText()));
        }
        assertEquals(Set.of("Dragonslayer", "Dragon Down", "Scale Collector"), seen);
    }

    @Test
    void highestMainPriorityRuleWinsBeforeVariantPick() {
        // Creeper streak (main 80) must beat basic creeper (30) even when its lowest variant (72) is picked.
        StaticTitleRegistry last = new StaticTitleRegistry(n -> n - 1);
        StaticTitleRegistry.StaticMatch m = last.findMatch(ctx(EventCategory.DEATH_MOB, "CREEPER", 3)).orElseThrow();
        assertEquals("creeper_consecutive", m.ruleId());
        assertEquals("Boom Loop", TitleText.stripTags(m.title().miniMessageText()));
        assertEquals(72, m.title().priority());
    }

    private AwardedTitle main(EventCategory category, String subject, int streak) {
        return registry.findMainMatch(ctx(category, subject, streak)).orElseThrow();
    }
}
