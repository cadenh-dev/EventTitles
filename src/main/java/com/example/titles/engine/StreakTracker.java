package com.example.titles.engine;

import com.example.titles.api.StreakService;
import com.example.titles.api.TitlesSettings;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class StreakTracker implements StreakService {
    private record StreakRecord(String key, int count, Instant last) {}

    private final Supplier<TitlesSettings> settings;
    private final Clock clock;
    private final ConcurrentHashMap<UUID, StreakRecord> records = new ConcurrentHashMap<>();

    public StreakTracker(Supplier<TitlesSettings> settings) { this(settings, Clock.systemUTC()); }

    public StreakTracker(Supplier<TitlesSettings> settings, Clock clock) {
        this.settings = settings;
        this.clock = clock;
    }

    @Override public int recordAndGet(UUID playerId, String streakKey) {
        Instant now = clock.instant();
        Duration window = window();
        return records.compute(playerId, (id, previous) -> {
            int count = previous != null && previous.key().equals(streakKey)
                && Duration.between(previous.last(), now).compareTo(window) <= 0
                ? previous.count() + 1 : 1;
            return new StreakRecord(streakKey, count, now);
        }).count();
    }

    @Override public void reset(UUID playerId) { records.remove(playerId); }

    public void purgeExpired() {
        Instant now = clock.instant();
        Duration window = window();
        records.entrySet().removeIf(entry -> Duration.between(entry.getValue().last(), now).compareTo(window) > 0);
    }

    private Duration window() {
        return Duration.ofMinutes(Math.max(1, settings.get().streakWindowMinutes()));
    }
}
