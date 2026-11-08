package com.example.titles.listeners;

import com.example.titles.api.AwardedTitle;
import com.example.titles.api.ChatSettings;
import com.example.titles.api.TitlesSettings;
import com.example.titles.display.TitleHover;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Sends LLM commentary lines to chat. Main thread only. The comment was already sanitized to
 * style-only tags by the LLM client; the player's name is inserted as unparsed text, so names can
 * never inject formatting.
 */
public final class CommentaryBroadcaster {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final Supplier<TitlesSettings> settings;
    private final Map<UUID, Long> lastSentMillis = new ConcurrentHashMap<>();

    public CommentaryBroadcaster(Supplier<TitlesSettings> settings) {
        this.settings = settings;
    }

    /** Broadcasts {@code title}'s comment about {@code subject}, if any, respecting config and cooldown. */
    public void broadcast(Player subject, AwardedTitle title) {
        ChatSettings chat = settings.get().chat();
        if (!chat.enabled() || title.comment() == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Long previous = lastSentMillis.get(subject.getUniqueId());
        if (previous != null && now - previous < chat.cooldownSeconds() * 1000L) {
            return;
        }
        Component line;
        try {
            // Hovering the comment shows the title it came with and why it was awarded.
            Component comment = MM.deserialize(title.comment(), Placeholder.unparsed("player", subject.getName()))
                .hoverEvent(HoverEvent.showText(TitleHover.card(MM.deserialize(title.miniMessageText()),
                    title.reason())));
            line = MM.deserialize(chat.format(), Placeholder.component("comment", comment),
                Placeholder.unparsed("player", subject.getName()));
        } catch (RuntimeException malformed) {
            return;
        }
        lastSentMillis.put(subject.getUniqueId(), now);
        for (Player viewer : audience(subject, chat)) {
            viewer.sendMessage(line);
        }
    }

    /** Renders a comment for previews (e.g. {@code /title testllm}) without sending or cooldown. */
    public static Component preview(String comment, String playerName) {
        return MM.deserialize(comment, Placeholder.unparsed("player", playerName));
    }

    public void forget(UUID playerId) {
        lastSentMillis.remove(playerId);
    }

    private static Collection<? extends Player> audience(Player subject, ChatSettings chat) {
        return switch (chat.audience()) {
            case SERVER -> Bukkit.getOnlinePlayers();
            case WORLD -> subject.getWorld().getPlayers();
            case NEARBY -> subject.getWorld().getNearbyPlayers(subject.getLocation(), chat.nearbyRadius());
            case PLAYER -> List.of(subject);
        };
    }
}
