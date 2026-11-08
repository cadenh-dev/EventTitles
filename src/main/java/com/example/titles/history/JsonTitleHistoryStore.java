package com.example.titles.history;

import com.example.titles.api.TitleHistory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Stores each player's title history as {@code <dir>/<uuid>.json} (newest first, capped). Bukkit-free.
 * All file I/O runs on one dedicated background thread, so reads and writes for a player are ordered
 * and the server thread never touches the disk. Files live only inside the plugin's data folder; file
 * names come from UUIDs only, so no input can escape that folder.
 */
public final class JsonTitleHistoryStore implements TitleHistory, AutoCloseable {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Type LIST_TYPE = new TypeToken<List<Entry>>() {}.getType();

    private final Path dir;
    private final IntSupplier maxEntries;
    private final Logger logger;
    private final ExecutorService io;

    public JsonTitleHistoryStore(Path dir, IntSupplier maxEntries, Logger logger) {
        this.dir = dir;
        this.maxEntries = maxEntries;
        this.logger = logger;
        this.io = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "EventTitles-History");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public void record(UUID playerId, Entry entry) {
        try {
            io.execute(() -> {
                try {
                    List<Entry> entries = new ArrayList<>(read(playerId));
                    entries.addFirst(entry);
                    int cap = Math.max(1, maxEntries.getAsInt());
                    while (entries.size() > cap) {
                        entries.removeLast();
                    }
                    write(playerId, entries);
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Could not save title history for " + playerId + ": " + e.getMessage());
                }
            });
        } catch (Exception rejected) {
            // executor already shut down (plugin disabling): history is best-effort
        }
    }

    @Override
    public CompletableFuture<List<Entry>> load(UUID playerId) {
        try {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return read(playerId);
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Could not read title history for " + playerId + ": " + e.getMessage());
                    return List.<Entry>of();
                }
            }, io);
        } catch (Exception rejected) {
            return CompletableFuture.completedFuture(List.of());
        }
    }

    /** Flushes pending writes (up to 5 s) and stops the I/O thread. */
    @Override
    public void close() {
        io.shutdown();
        try {
            if (!io.awaitTermination(5, TimeUnit.SECONDS)) {
                logger.warning("Title history writes did not finish within 5 seconds.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private Path file(UUID playerId) {
        return dir.resolve(playerId.toString() + ".json");
    }

    private List<Entry> read(UUID playerId) throws IOException {
        Path file = file(playerId);
        if (!Files.exists(file)) {
            return List.of();
        }
        List<Entry> entries = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), LIST_TYPE);
        if (entries == null) {
            return List.of();
        }
        return entries.stream().filter(e -> e != null && e.title() != null && !e.title().isBlank()).toList();
    }

    private void write(UUID playerId, List<Entry> entries) throws IOException {
        Files.createDirectories(dir);
        Path target = file(playerId);
        Path temp = Files.createTempFile(dir, playerId + "-", ".tmp");
        try {
            Files.writeString(temp, GSON.toJson(entries, LIST_TYPE), StandardCharsets.UTF_8);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
