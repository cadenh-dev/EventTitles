package com.example.titles.api;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Bukkit-free LLM title source. Implementations must never complete the future exceptionally:
 * any timeout, transport error, non-2xx status, or malformed output yields {@link Optional#empty()}.
 * Returned titles must already be length-limited via {@link TitleText#truncateVisible(String, int)}
 * and have {@code fromLlm == true}.
 */
public interface LlmTitleGenerator {
    CompletableFuture<Optional<AwardedTitle>> generateTitle(EventContext context);
}
