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
import java.util.Map;
import java.util.UUID;

public class ViewInvCommand implements CommandExecutor, TabCompleter {

    private final NeoChat plugin;
    private final MiniMessage miniMessage;

    public ViewInvCommand(NeoChat plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Solo jugadores.");
            return true;
        }

        if (args.length != 1) {
            return true;
        }

        try {
            UUID snapshotId = UUID.fromString(args[0]);
            InventorySnapshot snapshot = plugin.getPlayerDataManager().getSnapshot(snapshotId);

            if (snapshot == null) {
                plugin.sendMessage(player, miniMessage.deserialize(plugin.getMessages().getString("inv-expired", "<red>Este inventario ha expirado o ya no existe.")));
                return true;
            }

            String placeholderName = "inv";
            if (snapshot.getType() == InventorySnapshot.SnapshotType.ENDERCHEST) {
                placeholderName = "ender";
            } else if (snapshot.getType() == InventorySnapshot.SnapshotType.SHULKER) {
                placeholderName = "shulker";
            }

            String rawTitle = "Inventario de %player%";
            for (Map<?, ?> placeholder : plugin.getConfig().getMapList("chat-placeholders")) {
                Object name = placeholder.get("name");
                if (name instanceof String && placeholderName.equalsIgnoreCase((String) name)) {
                    Object title = placeholder.get("gui-title");
                    if (title instanceof String) {
                        rawTitle = (String) title;
                    }
                    break;
                }
            }

            rawTitle = rawTitle.replace("%player%", snapshot.getPlayerName());
            Inventory inventory = createInventory(snapshot, rawTitle);
            plugin.getPlayerDataManager().setViewingSnapshot(player, true);
            plugin.openInventory(player, inventory);
        } catch (IllegalArgumentException exception) {
            plugin.sendMessage(player, miniMessage.deserialize(plugin.getMessages().getString("inv-invalid-id", "<red>ID de inventario invalido.")));
        }

        return true;
    }

    private Inventory createInventory(InventorySnapshot snapshot, String rawTitle) {
        if (snapshot.getType() != InventorySnapshot.SnapshotType.PLAYER) {
            Inventory inventory = Bukkit.createInventory(null, 27, Component.text(rawTitle));
            inventory.setContents(snapshot.getContents());
            return inventory;
        }

        Inventory inventory = Bukkit.createInventory(null, 54, Component.text(rawTitle));
        ItemStack glass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = glass.getItemMeta();
        meta.displayName(Component.empty());
        glass.setItemMeta(meta);

        for (int slot = 0; slot < 9; slot++) {
            inventory.setItem(slot, glass);
        }
        for (int slot = 36; slot < 45; slot++) {
            inventory.setItem(slot, glass);
        }

        ItemStack[] armor = snapshot.getArmor();
        if (armor.length == 4) {
            inventory.setItem(0, armor[3]);
            inventory.setItem(1, armor[2]);
            inventory.setItem(2, armor[1]);
            inventory.setItem(3, armor[0]);
        }
        inventory.setItem(4, snapshot.getOffHand());

        ItemStack[] contents = snapshot.getContents();
        for (int slot = 9; slot < 36; slot++) {
            if (slot < contents.length && contents[slot] != null) {
                inventory.setItem(slot, contents[slot]);
            }
        }
        for (int slot = 0; slot < 9; slot++) {
            if (slot < contents.length && contents[slot] != null) {
                inventory.setItem(45 + slot, contents[slot]);
            }
        }

        return inventory;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        return Collections.emptyList();
    }
}
