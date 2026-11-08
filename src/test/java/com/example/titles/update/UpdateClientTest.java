package com.example.titles.update;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UpdateClientTest {
    @TempDir
    Path dir;

    @Test
    void versionComparison() {
        assertTrue(Versions.isNewer("v1.10.0", "1.9.2"));
        assertTrue(Versions.isNewer("1.0.1", "1.0.0"));
        assertFalse(Versions.isNewer("1.2", "1.2.0"));
        assertFalse(Versions.isNewer("1.0.0", "1.0.0"));
        assertFalse(Versions.isNewer("0.9.9", "1.0.0"));
        assertTrue(Versions.isNewer("1.0.0", "1.0.0-SNAPSHOT"));
        assertEquals("1.4.0", Versions.normalize(" V1.4.0 "));
    }

    @Test
    void parsesReleaseRequiringChecksumAsset() {
        String json = release("v1.2.0", false, "EventTitles-1.2.0.jar", "EventTitles-1.2.0.jar.sha256");
        var r = GitHubReleaseClient.parseRelease(json).orElseThrow();
        assertEquals("1.2.0", r.version());
        assertEquals("EventTitles-1.2.0.jar", r.jarName());
        assertTrue(GitHubReleaseClient.parseRelease(release("v1.2.0", false, "EventTitles-1.2.0.jar", null)).isEmpty());
        assertTrue(GitHubReleaseClient.parseRelease(release("v1.2.0", true, "EventTitles-1.2.0.jar",
            "EventTitles-1.2.0.jar.sha256")).isEmpty(), "prereleases are ignored");
    }

    @Test
    void repositoryValidation() {
        assertTrue(GitHubReleaseClient.isValidRepository("cadenh-dev/EventTitles"));
        assertFalse(GitHubReleaseClient.isValidRepository("cadenh-dev/EventTitles/../x"));
        assertFalse(GitHubReleaseClient.isValidRepository("https://github.com/a/b"));
        assertFalse(GitHubReleaseClient.isValidRepository(null));
    }

    @Test
    void checksumParsing() throws IOException {
        String hex = "a".repeat(64);
        assertEquals(hex, GitHubReleaseClient.parseChecksum(hex + "  EventTitles-1.2.0.jar\n"));
        assertThrows(IOException.class, () -> GitHubReleaseClient.parseChecksum("nope"));
    }

    @Test
    void downloadsVerifiedJarAndRejectsTampering() throws Exception {
        byte[] jar = "fake jar bytes".getBytes(StandardCharsets.UTF_8);
        String good = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(jar));
        try (Fake server = new Fake(Map.of(
                "/jar", jar,
                "/good.sha256", (good + "  EventTitles-1.2.0.jar").getBytes(StandardCharsets.UTF_8),
                "/bad.sha256", ("b".repeat(64) + "  EventTitles-1.2.0.jar").getBytes(StandardCharsets.UTF_8)))) {
            GitHubReleaseClient client = new GitHubReleaseClient(HttpClient.newHttpClient(), server.base(), "test", true);
            Path target = dir.resolve("update").resolve("EventTitles.jar");

            client.downloadVerified(release(server, "/good.sha256"), target);
            assertArrayEquals(jar, Files.readAllBytes(target));

            Path other = dir.resolve("update").resolve("Other.jar");
            assertThrows(IOException.class, () -> client.downloadVerified(release(server, "/bad.sha256"), other));
            assertFalse(Files.exists(other), "nothing written on checksum mismatch");
            try (var leftovers = Files.list(other.getParent())) {
                assertEquals(1, leftovers.count(), "temp file cleaned up");
            }
        }
    }

    @Test
    void productionClientRefusesNonGithubHosts() {
        GitHubReleaseClient client = new GitHubReleaseClient("test");
        var evil = new GitHubReleaseClient.Release("v9", "9", "https://github.com/x/y", "EventTitles-9.jar",
            java.net.URI.create("https://evil.example/EventTitles-9.jar"),
            java.net.URI.create("https://github.com/x/y/releases/download/v9/EventTitles-9.jar.sha256"));
        IOException e = assertThrows(IOException.class, () -> client.downloadVerified(evil, dir.resolve("x.jar")));
        assertTrue(e.getMessage().contains("untrusted"), e.getMessage());
    }

    private static GitHubReleaseClient.Release release(Fake server, String checksumPath) {
        return new GitHubReleaseClient.Release("v1.2.0", "1.2.0", "https://github.com/x/y", "EventTitles-1.2.0.jar",
            java.net.URI.create(server.base() + "/jar"), java.net.URI.create(server.base() + checksumPath));
    }

    private static String release(String tag, boolean prerelease, String jar, String checksum) {
        String assets = "{\"name\":\"" + jar + "\",\"browser_download_url\":\"https://github.com/x/y/releases/download/"
            + tag + "/" + jar + "\"}";
        if (checksum != null) {
            assets += ",{\"name\":\"" + checksum + "\",\"browser_download_url\":\"https://github.com/x/y/releases/download/"
                + tag + "/" + checksum + "\"}";
        }
        return "{\"tag_name\":\"" + tag + "\",\"draft\":false,\"prerelease\":" + prerelease
            + ",\"html_url\":\"https://github.com/x/y/releases/tag/" + tag + "\",\"assets\":[" + assets + "]}";
    }

    private static final class Fake implements AutoCloseable {
        private final HttpServer server;

        Fake(Map<String, byte[]> routes) throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            routes.forEach((path, body) -> server.createContext(path, ex -> {
                ex.sendResponseHeaders(200, body.length);
                ex.getResponseBody().write(body);
                ex.close();
            }));
            server.start();
        }

        String base() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
