package com.example.titles.api;

/**
 * Immutable, Bukkit-free view of config.yml. Built on the main thread by the plugin and supplied to
 * components through a {@code Supplier<TitlesSettings>} so {@code /title reload} takes effect live.
 */
public record TitlesSettings(
    boolean llmEnabled,
    String llmProvider,
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

    public static TitlesSettings defaults() {
        return new TitlesSettings(true, "deepseek", "https://api.deepseek.com/chat/completions",
            "deepseek-chat", PLACEHOLDER_KEY, 3, 0.7, -1, 5, "<gray>Unfortunate Soul</gray>");
    }

    /** Configured key unless blank/placeholder, else {@code DEEPSEEK_API_KEY}; null when neither is usable. */
    public String effectiveApiKey() {
        if (llmApiKey != null && !llmApiKey.isBlank() && !PLACEHOLDER_KEY.equals(llmApiKey)) {
            return llmApiKey;
        }
        String env = System.getenv("DEEPSEEK_API_KEY");
        return env == null || env.isBlank() ? null : env;
    }
}
