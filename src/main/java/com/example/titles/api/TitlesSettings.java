package com.example.titles.api;

import java.util.Locale;

/**
 * Immutable, Bukkit-free view of config.yml. Built on the main thread by the plugin and supplied to
 * components through a {@code Supplier<TitlesSettings>} so {@code /title reload} takes effect live.
 *
 * <p>The {@code llm*} endpoint/model/key/temperature/timeout fields are already resolved for the
 * active {@link #llmProvider()} (provider section overrides, then provider defaults).
 */
public record TitlesSettings(
    boolean llmEnabled,
    LlmProvider llmProvider,
    String llmEndpoint,
    String llmModel,
    String llmApiKey,
    int llmTimeoutSeconds,
    double llmTemperature,
    int maxTitleLength,
    int streakWindowMinutes,
    String defaultTitle
) {
    public static final String PLACEHOLDER_KEY = "sk-your-deepseek-key-here";

    public TitlesSettings {
        if (llmProvider == null) {
            llmProvider = LlmProvider.DEEPSEEK;
        }
    }

    public static TitlesSettings defaults() {
        LlmProvider p = LlmProvider.DEEPSEEK;
        return new TitlesSettings(true, p, p.defaultEndpoint(), p.defaultModel(), PLACEHOLDER_KEY,
            p.defaultTimeoutSeconds(), p.defaultTemperature(), -1, 5, "<gray>Unfortunate Soul</gray>");
    }

    /** True for blank keys and the shipped "...-your-...-here" placeholders. */
    public static boolean isPlaceholderKey(String key) {
        if (key == null || key.isBlank()) {
            return true;
        }
        String k = key.toLowerCase(Locale.ROOT);
        return k.contains("your-") && k.endsWith("-here");
    }

    /**
     * Configured key unless blank/placeholder, else the provider's environment variable
     * ({@code DEEPSEEK_API_KEY} / {@code XAI_API_KEY}); null when neither is usable.
     */
    public String effectiveApiKey() {
        if (!isPlaceholderKey(llmApiKey)) {
            return llmApiKey;
        }
        String env = System.getenv(llmProvider.apiKeyEnv());
        return env == null || env.isBlank() ? null : env;
    }
}
