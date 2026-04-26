package net.aquiles.neochat.announcer;

import net.aquiles.neochat.NeoChat;
import net.aquiles.neochat.utils.FoliaSupport;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.SoundCategory;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class AnnouncementManager implements Listener {

    public static final String USE_PERMISSION = "neochat.announcer.use";
    public static final String TOGGLE_PERMISSION = "neochat.announcer.toggle";
    public static final String PREVIEW_PERMISSION = "neochat.announcer.preview";
    public static final String SEND_PERMISSION = "neochat.announcer.send";
    public static final String RECEIVE_PERMISSION = "neochat.announcer.receive";
    public static final String FORCE_RECEIVE_PERMISSION = "neochat.announcer.force_receive";

    private static final String DEFAULT_PREFIX = "<dark_gray>[<aqua>Announcer<dark_gray>]";

    private final NeoChat plugin;
    private final MiniMessage miniMessage;
    private final Map<UUID, RecipientState> recipientStates = new ConcurrentHashMap<>();
    private final AnnouncementToastService toastService;

    private volatile List<AnnouncementDefinition> announcements = List.of();
    private volatile boolean moduleEnabled;
    private volatile int intervalSeconds;
    private volatile boolean randomOrder;
    private volatile boolean sendOnStartup;
    private volatile boolean skipIfNoPlayers;
    private volatile boolean skipIfNoRecipients;

    private volatile FoliaSupport.ScheduledTaskHandle automaticTask;
    private int announcementCursor;

    public AnnouncementManager(NeoChat plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
        this.toastService = new AnnouncementToastService(plugin);
    }

    public void enable() {
        loadConfiguration();
        startAutomaticTask(true);
    }

    public void reload() {
        stopAutomaticTask();
        clearAllRecipientStates();
        loadConfiguration();
        startAutomaticTask(false);
    }

    public void shutdown() {
        stopAutomaticTask();
        clearAllRecipientStates();
        toastService.shutdown();
        announcements = List.of();
        moduleEnabled = false;
        announcementCursor = 0;
    }

    public boolean isModuleEnabled() {
        return moduleEnabled;
    }

    public void sendTemplate(CommandSender sender, String key) {
        sendTemplate(sender, key, Map.of());
    }

    public void sendTemplate(CommandSender sender, String key, Map<String, String> placeholders) {
        String raw = plugin.getMessages().getString(key, "<red>Missing message: " + key);
        Player player = sender instanceof Player online ? online : null;
        plugin.sendMessage(sender, renderComponent(player, raw, placeholders));
    }

    public void showStatus(Player player) {
        if (hasForceReceive(player)) {
            sendTemplate(player, "announcer-status-forced");
            return;
        }

        sendTemplate(
                player,
                "announcer-status",
                Map.of("%status%", announcementsEnabled(player) ? "<green>ENABLED" : "<red>DISABLED")
        );
    }

    public void toggleAnnouncements(Player player, Boolean desiredState) {
        if (hasForceReceive(player)) {
            sendTemplate(player, "announcer-force-receive");
            return;
        }

        boolean enabled = desiredState != null ? desiredState : !plugin.getPlayerDataManager().areAnnouncementsEnabled(player);
        plugin.getPlayerDataManager().setAnnouncementsEnabled(player, enabled);
        sendTemplate(player, enabled ? "announcer-toggle-on" : "announcer-toggle-off");
    }

    public void showAnnouncementList(CommandSender sender) {
        if (announcements.isEmpty()) {
            sendTemplate(sender, "announcer-empty");
            return;
        }

        sendTemplate(sender, "announcer-list-header", Map.of("%count%", String.valueOf(announcements.size())));
        for (AnnouncementDefinition announcement : announcements) {
            sendTemplate(
                    sender,
                    "announcer-list-entry",
                    Map.of(
                            "%id%", announcement.id(),
                            "%status%", announcement.enabled() ? "<green>enabled" : "<red>disabled"
                    )
            );
        }
    }

    public List<String> announcementIds() {
        return announcements.stream()
                .map(AnnouncementDefinition::id)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    public List<String> onlinePlayers(String partialName) {
        String normalized = partialName == null ? "" : partialName.toLowerCase(Locale.ROOT);
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(normalized))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    public void preview(Player player, String announcementId) {
        Optional<AnnouncementDefinition> definition = findAnnouncement(announcementId);
        if (definition.isEmpty()) {
            sendTemplate(player, "announcer-unknown", Map.of("%id%", announcementId));
            return;
        }

        deliverToPlayer(player, definition.get(), true);
        sendTemplate(player, "announcer-preview-sent", Map.of("%id%", definition.get().id()));
    }

    public void sendAnnouncement(CommandSender sender, String announcementId, String targetName) {
        Optional<AnnouncementDefinition> definition = findAnnouncement(announcementId);
        if (definition.isEmpty()) {
            sendTemplate(sender, "announcer-unknown", Map.of("%id%", announcementId));
            return;
        }

        AnnouncementDefinition announcement = definition.get();
        if (targetName != null && !targetName.isBlank()) {
            Player target = Bukkit.getPlayer(targetName);
            if (target == null) {
                sendTemplate(sender, "announcer-offline");
                return;
            }

            deliverToPlayer(target, announcement, true);
            sendTemplate(sender, "announcer-sent-to-player", Map.of("%id%", announcement.id(), "%player%", target.getName()));
            return;
        }

        List<Player> recipients = eligibleRecipients(announcement);
        if (recipients.isEmpty()) {
            sendTemplate(sender, "announcer-no-targets", Map.of("%id%", announcement.id()));
            return;
        }

        for (Player recipient : recipients) {
            deliverToPlayer(recipient, announcement, false);
        }

        sendTemplate(sender, "announcer-broadcast-sent", Map.of("%id%", announcement.id(), "%count%", String.valueOf(recipients.size())));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        clearRecipientState(event.getPlayer().getUniqueId(), event.getPlayer());
    }

    private void loadConfiguration() {
        YamlConfiguration config = plugin.getAnnouncements();
        moduleEnabled = config.getBoolean("module.enabled", false);
        intervalSeconds = Math.max(1, config.getInt("settings.interval-seconds", 180));
        randomOrder = config.getBoolean("settings.random-order", false);
        sendOnStartup = config.getBoolean("settings.send-on-startup", false);
        skipIfNoPlayers = config.getBoolean("settings.skip-if-no-players", true);
        skipIfNoRecipients = config.getBoolean("settings.skip-if-no-recipients", true);
        announcementCursor = 0;

        ConfigurationSection announcementsSection = config.getConfigurationSection("announcements");
        if (announcementsSection == null) {
            announcements = List.of();
            toastService.reload(announcements);
            return;
        }

        List<AnnouncementDefinition> loaded = new ArrayList<>();
        for (String id : announcementsSection.getKeys(false)) {
            ConfigurationSection section = announcementsSection.getConfigurationSection(id);
            if (section == null) {
                continue;
            }

            loaded.add(parseAnnouncement(id, section));
        }

        announcements = List.copyOf(loaded);
        toastService.reload(announcements);
    }

    private AnnouncementDefinition parseAnnouncement(String id, ConfigurationSection section) {
        ConfigurationSection chatSection = section.getConfigurationSection("chat");
        ConfigurationSection titleSection = section.getConfigurationSection("title");
        ConfigurationSection actionBarSection = section.getConfigurationSection("actionbar");
        ConfigurationSection bossBarSection = section.getConfigurationSection("bossbar");
        ConfigurationSection soundSection = section.getConfigurationSection("sound");
        ConfigurationSection toastSection = section.getConfigurationSection("toast");

        List<String> worldPatterns = section.getStringList("worlds");
        if (worldPatterns.isEmpty()) {
            worldPatterns = List.of("*");
        }

        return new AnnouncementDefinition(
                id,
                section.getBoolean("enabled", true),
                section.getString("permission", ""),
                section.getString("exclude-permission", ""),
                List.copyOf(worldPatterns),
                new ChatSpec(
                        chatSection != null && chatSection.getBoolean("enabled", true),
                        chatSection == null ? List.of() : List.copyOf(chatSection.getStringList("lines"))
                ),
                new TitleSpec(
                        titleSection != null && titleSection.getBoolean("enabled", false),
                        titleSection == null ? "" : titleSection.getString("title", ""),
                        titleSection == null ? "" : titleSection.getString("subtitle", ""),
                        titleSection == null ? 10 : Math.max(0, titleSection.getInt("fade-in", 10)),
                        titleSection == null ? 50 : Math.max(0, titleSection.getInt("stay", 50)),
                        titleSection == null ? 10 : Math.max(0, titleSection.getInt("fade-out", 10))
                ),
                new ActionBarSpec(
                        actionBarSection != null && actionBarSection.getBoolean("enabled", false),
                        actionBarSection == null ? "" : actionBarSection.getString("text", ""),
                        actionBarSection == null ? 60 : Math.max(20, actionBarSection.getInt("duration-ticks", 60))
                ),
                new BossBarSpec(
                        bossBarSection != null && bossBarSection.getBoolean("enabled", false),
                        bossBarSection == null ? "" : bossBarSection.getString("text", ""),
                        bossBarSection == null ? 1.0F : (float) Math.max(0.0D, Math.min(1.0D, bossBarSection.getDouble("progress", 1.0D))),
                        bossBarSection == null ? "blue" : bossBarSection.getString("color", "blue"),
                        bossBarSection == null ? "progress" : bossBarSection.getString("overlay", "progress"),
                        bossBarSection == null ? 100 : Math.max(20, bossBarSection.getInt("duration-ticks", 100)),
                        bossBarSection == null ? List.of() : List.copyOf(bossBarSection.getStringList("flags"))
                ),
                new SoundSpec(
                        soundSection != null && soundSection.getBoolean("enabled", false),
                        soundSection == null ? "" : soundSection.getString("key", ""),
                        soundSection == null ? 1.0F : (float) soundSection.getDouble("volume", 1.0D),
                        soundSection == null ? 1.0F : (float) soundSection.getDouble("pitch", 1.0D)
                ),
                new ToastSpec(
                        toastSection != null && toastSection.getBoolean("enabled", false),
                        toastSection == null ? "" : toastSection.getString("title", ""),
                        toastSection == null ? "" : toastSection.getString("description", ""),
                        toastSection == null ? "minecraft:paper" : toastSection.getString("icon", "minecraft:paper"),
                        toastSection == null ? "task" : toastSection.getString("frame", "task")
                )
        );
    }

    private void startAutomaticTask(boolean allowStartupSend) {
        if (!moduleEnabled || enabledAnnouncements().isEmpty()) {
            return;
        }

        long intervalTicks = intervalSeconds * 20L;
        automaticTask = plugin.runTimer(this::dispatchNextAutomaticAnnouncement, intervalTicks, intervalTicks);
        if (allowStartupSend && sendOnStartup) {
            plugin.runLater(this::dispatchNextAutomaticAnnouncement, 1L);
        }
    }

    private void stopAutomaticTask() {
        cancelTask(automaticTask);
        automaticTask = null;
    }

    private void dispatchNextAutomaticAnnouncement() {
        List<AnnouncementDefinition> enabledDefinitions = enabledAnnouncements();
        if (!moduleEnabled || enabledDefinitions.isEmpty()) {
            return;
        }

        if (skipIfNoPlayers && Bukkit.getOnlinePlayers().isEmpty()) {
            return;
        }

        List<AnnouncementDefinition> deliveryOrder = buildDeliveryOrder(enabledDefinitions);
        for (AnnouncementDefinition announcement : deliveryOrder) {
            List<Player> recipients = eligibleRecipients(announcement);
            if (recipients.isEmpty() && skipIfNoRecipients) {
                continue;
            }

            for (Player recipient : recipients) {
                deliverToPlayer(recipient, announcement, false);
            }
            return;
        }
    }

    private List<AnnouncementDefinition> enabledAnnouncements() {
        return announcements.stream()
                .filter(AnnouncementDefinition::enabled)
                .toList();
    }

    private List<AnnouncementDefinition> buildDeliveryOrder(List<AnnouncementDefinition> enabledDefinitions) {
        if (enabledDefinitions.isEmpty()) {
            return List.of();
        }

        if (randomOrder) {
            List<AnnouncementDefinition> shuffled = new ArrayList<>(enabledDefinitions);
            for (int i = shuffled.size() - 1; i > 0; i--) {
                int swapIndex = ThreadLocalRandom.current().nextInt(i + 1);
                AnnouncementDefinition temporary = shuffled.get(i);
                shuffled.set(i, shuffled.get(swapIndex));
                shuffled.set(swapIndex, temporary);
            }
            return shuffled;
        }

        List<AnnouncementDefinition> ordered = new ArrayList<>(enabledDefinitions.size());
        for (int offset = 0; offset < enabledDefinitions.size(); offset++) {
            int index = (announcementCursor + offset) % enabledDefinitions.size();
            ordered.add(enabledDefinitions.get(index));
        }
        announcementCursor = (announcementCursor + 1) % enabledDefinitions.size();
        return ordered;
    }

    private Optional<AnnouncementDefinition> findAnnouncement(String announcementId) {
        return announcements.stream()
                .filter(announcement -> announcement.id().equalsIgnoreCase(announcementId))
                .findFirst();
    }

    private List<Player> eligibleRecipients(AnnouncementDefinition announcement) {
        List<Player> recipients = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (canReceiveAnnouncement(player, announcement)) {
                recipients.add(player);
            }
        }
        return recipients;
    }

    private boolean canReceiveAnnouncement(Player player, AnnouncementDefinition announcement) {
        if (player == null || !player.isOnline()) {
            return false;
        }

        if (hasForceReceive(player)) {
            return true;
        }

        if (!player.hasPermission(RECEIVE_PERMISSION)) {
            return false;
        }

        if (!plugin.getPlayerDataManager().areAnnouncementsEnabled(player)) {
            return false;
        }

        if (!announcement.permission().isBlank() && !player.hasPermission(announcement.permission())) {
            return false;
        }

        if (!announcement.excludePermission().isBlank() && player.hasPermission(announcement.excludePermission())) {
            return false;
        }

        return matchesWorld(player.getWorld().getName(), announcement.worldPatterns());
    }

    private boolean announcementsEnabled(Player player) {
        return hasForceReceive(player) || plugin.getPlayerDataManager().areAnnouncementsEnabled(player);
    }

    private boolean hasForceReceive(Player player) {
        return player.hasPermission(FORCE_RECEIVE_PERMISSION);
    }

    private void deliverToPlayer(Player player, AnnouncementDefinition announcement, boolean forceSend) {
        if (player == null || !player.isOnline()) {
            return;
        }

        if (!forceSend && !canReceiveAnnouncement(player, announcement)) {
            return;
        }

        Map<String, String> placeholders = placeholders(player, announcement);
        sendChat(player, announcement.chat(), placeholders);
        playSound(player, announcement.sound());
        showToast(player, announcement);
        showTitle(player, announcement.title(), placeholders);
        showActionBar(player, announcement.actionBar(), placeholders);
        showBossBar(player, announcement.bossBar(), placeholders);
    }

    private void showToast(Player player, AnnouncementDefinition announcement) {
        toastService.showToast(player, announcement);
    }

    private void sendChat(Player player, ChatSpec chatSpec, Map<String, String> placeholders) {
        if (!chatSpec.enabled() || chatSpec.lines().isEmpty()) {
            return;
        }

        for (String line : chatSpec.lines()) {
            if (line == null || line.isBlank()) {
                continue;
            }
            plugin.sendMessage(player, renderComponent(player, line, placeholders));
        }
    }

    private void playSound(Player player, SoundSpec soundSpec) {
        if (!soundSpec.enabled() || soundSpec.key().isBlank()) {
            return;
        }

        String key = normalizeSoundKey(soundSpec.key());
        plugin.runForEntity(player, () -> {
            try {
                player.playSound(player.getLocation(), key, SoundCategory.MASTER, soundSpec.volume(), soundSpec.pitch());
            } catch (Exception exception) {
                plugin.getLogger().warning("Invalid announcer sound key: " + soundSpec.key());
            }
        });
    }

    private void showTitle(Player player, TitleSpec titleSpec, Map<String, String> placeholders) {
        if (!titleSpec.enabled()) {
            return;
        }

        Component title = renderComponent(player, titleSpec.titleText(), placeholders);
        Component subtitle = renderComponent(player, titleSpec.subtitleText(), placeholders);
        if (title.equals(Component.empty()) && subtitle.equals(Component.empty())) {
            return;
        }

        Title adventureTitle = Title.title(
                title,
                subtitle,
                Title.Times.times(
                        Duration.ofMillis(titleSpec.fadeIn() * 50L),
                        Duration.ofMillis(titleSpec.stay() * 50L),
                        Duration.ofMillis(titleSpec.fadeOut() * 50L)
                )
        );

        plugin.runForEntity(player, () -> player.showTitle(adventureTitle));
    }

    private void showActionBar(Player player, ActionBarSpec actionBarSpec, Map<String, String> placeholders) {
        RecipientState state = recipientState(player.getUniqueId());
        cancelTask(state.actionBarTask);
        state.actionBarTask = null;

        if (!actionBarSpec.enabled() || actionBarSpec.text().isBlank()) {
            pruneRecipientState(player.getUniqueId());
            return;
        }

        Component message = renderComponent(player, actionBarSpec.text(), placeholders);
        if (message.equals(Component.empty())) {
            pruneRecipientState(player.getUniqueId());
            return;
        }

        plugin.runForEntity(player, () -> player.sendActionBar(message));

        int iterations = Math.max(1, (int) Math.ceil(actionBarSpec.durationTicks() / 20.0D));
        if (iterations <= 1) {
            pruneRecipientState(player.getUniqueId());
            return;
        }

        AtomicInteger remainingRuns = new AtomicInteger(iterations - 1);
        AtomicReference<FoliaSupport.ScheduledTaskHandle> taskReference = new AtomicReference<>();
        taskReference.set(plugin.runTimer(() -> {
            Player current = Bukkit.getPlayer(player.getUniqueId());
            if (current == null || !current.isOnline()) {
                cancelTask(taskReference.get());
                clearRecipientState(player.getUniqueId(), null);
                return;
            }

            plugin.runForEntity(current, () -> current.sendActionBar(message));
            if (remainingRuns.decrementAndGet() <= 0) {
                cancelTask(taskReference.get());
                RecipientState currentState = recipientStates.get(player.getUniqueId());
                if (currentState != null) {
                    currentState.actionBarTask = null;
                }
                pruneRecipientState(player.getUniqueId());
            }
        }, 20L, 20L));
        state.actionBarTask = taskReference.get();
    }

    private void showBossBar(Player player, BossBarSpec bossBarSpec, Map<String, String> placeholders) {
        cancelBossBar(player.getUniqueId(), player);

        if (!bossBarSpec.enabled() || bossBarSpec.text().isBlank()) {
            return;
        }

        Component name = renderComponent(player, bossBarSpec.text(), placeholders);
        if (name.equals(Component.empty())) {
            return;
        }

        BossBar bossBar = BossBar.bossBar(name, bossBarSpec.progress(), parseBossBarColor(bossBarSpec.color()), parseBossBarOverlay(bossBarSpec.overlay()));
        for (BossBar.Flag flag : parseBossBarFlags(bossBarSpec.flags())) {
            bossBar.addFlag(flag);
        }

        RecipientState state = recipientState(player.getUniqueId());
        state.activeBossBar = bossBar;
        plugin.runForEntity(player, () -> player.showBossBar(bossBar));

        state.bossBarTask = plugin.runLater(() -> {
            Player current = Bukkit.getPlayer(player.getUniqueId());
            if (current != null && current.isOnline()) {
                plugin.runForEntity(current, () -> current.hideBossBar(bossBar));
            }

            RecipientState currentState = recipientStates.get(player.getUniqueId());
            if (currentState != null && currentState.activeBossBar == bossBar) {
                currentState.activeBossBar = null;
                currentState.bossBarTask = null;
            }
            pruneRecipientState(player.getUniqueId());
        }, bossBarSpec.durationTicks());
    }

    private Component renderComponent(Player player, String raw, Map<String, String> placeholders) {
        if (raw == null || raw.isBlank()) {
            return Component.empty();
        }

        String value = raw.replace("<prefix>", plugin.getMessages().getString("announcer-prefix", DEFAULT_PREFIX));
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            value = value.replace(entry.getKey(), entry.getValue());
        }

        if (player != null) {
            value = plugin.applyPapiPlaceholders(player, value);
        }

        value = plugin.translateLegacy(value);
        return miniMessage.deserialize(value);
    }

    private Map<String, String> placeholders(Player player, AnnouncementDefinition announcement) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%player%", player.getName());
        placeholders.put("%player_name%", player.getName());
        placeholders.put("%world%", player.getWorld().getName());
        placeholders.put("%announcement_id%", announcement.id());
        placeholders.put("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()));
        return placeholders;
    }

    private boolean matchesWorld(String currentWorld, List<String> patterns) {
        if (patterns == null || patterns.isEmpty()) {
            return true;
        }

        for (String rawPattern : patterns) {
            String pattern = rawPattern == null ? "" : rawPattern.trim();
            if (pattern.isBlank() || pattern.equalsIgnoreCase("*") || pattern.equalsIgnoreCase("all")) {
                return true;
            }

            String regex = "^" + pattern.replace(".", "\\.").replace("*", ".*") + "$";
            if (currentWorld.toLowerCase(Locale.ROOT).matches(regex.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }

        return false;
    }

    private String normalizeSoundKey(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return "";
        }

        String normalized = rawKey.trim();
        if (!normalized.contains(":")) {
            normalized = normalized.toLowerCase(Locale.ROOT);
            if (!normalized.contains(".")) {
                normalized = normalized.replace('_', '.');
            }
            normalized = "minecraft:" + normalized;
        }
        return normalized;
    }

    private RecipientState recipientState(UUID uniqueId) {
        return recipientStates.computeIfAbsent(uniqueId, ignored -> new RecipientState());
    }

    private void clearAllRecipientStates() {
        for (UUID uniqueId : new ArrayList<>(recipientStates.keySet())) {
            clearRecipientState(uniqueId, null);
        }
        recipientStates.clear();
    }

    private void clearRecipientState(UUID uniqueId, Player player) {
        RecipientState state = recipientStates.remove(uniqueId);
        if (state == null) {
            return;
        }

        cancelTask(state.actionBarTask);
        cancelTask(state.bossBarTask);

        if (state.activeBossBar != null) {
            Player resolvedPlayer = player != null ? player : Bukkit.getPlayer(uniqueId);
            if (resolvedPlayer != null && resolvedPlayer.isOnline()) {
                plugin.runForEntity(resolvedPlayer, () -> resolvedPlayer.hideBossBar(state.activeBossBar));
            }
            state.activeBossBar = null;
        }
    }

    private void cancelBossBar(UUID uniqueId, Player player) {
        RecipientState state = recipientStates.get(uniqueId);
        if (state == null) {
            return;
        }

        cancelTask(state.bossBarTask);
        state.bossBarTask = null;

        if (state.activeBossBar == null) {
            pruneRecipientState(uniqueId);
            return;
        }

        Player resolvedPlayer = player != null ? player : Bukkit.getPlayer(uniqueId);
        if (resolvedPlayer != null && resolvedPlayer.isOnline()) {
            plugin.runForEntity(resolvedPlayer, () -> resolvedPlayer.hideBossBar(state.activeBossBar));
        }
        state.activeBossBar = null;
        pruneRecipientState(uniqueId);
    }

    private void pruneRecipientState(UUID uniqueId) {
        RecipientState state = recipientStates.get(uniqueId);
        if (state == null) {
            return;
        }

        if (state.actionBarTask == null && state.bossBarTask == null && state.activeBossBar == null) {
            recipientStates.remove(uniqueId, state);
        }
    }

    private void cancelTask(FoliaSupport.ScheduledTaskHandle task) {
        if (task != null) {
            task.cancel();
        }
    }

    private BossBar.Color parseBossBarColor(String rawColor) {
        String normalized = rawColor == null ? "" : rawColor.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "PINK" -> BossBar.Color.PINK;
            case "BLUE" -> BossBar.Color.BLUE;
            case "RED" -> BossBar.Color.RED;
            case "GREEN" -> BossBar.Color.GREEN;
            case "YELLOW" -> BossBar.Color.YELLOW;
            case "PURPLE" -> BossBar.Color.PURPLE;
            case "WHITE" -> BossBar.Color.WHITE;
            default -> BossBar.Color.BLUE;
        };
    }

    private BossBar.Overlay parseBossBarOverlay(String rawOverlay) {
        String normalized = rawOverlay == null ? "" : rawOverlay.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "NOTCHED_6" -> BossBar.Overlay.NOTCHED_6;
            case "NOTCHED_10" -> BossBar.Overlay.NOTCHED_10;
            case "NOTCHED_12" -> BossBar.Overlay.NOTCHED_12;
            case "NOTCHED_20" -> BossBar.Overlay.NOTCHED_20;
            default -> BossBar.Overlay.PROGRESS;
        };
    }

    private List<BossBar.Flag> parseBossBarFlags(List<String> rawFlags) {
        List<BossBar.Flag> flags = new ArrayList<>();
        for (String rawFlag : rawFlags) {
            String normalized = rawFlag == null ? "" : rawFlag.trim().toUpperCase(Locale.ROOT);
            switch (normalized) {
                case "DARKEN_SCREEN" -> flags.add(BossBar.Flag.DARKEN_SCREEN);
                case "PLAY_BOSS_MUSIC" -> flags.add(BossBar.Flag.PLAY_BOSS_MUSIC);
                case "CREATE_WORLD_FOG" -> flags.add(BossBar.Flag.CREATE_WORLD_FOG);
                default -> {
                }
            }
        }
        return List.copyOf(flags);
    }

    record AnnouncementDefinition(
            String id,
            boolean enabled,
            String permission,
            String excludePermission,
            List<String> worldPatterns,
            ChatSpec chat,
            TitleSpec title,
            ActionBarSpec actionBar,
            BossBarSpec bossBar,
            SoundSpec sound,
            ToastSpec toast
    ) {
    }

    record ChatSpec(boolean enabled, List<String> lines) {
    }

    record TitleSpec(boolean enabled, String titleText, String subtitleText, int fadeIn, int stay, int fadeOut) {
    }

    record ActionBarSpec(boolean enabled, String text, int durationTicks) {
    }

    record BossBarSpec(
            boolean enabled,
            String text,
            float progress,
            String color,
            String overlay,
            int durationTicks,
            List<String> flags
    ) {
    }

    record SoundSpec(boolean enabled, String key, float volume, float pitch) {
    }

    record ToastSpec(boolean enabled, String title, String description, String icon, String frame) {
    }

    static final class RecipientState {
        private FoliaSupport.ScheduledTaskHandle actionBarTask;
        private FoliaSupport.ScheduledTaskHandle bossBarTask;
        private BossBar activeBossBar;
    }
}
