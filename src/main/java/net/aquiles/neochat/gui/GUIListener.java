package net.aquiles.neochat.gui;

import net.aquiles.neochat.NeoChat;
import net.aquiles.neochat.utils.InventorySnapshot;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.UUID;

public class GUIListener implements Listener {

    private final NeoChat plugin;
    private final MiniMessage mm;

    public GUIListener(NeoChat plugin) {
        this.plugin = plugin;
        this.mm = MiniMessage.miniMessage();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();

        if (message.toLowerCase().startsWith("/neochat viewinv ")) {
            event.setCancelled(true);

            Player player = event.getPlayer();
            String[] args = message.split(" ");

            if (args.length != 3) return;

            try {
                UUID snapshotId = UUID.fromString(args[2]);
                InventorySnapshot snapshot = plugin.getPlayerDataManager().getSnapshot(snapshotId);

                if (snapshot == null) {
                    player.sendMessage(mm.deserialize(plugin.getMessages().getString("inv-expired", "<red>Este inventario ha expirado o ya no existe.")));
                    return;
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
                player.sendMessage(mm.deserialize(plugin.getMessages().getString("inv-invalid-id", "<red>ID de inventario inválido.")));
            }
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            if (plugin.getPlayerDataManager().isViewingSnapshot(player)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            if (plugin.getPlayerDataManager().isViewingSnapshot(player)) {
                plugin.getPlayerDataManager().setViewingSnapshot(player, false);
            }
        }
    }
}