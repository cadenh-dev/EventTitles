package com.example.titles.commands;

import com.example.titles.api.AwardedTitle;
import com.example.titles.api.EventCategory;
import com.example.titles.api.EventContext;
import com.example.titles.api.LlmTitleGenerator;
import com.example.titles.api.TitleDisplayService;
import com.example.titles.api.TitleText;
import com.example.titles.api.TitleHistory;
import com.example.titles.api.TitlesSettings;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;

/** Administrative commands for setting, clearing, and inspecting event titles. */
public final class TitleAdminCommand implements CommandExecutor, TabCompleter, BasicCommand {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final List<String> SUBCOMMANDS = List.of("info", "history", "set", "clear", "reload", "testllm");
    private static final List<String> PRIORITIES = List.of("10", "50", "90", "100");

    private final Plugin plugin;
    private final TitleDisplayService displays;
    private final LlmTitleGenerator llm;
    private final Supplier<TitlesSettings> settings;
    private final Runnable reloadAction;
    private final TitleHistory history;
    private static final int HISTORY_PAGE_SIZE = 8;

    public TitleAdminCommand(Plugin plugin, TitleDisplayService displays, LlmTitleGenerator llm,
                             Supplier<TitlesSettings> settings, Runnable reloadAction) {
        this(plugin, displays, llm, settings, reloadAction, null);
    }

    /** {@code history} may be null (history disabled). */
    public TitleAdminCommand(Plugin plugin, TitleDisplayService displays, LlmTitleGenerator llm,
                             Supplier<TitlesSettings> settings, Runnable reloadAction, TitleHistory history) {
        this.history = history;
        this.plugin = plugin;
        this.displays = displays;
        this.llm = llm;
        this.settings = settings;
        this.reloadAction = reloadAction;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        handle(source.getSender(), "title", args);
    }

    @Override
    public java.util.Collection<String> suggest(CommandSourceStack source, String[] args) {
        return complete(args);
    }

    /** Base permission to run /title at all ({@code info}); admin subcommands also need titles.admin. */
    @Override
    public String permission() {
        return INFO_PERMISSION;
    }

