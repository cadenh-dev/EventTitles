package com.example.titles;

import com.example.titles.api.ChatPrefixSettings;
import com.example.titles.api.ChatSettings;
import com.example.titles.api.LlmProvider;
import com.example.titles.api.LlmTitleGenerator;
import com.example.titles.api.StreakService;
import com.example.titles.api.ShowcaseSettings;
import com.example.titles.api.TabListSettings;
import com.example.titles.api.TitleDisplayService;
import com.example.titles.api.TitleResolverService;
import com.example.titles.api.TitlesSettings;
import com.example.titles.commands.TitleAdminCommand;
import com.example.titles.display.DisplayLifecycleListener;
import com.example.titles.display.PaperTitleDisplayManager;
import com.example.titles.engine.HybridTitleResolver;
import com.example.titles.engine.StaticTitleRegistry;
import com.example.titles.engine.StreakTracker;
import com.example.titles.listeners.ChatTitleListener;
import com.example.titles.listeners.CommentaryBroadcaster;
import com.example.titles.listeners.GameplayEventCollector;
import com.example.titles.llm.ChatCompletionsTitleClient;
import com.example.titles.history.JsonTitleHistoryStore;
import com.example.titles.update.UpdateService;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Lifecycle entrypoint: reads config, wires services, registers listeners and the /title command. */
public final class TitlesPlugin extends JavaPlugin {
    private static final long STREAK_PURGE_PERIOD_TICKS = 20L * 60L * 5L;

    private final AtomicReference<TitlesSettings> settings = new AtomicReference<>(TitlesSettings.defaults());
    private TitleDisplayService displayService;
    private final AtomicReference<UpdateService.Settings> updateSettings =
        new AtomicReference<>(UpdateService.Settings.defaults());
    private UpdateService updateService;
    private JsonTitleHistoryStore historyStore;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadSettings();

        StreakTracker streakTracker = new StreakTracker(settings::get);
        StreakService streaks = streakTracker;
        LlmTitleGenerator llm = new ChatCompletionsTitleClient(settings::get);
        StaticTitleRegistry registry = new StaticTitleRegistry();
        TitleResolverService resolver = new HybridTitleResolver(registry, llm, settings::get);
        if (getConfig().getBoolean("history.enabled", true)) {
            historyStore = new JsonTitleHistoryStore(getDataFolder().toPath().resolve("history"),
                () -> Math.clamp(getConfig().getInt("history.max_entries", 50), 1, 500), getLogger());
        }
        displayService = new PaperTitleDisplayManager(this, settings::get, historyStore);

        var pm = getServer().getPluginManager();
        pm.registerEvents(new DisplayLifecycleListener(this, displayService), this);
        pm.registerEvents(new ChatTitleListener(displayService, settings::get, getLogger()), this);
        pm.registerEvents(new GameplayEventCollector(this, resolver, displayService, streaks,
            new CommentaryBroadcaster(settings::get)), this);

        registerCommand("title", "Manage event titles", List.of("eventtitle"),
            new TitleAdminCommand(this, displayService, llm, settings::get, () -> {
                reloadSettings();
                // Display options (offset, show_to_self) are applied at spawn, so rebuild live displays.
                Bukkit.getOnlinePlayers().forEach(displayService::refreshDisplay);
                updateService.start(); // picks up updates.* changes
            }, historyStore));

