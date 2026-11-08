package com.example.titles.api;

import java.util.Locale;
import java.util.Optional;

/**
 * Supported OpenAI-compatible chat-completions providers and their defaults. Bukkit-free.
 * Each provider's config section may override endpoint, model, api key, temperature and timeout.
 */
public enum LlmProvider {
    /** DeepSeek: very cheap, dry sarcastic spectator humour. */
    DEEPSEEK("deepseek", "https://api.deepseek.com/chat/completions", "deepseek-chat",
        "DEEPSEEK_API_KEY", 0.7, 3, true),
    /** xAI Grok: spicier, terminally-online X/Twitter style roasts. */
    GROK("grok", "https://api.x.ai/v1/chat/completions", "grok-4.7",
        "XAI_API_KEY", 0.9, 5, false);

    private final String configId;
    private final String defaultEndpoint;
    private final String defaultModel;
    private final String apiKeyEnv;
    private final double defaultTemperature;
    private final int defaultTimeoutSeconds;
    private final boolean jsonMode;

    LlmProvider(String configId, String defaultEndpoint, String defaultModel, String apiKeyEnv,
                double defaultTemperature, int defaultTimeoutSeconds, boolean jsonMode) {
        this.configId = configId;
        this.defaultEndpoint = defaultEndpoint;
        this.defaultModel = defaultModel;
        this.apiKeyEnv = apiKeyEnv;
        this.defaultTemperature = defaultTemperature;
        this.defaultTimeoutSeconds = defaultTimeoutSeconds;
        this.jsonMode = jsonMode;
    }

    /** Key used in config.yml ({@code llm.provider} and {@code llm.providers.<id>}). */
    public String configId() { return configId; }
    public String defaultEndpoint() { return defaultEndpoint; }
    public String defaultModel() { return defaultModel; }
    /** Environment variable consulted when the configured api_key is blank or a placeholder. */
    public String apiKeyEnv() { return apiKeyEnv; }
    public double defaultTemperature() { return defaultTemperature; }
    public int defaultTimeoutSeconds() { return defaultTimeoutSeconds; }
    /** Whether to send {@code response_format: {"type":"json_object"}}; replies are parsed leniently either way. */
    public boolean jsonMode() { return jsonMode; }

    /** Case-insensitive lookup by config id or enum name; accepts "xai" as an alias for Grok. */
    public static Optional<LlmProvider> fromConfig(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String v = value.trim().toLowerCase(Locale.ROOT);
        if (v.equals("xai") || v.equals("x.ai")) {
            return Optional.of(GROK);
        }
        for (LlmProvider p : values()) {
            if (p.configId.equals(v) || p.name().equalsIgnoreCase(v)) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }
}