    private static final String ADMIN_PERMISSION = "titles.admin";
    private static final String INFO_PERMISSION = "titles.info";

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        handle(sender, label, args);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return complete(args);
    }

    private void handle(CommandSender sender, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("info")) {
            handleInfo(sender, args);
            return;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("history")) {
            handleHistory(sender, args);
            return;
        }
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            if (args.length == 0 && sender.hasPermission(INFO_PERMISSION)) {
                sender.sendMessage(MINI_MESSAGE.deserialize(
                    "<yellow>/<label> info [player]</yellow>  <yellow>/<label> history [player] [page]",
                    Placeholder.unparsed("label", label)));
                return;
            }
            sender.sendMessage(MINI_MESSAGE.deserialize("<red>You do not have permission to use this command."));
            return;
        }
        if (args.length == 0) {
            sendHelp(sender, label);
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "set" -> handleSet(sender, args);
            case "clear" -> handleClear(sender, args);
            case "reload" -> {
                if (args.length != 1) {
                    sendHelp(sender, label);
                    return;
                }
                reloadAction.run();
                sender.sendMessage(MINI_MESSAGE.deserialize("<green>EventTitles configuration reloaded."));
            }
            case "testllm" -> handleTestLlm(sender, args);
            default -> sendHelp(sender, label);
        }
    }

    /** /title history [player] [page]: a player's past titles, newest first; works for offline players. */
    private void handleHistory(CommandSender sender, String[] args) {
        if (!sender.hasPermission(INFO_PERMISSION)) {
            sender.sendMessage(MINI_MESSAGE.deserialize("<red>You do not have permission to use this command."));
            return;
        }
        if (history == null) {
            sender.sendMessage(MINI_MESSAGE.deserialize("<gray>Title history is turned off on this server."));
            return;
        }
        String name = null;
        int page = 1;
        for (int i = 1; i < args.length && i <= 2; i++) {
            if (args[i].matches("\\d{1,4}")) {
                page = Math.max(1, Integer.parseInt(args[i]));
            } else if (name == null) {
                name = args[i];
            }
        }
        UUID targetId;
        String targetName;
        if (name == null) {
            if (!(sender instanceof Player self)) {
                sender.sendMessage(MINI_MESSAGE.deserialize("<red>Usage: /title history \\<player> [page]"));
                return;
            }
            targetId = self.getUniqueId();
            targetName = self.getName();
        } else {
            Player online = Bukkit.getPlayerExact(name);
            org.bukkit.OfflinePlayer known = online != null ? online : Bukkit.getOfflinePlayerIfCached(name);
            if (known == null) {
                sendNamed(sender, "<red><player> has never played on this server.", name);
                return;
            }
            targetId = known.getUniqueId();
            targetName = known.getName() == null ? name : known.getName();
        }
        final int requestedPage = page;
        history.load(targetId).whenComplete((entries, error) -> {
            if (!plugin.isEnabled()) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (sender instanceof Player p && !p.isOnline()) {
                    return;
                }
                showHistoryPage(sender, targetName, entries == null ? List.of() : entries, requestedPage);
            });
        });
    }

    private void showHistoryPage(CommandSender sender, String targetName, List<TitleHistory.Entry> entries, int page) {
        if (entries.isEmpty()) {
            sendNamed(sender, "<gray><player> has no title history yet.", targetName);
            return;
        }
        int pages = (entries.size() + HISTORY_PAGE_SIZE - 1) / HISTORY_PAGE_SIZE;
        int current = Math.min(page, pages);
        sender.sendMessage(MINI_MESSAGE.deserialize(
            "<gold><player>'s titles</gold> <dark_gray>(<count> total · page <page>/<pages> · hover for why)</dark_gray>",
            Placeholder.unparsed("player", targetName),
            Placeholder.unparsed("count", Integer.toString(entries.size())),
            Placeholder.unparsed("page", Integer.toString(current)),
            Placeholder.unparsed("pages", Integer.toString(pages))));
        long now = System.currentTimeMillis();
        int start = (current - 1) * HISTORY_PAGE_SIZE;
        for (int i = start; i < Math.min(entries.size(), start + HISTORY_PAGE_SIZE); i++) {
            TitleHistory.Entry e = entries.get(i);
            String when = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                .withZone(java.time.ZoneId.systemDefault()).format(java.time.Instant.ofEpochMilli(e.awardedAtMillis()));
            String reason = (e.reason() == null ? "" : e.reason() + "\n") + "Awarded: " + when;
            Component title;
            try {
                title = com.example.titles.display.TitleHover.titleWithReason(e.title(), reason);
            } catch (RuntimeException malformed) {
                continue;
            }
            sender.sendMessage(MINI_MESSAGE.deserialize(
                "<dark_gray><n>.</dark_gray> <title> <dark_gray>· <source> · <ago></dark_gray>",
                Placeholder.unparsed("n", Integer.toString(i + 1)),
                Placeholder.component("title", title),
                Placeholder.unparsed("source", e.source() == null ? "?" : e.source()),
                Placeholder.unparsed("ago", ago(now - e.awardedAtMillis()))));
        }
        if (current < pages) {
            String next = "/title history " + targetName + " " + (current + 1);
            sender.sendMessage(Component.text("[Next page]", net.kyori.adventure.text.format.NamedTextColor.AQUA)
                .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand(next))
                .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(Component.text(next))));
        }
    }

    /** "just now", "5m ago", "3h ago", "2d ago". */
    static String ago(long millis) {
        long minutes = Math.max(0, millis) / 60_000L;
        if (minutes < 1) {
            return "just now";
        }
        if (minutes < 60) {
            return minutes + "m ago";
        }
        long hours = minutes / 60;
        if (hours < 48) {
            return hours + "h ago";
        }
        return (hours / 24) + "d ago";
    }

    /** /title info [player]: shows a player's current title; hover it for the reason. */
    private void handleInfo(CommandSender sender, String[] args) {
        if (!sender.hasPermission(INFO_PERMISSION)) {
            sender.sendMessage(MINI_MESSAGE.deserialize("<red>You do not have permission to use this command."));
            return;
        }
        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null || !target.isOnline()) {
                sendNamed(sender, "<red>Player <player> is not online.", args[1]);
                return;
            }
        } else if (sender instanceof Player self) {
            target = self;
        } else {
            sender.sendMessage(MINI_MESSAGE.deserialize("<red>Usage: /title info \\<player>"));
            return;
        }
        var current = displays.currentTitle(target);
        if (current.isEmpty()) {
            sendNamed(sender, "<gray><player> has no title yet.", target.getName());
            return;
        }
        sender.sendMessage(MINI_MESSAGE.deserialize(
            "<gray><player>'s title: <title> <dark_gray>(hover for why)</dark_gray>",
            Placeholder.unparsed("player", target.getName()),
            Placeholder.component("title", com.example.titles.display.TitleHover.titleWithReason(current.get()))));
    }

    private void handleSet(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(MINI_MESSAGE.deserialize("<red>Usage: /title set \\<player> \\<priority> \\<miniMessage...>"));
            return;
        }
        Player player = Bukkit.getPlayerExact(args[1]);
        if (player == null || !player.isOnline()) {
            sendNamed(sender, "<red>Player <player> is not online.", args[1]);
            return;
        }
        int priority;
        try {
            priority = Integer.parseInt(args[2]);
        } catch (NumberFormatException exception) {
            sender.sendMessage(MINI_MESSAGE.deserialize("<red>Priority must be an integer from 1 to 100."));
            return;
        }
        if (priority < AwardedTitle.MIN_PRIORITY || priority > AwardedTitle.MAX_PRIORITY) {
            sender.sendMessage(MINI_MESSAGE.deserialize("<red>Priority must be from 1 to 100."));
            return;
        }
        String titleText = String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length));
        Component rendered;
        try {
            rendered = MINI_MESSAGE.deserialize(titleText);
        } catch (RuntimeException exception) {
            sender.sendMessage(MINI_MESSAGE.deserialize("<red>Title contains invalid MiniMessage syntax."));
            return;
        }
        if (TitleText.stripTags(titleText).isBlank()) {
            sender.sendMessage(MINI_MESSAGE.deserialize("<red>Title must contain visible text."));
            return;
        }
        titleText = TitleText.truncateVisible(titleText, settings.get().maxTitleLength());
        try {
            rendered = MINI_MESSAGE.deserialize(titleText);
        } catch (RuntimeException exception) {
            sender.sendMessage(MINI_MESSAGE.deserialize("<red>Title contains invalid MiniMessage syntax."));
            return;
        }
        displays.clearPlayerTitle(player);
        displays.setPlayerTitle(player, new AwardedTitle(titleText, priority, false, null,
            "Why: Set by " + sender.getName() + " with /title set\nSource: Admin · Priority " + priority));
        sender.sendMessage(MINI_MESSAGE.deserialize("<green>Set title for <player> to <title> (priority <priority>).",
            Placeholder.unparsed("player", player.getName()),
            Placeholder.component("title", rendered),
            Placeholder.unparsed("priority", Integer.toString(priority))));
    }

    private void handleClear(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage(MINI_MESSAGE.deserialize("<red>Usage: /title clear \\<player>"));
            return;
        }
        Player player = Bukkit.getPlayerExact(args[1]);
        if (player == null || !player.isOnline()) {
            sendNamed(sender, "<red>Player <player> is not online.", args[1]);
            return;
        }
        displays.clearPlayerTitle(player);
        sendNamed(sender, "<green>Cleared the title for <player>.", player.getName());
    }

    private void handleTestLlm(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage(MINI_MESSAGE.deserialize("<red>Usage: /title testllm \\<player>"));
            return;
        }
        Player player = Bukkit.getPlayerExact(args[1]);
        if (player == null || !player.isOnline()) {
            sendNamed(sender, "<red>Player <player> is not online.", args[1]);
            return;
        }
        EventContext context = snapshotForTest(player);
        TitlesSettings current = settings.get();
        if (!current.llmEnabled() || current.effectiveApiKey() == null) {
            sender.sendMessage(MINI_MESSAGE.deserialize("<yellow>LLM testing is unavailable: enable LLM and configure an API key."));
            return;
        }
        sender.sendMessage(MINI_MESSAGE.deserialize("<gray>Querying the title generator for <player>...",
            Placeholder.unparsed("player", player.getName())));
        llm.generateTitle(context).whenComplete((result, error) -> {
            if (!plugin.isEnabled()) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (sender instanceof Player senderPlayer && !senderPlayer.isOnline()) {
                    return;
                }
                if (error != null || result == null || result.isEmpty()) {
                    sender.sendMessage(MINI_MESSAGE.deserialize("<yellow>Title generation returned no title; fallback may be active."));
                    return;
                }
                AwardedTitle title = result.get();
                Component preview;
                try {
                    preview = MINI_MESSAGE.deserialize(title.miniMessageText());
                } catch (RuntimeException exception) {
                    sender.sendMessage(MINI_MESSAGE.deserialize("<yellow>The generated title could not be rendered."));
                    return;
                }
                sender.sendMessage(MINI_MESSAGE.deserialize("<gray>Raw MiniMessage: <raw>",
                    Placeholder.unparsed("raw", title.miniMessageText())));
                sender.sendMessage(MINI_MESSAGE.deserialize("<gray>Preview: <preview> <dark_gray>(priority <priority>)</dark_gray>",
                    Placeholder.component("preview", preview),
                    Placeholder.unparsed("priority", Integer.toString(title.priority()))));
                if (title.comment() != null) {
                    sender.sendMessage(MINI_MESSAGE.deserialize("<gray>Chat comment: <comment>",
                        Placeholder.component("comment", com.example.titles.listeners.CommentaryBroadcaster
                            .preview(title.comment(), player.getName()))));
                } else {
                    sender.sendMessage(MINI_MESSAGE.deserialize("<dark_gray>(no chat comment returned)"));
                }
                if (player.isOnline()) {
                    TitlesSettings now = settings.get();
                    displays.setPlayerTitle(player, title.withReason("Why: LLM test run by " + sender.getName()
                        + " with /title testllm\nSource: " + now.llmProvider().configId() + " (" + now.llmModel()
                        + ") · Priority " + title.priority()));
                }
            });
        });
    }

    private static EventContext snapshotForTest(Player player) {
        String biome = player.getLocation().getBlock().getBiome().key().value().toUpperCase(Locale.ROOT);
        PlayerInventory inventory = player.getInventory();
        ItemStack held = inventory.getItemInMainHand();
        String heldItem = held == null || held.getType().isAir() ? Material.AIR.name() : held.getType().name();
        List<String> armor = new ArrayList<>();
        for (ItemStack item : inventory.getArmorContents()) {
            if (item != null && !item.getType().isAir()) {
                armor.add(item.getType().name());
            }
        }
        String metadata = "health=" + String.format(Locale.ROOT, "%.1f", player.getHealth())
            + ";y=" + player.getLocation().getBlockY();
        return new EventContext(player.getUniqueId(), player.getName(), EventCategory.SHENANIGANS,
            "ADMIN_TEST", biome, heldItem, armor.isEmpty() ? "NONE" : String.join(",", armor), 1, metadata);
    }

    private List<String> complete(String[] args) {
        if (args.length == 1) {
            return matching(SUBCOMMANDS, args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("clear")
            || args[0].equalsIgnoreCase("testllm") || args[0].equalsIgnoreCase("info")
            || args[0].equalsIgnoreCase("history"))) {
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                names.add(player.getName());
            }
            return matching(names, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            return matching(PRIORITIES, args[2]);
        }
        return List.of();
    }

    private static List<String> matching(List<String> choices, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        return choices.stream().filter(choice -> choice.toLowerCase(Locale.ROOT).startsWith(normalized)).toList();
    }

    private static void sendNamed(CommandSender sender, String template, String name) {
        sender.sendMessage(MINI_MESSAGE.deserialize(template, Placeholder.unparsed("player", name)));
    }

    private static void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(MINI_MESSAGE.deserialize("<gold>EventTitles commands</gold>"));
        sender.sendMessage(MINI_MESSAGE.deserialize("<yellow>/<label> info [player]",
            Placeholder.unparsed("label", label)));
        sender.sendMessage(MINI_MESSAGE.deserialize("<yellow>/<label> history [player] [page]",
            Placeholder.unparsed("label", label)));
        sender.sendMessage(MINI_MESSAGE.deserialize("<yellow>/<label> set \\<player> \\<priority> \\<miniMessage...>",
            Placeholder.unparsed("label", label)));
        sender.sendMessage(MINI_MESSAGE.deserialize("<yellow>/<label> clear \\<player>",
            Placeholder.unparsed("label", label)));
        sender.sendMessage(MINI_MESSAGE.deserialize("<yellow>/<label> reload", Placeholder.unparsed("label", label)));
        sender.sendMessage(MINI_MESSAGE.deserialize("<yellow>/<label> testllm \\<player>",
            Placeholder.unparsed("label", label)));
    }
}
