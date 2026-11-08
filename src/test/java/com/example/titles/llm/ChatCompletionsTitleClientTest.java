package com.example.titles.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.titles.api.AwardedTitle;
import com.example.titles.api.EventCategory;
import com.example.titles.api.EventContext;
import com.example.titles.api.LlmProvider;
import com.example.titles.api.TitleText;
import com.example.titles.api.TitlesSettings;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class ChatCompletionsTitleClientTest {
    private static final EventContext CONTEXT = new EventContext(UUID.randomUUID(), "IgnoredName",
        EventCategory.DEATH_MOB, "CREEPER", "PLAINS", "DIAMOND_SWORD", "IRON_HELMET", 2, "health=0.5");

    @Test
    void sendsAuthenticatedRequestAndExtractsTitle() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<JsonObject> requestBody = new AtomicReference<>();
        try (FakeServer server = new FakeServer(exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8))
                .getAsJsonObject());
            respond(exchange, 200, completion("<gold>Explosive Exit</gold>", 72));
        })) {
            Optional<AwardedTitle> result = client(server, settings(server, true, "test-key", 3, -1))
                .generateTitle(CONTEXT).get(3, TimeUnit.SECONDS);
            assertTrue(result.isPresent());
            assertEquals("<gold>Explosive Exit</gold>", result.get().miniMessageText());
            assertEquals(72, result.get().priority());
            assertTrue(result.get().fromLlm());
            assertEquals("Bearer test-key", authorization.get());
            assertEquals("deepseek-chat", requestBody.get().get("model").getAsString());
            assertEquals("json_object", requestBody.get().getAsJsonObject("response_format").get("type").getAsString());
            assertEquals(2, requestBody.get().getAsJsonArray("messages").size());
            String userContent = requestBody.get().getAsJsonArray("messages").get(1).getAsJsonObject().get("content").getAsString();
            assertTrue(userContent.contains("category=DEATH_MOB"));
            assertTrue(userContent.contains("extraMetadata=health=0.5"));
            assertFalse(userContent.contains(CONTEXT.playerId().toString()));
        }
    }

    @Test
    void extractsJsonFromFencedAndProseWrappedContent() {
        Optional<AwardedTitle> parsed = parse("Here is your result:\n```json\n{\"title\":\"<red>Oops\" ,\"priority\":25}\n``` done.");
        assertTrue(parsed.isPresent());
        assertEquals("<red>Oops", parsed.get().miniMessageText());
    }

    @Test
    void clampsPriorityAndDefaultsMissingPriority() {
        assertEquals(89, parse("{\"title\":\"A\",\"priority\":100}").orElseThrow().priority());
        assertEquals(1, parse("{\"title\":\"A\",\"priority\":0}").orElseThrow().priority());
        assertEquals(50, parse("{\"title\":\"A\"}").orElseThrow().priority());
        assertEquals(50, parse("{\"title\":\"A\",\"priority\":\"high\"}").orElseThrow().priority());
    }

    @Test
    void rejectsTagOnlyOrBlankVisibleTitle() {
        assertTrue(parse("{\"title\":\"<red></red>\",\"priority\":50}").isEmpty());
        assertTrue(parse("{\"title\":\"   \"}").isEmpty());
    }

    @Test
    void truncatesVisibleTextAndClosesTags() {
        AwardedTitle title = parse("{\"title\":\"<red>Creeper Hugger</red>\",\"priority\":50}", 5).orElseThrow();
        assertEquals("<red>Creep</red>", title.miniMessageText());
        assertEquals(5, TitleText.visibleLength(title.miniMessageText()));
    }

    @Test
    void unlimitedLengthPassesTitleThrough() {
        String title = "<gradient:#a:#b>A very long title</gradient>";
        assertEquals(title, parse("{\"title\":\"" + title + "\"}", -1).orElseThrow().miniMessageText());
    }

    @Test
    void malformedJsonMissingChoicesAndServerErrorReturnEmpty() throws Exception {
        assertTrue(ChatCompletionsTitleClient.parseResponse(200, "not json", -1).isEmpty());
        assertTrue(ChatCompletionsTitleClient.parseResponse(200, "{}", -1).isEmpty());
        try (FakeServer server = new FakeServer(exchange -> respond(exchange, 500, "failure"))) {
            assertTrue(client(server, settings(server, true, "test-key", 3, -1))
                .generateTitle(CONTEXT).get(3, TimeUnit.SECONDS).isEmpty());
        }
    }

    @Test
    void rejectsLegacyColorCodes() {
        assertTrue(parse("{\"title\":\"\u00a7cRed title\"}").isEmpty());
        assertTrue(parse("{\"title\":\"&cRed title\"}").isEmpty());
    }

    @Test
    void timeoutCompletesEmptyWithoutThrowing() throws Exception {
        try (FakeServer server = new FakeServer(exchange -> {
            try {
                Thread.sleep(3_000);
                respond(exchange, 200, completion("<red>Too Late</red>", 50));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        })) {
            long started = System.nanoTime();
            Optional<AwardedTitle> result = client(server, settings(server, true, "test-key", 1, -1))
                .generateTitle(CONTEXT).get(3, TimeUnit.SECONDS);
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            assertTrue(result.isEmpty());
            assertTrue(elapsedMillis < 2_500, "timeout took " + elapsedMillis + " ms");
        }
    }

    @Test
    void disabledOrPlaceholderKeyDoesNotCallHttpServer() throws Exception {
        Assumptions.assumeTrue(System.getenv("DEEPSEEK_API_KEY") == null);
        AtomicInteger calls = new AtomicInteger();
        try (FakeServer server = new FakeServer(exchange -> {
            calls.incrementAndGet();
            respond(exchange, 200, completion("<red>Unexpected</red>", 50));
        })) {
            assertTrue(client(server, settings(server, false, "test-key", 1, -1))
                .generateTitle(CONTEXT).get(1, TimeUnit.SECONDS).isEmpty());
            assertTrue(client(server, settings(server, true, TitlesSettings.PLACEHOLDER_KEY, 1, -1))
                .generateTitle(CONTEXT).get(1, TimeUnit.SECONDS).isEmpty());
            assertEquals(0, calls.get());
        }
    }

    @Test
    void grokRequestUsesGrokModelPersonaAndNoJsonMode() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<JsonObject> requestBody = new AtomicReference<>();
        try (FakeServer server = new FakeServer(exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8))
                .getAsJsonObject());
            // Grok replies without JSON mode may wrap the object in prose; the lenient parser must cope.
            respond(exchange, 200, completion("ok here you go: {\"title\":\"<aqua>Ratio'd by Gravity</aqua>\",\"priority\":40}"));
        })) {
            AwardedTitle title = client(server, grokSettings(server, "xai-test-key"))
                .generateTitle(CONTEXT).get(3, TimeUnit.SECONDS).orElseThrow();
            assertEquals("<aqua>Ratio'd by Gravity</aqua>", title.miniMessageText());
            assertEquals(40, title.priority());
            assertEquals("Bearer xai-test-key", authorization.get());
            assertEquals("grok-4.7", requestBody.get().get("model").getAsString());
            assertEquals(0.9, requestBody.get().get("temperature").getAsDouble(), 1e-9);
            assertFalse(requestBody.get().has("response_format"));
            String system = requestBody.get().getAsJsonArray("messages").get(0).getAsJsonObject().get("content").getAsString();
            assertTrue(system.contains("X/Twitter"));
        }
    }

    @Test
    void parsesSanitizedCommentWithPlayerToken() {
        AwardedTitle title = parse("{\"title\":\"<green>Creeper Hugger</green>\",\"priority\":30,"
            + "\"comment\":\"<player> really <bold>hugged</bold> that creeper <click:run_command:/op me>free op</click>\"}")
            .orElseThrow();
        assertEquals("<player> really <bold>hugged</bold> that creeper free op", title.comment());
        assertTrue(title.fromLlm());
    }

    @Test
    void badOrMissingCommentNeverSinksTheTitle() {
        assertEquals(null, parse("{\"title\":\"<red>Oops</red>\"}").orElseThrow().comment());
        assertEquals(null, parse("{\"title\":\"<red>Oops</red>\",\"comment\":\"&cLegacy\"}").orElseThrow().comment());
        assertEquals(null, parse("{\"title\":\"<red>Oops</red>\",\"comment\":\"<player>\"}").orElseThrow().comment());
        assertEquals(null, parse("{\"title\":\"<red>Oops</red>\",\"comment\":42}").orElseThrow().comment());
    }

    @Test
    void commentIsLengthCapped() {
        String longComment = "a".repeat(400);
        String comment = parse("{\"title\":\"<red>Oops</red>\",\"comment\":\"" + longComment + "\"}").orElseThrow().comment();
        assertEquals(ChatCompletionsTitleClient.MAX_COMMENT_LENGTH, TitleText.visibleLength(comment));
    }

    @Test
    void titleTagsAreSanitized() {
        AwardedTitle title = parse("{\"title\":\"<hover:show_text:'x'><gold>Sneaky</gold></hover>\"}").orElseThrow();
        assertEquals("<gold>Sneaky</gold>", title.miniMessageText());
    }

    @Test
    void promptAsksForCommentOnlyWhenChatEnabled() {
        TitlesSettings on = TitlesSettings.defaults();
        assertTrue(ChatCompletionsTitleClient.systemPrompt(on).contains("\"comment\""));
        TitlesSettings off = new TitlesSettings(on.llmEnabled(), on.llmProvider(), on.llmEndpoint(), on.llmModel(),
            on.llmApiKey(), on.llmTimeoutSeconds(), on.llmTemperature(), on.maxTitleLength(),
            on.streakWindowMinutes(), on.defaultTitle(), on.displayOffsetY(), on.showToSelf(), on.announceToSelf(),
            new com.example.titles.api.ChatSettings(false, null, 64, null, 15));
        assertFalse(ChatCompletionsTitleClient.systemPrompt(off).contains("\"comment\""));
    }

    @Test
    void personasDifferPerProvider() {
        assertTrue(ChatCompletionsTitleClient.persona(LlmProvider.DEEPSEEK).contains("sarcastic"));
        assertTrue(ChatCompletionsTitleClient.persona(LlmProvider.GROK).contains("quote-tweeting"));
    }

    @Test
    void providerLookupAndPlaceholderKeys() {
        assertEquals(Optional.of(LlmProvider.GROK), LlmProvider.fromConfig("grok"));
        assertEquals(Optional.of(LlmProvider.GROK), LlmProvider.fromConfig(" XAI "));
        assertEquals(Optional.of(LlmProvider.DEEPSEEK), LlmProvider.fromConfig("DeepSeek"));
        assertTrue(LlmProvider.fromConfig("openai").isEmpty());
        assertTrue(TitlesSettings.isPlaceholderKey("sk-your-deepseek-key-here"));
        assertTrue(TitlesSettings.isPlaceholderKey("xai-your-grok-key-here"));
        assertTrue(TitlesSettings.isPlaceholderKey(""));
        assertFalse(TitlesSettings.isPlaceholderKey("xai-abc123"));
    }

    private static Optional<AwardedTitle> parse(String content) {
        return parse(content, -1);
    }

    private static Optional<AwardedTitle> parse(String content, int maxLength) {
        return ChatCompletionsTitleClient.parseResponse(200, completion(content), maxLength);
    }

    private static String completion(String content, int priority) {
        return "{\"choices\":[{\"message\":{\"content\":" + new com.google.gson.Gson().toJson(
            "{\"title\":" + new com.google.gson.Gson().toJson(content) + ",\"priority\":" + priority + "}") + "}}]}";
    }

    private static String completion(String content) {
        return "{\"choices\":[{\"message\":{\"content\":" + new com.google.gson.Gson().toJson(content) + "}}]}";
    }

    private static ChatCompletionsTitleClient client(FakeServer server, TitlesSettings settings) {
        return new ChatCompletionsTitleClient(() -> settings, HttpClient.newHttpClient());
    }

    private static TitlesSettings settings(FakeServer server, boolean enabled, String key, int timeout, int maxLength) {
        return new TitlesSettings(enabled, LlmProvider.DEEPSEEK, server.endpoint(), "deepseek-chat", key,
            timeout, 0.7, maxLength, 5, "<gray>Default</gray>");
    }

    private static TitlesSettings grokSettings(FakeServer server, String key) {
        return new TitlesSettings(true, LlmProvider.GROK, server.endpoint(), "grok-4.7", key,
            3, 0.9, -1, 5, "<gray>Default</gray>");
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @FunctionalInterface
    private interface Handler {
        void handle(HttpExchange exchange) throws IOException;
    }

    private static final class FakeServer implements AutoCloseable {
        private final HttpServer server;

        FakeServer(Handler handler) throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/chat/completions", handler::handle);
            server.start();
        }

        String endpoint() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/chat/completions";
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
