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
    String defaultTitle,
    double displayOffsetY,
    boolean showToSelf,
    boolean announceToSelf,
    ChatSettings chat
) {
    public static final String PLACEHOLDER_KEY = "sk-your-deepseek-key-here";
    /** Blocks above the top of the player's hitbox; vanilla draws the nametag at about +0.5. */
    public static final double DEFAULT_DISPLAY_OFFSET_Y = 0.8;

    public TitlesSettings {
        if (llmProvider == null) {
            llmProvider = LlmProvider.DEEPSEEK;
        }
        if (chat == null) {
            chat = ChatSettings.defaults();
        }
    }

    /** Display and chat options at their defaults (offset 0.8, hidden from owner, owner notified). */
    public TitlesSettings(boolean llmEnabled, LlmProvider llmProvider, String llmEndpoint, String llmModel,
                          String llmApiKey, int llmTimeoutSeconds, double llmTemperature, int maxTitleLength,
                          int streakWindowMinutes, String defaultTitle) {
        this(llmEnabled, llmProvider, llmEndpoint, llmModel, llmApiKey, llmTimeoutSeconds, llmTemperature,
            maxTitleLength, streakWindowMinutes, defaultTitle, DEFAULT_DISPLAY_OFFSET_Y, false, true,
            ChatSettings.defaults());
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
