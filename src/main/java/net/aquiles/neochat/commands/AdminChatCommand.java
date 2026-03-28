package net.aquiles.neochat.commands;

import net.aquiles.neochat.NeoChat;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
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

            plugin.sendMessage(sender, miniMessage.deserialize("<aqua>NeoChat <gray>v" + plugin.getDescription().getVersion() + " por <white>Aquiles"));
            return true;
        }

        return false;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("neochat") && args.length == 1 && sender.hasPermission("neochat.admin.reload")) {
            completions.add("reload");
        }
        return completions;
    }
}
