package com.example.titles.llm;

import com.example.titles.api.AwardedTitle;
import com.example.titles.api.EventContext;
import com.example.titles.api.LlmTitleGenerator;
import com.example.titles.api.TitleText;
import com.example.titles.api.TitlesSettings;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.regex.Pattern;

/** Bukkit-free asynchronous DeepSeek chat completions client. */
public final class DeepSeekTitleClient implements LlmTitleGenerator {
    private static final Logger LOGGER = Logger.getLogger("EventTitles/DeepSeek");
    private static final Gson GSON = new GsonBuilder().create();
    private static final Pattern LEGACY_COLOR = Pattern.compile("&[0-9a-fk-or]", Pattern.CASE_INSENSITIVE);
    private final Supplier<TitlesSettings> settings;
    private final HttpClient http;

    public DeepSeekTitleClient(Supplier<TitlesSettings> settings) {
        this(settings, defaultClient(settings));
    }

    public DeepSeekTitleClient(Supplier<TitlesSettings> settings, HttpClient http) {
        this.settings = settings;
        this.http = http;
    }

    private static HttpClient defaultClient(Supplier<TitlesSettings> settings) {
        int timeout = Math.max(1, settings.get().llmTimeoutSeconds());
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(timeout)).build();
    }

    @Override
    public CompletableFuture<Optional<AwardedTitle>> generateTitle(EventContext ctx) {
        try {
            TitlesSettings s = settings.get();
            String key = s.effectiveApiKey();
            if (!s.llmEnabled() || key == null) {
                return CompletableFuture.completedFuture(Optional.empty());
            }

            JsonObject body = new JsonObject();
            body.addProperty("model", s.llmModel());
            body.addProperty("temperature", s.llmTemperature());
            body.addProperty("max_tokens", 120);
            JsonObject responseFormat = new JsonObject();
            responseFormat.addProperty("type", "json_object");
            body.add("response_format", responseFormat);
            var messages = new com.google.gson.JsonArray();
            JsonObject system = new JsonObject();
            system.addProperty("role", "system");
            system.addProperty("content", systemPrompt(s));
            JsonObject user = new JsonObject();
            user.addProperty("role", "user");
            user.addProperty("content", describe(ctx));
            messages.add(system);
            messages.add(user);
            body.add("messages", messages);

            int timeout = Math.max(1, s.llmTimeoutSeconds());
            HttpRequest request = HttpRequest.newBuilder(URI.create(s.llmEndpoint()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + key)
                .timeout(Duration.ofSeconds(timeout))
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                .build();
            return http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .orTimeout(timeout, TimeUnit.SECONDS)
                .thenApply(response -> parseResponse(response.statusCode(), response.body(), s.maxTitleLength()))
                .exceptionally(error -> {
                    LOGGER.log(Level.FINE, "DeepSeek request failed", error);
                    return Optional.empty();
                });
        } catch (Exception error) {
            LOGGER.log(Level.FINE, "Could not prepare DeepSeek request", error);
            return CompletableFuture.completedFuture(Optional.empty());
        }
    }

    private static String systemPrompt(TitlesSettings settings) {
        String prompt = "You are a sarcastic, witty Minecraft spectator/commentator who awards a short nickname title for what just happened. "
            + "Reply with ONLY a JSON object exactly {\"title\": \"<color>...</color>\", \"priority\": 50}. "
            + "The title uses Kyori MiniMessage tags only (named colors like <red>, <gold>, hex <#ff8800>, <gradient:#a:#b>, <bold>, <italic>); "
            + "never legacy & or § codes. Use 1–4 words, no player name, no quotes or emoji. "
            + "Priority is an integer from 1 to 89 reflecting how impressive or embarrassing; bosses are 80+, mundane deaths are 10–30.";
        if (settings.maxTitleLength() > 0) {
            prompt += " The visible text must be at most " + settings.maxTitleLength() + " characters.";
        }
        return prompt;
    }

    private static String describe(EventContext context) {
        return "category=" + context.category()
            + "; primarySubject=" + context.primarySubject()
            + "; biome=" + context.biome()
            + "; heldItem=" + context.heldItem()
            + "; armorSummary=" + context.armorSummary()
            + "; streakCount=" + context.streakCount()
            + "; extraMetadata=" + context.extraMetadata();
    }

    static Optional<AwardedTitle> parseResponse(int statusCode, String body, int maxTitleLength) {
        try {
            if (statusCode < 200 || statusCode >= 300) {
                return Optional.empty();
            }
            JsonElement root = JsonParser.parseString(body);
            JsonElement contentElement = root.getAsJsonObject().getAsJsonArray("choices")
                .get(0).getAsJsonObject().getAsJsonObject("message").get("content");
            if (contentElement == null || !contentElement.isJsonPrimitive()
                || !contentElement.getAsJsonPrimitive().isString()) {
                return Optional.empty();
            }
            String content = contentElement.getAsString();
            int first = content.indexOf('{');
            int last = content.lastIndexOf('}');
            if (first < 0 || last < first) {
                return Optional.empty();
            }
            JsonObject result = JsonParser.parseString(content.substring(first, last + 1)).getAsJsonObject();
            JsonElement titleElement = result.get("title");
            if (titleElement == null || !titleElement.isJsonPrimitive() || !titleElement.getAsJsonPrimitive().isString()) {
                return Optional.empty();
            }
            String title = titleElement.getAsString().trim();
            if (title.isBlank() || title.indexOf('\u00a7') >= 0 || LEGACY_COLOR.matcher(title).find()
                || TitleText.stripTags(title).isBlank()) {
                return Optional.empty();
            }
            int priority = 50;
            JsonElement priorityElement = result.get("priority");
            if (priorityElement != null && priorityElement.isJsonPrimitive()
                && priorityElement.getAsJsonPrimitive().isNumber()) {
                priority = priorityElement.getAsInt();
            }
            priority = Math.clamp(priority, 1, 89);
            return Optional.of(new AwardedTitle(TitleText.truncateVisible(title, maxTitleLength), priority, true));
        } catch (Exception error) {
            LOGGER.log(Level.FINE, "Could not parse DeepSeek response", error);
            return Optional.empty();
        }
    }
}
