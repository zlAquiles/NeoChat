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

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class ViewInvCommand implements CommandExecutor, TabCompleter {

    private final NeoChat plugin;
    private final MiniMessage mm;

    public ViewInvCommand(NeoChat plugin) {
        this.plugin = plugin;
        this.mm = MiniMessage.miniMessage();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Solo jugadores.");
            return true;
        }

        if (args.length != 1) return true;

        try {
            UUID snapshotId = UUID.fromString(args[0]);
            InventorySnapshot snapshot = plugin.getPlayerDataManager().getSnapshot(snapshotId);

            if (snapshot == null) {
                player.sendMessage(mm.deserialize(plugin.getMessages().getString("inv-expired", "<red>Este inventario ha expirado o ya no existe.")));
                return true;
            }

            String targetName = "inv";
            if (snapshot.getType() == InventorySnapshot.SnapshotType.ENDERCHEST) targetName = "ender";
            if (snapshot.getType() == InventorySnapshot.SnapshotType.SHULKER) targetName = "shulker";

            String rawTitle = "Inventario de %player%";

            for (java.util.Map<?, ?> phMap : plugin.getConfig().getMapList("chat-placeholders")) {
                Object nameObj = phMap.get("name");
                if (nameObj instanceof String && targetName.equalsIgnoreCase((String) nameObj)) {
                    Object titleObj = phMap.get("gui-title");
                    if (titleObj instanceof String) {
                        rawTitle = (String) titleObj;
                    }
                    break;
                }
            }

            rawTitle = rawTitle.replace("%player%", snapshot.getPlayerName());
            Inventory inv;

            if (snapshot.getType() == InventorySnapshot.SnapshotType.PLAYER) {
                inv = Bukkit.createInventory(null, 54, Component.text(rawTitle));
                ItemStack glass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
                ItemMeta meta = glass.getItemMeta();
                meta.displayName(Component.empty());
                glass.setItemMeta(meta);
                for (int i = 0; i < 9; i++) inv.setItem(i, glass);
                for (int i = 36; i < 45; i++) inv.setItem(i, glass);

                ItemStack[] armor = snapshot.getArmor();
                if (armor.length == 4) {
                    inv.setItem(0, armor[3]);
                    inv.setItem(1, armor[2]);
                    inv.setItem(2, armor[1]);
                    inv.setItem(3, armor[0]);
                }
                inv.setItem(4, snapshot.getOffHand());

                ItemStack[] contents = snapshot.getContents();
                for (int i = 9; i < 36; i++) {
                    if (i < contents.length && contents[i] != null) inv.setItem(i, contents[i]);
                }
                for (int i = 0; i < 9; i++) {
                    if (i < contents.length && contents[i] != null) inv.setItem(45 + i, contents[i]);
                }
            } else {
                inv = Bukkit.createInventory(null, 27, Component.text(rawTitle));
                inv.setContents(snapshot.getContents());
            }

            plugin.getPlayerDataManager().setViewingSnapshot(player, true);
            player.openInventory(inv);

        } catch (IllegalArgumentException e) {
            sender.sendMessage(mm.deserialize(plugin.getMessages().getString("inv-invalid-id", "<red>ID de inventario inválido.")));
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        return Collections.emptyList();
    }
}