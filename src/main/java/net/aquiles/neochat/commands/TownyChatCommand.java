package net.aquiles.neochat.commands;

import net.aquiles.neochat.NeoChat;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class TownyChatCommand implements CommandExecutor {

    private final NeoChat plugin;

    public TownyChatCommand(NeoChat plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Solo jugadores.");
            return true;
        }

        if (!plugin.isTownyChatEnabled()) {
            player.sendMessage("Towny Chat está desactivado en la configuración.");
            return true;
        }

        if (!player.hasPermission("neochat.command.townychat")) {
            player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(plugin.getMessages().getString("no-permission", "<red>No tienes permisos.")));
            return true;
        }

        if (!plugin.getTownyManager().hasTown(player)) {
            player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(plugin.getMessages().getString("towny-need-town", "<red>¡No tienes ciudad!")));
            return true;
        }

        if (args.length == 0) {
            if (plugin.getTownyChatToggled().contains(player.getUniqueId())) {
                plugin.getTownyChatToggled().remove(player.getUniqueId());
                player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(plugin.getMessages().getString("towny-chat-off", "<red>Chat de ciudad desactivado.")));
            } else {
                plugin.getTownyChatToggled().add(player.getUniqueId());
                player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(plugin.getMessages().getString("towny-chat-on", "<green>Chat de ciudad activado.")));
            }
            return true;
        }

        String message = String.join(" ", args);
        plugin.getTownyManager().sendTownMessage(player, message);
        return true;
    }
}