package com.example.titles.api;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Resolves an event into a title. Bukkit-free; the future may complete on any thread and never completes exceptionally. */
public interface TitleResolverService {
    CompletableFuture<Optional<AwardedTitle>> resolve(EventContext context);
}
