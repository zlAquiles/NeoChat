package net.aquiles.neochat.commands;

import net.aquiles.neochat.NeoChat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class MessageCommand implements CommandExecutor, TabCompleter {

    private final NeoChat plugin;
    private final MiniMessage miniMessage;

    public MessageCommand(NeoChat plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Solo los jugadores pueden usar este comando.");
            return true;
        }

        if (!plugin.getConfig().getBoolean("private-messages.enable", true)) {
            plugin.sendMessage(player, miniMessage.deserialize("<red>El sistema de mensajes privados esta desactivado."));
            return true;
        }

        String commandName = command.getName().toLowerCase();
        String noPermission = plugin.getMessages().getString("no-permission", "<red>No tienes permisos.");

        if (commandName.equals("togglemsg")) {
            if (!player.hasPermission("neochat.pm.toggle")) {
                plugin.sendMessage(player, miniMessage.deserialize(noPermission));
                return true;
            }

            boolean enabled = plugin.getPlayerDataManager().togglePM(player);
            String key = enabled ? "pm-toggle-on" : "pm-toggle-off";
            plugin.sendMessage(player, miniMessage.deserialize(plugin.getMessages().getString(key, "<gray>Estado cambiado.")));
            return true;
        }

        if (commandName.equals("socialspy")) {
            if (!player.hasPermission("neochat.admin.spy")) {
                plugin.sendMessage(player, miniMessage.deserialize(noPermission));
                return true;
            }

            boolean enabled = plugin.getPlayerDataManager().toggleSpy(player);
            String key = enabled ? "pm-spy-on" : "pm-spy-off";
            plugin.sendMessage(player, miniMessage.deserialize(plugin.getMessages().getString(key, "<gray>SocialSpy cambiado.")));
            return true;
        }

        if (!commandName.equals("msg") && !commandName.equals("reply")) {
            return false;
        }

        if (!player.hasPermission("neochat.pm.use")) {
            plugin.sendMessage(player, miniMessage.deserialize(noPermission));
            return true;
        }

        if (plugin.getPlayerDataManager().hasPMsDisabled(player)) {
            plugin.sendMessage(player, miniMessage.deserialize(plugin.getMessages().getString("pm-self-disabled", "<red>Tienes los mensajes desactivados.")));
            return true;
        }

        Player target;
        String rawMessage;

        if (commandName.equals("msg")) {
            if (args.length < 2) {
                plugin.sendMessage(player, miniMessage.deserialize("<red>Uso: /" + label + " <jugador> <mensaje>"));
                return true;
            }

            target = Bukkit.getPlayer(args[0]);
            rawMessage = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        } else {
            if (args.length < 1) {
                plugin.sendMessage(player, miniMessage.deserialize("<red>Uso: /" + label + " <mensaje>"));
                return true;
            }

            UUID targetId = plugin.getPlayerDataManager().getReplyTarget(player);
            target = targetId == null ? null : Bukkit.getPlayer(targetId);
            rawMessage = String.join(" ", args);
        }

        if (target == null || !plugin.callForEntity(player, () -> player.canSee(target))) {
            String key = commandName.equals("reply") ? "pm-no-reply" : "pm-player-not-found";
            plugin.sendMessage(player, miniMessage.deserialize(plugin.getMessages().getString(key, "<red>Jugador no encontrado.")));
            return true;
        }

        if (target.equals(player)) {
            plugin.sendMessage(player, miniMessage.deserialize(plugin.getMessages().getString("pm-cannot-message-self", "<red>No puedes hablarte a ti mismo.")));
            return true;
        }

        if (plugin.getPlayerDataManager().hasPMsDisabled(target) && !player.hasPermission("neochat.pm.bypass")) {
            plugin.sendMessage(player, miniMessage.deserialize(plugin.getMessages().getString("pm-target-disabled", "<red>Ese jugador tiene los mensajes desactivados.")));
            return true;
        }

        if (plugin.getPlayerDataManager().isIgnoring(target, player)) {
            plugin.sendMessage(player, miniMessage.deserialize(plugin.getMessages().getString("ignore-target-ignoring-you", "<red>No puedes enviar un mensaje a este jugador porque te ha ignorado.")));
            return true;
        }

        plugin.getPlayerDataManager().setReplyTarget(player, target);

        Component parsedMessage = player.hasPermission("neochat.chat.color")
                ? miniMessage.deserialize(rawMessage)
                : Component.text(rawMessage);

        String sendFormat = plugin.getMessages().getString("pm-format-send", "<gray>[Yo -> %target%] <white><message>")
                .replace("%target%", target.getName());
        plugin.sendMessage(player, miniMessage.deserialize(sendFormat, Placeholder.component("message", parsedMessage)));

        String receiveFormat = plugin.getMessages().getString("pm-format-receive", "<gray>[%sender% -> Yo] <white><message>")
                .replace("%sender%", player.getName());
        plugin.sendMessage(target, miniMessage.deserialize(receiveFormat, Placeholder.component("message", parsedMessage)));

        String spyFormat = plugin.getMessages().getString("pm-format-spy", "<dark_gray>[Spy] %sender% -> %target%: <message>")
                .replace("%sender%", player.getName())
                .replace("%target%", target.getName());
        Component spyComponent = miniMessage.deserialize(spyFormat, Placeholder.component("message", parsedMessage));

        for (UUID spyId : plugin.getPlayerDataManager().getSpies()) {
            Player spyPlayer = Bukkit.getPlayer(spyId);
            if (spyPlayer != null && !spyPlayer.equals(player) && !spyPlayer.equals(target)) {
                plugin.sendMessage(spyPlayer, spyComponent);
            }
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!command.getName().equalsIgnoreCase("msg") || args.length != 1) {
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
