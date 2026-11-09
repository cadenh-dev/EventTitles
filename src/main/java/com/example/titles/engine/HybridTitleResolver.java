package com.example.titles.engine;

import com.example.titles.api.AwardedTitle;
import com.example.titles.api.EventCategory;
import com.example.titles.api.EventContext;
import com.example.titles.api.LlmTitleGenerator;
import com.example.titles.api.TitleResolverService;
import com.example.titles.api.TitleText;
import com.example.titles.api.TitlesSettings;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Tier 1 → 2 → 3 cascade: boss-level static rules resolve instantly; otherwise the LLM is asked; on
 * any LLM miss the (randomized) static match or the default title is used. Every result carries a
 * plain-text {@link AwardedTitle#reason()} for hover text. Never completes exceptionally.
 */
public final class HybridTitleResolver implements TitleResolverService {
    /** Rules whose main priority is at least this resolve instantly without the LLM (bosses). */
    static final int SHORT_CIRCUIT_PRIORITY = 90;
    static final int DEFAULT_TITLE_PRIORITY = 5;
    /** Events common enough to spam the LLM use hardcoded titles until they happen this often in a row. */
    static final int FREQUENT_EVENT_STREAK_THRESHOLD = 10;
    /** Upper bound on remembered AI titles (LRU); older entries are regenerated when needed. */
    static final int LLM_CACHE_LIMIT = 512;
    private static final Set<EventCategory> FREQUENT_EVENT_CATEGORIES =
        Set.of(EventCategory.MINING, EventCategory.DEATH_MOB, EventCategory.DEATH_ENV,
            EventCategory.SHENANIGANS);

    private final StaticTitleRegistry registry;
    private final LlmTitleGenerator llm;
    private final Supplier<TitlesSettings> settings;
    /** AI titles remembered by event signature so repeats reuse them instead of calling the LLM again. */
    private final Map<String, AwardedTitle> llmCache = Collections.synchronizedMap(
        new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, AwardedTitle> eldest) {
                return size() > LLM_CACHE_LIMIT;
            }
        });

    public HybridTitleResolver(StaticTitleRegistry registry, LlmTitleGenerator llm, Supplier<TitlesSettings> settings) {
        this.registry = registry;
        this.llm = llm;
        this.settings = settings;
    }

    @Override
    public CompletableFuture<Optional<AwardedTitle>> resolve(EventContext context) {
        Optional<StaticTitleRegistry.StaticMatch> found;
        try {
            found = registry.findMatch(context);
        } catch (Throwable t) {
            found = Optional.empty();
        }
        final Optional<StaticTitleRegistry.StaticMatch> match = found;

        // Tier 1: boss-level rule (decided by the rule's MAIN priority, so sub-90 variants still count).
        if (match.isPresent() && match.get().triggerPriority() >= SHORT_CIRCUIT_PRIORITY) {
            return CompletableFuture.completedFuture(Optional.of(staticTitle(context, match.get())));
        }

        TitlesSettings config;
        try {
            config = settings.get();
        } catch (Throwable t) {
            return CompletableFuture.completedFuture(match.map(m -> staticTitle(context, m)));
        }
        if (!config.llmEnabled()) {
            return CompletableFuture.completedFuture(fallback(context, match, config));
        }
        // Frequent events (mining, deaths) stay on hardcoded titles unless they happen many times in
        // a row, so routine events neither spam the LLM nor depend on its priority to be seen.
        if (FREQUENT_EVENT_CATEGORIES.contains(context.category())
            && context.streakCount() < FREQUENT_EVENT_STREAK_THRESHOLD) {
            return CompletableFuture.completedFuture(fallback(context, match, config));
        }

        // Reuse an AI title already generated for the same kind of event (per provider/model).
        String cacheKey = llmCacheKey(context, config);
        AwardedTitle cached = llmCache.get(cacheKey);
        if (cached != null) {
            return CompletableFuture.completedFuture(Optional.of(cachedTitle(context, match, config, cached)));
        }

        // Tier 2: LLM, with a safety timeout on top of the client's own.
        CompletableFuture<Optional<AwardedTitle>> request;
        try {
            request = llm.generateTitle(context);
            if (request == null) {
                throw new IllegalStateException("LLM returned null future");
            }
        } catch (Throwable t) {
            return CompletableFuture.completedFuture(fallback(context, match, config));
        }
        int timeout = Math.max(0, config.llmTimeoutSeconds());
        return request
            .completeOnTimeout(Optional.empty(), timeout + 1L, TimeUnit.SECONDS)
            .exceptionally(t -> Optional.empty())
            .thenApply(result -> {
                Optional<AwardedTitle> safe = result == null ? Optional.empty() : result;
                if (safe.isEmpty()) {
                    return fallback(context, match, config); // Tier 3
                }
                AwardedTitle title = safe.get();
                // Never let LLM flavour devalue the event: lift to the static rule's priority.
                int priority = Math.max(title.priority(), match.map(m -> m.title().priority()).orElse(1));
                AwardedTitle lifted = title
                    .withText(TitleText.truncateVisible(title.miniMessageText(), config.maxTitleLength()))
                    .withPriority(priority);
                AwardedTitle withReason = lifted.withReason(TitleReasons.forLlm(context, config.llmProvider(),
                    config.llmModel(), match.map(StaticTitleRegistry.StaticMatch::trigger).orElse(null), lifted));
                llmCache.put(cacheKey, withReason);
                return Optional.of(withReason);
            })
            .exceptionally(t -> fallback(context, match, config));
    }

    /** Stable signature of the event facts that shape an AI title (player identity/time excluded). */
    private static String llmCacheKey(EventContext ctx, TitlesSettings config) {
        return config.llmProvider().configId() + '\u0000' + config.llmModel() + '\u0000'
            + ctx.category().name() + '\u0000' + ctx.primarySubject() + '\u0000' + ctx.biome() + '\u0000'
            + ctx.heldItem() + '\u0000' + ctx.armorSummary();
    }

    /** Reuses a cached AI title, re-lifting priority and refreshing the hover reason for this event. */
    private static AwardedTitle cachedTitle(EventContext ctx, Optional<StaticTitleRegistry.StaticMatch> match,
                                            TitlesSettings config, AwardedTitle cached) {
        int priority = Math.max(cached.priority(), match.map(m -> m.title().priority()).orElse(1));
        AwardedTitle title = cached
            .withText(TitleText.truncateVisible(cached.miniMessageText(), config.maxTitleLength()))
            .withPriority(priority);
        return title.withReason(TitleReasons.forLlm(ctx, config.llmProvider(), config.llmModel(),
            match.map(StaticTitleRegistry.StaticMatch::trigger).orElse(null), title));
    }

    private static AwardedTitle staticTitle(EventContext ctx, StaticTitleRegistry.StaticMatch match) {
        return match.title().withReason(TitleReasons.forStatic(ctx, match.trigger(), match.title()));
    }

    private static Optional<AwardedTitle> fallback(EventContext ctx, Optional<StaticTitleRegistry.StaticMatch> match,
                                                   TitlesSettings config) {
        if (match.isPresent()) {
            return Optional.of(staticTitle(ctx, match.get()));
        }
        if (config.defaultTitle() == null || config.defaultTitle().isBlank()) {
            return Optional.empty();
        }
        String text = TitleText.truncateVisible(config.defaultTitle(), config.maxTitleLength());
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        AwardedTitle title = new AwardedTitle(text, DEFAULT_TITLE_PRIORITY, false);
        return Optional.of(title.withReason(TitleReasons.forDefault(ctx, title)));
    }
}
