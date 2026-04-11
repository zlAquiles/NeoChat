package net.aquiles.neochat.commands;

import net.aquiles.neochat.NeoChat;
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

public class IgnoreCommand implements CommandExecutor, TabCompleter {

    private final NeoChat plugin;
    private final MiniMessage miniMessage;

    public IgnoreCommand(NeoChat plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Solo jugadores.");
            return true;
        }

        String noPermission = plugin.getMessages().getString("no-permission", "<red>No tienes permisos.");

        if (command.getName().equalsIgnoreCase("ignoreall")) {
            if (!player.hasPermission("neochat.ignore.ignoreall")) {
                plugin.sendMessage(player, miniMessage.deserialize(noPermission));
                return true;
            }

            boolean enabled = plugin.getPlayerDataManager().toggleIgnoreAll(player);
            String key = enabled ? "ignore-all-on" : "ignore-all-off";
            plugin.sendMessage(player, miniMessage.deserialize(plugin.getMessages().getString(key, "<gray>Estado de IgnoreAll cambiado.")));
            return true;
        }

        if (!command.getName().equalsIgnoreCase("ignore")) {
            return false;
        }

        if (!player.hasPermission("neochat.ignore.use")) {
            plugin.sendMessage(player, miniMessage.deserialize(noPermission));
            return true;
        }

        if (args.length < 1) {
            plugin.sendMessage(player, miniMessage.deserialize(plugin.getMessages().getString("ignore-usage", "<red>Uso: /ignore <jugador>")));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !plugin.callForEntity(player, () -> player.canSee(target))) {
            plugin.sendMessage(player, miniMessage.deserialize(plugin.getMessages().getString("pm-player-not-found", "<red>Jugador no encontrado.")));
            return true;
        }

        if (target.equals(player)) {
            plugin.sendMessage(player, miniMessage.deserialize(plugin.getMessages().getString("ignore-cannot-ignore-self", "<red>No puedes ignorarte a ti mismo.")));
            return true;
        }

        if (plugin.callForEntityOrDefault(target, () -> target.hasPermission("neochat.ignore.bypass"), false)) {
            plugin.sendMessage(player, miniMessage.deserialize(plugin.getMessages().getString("ignore-cannot-ignore-admin", "<red>No puedes ignorar a un administrador.")));
            return true;
        }

        boolean ignored = plugin.getPlayerDataManager().toggleIgnore(player, target);
        String key = ignored ? "ignore-add" : "ignore-remove";
        String message = plugin.getMessages().getString(key, "<gray>Estado de ignore actualizado.").replace("%player%", target.getName());
        plugin.sendMessage(player, miniMessage.deserialize(message));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!command.getName().equalsIgnoreCase("ignore") || args.length != 1) {
            return List.of();
        }

        String prefix = args[0].toLowerCase();
        if (sender instanceof Player player) {
            return plugin.callForEntity(player, () -> collectVisiblePlayers(player, prefix));
        }

        return collectVisiblePlayers(null, prefix);
    }

    private List<String> collectVisiblePlayers(Player viewer, String prefix) {
        List<String> completions = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (viewer != null && !viewer.canSee(online)) {
                continue;
            }
            if (online.getName().toLowerCase().startsWith(prefix)) {
                completions.add(online.getName());
            }
        }
        return completions;
    }
}
