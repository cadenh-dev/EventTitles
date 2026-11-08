package com.example.titles.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.titles.api.AwardedTitle;
import com.example.titles.api.EventCategory;
import com.example.titles.api.EventContext;
import com.example.titles.api.LlmProvider;
import com.example.titles.api.TitlesSettings;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class TitleReasonsTest {
    private static EventContext ctx(EventCategory cat, String subject, String biome, String armor, int streak,
                                    String meta) {
        return new EventContext(UUID.randomUUID(), "tester", cat, subject, biome, "AIR", armor, streak, meta);
    }

    @Test
    void describesEventsInPlainEnglish() {
        assertEquals("Killed by Creeper · Deep Dark · 3 in a row · no armor",
            TitleReasons.describeEvent(ctx(EventCategory.DEATH_MOB, "CREEPER", "DEEP_DARK", "NONE", 3, "")));
        assertEquals("Killed the Ender Dragon · The End",
            TitleReasons.describeEvent(ctx(EventCategory.BOSS_KILL, "ENDER_DRAGON", "THE_END", "DIAMOND_HELMET", 1, "")));
        assertEquals("Killed by Notch · Plains",
            TitleReasons.describeEvent(ctx(EventCategory.DEATH_MOB, "PLAYER", "PLAINS", "IRON_HELMET", 1, "killer=Notch")));
        assertEquals("Survived a hit from Zombie on 0.5 HP · Plains",
            TitleReasons.describeEvent(ctx(EventCategory.CLUTCH_SURVIVAL, "ZOMBIE", "PLAINS", "IRON_HELMET", 1,
                "health=6.0;health_after=0.5")));
    }

    @Test
    void staticResolutionCarriesTriggerReason() throws Exception {
        var resolver = new HybridTitleResolver(new StaticTitleRegistry(n -> 0),
            c -> CompletableFuture.completedFuture(Optional.empty()), TitlesSettings::defaults);
        AwardedTitle t = resolver.resolve(ctx(EventCategory.DEATH_MOB, "CREEPER", "PLAINS", "IRON_HELMET", 3, ""))
            .get(4, TimeUnit.SECONDS).orElseThrow();
        assertEquals("Why: Killed by a Creeper 3+ times in a row\n"
            + "Event: Killed by Creeper · Plains · 3 in a row\n"
            + "Source: Static title · Priority 80", t.reason());
    }

    @Test
    void llmResolutionNamesProviderAndKeepsComment() throws Exception {
        var resolver = new HybridTitleResolver(new StaticTitleRegistry(n -> 0),
            c -> CompletableFuture.completedFuture(Optional.of(
                new AwardedTitle("<red>Boom</red>", 20, true, "<player> went boom"))),
            TitlesSettings::defaults);
        AwardedTitle t = resolver.resolve(ctx(EventCategory.DEATH_MOB, "CREEPER", "PLAINS", "IRON_HELMET", 1, ""))
            .get(4, TimeUnit.SECONDS).orElseThrow();
        assertEquals("<player> went boom", t.comment());
        assertEquals(30, t.priority());
        assertNotNull(t.reason());
        assertTrue(t.reason().startsWith("Why: Killed by a Creeper (custom title by DeepSeek)"), t.reason());
        assertTrue(t.reason().endsWith("Source: DeepSeek (deepseek-chat) · Priority 30"), t.reason());
    }

    @Test
    void defaultTitleExplainsItself() throws Exception {
        var resolver = new HybridTitleResolver(new StaticTitleRegistry(n -> 0),
            c -> CompletableFuture.completedFuture(Optional.empty()), TitlesSettings::defaults);
        AwardedTitle t = resolver.resolve(ctx(EventCategory.SHENANIGANS, "SPIDER_EYE", "PLAINS", "IRON_HELMET", 1, ""))
            .get(4, TimeUnit.SECONDS).orElseThrow();
        assertTrue(t.reason().startsWith("Why: Nothing more specific fit this event"), t.reason());
        assertTrue(t.reason().contains("Event: Ate Spider Eye"), t.reason());
    }

    @Test
    void humanizesIds() {
        assertEquals("Ender Dragon", TitleReasons.humanize("ENDER_DRAGON"));
        assertEquals("Grok", LlmProvider.GROK.name().charAt(0) + LlmProvider.GROK.name().substring(1).toLowerCase());
    }
}
