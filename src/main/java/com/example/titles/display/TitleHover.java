package com.example.titles.display;

import com.example.titles.api.AwardedTitle;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;

/**
 * Renders a title as a chat component whose hover text explains why it was awarded. The reason is
 * plain text ({@link Component#text}), never parsed as MiniMessage, so it cannot inject formatting.
 */
public final class TitleHover {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private TitleHover() {}

    /** The rendered title with a hover card: title, then one gray line per reason fact. */
    public static Component titleWithReason(AwardedTitle title) {
        return titleWithReason(title.miniMessageText(), title.reason());
    }

    public static Component titleWithReason(String miniMessageTitle, String reason) {
        Component rendered = MM.deserialize(miniMessageTitle);
        return rendered.hoverEvent(HoverEvent.showText(card(rendered, reason)));
    }

    /** Hover card body; also usable as a hover on other text (e.g. a chat comment). */
    public static Component card(Component renderedTitle, String reason) {
        Component card = renderedTitle;
        String body = reason == null ? "No details recorded for this title." : reason;
        for (String line : body.split("\n")) {
            card = card.appendNewline().append(Component.text(line, NamedTextColor.GRAY));
        }
        return card;
    }
}
