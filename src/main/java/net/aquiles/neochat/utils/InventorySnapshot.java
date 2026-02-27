package net.aquiles.neochat.utils;

import org.bukkit.inventory.ItemStack;

public class InventorySnapshot {

    public enum SnapshotType {
        PLAYER, ENDERCHEST, SHULKER
    }

    private final String playerName;
    private final SnapshotType type;
    private final ItemStack[] contents;
    private final ItemStack[] armor;
    private final ItemStack offHand;

    public InventorySnapshot(String playerName, ItemStack[] contents, ItemStack[] armor, ItemStack offHand) {
        this.playerName = playerName;
        this.type = SnapshotType.PLAYER;
        this.contents = contents != null ? contents.clone() : new ItemStack[0];
        this.armor = armor != null ? armor.clone() : new ItemStack[0];
        this.offHand = offHand != null ? offHand.clone() : null;
    }

    public InventorySnapshot(String playerName, SnapshotType type, ItemStack[] contents) {
        this.playerName = playerName;
        this.type = type;
        this.contents = contents != null ? contents.clone() : new ItemStack[0];
        this.armor = new ItemStack[0];
        this.offHand = null;
    }

    public String getPlayerName() { return playerName; }
    public SnapshotType getType() { return type; }
    public ItemStack[] getContents() { return contents; }
    public ItemStack[] getArmor() { return armor; }
    public ItemStack getOffHand() { return offHand; }
}