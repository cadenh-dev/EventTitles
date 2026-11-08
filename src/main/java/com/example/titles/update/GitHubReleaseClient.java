package com.example.titles.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Bukkit-free GitHub Releases client for the update checker. Blocking; callers must run it off the
 * main thread. Only HTTPS, only GitHub hosts, and every jar must match its published SHA-256.
 */
public final class GitHubReleaseClient {
    /** owner/name, GitHub's allowed characters. */
    private static final Pattern REPO = Pattern.compile("[A-Za-z0-9-]{1,39}/[A-Za-z0-9._-]{1,100}");
    private static final Set<String> DOWNLOAD_HOSTS = Set.of("github.com", "api.github.com",
        "objects.githubusercontent.com", "release-assets.githubusercontent.com");
    private static final long MAX_JAR_BYTES = 32L * 1024 * 1024;

    /** A release with a jar and its checksum file. */
    public record Release(String tag, String version, String pageUrl, String jarName, URI jarUrl, URI checksumUrl) {}

    private final HttpClient http;
    private final String apiBase;
    private final String userAgent;
    private final boolean allowAnyHost;

    /** Production client against api.github.com. */
    public GitHubReleaseClient(String pluginVersion) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL).build(),
            "https://api.github.com", pluginVersion, false);
    }

    /** For tests: custom client/base URL; {@code allowAnyHost} lifts the HTTPS/GitHub-host rule. */
    GitHubReleaseClient(HttpClient http, String apiBase, String pluginVersion, boolean allowAnyHost) {
        this.http = http;
        this.apiBase = apiBase;
        this.userAgent = "EventTitles-UpdateChecker/" + pluginVersion;
        this.allowAnyHost = allowAnyHost;
    }

    public static boolean isValidRepository(String repository) {
        return repository != null && REPO.matcher(repository).matches();
    }

    /** Latest non-draft, non-prerelease release that has a jar + .sha256 asset; empty if none. */
    public Optional<Release> latestRelease(String repository) throws IOException, InterruptedException {
        if (!isValidRepository(repository)) {
            throw new IllegalArgumentException("Invalid repository: " + repository);
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(apiBase + "/repos/" + repository + "/releases/latest"))
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", userAgent)
            .timeout(Duration.ofSeconds(15))
            .GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404) {
            return Optional.empty(); // no releases yet
        }
        if (response.statusCode() != 200) {
            throw new IOException("GitHub API returned HTTP " + response.statusCode());
        }
        return parseRelease(response.body());
    }

    static Optional<Release> parseRelease(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        if (bool(root, "draft") || bool(root, "prerelease")) {
            return Optional.empty();
        }
        String tag = string(root, "tag_name");
        if (tag == null) {
            return Optional.empty();
        }
        String version = Versions.normalize(tag);
        JsonElement assets = root.get("assets");
        if (assets == null || !assets.isJsonArray()) {
            return Optional.empty();
        }
        String jarName = null;
        URI jarUrl = null;
        URI checksumUrl = null;
        JsonArray list = assets.getAsJsonArray();
        for (JsonElement e : list) {
            JsonObject asset = e.getAsJsonObject();
            String name = string(asset, "name");
            String url = string(asset, "browser_download_url");
            if (name == null || url == null) {
                continue;
            }
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".jar") && !lower.contains("sources") && !lower.contains("javadoc")) {
                jarName = name;
                jarUrl = URI.create(url);
            }
        }
        if (jarName == null) {
            return Optional.empty();
        }
        for (JsonElement e : list) {
            JsonObject asset = e.getAsJsonObject();
            if ((jarName + ".sha256").equals(string(asset, "name")) && string(asset, "browser_download_url") != null) {
                checksumUrl = URI.create(string(asset, "browser_download_url"));
            }
        }
        if (checksumUrl == null) {
            return Optional.empty(); // never offer an unverifiable jar
        }
        return Optional.of(new Release(tag, version, string(root, "html_url"), jarName, jarUrl, checksumUrl));
    }

    /**
     * Downloads the release jar, verifies it against the release's SHA-256 file, then atomically moves it
     * to {@code target}. Nothing is written to {@code target} unless the checksum matches.
     */
    public void downloadVerified(Release release, Path target) throws IOException, InterruptedException {
        requireAllowed(release.jarUrl());
        requireAllowed(release.checksumUrl());
        String expected = fetchChecksum(release.checksumUrl());

        Files.createDirectories(target.toAbsolutePath().getParent());
        Path temp = Files.createTempFile(target.toAbsolutePath().getParent(), ".eventtitles-", ".part");
        try {
            HttpResponse<InputStream> response = http.send(get(release.jarUrl(), Duration.ofSeconds(120)),
                HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                response.body().close();
                throw new IOException("Jar download returned HTTP " + response.statusCode());
            }
            requireAllowed(response.uri()); // final URL after redirects
            MessageDigest sha256 = sha256();
            try (InputStream in = response.body(); var out = Files.newOutputStream(temp)) {
                byte[] buffer = new byte[64 * 1024];
                long total = 0;
                int read;
                while ((read = in.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_JAR_BYTES) {
                        throw new IOException("Jar is larger than " + MAX_JAR_BYTES + " bytes; refusing");
                    }
                    sha256.update(buffer, 0, read);
                    out.write(buffer, 0, read);
                }
            }
            String actual = HexFormat.of().formatHex(sha256.digest());
            if (!actual.equalsIgnoreCase(expected)) {
                throw new IOException("Checksum mismatch: expected " + expected + " but downloaded " + actual);
            }
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private String fetchChecksum(URI url) throws IOException, InterruptedException {
        HttpResponse<String> response = http.send(get(url, Duration.ofSeconds(20)),
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IOException("Checksum download returned HTTP " + response.statusCode());
        }
        requireAllowed(response.uri());
        return parseChecksum(response.body());
    }

    /** First token of a sha256sum-style line; must be 64 hex chars. */
    static String parseChecksum(String body) throws IOException {
        String token = body == null ? "" : body.trim().split("\\s+")[0];
        if (!token.matches("[0-9a-fA-F]{64}")) {
            throw new IOException("Malformed .sha256 file");
        }
        return token.toLowerCase(Locale.ROOT);
    }

    private HttpRequest get(URI url, Duration timeout) {
        return HttpRequest.newBuilder(url).header("User-Agent", userAgent).header("Accept", "application/octet-stream")
            .timeout(timeout).GET().build();
    }

    private void requireAllowed(URI url) throws IOException {
        if (allowAnyHost) {
            return;
        }
        if (url == null || !"https".equalsIgnoreCase(url.getScheme()) || url.getHost() == null
            || !DOWNLOAD_HOSTS.contains(url.getHost().toLowerCase(Locale.ROOT))) {
            throw new IOException("Refusing to download from untrusted URL: " + url);
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String string(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() || !e.isJsonPrimitive() ? null : e.getAsString();
    }

    private static boolean bool(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsBoolean();
    }
}
