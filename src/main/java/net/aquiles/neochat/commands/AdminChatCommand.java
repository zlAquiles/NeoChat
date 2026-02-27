package net.aquiles.neochat.commands;

import net.aquiles.neochat.NeoChat;
import net.aquiles.neochat.utils.InventorySnapshot;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AdminChatCommand implements CommandExecutor, TabCompleter {

    private final NeoChat plugin;
    private final MiniMessage mm;

    public AdminChatCommand(NeoChat plugin) {
        this.plugin = plugin;
        this.mm = MiniMessage.miniMessage();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {

        String noPermMsg = plugin.getMessages().getString("no-permission", "<red>No tienes permisos.");

        if (command.getName().equalsIgnoreCase("chatmute")) {
            if (!sender.hasPermission("neochat.admin.mute")) {
                sender.sendMessage(mm.deserialize(noPermMsg));
                return true;
            }
            if (plugin.isChatMuted()) {
                sender.sendMessage(mm.deserialize(plugin.getMessages().getString("chat-already-muted", "<red>El chat ya está silenciado.")));
                return true;
            }

            plugin.setChatMuted(true);
            String broadcastMsg = plugin.getMessages().getString("chat-muted-broadcast", "<red>El chat ha sido silenciado.");
            Bukkit.broadcast(mm.deserialize(broadcastMsg));
            return true;
        }

        if (command.getName().equalsIgnoreCase("chatunmute")) {
            if (!sender.hasPermission("neochat.admin.mute")) {
                sender.sendMessage(mm.deserialize(noPermMsg));
                return true;
            }
            if (!plugin.isChatMuted()) {
                sender.sendMessage(mm.deserialize(plugin.getMessages().getString("chat-already-unmuted", "<red>El chat ya está habilitado.")));
                return true;
            }

            plugin.setChatMuted(false);
            String broadcastMsg = plugin.getMessages().getString("chat-unmuted-broadcast", "<green>El chat ha sido habilitado.");
            Bukkit.broadcast(mm.deserialize(broadcastMsg));
            return true;
        }

        if (command.getName().equalsIgnoreCase("neochat")) {

            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("neochat.admin.reload")) {
                    sender.sendMessage(mm.deserialize(noPermMsg));
                    return true;
                }

                plugin.reloadPlugin();
                String reloadMsg = plugin.getMessages().getString("reload", "<green>Recargado.");
                sender.sendMessage(mm.deserialize(reloadMsg));
                return true;
            }

            sender.sendMessage(mm.deserialize("<aqua>NeoChat <gray>v" + plugin.getDescription().getVersion() + " por <white>Aquiles"));
            return true;
        }

        return false;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("neochat") && args.length == 1) {
            if (sender.hasPermission("neochat.admin.reload")) {
                completions.add("reload");
            }
        }
        return completions;
    }
}