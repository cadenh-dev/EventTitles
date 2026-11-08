package com.example.titles;

import com.example.titles.api.LlmTitleGenerator;
import com.example.titles.api.StreakService;
import com.example.titles.api.TitleDisplayService;
import com.example.titles.api.TitleResolverService;
import com.example.titles.api.TitlesSettings;
import com.example.titles.commands.TitleAdminCommand;
import com.example.titles.display.DisplayLifecycleListener;
import com.example.titles.display.PaperTitleDisplayManager;
import com.example.titles.engine.HybridTitleResolver;
import com.example.titles.engine.StaticTitleRegistry;
import com.example.titles.engine.StreakTracker;
import com.example.titles.listeners.GameplayEventCollector;
import com.example.titles.llm.DeepSeekTitleClient;
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

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadSettings();

        StreakTracker streakTracker = new StreakTracker(settings::get);
        StreakService streaks = streakTracker;
        LlmTitleGenerator llm = new DeepSeekTitleClient(settings::get);
        StaticTitleRegistry registry = new StaticTitleRegistry();
        TitleResolverService resolver = new HybridTitleResolver(registry, llm, settings::get);
        displayService = new PaperTitleDisplayManager(this, settings::get);

        var pm = getServer().getPluginManager();
        pm.registerEvents(new DisplayLifecycleListener(this, displayService), this);
        pm.registerEvents(new GameplayEventCollector(this, resolver, displayService, streaks), this);

        registerCommand("title", "Manage event titles", List.of("eventtitle"),
            new TitleAdminCommand(this, displayService, llm, settings::get, this::reloadSettings));

        // StreakTracker is Bukkit-free and thread-safe, so purging off the main thread is allowed.
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, streakTracker::purgeExpired,
            STREAK_PURGE_PERIOD_TICKS, STREAK_PURGE_PERIOD_TICKS);

        // Restore displays for players already online (e.g. after a /reload or plugin manager load).
        for (Player player : Bukkit.getOnlinePlayers()) {
            displayService.refreshDisplay(player);
        }

        getLogger().info(() -> "EventTitles enabled with " + registry.size() + " static rules; LLM "
            + (settings.get().llmEnabled() && settings.get().effectiveApiKey() != null ? "active" : "inactive") + ".");
    }

    @Override
    public void onDisable() {
        if (displayService != null) {
            displayService.cleanupAll();
        }
    }

    /** Re-reads config.yml into an immutable snapshot; components observe it through {@code settings::get}. */
    private void reloadSettings() {
        reloadConfig();
        FileConfiguration c = getConfig();
        TitlesSettings d = TitlesSettings.defaults();
        TitlesSettings loaded = new TitlesSettings(
            c.getBoolean("llm.enabled", d.llmEnabled()),
            c.getString("llm.provider", d.llmProvider()),
            c.getString("llm.endpoint", d.llmEndpoint()),
            c.getString("llm.model", d.llmModel()),
            c.getString("llm.api_key", d.llmApiKey()),
            Math.max(1, c.getInt("llm.timeout_seconds", d.llmTimeoutSeconds())),
            c.getDouble("llm.temperature", d.llmTemperature()),
            c.getInt("settings.max_title_length", d.maxTitleLength()),
            Math.max(1, c.getInt("settings.streak_window_minutes", d.streakWindowMinutes())),
            c.getString("fallbacks.default_title", d.defaultTitle()));
        settings.set(loaded);
        if (loaded.llmEnabled() && loaded.effectiveApiKey() == null) {
            getLogger().warning("llm.enabled is true but no DeepSeek API key is configured "
                + "(set llm.api_key or DEEPSEEK_API_KEY); using static titles only.");
        }
    }
}
