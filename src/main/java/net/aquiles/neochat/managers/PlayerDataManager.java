package net.aquiles.neochat.managers;

import net.aquiles.neochat.NeoChat;
import net.aquiles.neochat.utils.InventorySnapshot;
import org.bukkit.entity.Player;
import java.util.*;

public class PlayerDataManager {

    private final Map<UUID, UUID> lastMessaged = new HashMap<>();
    private final Set<UUID> pmToggledOff = new HashSet<>();
    private final Set<UUID> spyEnabled = new HashSet<>();

    private final Map<UUID, InventorySnapshot> inventorySnapshots = new HashMap<>();
    private final Set<UUID> viewingSnapshot = new HashSet<>();

    public PlayerDataManager(NeoChat neoChat) {
    }

    public void addSnapshot(UUID id, InventorySnapshot snapshot) {
        inventorySnapshots.put(id, snapshot);
    }

    public InventorySnapshot getSnapshot(UUID id) {
        return inventorySnapshots.get(id);
    }

    public void setViewingSnapshot(Player player, boolean viewing) {
        if (viewing) viewingSnapshot.add(player.getUniqueId());
        else viewingSnapshot.remove(player.getUniqueId());
    }

    public boolean isViewingSnapshot(Player player) {
        return viewingSnapshot.contains(player.getUniqueId());
    }

    public void setReplyTarget(Player sender, Player target) {
        lastMessaged.put(sender.getUniqueId(), target.getUniqueId());
        lastMessaged.put(target.getUniqueId(), sender.getUniqueId());
    }

    public UUID getReplyTarget(Player player) {
        return lastMessaged.get(player.getUniqueId());
    }

    public boolean togglePM(Player player) {
        if (pmToggledOff.contains(player.getUniqueId())) {
            pmToggledOff.remove(player.getUniqueId());
            return true;
        } else {
            pmToggledOff.add(player.getUniqueId());
            return false;
        }
    }

    public boolean hasPMsDisabled(Player player) {
        return pmToggledOff.contains(player.getUniqueId());
    }

    public boolean toggleSpy(Player player) {
        if (spyEnabled.contains(player.getUniqueId())) {
            spyEnabled.remove(player.getUniqueId());
            return false;
        } else {
            spyEnabled.add(player.getUniqueId());
            return true;
        }
    }

    public Set<UUID> getSpies() {
        return spyEnabled;
    }
    private final Map<UUID, Set<UUID>> ignoredPlayers = new HashMap<>();
    private final Set<UUID> ignoreAllEnabled = new HashSet<>();

    public boolean toggleIgnore(Player player, Player target) {
        ignoredPlayers.putIfAbsent(player.getUniqueId(), new HashSet<>());
        Set<UUID> ignores = ignoredPlayers.get(player.getUniqueId());

        if (ignores.contains(target.getUniqueId())) {
            ignores.remove(target.getUniqueId());
            return false;
        } else {
            ignores.add(target.getUniqueId());
            return true;
        }
    }

    public boolean toggleIgnoreAll(Player player) {
        if (ignoreAllEnabled.contains(player.getUniqueId())) {
            ignoreAllEnabled.remove(player.getUniqueId());
            return false;
        } else {
            ignoreAllEnabled.add(player.getUniqueId());
            return true;
        }
    }

    public boolean isIgnoring(Player receiver, Player sender) {
        if (sender.hasPermission("neochat.ignore.bypass")) return false;

        if (ignoreAllEnabled.contains(receiver.getUniqueId())) return true;

        Set<UUID> ignores = ignoredPlayers.get(receiver.getUniqueId());
        return ignores != null && ignores.contains(sender.getUniqueId());
    }
}