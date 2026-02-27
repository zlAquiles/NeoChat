package net.aquiles.neochat.gui;

import net.aquiles.neochat.NeoChat;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

public class GUIListener implements Listener {

    private final NeoChat plugin;

    public GUIListener(NeoChat plugin) {
        this.plugin = plugin;
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