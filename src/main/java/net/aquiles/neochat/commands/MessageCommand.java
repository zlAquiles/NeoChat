package net.aquiles.neochat.commands;

import net.aquiles.neochat.NeoChat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.UUID;

public class MessageCommand implements CommandExecutor, org.bukkit.command.TabCompleter {

    private final NeoChat plugin;
    private final MiniMessage mm;

    public MessageCommand(NeoChat plugin) {
        this.plugin = plugin;
        this.mm = MiniMessage.miniMessage();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Solo los jugadores pueden usar este comando.");
            return true;
        }

        if (!plugin.getConfig().getBoolean("private-messages.enable", true)) {
            player.sendMessage(mm.deserialize("<red>El sistema de mensajes privados está desactivado."));
            return true;
        }

        String cmdName = command.getName().toLowerCase();
        String noPermMsg = plugin.getMessages().getString("no-permission", "<red>No tienes permisos.");

        if (cmdName.equals("togglemsg")) {
            if (!player.hasPermission("neochat.pm.toggle")) {
                player.sendMessage(mm.deserialize(noPermMsg));
                return true;
            }
            boolean isNowEnabled = plugin.getPlayerDataManager().togglePM(player);
            String msgKey = isNowEnabled ? "pm-toggle-on" : "pm-toggle-off";
            player.sendMessage(mm.deserialize(plugin.getMessages().getString(msgKey, "<gray>Estado cambiado.")));
            return true;
        }

        if (cmdName.equals("socialspy")) {
            if (!player.hasPermission("neochat.admin.spy")) {
                player.sendMessage(mm.deserialize(noPermMsg));
                return true;
            }
            boolean isSpyOn = plugin.getPlayerDataManager().toggleSpy(player);
            String msgKey = isSpyOn ? "pm-spy-on" : "pm-spy-off";
            player.sendMessage(mm.deserialize(plugin.getMessages().getString(msgKey, "<gray>SocialSpy cambiado.")));
            return true;
        }

        if (cmdName.equals("msg") || cmdName.equals("reply")) {
            if (!player.hasPermission("neochat.pm.use")) {
                player.sendMessage(mm.deserialize(noPermMsg));
                return true;
            }

            if (plugin.getPlayerDataManager().hasPMsDisabled(player)) {
                player.sendMessage(mm.deserialize(plugin.getMessages().getString("pm-self-disabled", "<red>Tienes los mensajes desactivados.")));
                return true;
            }

            Player target = null;
            String messageRaw = "";

            if (cmdName.equals("msg")) {
                if (args.length < 2) {
                    player.sendMessage(mm.deserialize("<red>Uso: /" + label + " <jugador> <mensaje>"));
                    return true;
                }
                target = Bukkit.getPlayer(args[0]);
                messageRaw = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            } else {
                if (args.length < 1) {
                    player.sendMessage(mm.deserialize("<red>Uso: /" + label + " <mensaje>"));
                    return true;
                }
                UUID targetId = plugin.getPlayerDataManager().getReplyTarget(player);
                if (targetId != null) {
                    target = Bukkit.getPlayer(targetId);
                }
                messageRaw = String.join(" ", args);
            }

            if (target == null || !target.isOnline() || !player.canSee(target)) {
                player.sendMessage(mm.deserialize(plugin.getMessages().getString(cmdName.equals("reply") ? "pm-no-reply" : "pm-player-not-found", "<red>Jugador no encontrado.")));
                return true;
            }

            if (target.equals(player)) {
                player.sendMessage(mm.deserialize(plugin.getMessages().getString("pm-cannot-message-self", "<red>No puedes hablarte a ti mismo.")));
                return true;
            }

            if (plugin.getPlayerDataManager().hasPMsDisabled(target) && !player.hasPermission("neochat.pm.bypass")) {
                player.sendMessage(mm.deserialize(plugin.getMessages().getString("pm-target-disabled", "<red>Ese jugador tiene los mensajes desactivados.")));
                return true;
            }

            if (plugin.getPlayerDataManager().isIgnoring(target, player)) {
                player.sendMessage(mm.deserialize(plugin.getMessages().getString("ignore-target-ignoring-you", "<red>No puedes enviar un mensaje a este jugador porque te ha ignorado.")));
                return true;
            }

            plugin.getPlayerDataManager().setReplyTarget(player, target);

            Component parsedMessage = player.hasPermission("neochat.chat.color") ? mm.deserialize(messageRaw) : Component.text(messageRaw);

            String sendFormat = plugin.getMessages().getString("pm-format-send", "<gray>[Yo -> %target%] <white><message>")
                    .replace("%target%", target.getName());
            player.sendMessage(mm.deserialize(sendFormat, Placeholder.component("message", parsedMessage)));

            String receiveFormat = plugin.getMessages().getString("pm-format-receive", "<gray>[%sender% -> Yo] <white><message>")
                    .replace("%sender%", player.getName());
            target.sendMessage(mm.deserialize(receiveFormat, Placeholder.component("message", parsedMessage)));

            String spyFormatStr = plugin.getMessages().getString("pm-format-spy", "<dark_gray>[Spy] %sender% -> %target%: <message>")
                    .replace("%sender%", player.getName())
                    .replace("%target%", target.getName());
            Component spyComponent = mm.deserialize(spyFormatStr, Placeholder.component("message", parsedMessage));

            for (UUID spyId : plugin.getPlayerDataManager().getSpies()) {
                Player spyPlayer = Bukkit.getPlayer(spyId);
                if (spyPlayer != null && spyPlayer.isOnline() && !spyPlayer.equals(player) && !spyPlayer.equals(target)) {
                    spyPlayer.sendMessage(spyComponent);
                }
            }
            return true;
        }

        return false;
    }

    @Override
    public @org.jetbrains.annotations.Nullable java.util.List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        java.util.List<String> completions = new java.util.ArrayList<>();

        if (command.getName().equalsIgnoreCase("msg") && args.length == 1) {
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