package com.example.titles.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.titles.api.AwardedTitle;
import com.example.titles.api.TitleHistory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonTitleHistoryStoreTest {
    @TempDir
    Path dir;

    private static TitleHistory.Entry entry(String title, long at) {
        return new TitleHistory.Entry(title, 30, "Static", "Why: test\nSource: Static title · Priority 30", at);
    }

    @Test
    void recordsNewestFirstAndCaps() throws Exception {
        UUID id = UUID.randomUUID();
        try (var store = new JsonTitleHistoryStore(dir, () -> 3, Logger.getAnonymousLogger())) {
            for (int i = 1; i <= 5; i++) {
                store.record(id, entry("<green>T" + i + "</green>", i));
            }
            List<TitleHistory.Entry> entries = store.load(id).get(5, TimeUnit.SECONDS);
            assertEquals(List.of("<green>T5</green>", "<green>T4</green>", "<green>T3</green>"),
                entries.stream().map(TitleHistory.Entry::title).toList());
            assertEquals("Why: test\nSource: Static title · Priority 30", entries.getFirst().reason());
        }
    }

    @Test
    void survivesRestartAndKeepsPlayersSeparate() throws Exception {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        try (var store = new JsonTitleHistoryStore(dir, () -> 50, Logger.getAnonymousLogger())) {
            store.record(a, entry("<red>A</red>", 1));
            store.record(b, entry("<blue>B</blue>", 2));
        } // close() flushes
        try (var reopened = new JsonTitleHistoryStore(dir, () -> 50, Logger.getAnonymousLogger())) {
            assertEquals("<red>A</red>", reopened.load(a).get(5, TimeUnit.SECONDS).getFirst().title());
            assertEquals("<blue>B</blue>", reopened.load(b).get(5, TimeUnit.SECONDS).getFirst().title());
        }
        try (var files = Files.list(dir)) {
            assertEquals(2, files.filter(p -> p.toString().endsWith(".json")).count(), "no temp files left");
        }
    }

    @Test
    void unknownOrCorruptFileYieldsEmpty() throws Exception {
        UUID id = UUID.randomUUID();
        Files.writeString(dir.resolve(id + ".json"), "{not json");
        try (var store = new JsonTitleHistoryStore(dir, () -> 50, Logger.getAnonymousLogger())) {
            assertTrue(store.load(UUID.randomUUID()).get(5, TimeUnit.SECONDS).isEmpty());
            assertTrue(store.load(id).get(5, TimeUnit.SECONDS).isEmpty());
        }
    }

    @Test
    void sourceLabels() {
        assertEquals("AI", TitleHistory.sourceOf(new AwardedTitle("<red>x</red>", 10, true)));
        assertEquals("Admin", TitleHistory.sourceOf(new AwardedTitle("<red>x</red>", 10, false, null,
            "Why: Set by Zach with /title set\nSource: Admin · Priority 10")));
        assertEquals("Static", TitleHistory.sourceOf(new AwardedTitle("<red>x</red>", 10, false)));
    }
}
