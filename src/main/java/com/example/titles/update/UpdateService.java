package com.example.titles.update;

import java.io.File;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Checks GitHub Releases off the main thread and either notifies admins (default) or downloads the
 * verified jar into Paper's update folder, which Paper swaps in on the next restart. Never touches the
 * running plugin.
 */
public final class UpdateService implements Listener {
    public static final String NOTIFY_PERMISSION = "titles.admin";

    /** Bukkit-free view of the {@code updates:} config section. */
    public record Settings(boolean enabled, String repository, boolean autoDownload, int checkIntervalHours) {
        public static final String DEFAULT_REPOSITORY = "cadenh-dev/EventTitles";

        public Settings {
            if (!GitHubReleaseClient.isValidRepository(repository)) {
                repository = DEFAULT_REPOSITORY;
            }
            checkIntervalHours = Math.clamp(checkIntervalHours, 1, 168);
        }

        public static Settings defaults() {
            return new Settings(true, DEFAULT_REPOSITORY, false, 6);
        }
    }

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final Plugin plugin;
    private final Supplier<Settings> settings;
    private final String currentVersion;
    private final File pluginJar;
    private final GitHubReleaseClient client;
    private final AtomicReference<GitHubReleaseClient.Release> available = new AtomicReference<>();
    /** Version already saved to the update folder this session (null = none). */
    private final AtomicReference<String> downloadedVersion = new AtomicReference<>();
    private final AtomicBoolean checking = new AtomicBoolean();
    private BukkitTask task;

    /** {@code pluginJar} is this plugin's jar file; the update is saved under the same name. */
    public UpdateService(Plugin plugin, Supplier<Settings> settings, File pluginJar) {
        this.plugin = plugin;
        this.settings = settings;
        this.currentVersion = plugin.getPluginMeta().getVersion();
        this.pluginJar = pluginJar;
        this.client = new GitHubReleaseClient(currentVersion);
    }

    /** (Re)schedules periodic checks from current settings; first check ~10s after startup. */
    public void start() {
        stop();
        Settings s = settings.get();
        if (!s.enabled()) {
            return;
        }
        long period = s.checkIntervalHours() * 60L * 60L * 20L;
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::checkNow, 20L * 10, period);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public Optional<GitHubReleaseClient.Release> availableUpdate() {
        return Optional.ofNullable(available.get());
    }

    /** Blocking check; run off the main thread. Safe to call concurrently (extra calls are skipped). */
    public void checkNow() {
        if (!checking.compareAndSet(false, true)) {
            return;
        }
        try {
            Settings s = settings.get();
            Optional<GitHubReleaseClient.Release> latest = client.latestRelease(s.repository());
            if (latest.isEmpty() || !Versions.isNewer(latest.get().version(), currentVersion)) {
                available.set(null);
                return;
            }
            GitHubReleaseClient.Release release = latest.get();
            GitHubReleaseClient.Release previous = available.getAndSet(release);
            boolean firstSighting = previous == null || !previous.version().equals(release.version());
            if (s.autoDownload() && !release.version().equals(downloadedVersion.get())) {
                Path target = Bukkit.getUpdateFolderFile().toPath().resolve(pluginJar.getName());
                client.downloadVerified(release, target);
                downloadedVersion.set(release.version());
                plugin.getLogger().info("Downloaded and verified EventTitles " + release.version() + " to "
                    + target + "; it will be installed on the next server restart.");
                notifyAdmins(release);
            } else if (firstSighting) {
                plugin.getLogger().info("EventTitles " + release.version() + " is available (running "
                    + currentVersion + "): " + release.pageUrl());
                notifyAdmins(release);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Update check failed: " + e.getMessage());
            plugin.getLogger().log(Level.FINE, "Update check failure", e);
        } finally {
            checking.set(false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        GitHubReleaseClient.Release release = available.get();
        if (release != null && event.getPlayer().hasPermission(NOTIFY_PERMISSION)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (event.getPlayer().isOnline()) {
                    message(event.getPlayer(), release);
                }
            }, 40L);
        }
    }

    private void notifyAdmins(GitHubReleaseClient.Release release) {
        if (!plugin.isEnabled()) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.hasPermission(NOTIFY_PERMISSION)) {
                    message(p, release);
                }
            }
        });
    }

    private void message(Player player, GitHubReleaseClient.Release release) {
        boolean ready = release.version().equals(downloadedVersion.get());
        Component link = Component.empty();
        String page = release.pageUrl();
        if (page != null && page.startsWith("https://github.com/")) { // only ever link to GitHub
            link = Component.text(" View release", NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.openUrl(page));
        }
        player.sendMessage(MM.deserialize("<gold>[EventTitles]</gold> <white>Version <new></white> <gray>(you run "
                + "<old>)</gray> " + (ready
                    ? "<green>downloaded</green><gray>; restart the server to install it.</gray>"
                    : "<gray>is available.</gray>"),
            Placeholder.unparsed("new", release.version()),
            Placeholder.unparsed("old", currentVersion)).append(link));
    }
}
