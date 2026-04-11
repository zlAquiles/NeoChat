package net.aquiles.neochat.commands;

import net.aquiles.neochat.NeoChat;
import net.aquiles.neochat.chat.ChatListener;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class AdminChatCommand implements CommandExecutor, TabCompleter {

    private final NeoChat plugin;
    private final MiniMessage miniMessage;

    public AdminChatCommand(NeoChat plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        String noPermission = plugin.getMessages().getString("no-permission", "<red>No tienes permisos.");

        if (command.getName().equalsIgnoreCase("chatmute")) {
            if (!sender.hasPermission("neochat.admin.mute")) {
                plugin.sendMessage(sender, miniMessage.deserialize(noPermission));
                return true;
            }
            if (plugin.isChatMuted()) {
                plugin.sendMessage(sender, miniMessage.deserialize(plugin.getMessages().getString("chat-already-muted", "<red>El chat ya esta silenciado.")));
                return true;
            }

            plugin.setChatMuted(true);
            plugin.broadcast(miniMessage.deserialize(plugin.getMessages().getString("chat-muted-broadcast", "<red>El chat ha sido silenciado.")));
            return true;
        }

        if (command.getName().equalsIgnoreCase("chatunmute")) {
            if (!sender.hasPermission("neochat.admin.mute")) {
                plugin.sendMessage(sender, miniMessage.deserialize(noPermission));
                return true;
            }
            if (!plugin.isChatMuted()) {
                plugin.sendMessage(sender, miniMessage.deserialize(plugin.getMessages().getString("chat-already-unmuted", "<red>El chat ya esta habilitado.")));
                return true;
            }

            plugin.setChatMuted(false);
            plugin.broadcast(miniMessage.deserialize(plugin.getMessages().getString("chat-unmuted-broadcast", "<green>El chat ha sido habilitado.")));
            return true;
        }

        if (command.getName().equalsIgnoreCase("neochat")) {
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("neochat.admin.reload")) {
                    plugin.sendMessage(sender, miniMessage.deserialize(noPermission));
                    return true;
                }

                plugin.reloadPlugin();
                plugin.sendMessage(sender, miniMessage.deserialize(plugin.getMessages().getString("reload", "<green>Recargado.")));
                return true;
            }

            if (args.length >= 1 && args[0].equalsIgnoreCase("debug")) {
                if (!sender.hasPermission("neochat.admin.debug")) {
                    plugin.sendMessage(sender, miniMessage.deserialize(noPermission));
                    return true;
                }

                if (args.length != 2) {
                    plugin.sendMessage(sender, miniMessage.deserialize("<red>Usage: /" + label + " debug <player>"));
                    return true;
                }

                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    plugin.sendMessage(sender, miniMessage.deserialize("<red>That player is not online."));
                    return true;
                }

                ChatListener.FormatDebugInfo debugInfo = plugin.callForEntity(target, () -> plugin.debugChatFormat(target));
                if (debugInfo == null) {
                    plugin.sendMessage(sender, miniMessage.deserialize("<red>Unable to inspect chat format right now."));
                    return true;
                }

                sendDebugInfo(sender, target, debugInfo);
                return true;
            }

            plugin.sendMessage(sender, miniMessage.deserialize("<aqua>NeoChat <gray>v" + plugin.getDescription().getVersion() + " por <white>Aquiles"));
            return true;
        }

        return false;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (!command.getName().equalsIgnoreCase("neochat")) {
            return completions;
        }

        if (args.length == 1) {
            if (sender.hasPermission("neochat.admin.reload")) {
                completions.add("reload");
            }
            if (sender.hasPermission("neochat.admin.debug")) {
                completions.add("debug");
            }
            return completions;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("debug") && sender.hasPermission("neochat.admin.debug")) {
            String prefix = args[1].toLowerCase();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().toLowerCase().startsWith(prefix)) {
                    completions.add(online.getName());
                }
            }
        }

        return completions;
    }

    private void sendDebugInfo(CommandSender sender, Player target, ChatListener.FormatDebugInfo debugInfo) {
        plugin.sendMessage(sender, miniMessage.deserialize("<dark_gray>----- <aqua>NeoChat Debug <dark_gray>-----"));
        sendDebugLine(sender, "Player", target.getName());
        sendDebugLine(sender, "Format key", debugInfo.formatKey());
        sendDebugLine(sender, "Priority", debugInfo.priority() < 0 ? "fallback" : String.valueOf(debugInfo.priority()));
        sendDebugLine(sender, "Permission", debugInfo.permission());
        sendDebugLine(sender, "Matched by", debugInfo.matchedBy());
        sendDebugLine(sender, "Groups", debugInfo.groups().isEmpty() ? "<none>" : String.join(", ", debugInfo.groups()));
        sendDebugLine(sender, "LuckPerms prefix", displayValue(debugInfo.luckPermsPrefix()));
        sendDebugLine(sender, "LuckPerms suffix", displayValue(debugInfo.luckPermsSuffix()));
        sendDebugLine(sender, "Raw format", displayValue(debugInfo.rawGroupFormat()));
        sendDebugLine(sender, "Resolved format", displayValue(debugInfo.resolvedGroupFormat()));
        sendDebugLine(sender, "Raw hover", displayValue(debugInfo.rawHoverText()));
        sendDebugLine(sender, "Resolved hover", displayValue(debugInfo.resolvedHoverText()));
        sendDebugLine(sender, "Unresolved placeholders", "format=" + debugInfo.groupFormatHasUnresolvedPlaceholders() + ", hover=" + debugInfo.hoverHasUnresolvedPlaceholders());
    }

    private void sendDebugLine(CommandSender sender, String label, String value) {
        plugin.sendMessage(
                sender,
                miniMessage.deserialize(
                        "<gray>" + label + ": <white><value>",
                        Placeholder.unparsed("value", value)
                )
        );
    }

    private String displayValue(String value) {
        if (value == null || value.isEmpty()) {
            return "<empty>";
        }

        return value.replace("\n", "\\n");
    }
}
