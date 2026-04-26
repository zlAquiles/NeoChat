package net.aquiles.neochat.managers;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.aquiles.neochat.NeoChat;
import net.aquiles.neochat.utils.InventorySnapshot;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class PlayerDataManager {

    private final Map<UUID, UUID> lastMessaged = new ConcurrentHashMap<>();
    private final Set<UUID> pmToggledOff = ConcurrentHashMap.newKeySet();
    private final Set<UUID> spyEnabled = ConcurrentHashMap.newKeySet();

    private final Cache<UUID, InventorySnapshot> inventorySnapshots = CacheBuilder.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build();

    private final Set<UUID> viewingSnapshot = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Set<UUID>> ignoredPlayers = new ConcurrentHashMap<>();
    private final Set<UUID> ignoreAllEnabled = ConcurrentHashMap.newKeySet();
    private final Set<UUID> announcementsDisabled = ConcurrentHashMap.newKeySet();

    public PlayerDataManager(NeoChat neoChat) {
    }

    public void addSnapshot(UUID id, InventorySnapshot snapshot) {
        inventorySnapshots.put(id, snapshot);
    }

    public InventorySnapshot getSnapshot(UUID id) {
        return inventorySnapshots.getIfPresent(id);
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
        return Set.copyOf(spyEnabled);
    }

    public boolean toggleIgnore(Player player, Player target) {
        ignoredPlayers.putIfAbsent(player.getUniqueId(), ConcurrentHashMap.newKeySet());
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

    public boolean areAnnouncementsEnabled(Player player) {
        return !announcementsDisabled.contains(player.getUniqueId());
    }

    public void setAnnouncementsEnabled(Player player, boolean enabled) {
        if (enabled) {
            announcementsDisabled.remove(player.getUniqueId());
            return;
        }

        announcementsDisabled.add(player.getUniqueId());
    }
}
