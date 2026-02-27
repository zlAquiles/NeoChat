package net.aquiles.neochat.commands;

import net.aquiles.neochat.NeoChat;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class IgnoreCommand implements CommandExecutor, org.bukkit.command.TabCompleter {

    private final NeoChat plugin;
    private final MiniMessage mm;

    public IgnoreCommand(NeoChat plugin) {
        this.plugin = plugin;
        this.mm = MiniMessage.miniMessage();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Solo jugadores.");
            return true;
        }

        String noPermMsg = plugin.getMessages().getString("no-permission", "<red>No tienes permisos.");

        if (command.getName().equalsIgnoreCase("ignoreall")) {
            if (!player.hasPermission("neochat.ignore.use")) {
                player.sendMessage(mm.deserialize(noPermMsg));
                return true;
            }

            boolean isNowOn = plugin.getPlayerDataManager().toggleIgnoreAll(player);
            String msgKey = isNowOn ? "ignore-all-on" : "ignore-all-off";
            player.sendMessage(mm.deserialize(plugin.getMessages().getString(msgKey, "<gray>Estado de IgnoreAll cambiado.")));
            return true;
        }

        if (command.getName().equalsIgnoreCase("ignore")) {
            if (!player.hasPermission("neochat.ignore.use")) {
                player.sendMessage(mm.deserialize(noPermMsg));
                return true;
            }

            if (args.length < 1) {
                player.sendMessage(mm.deserialize(plugin.getMessages().getString("ignore-usage", "<red>Uso: /ignore <jugador>")));
                return true;
            }

            Player target = Bukkit.getPlayer(args[0]);

            if (target == null || !target.isOnline() || !player.canSee(target)) {
                player.sendMessage(mm.deserialize(plugin.getMessages().getString("pm-player-not-found", "<red>Jugador no encontrado.")));
                return true;
            }

            if (target.equals(player)) {
                player.sendMessage(mm.deserialize(plugin.getMessages().getString("ignore-cannot-ignore-self", "<red>No puedes ignorarte a ti mismo.")));
                return true;
            }

            if (target.hasPermission("neochat.ignore.bypass")) {
                player.sendMessage(mm.deserialize(plugin.getMessages().getString("ignore-cannot-ignore-admin", "<red>No puedes ignorar a un administrador.")));
                return true;
            }

            boolean isIgnored = plugin.getPlayerDataManager().toggleIgnore(player, target);
            String msgKey = isIgnored ? "ignore-add" : "ignore-remove";
            String msg = plugin.getMessages().getString(msgKey, "<gray>Estado de ignore actualizado.");

            player.sendMessage(mm.deserialize(msg.replace("%player%", target.getName())));
            return true;
        }

        return false;
    }

    @Override
    public @org.jetbrains.annotations.Nullable java.util.List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        java.util.List<String> completions = new java.util.ArrayList<>();

        if (command.getName().equalsIgnoreCase("ignore") && args.length == 1) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (sender instanceof Player player && !player.canSee(p)) continue;
                if (p.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(p.getName());
                }
            }
        }
        return completions;
    }
}