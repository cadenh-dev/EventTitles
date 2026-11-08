package com.example.titles.listeners;

import com.example.titles.api.AwardedTitle;
import com.example.titles.api.ChatPrefixSettings;
import com.example.titles.api.TitleDisplayService;
import com.example.titles.api.TitleText;
import com.example.titles.api.TitlesSettings;
import com.example.titles.display.TitleHover;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Shows the sender's title on their chat messages, e.g. {@code Creeper Magnet Caden: hi}; hovering the
 * title explains it. {@link AsyncChatEvent} runs off the main thread, so the title comes from
 * {@link TitleDisplayService#cachedTitle} (a thread-safe cache) and nothing else on the player is
 * touched. Players without a title keep the existing chat line untouched.
 */
public final class ChatTitleListener implements Listener {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final TitleDisplayService displays;
    private final Supplier<TitlesSettings> settings;
    private final Logger logger;

    public ChatTitleListener(TitleDisplayService displays, Supplier<TitlesSettings> settings, Logger logger) {
        this.displays = displays;
        this.settings = settings;
        this.logger = logger;
    }

    /** HIGHEST so we wrap whatever renderer other chat plugins installed at lower priorities. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        ChatPrefixSettings cfg = settings.get().chatPrefix();
        if (!cfg.enabled()) {
            return;
        }
        Optional<AwardedTitle> title = displays.cachedTitle(event.getPlayer().getUniqueId());
        if (title.isEmpty()) {
            return;
        }
        Component titleComponent;
        try {
            AwardedTitle t = title.get();
            titleComponent = TitleHover.titleWithReason(
                TitleText.truncateVisible(t.miniMessageText(), cfg.maxTitleLength()), t.reason());
        } catch (RuntimeException malformed) {
            return;
        }

        ChatRenderer existing = event.renderer();
        event.renderer((source, sourceDisplayName, message, viewer) -> {
            try {
                return switch (cfg.mode()) {
                    case PREFIX -> MM.deserialize(cfg.format(), Placeholder.component("title", titleComponent))
                        .append(existing.render(source, sourceDisplayName, message, viewer));
                    // displayName keeps nicknames / team colours from other plugins.
                    case REPLACE -> MM.deserialize(cfg.format(),
                        Placeholder.component("title", titleComponent),
                        Placeholder.component("player", sourceDisplayName),
                        Placeholder.component("message", message));
                };
            } catch (RuntimeException malformed) {
                logger.log(Level.FINE, "Invalid chat_prefix.format; using the normal chat line", malformed);
                return existing.render(source, sourceDisplayName, message, viewer);
            }
        });
    }
}
