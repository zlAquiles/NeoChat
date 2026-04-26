package net.aquiles.neochat.announcer;

import net.aquiles.neochat.NeoChat;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public final class AnnouncementCommand implements CommandExecutor, TabCompleter {

    private final NeoChat plugin;
    private final AnnouncementManager manager;
    private final MiniMessage miniMessage;

    public AnnouncementCommand(NeoChat plugin, AnnouncementManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        this.miniMessage = MiniMessage.miniMessage();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        String noPermission = plugin.getMessages().getString("no-permission", "<red>You don't have permission to use this command.");
        if (!manager.isModuleEnabled()) {
            manager.sendTemplate(sender, "announcer-module-disabled");
            return true;
        }

        if (args.length == 0) {
            if (sender instanceof Player player && player.hasPermission(AnnouncementManager.TOGGLE_PERMISSION)) {
                manager.showStatus(player);
            }
            sendCommandSummary(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "toggle" -> {
                if (!(sender instanceof Player player)) {
                    manager.sendTemplate(sender, "announcer-player-only");
                    return true;
                }
                if (!player.hasPermission(AnnouncementManager.TOGGLE_PERMISSION)) {
                    plugin.sendMessage(sender, miniMessage.deserialize(noPermission));
                    return true;
                }

                Boolean enabledState = null;
                if (args.length >= 2) {
                    if (args[1].equalsIgnoreCase("on")) {
                        enabledState = true;
                    } else if (args[1].equalsIgnoreCase("off")) {
                        enabledState = false;
                    } else {
                        manager.sendTemplate(sender, "announcer-toggle-usage", java.util.Map.of("%command%", label));
                        return true;
                    }
                }

                manager.toggleAnnouncements(player, enabledState);
                return true;
            }
            case "status" -> {
                if (!(sender instanceof Player player)) {
                    manager.sendTemplate(sender, "announcer-player-only");
                    return true;
                }
                if (!player.hasPermission(AnnouncementManager.TOGGLE_PERMISSION)) {
                    plugin.sendMessage(sender, miniMessage.deserialize(noPermission));
                    return true;
                }

                manager.showStatus(player);
                return true;
            }
            case "list" -> {
                if (!sender.hasPermission(AnnouncementManager.USE_PERMISSION)) {
                    plugin.sendMessage(sender, miniMessage.deserialize(noPermission));
                    return true;
                }

                manager.showAnnouncementList(sender);
                return true;
            }
            case "preview" -> {
                if (!(sender instanceof Player player)) {
                    manager.sendTemplate(sender, "announcer-player-only");
                    return true;
                }
                if (!player.hasPermission(AnnouncementManager.PREVIEW_PERMISSION)) {
                    plugin.sendMessage(sender, miniMessage.deserialize(noPermission));
                    return true;
                }
                if (args.length < 2) {
                    manager.sendTemplate(sender, "announcer-preview-usage", java.util.Map.of("%command%", label));
                    return true;
                }

                manager.preview(player, args[1]);
                return true;
            }
            case "send" -> {
                if (!sender.hasPermission(AnnouncementManager.SEND_PERMISSION)) {
                    plugin.sendMessage(sender, miniMessage.deserialize(noPermission));
                    return true;
                }
                if (args.length < 2) {
                    manager.sendTemplate(sender, "announcer-send-usage", java.util.Map.of("%command%", label));
                    return true;
                }

                String targetName = args.length >= 3 ? args[2] : null;
                manager.sendAnnouncement(sender, args[1], targetName);
                return true;
            }
            default -> {
                sendCommandSummary(sender);
                return true;
            }
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!manager.isModuleEnabled()) {
            return List.of();
        }

        if (args.length <= 1) {
            String partial = args.length == 0 ? "" : args[0].toLowerCase();
            List<String> suggestions = new ArrayList<>();
            if (sender instanceof Player player && player.hasPermission(AnnouncementManager.TOGGLE_PERMISSION)) {
                suggestions.add("toggle");
                suggestions.add("status");
            }
            if (sender.hasPermission(AnnouncementManager.USE_PERMISSION)) {
                suggestions.add("list");
            }
            if (sender instanceof Player player && player.hasPermission(AnnouncementManager.PREVIEW_PERMISSION)) {
                suggestions.add("preview");
            }
            if (sender.hasPermission(AnnouncementManager.SEND_PERMISSION)) {
                suggestions.add("send");
            }
            return suggestions.stream()
                    .filter(value -> value.startsWith(partial))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("toggle")) {
            String partial = args[1].toLowerCase();
            return Stream.of("on", "off")
                    .filter(option -> option.startsWith(partial))
                    .toList();
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("preview") || args[0].equalsIgnoreCase("send"))) {
            String partial = args[1].toLowerCase();
            return manager.announcementIds().stream()
                    .filter(id -> id.toLowerCase().startsWith(partial))
                    .toList();
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("send")) {
            return manager.onlinePlayers(args[2]);
        }

        return List.of();
    }

    private void sendCommandSummary(CommandSender sender) {
        List<String> available = new ArrayList<>();
        if (sender instanceof Player player && player.hasPermission(AnnouncementManager.TOGGLE_PERMISSION)) {
            available.add("toggle");
            available.add("status");
        }
        if (sender.hasPermission(AnnouncementManager.USE_PERMISSION)) {
            available.add("list");
        }
        if (sender instanceof Player player && player.hasPermission(AnnouncementManager.PREVIEW_PERMISSION)) {
            available.add("preview");
        }
        if (sender.hasPermission(AnnouncementManager.SEND_PERMISSION)) {
            available.add("send");
        }

        String summary = available.isEmpty() ? "<none>" : String.join("<gray>, <white>", available);
        manager.sendTemplate(
                sender,
                "announcer-usage",
                java.util.Map.of("%subcommands%", "<white>" + summary)
        );
    }
}