        // StreakTracker is Bukkit-free and thread-safe, so purging off the main thread is allowed.
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, streakTracker::purgeExpired,
            STREAK_PURGE_PERIOD_TICKS, STREAK_PURGE_PERIOD_TICKS);

        // GitHub release checks (async). Saves verified updates under this jar's file name in Paper's
        // update folder, which Paper installs on the next restart.
        updateService = new UpdateService(this, updateSettings::get, getFile());
        pm.registerEvents(updateService, this);
        updateService.start();

        // Restore displays for players already online (e.g. after a /reload or plugin manager load).
        for (Player player : Bukkit.getOnlinePlayers()) {
            displayService.refreshDisplay(player);
        }

        getLogger().info(() -> "EventTitles enabled with " + registry.size() + " static rules (" + registry.titleCount()
            + " titles); LLM "
            + (settings.get().llmEnabled() && settings.get().effectiveApiKey() != null ? "active" : "inactive") + ".");
    }

    @Override
    public void onDisable() {
        if (updateService != null) {
            updateService.stop();
        }
        if (displayService != null) {
            displayService.cleanupAll();
        }
        if (historyStore != null) {
            historyStore.close(); // flush pending history writes
        }
    }

    /** Re-reads config.yml into an immutable snapshot; components observe it through {@code settings::get}. */
    private void reloadSettings() {
        reloadConfig();
        FileConfiguration c = getConfig();
        TitlesSettings d = TitlesSettings.defaults();

        String providerName = c.getString("llm.provider", LlmProvider.DEEPSEEK.configId());
        LlmProvider provider = LlmProvider.fromConfig(providerName).orElseGet(() -> {
            getLogger().warning("Unknown llm.provider '" + providerName + "'; expected one of "
                + Arrays.stream(LlmProvider.values()).map(LlmProvider::configId).toList() + ". Using deepseek.");
            return LlmProvider.DEEPSEEK;
        });
        String section = "llm.providers." + provider.configId() + ".";
        // Configs written before multi-provider support kept DeepSeek's values directly under "llm.".
        boolean legacy = provider == LlmProvider.DEEPSEEK;

        TitlesSettings loaded = new TitlesSettings(
            c.getBoolean("llm.enabled", d.llmEnabled()),
            provider,
            providerString(c, section + "endpoint", legacy ? "llm.endpoint" : null, provider.defaultEndpoint()),
            providerString(c, section + "model", legacy ? "llm.model" : null, provider.defaultModel()),
            providerString(c, section + "api_key", legacy ? "llm.api_key" : null, ""),
            Math.max(1, c.getInt(section + "timeout_seconds",
                c.getInt("llm.timeout_seconds", provider.defaultTimeoutSeconds()))),
            c.getDouble(section + "temperature", c.getDouble("llm.temperature", provider.defaultTemperature())),
            c.getInt("settings.max_title_length", d.maxTitleLength()),
            Math.max(1, c.getInt("settings.streak_window_minutes", d.streakWindowMinutes())),
            c.getString("fallbacks.default_title", d.defaultTitle()),
            Math.clamp(c.getDouble("display.offset_y", d.displayOffsetY()), -2.0, 5.0),
            c.getBoolean("display.show_to_self", d.showToSelf()),
            c.getBoolean("display.announce_to_self", d.announceToSelf()),
            loadChat(c, d.chat()),
            loadTabList(c, d.tabList()),
            loadChatPrefix(c, d.chatPrefix()),
            c.getInt("display.interpolation_ticks", d.displayInterpolationTicks()),
            new ShowcaseSettings(c.getBoolean("showcase.enabled", d.showcase().enabled()),
                c.getInt("showcase.max_titles", d.showcase().maxTitles())),
            c.getInt("settings.protection_decay_minutes", d.protectionDecayMinutes()));
        settings.set(loaded);
        UpdateService.Settings ud = UpdateService.Settings.defaults();
        String repository = c.getString("updates.repository", ud.repository());
        if (!com.example.titles.update.GitHubReleaseClient.isValidRepository(repository)) {
            getLogger().warning("updates.repository must look like owner/name; using " + ud.repository() + ".");
        }
        updateSettings.set(new UpdateService.Settings(
            c.getBoolean("updates.enabled", ud.enabled()),
            repository,
            c.getBoolean("updates.auto_download", ud.autoDownload()),
            c.getInt("updates.check_interval_hours", ud.checkIntervalHours())));
        if (loaded.llmEnabled() && loaded.effectiveApiKey() == null) {
            getLogger().warning("llm.enabled is true but no " + provider.configId() + " API key is configured (set "
                + section + "api_key or " + provider.apiKeyEnv() + "); using static titles only.");
        } else if (loaded.llmEnabled()) {
            getLogger().info("LLM provider: " + provider.configId() + " (" + loaded.llmModel() + ")");
        }
    }

    private ChatPrefixSettings loadChatPrefix(FileConfiguration c, ChatPrefixSettings d) {
        String modeName = c.getString("chat_prefix.mode", d.mode().name());
        ChatPrefixSettings.Mode mode = ChatPrefixSettings.Mode.fromConfig(modeName).orElseGet(() -> {
            getLogger().warning("Unknown chat_prefix.mode '" + modeName + "'; expected replace or prefix. Using replace.");
            return ChatPrefixSettings.Mode.REPLACE;
        });
        String defaultFormat = mode == ChatPrefixSettings.Mode.REPLACE
            ? ChatPrefixSettings.DEFAULT_REPLACE_FORMAT : ChatPrefixSettings.DEFAULT_PREFIX_FORMAT;
        String format = c.getString("chat_prefix.format", defaultFormat);
        if (!ChatPrefixSettings.isValidFormat(mode, format)) {
            getLogger().warning("chat_prefix.format is invalid for mode " + mode.name().toLowerCase()
                + " (replace needs <title>, <player> and <message>; prefix needs <title>); using the default.");
        }
        return new ChatPrefixSettings(
            c.getBoolean("chat_prefix.enabled", d.enabled()),
            mode,
            format,
            c.getInt("chat_prefix.max_title_length", d.maxTitleLength()));
    }

    private TabListSettings loadTabList(FileConfiguration c, TabListSettings d) {
        String format = c.getString("tab_list.format", d.format());
        if (format == null || !format.contains(TabListSettings.TITLE_TOKEN)
            || !format.contains(TabListSettings.PLAYER_TOKEN)) {
            getLogger().warning("tab_list.format must contain " + TabListSettings.PLAYER_TOKEN + " and "
                + TabListSettings.TITLE_TOKEN + "; using the default.");
        }
        return new TabListSettings(
            c.getBoolean("tab_list.enabled", d.enabled()),
            format,
            c.getInt("tab_list.max_title_length", d.maxTitleLength()));
    }

    private ChatSettings loadChat(FileConfiguration c, ChatSettings d) {
        String audienceName = c.getString("chat.audience", d.audience().name());
        ChatSettings.Audience audience = ChatSettings.Audience.fromConfig(audienceName).orElseGet(() -> {
            getLogger().warning("Unknown chat.audience '" + audienceName + "'; expected server, world, nearby "
                + "or player. Using server.");
            return ChatSettings.Audience.SERVER;
        });
        String format = c.getString("chat.format", d.format());
        if (format == null || !format.contains(ChatSettings.COMMENT_TOKEN)) {
            getLogger().warning("chat.format must contain " + ChatSettings.COMMENT_TOKEN + "; using the default.");
        }
        return new ChatSettings(
            c.getBoolean("chat.enabled", d.enabled()),
            audience,
            c.getInt("chat.nearby_radius", d.nearbyRadius()),
            format,
            c.getInt("chat.cooldown_seconds", d.cooldownSeconds()));
    }

    private static String providerString(FileConfiguration c, String path, String legacyPath, String fallback) {
        String value = c.getString(path);
        if ((value == null || value.isBlank()) && legacyPath != null) {
            value = c.getString(legacyPath);
        }
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
