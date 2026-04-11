package net.aquiles.neochat;

import me.clip.placeholderapi.PlaceholderAPI;
import net.aquiles.neochat.chat.ChatListener;
import net.aquiles.neochat.commands.AdminChatCommand;
import net.aquiles.neochat.commands.IgnoreCommand;
import net.aquiles.neochat.commands.MessageCommand;
import net.aquiles.neochat.gui.GUIListener;
import net.aquiles.neochat.managers.PlayerDataManager;
import net.aquiles.neochat.utils.FoliaSupport;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class NeoChat extends JavaPlugin implements org.bukkit.event.Listener {

    private static final String UPDATE_URL = "https://modrinth.com/plugin/neochat/";
    private static final Pattern PAPI_PLACEHOLDER_PATTERN = Pattern.compile("%[^%\\s]+%");

    private PlayerDataManager playerDataManager;
    private YamlConfiguration messages;
    private File messagesFile;
    private File formatsFile;
    private YamlConfiguration formats;
    private volatile boolean chatMuted = false;
    private boolean papiEnabled = false;
    private volatile String latestVersion = null;
    private net.aquiles.neochat.managers.TownyManager townyManager;
    private final Set<UUID> townyChatToggled = ConcurrentHashMap.newKeySet();
    private boolean townyChatEnabled = false;
    private net.aquiles.neochat.utils.DiscordManager discordManager;
    private ChatListener chatListener;
    private FoliaSupport foliaSupport;

    @Override
    public void onEnable() {
        long startTime = System.currentTimeMillis();

        saveDefaultConfig();
        saveDefaultMessages();

        formatsFile = new File(getDataFolder(), "formats.yml");
        if (!formatsFile.exists()) {
            saveResource("formats.yml", false);
        }
        formats = YamlConfiguration.loadConfiguration(formatsFile);

        playerDataManager = new PlayerDataManager(this);
        discordManager = new net.aquiles.neochat.utils.DiscordManager(this);
        foliaSupport = new FoliaSupport(this);

        if (getConfig().getBoolean("update-checker.enable", true)) {
            String gistUrl = "https://gist.githubusercontent.com/zlAquiles/4f2a1a36bcb16aa7d15cfb7f785ea071/raw/version.txt";

            new net.aquiles.neochat.utils.UpdateChecker(this, gistUrl).getVersion(version -> {
                if (!this.getDescription().getVersion().equals(version)) {
                    this.latestVersion = version;
                    runSync(() -> sendConsoleUpdateAvailable(version));
                } else {
                    this.latestVersion = null;
                    runSync(() -> sendConsoleLegacyMessage("&8[&bNeoChat&8] &aYou are running the latest version!"));
                }
            });
        }

        int pluginId = 29796;
        try {
            new org.bstats.bukkit.Metrics(this, pluginId);
            getLogger().info("Metrics (bStats) enabled.");
        } catch (Exception e) {
            getLogger().warning("bStats could not be initialized.");
        }

        try {
            org.apache.logging.log4j.core.Logger coreLogger =
                    (org.apache.logging.log4j.core.Logger) org.apache.logging.log4j.LogManager.getRootLogger();
            coreLogger.addFilter(new net.aquiles.neochat.utils.LogFilter());
        } catch (Exception e) {
            getLogger().warning("The console filter could not be injected (Log4j).");
        }

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            papiEnabled = true;
            getLogger().info("PlaceholderAPI detected and activated.");
        }

        registerListeners();
        registerCommands();

        if (getConfig().getBoolean("towny-chat.enable", false) && Bukkit.getPluginManager().getPlugin("Towny") != null) {
            townyChatEnabled = true;
            townyManager = new net.aquiles.neochat.managers.TownyManager(this);
            getCommand("tc").setExecutor(new net.aquiles.neochat.commands.TownyChatCommand(this));
            getLogger().info("Towny Chat detected and activated.");
        }

        printLogo();
        long took = System.currentTimeMillis() - startTime;
        sendConsoleLegacyMessage("&aSuccessfully enabled. &7(took &f" + took + "ms&7)");
    }

    @Override
    public void onDisable() {
        getLogger().info("NeoChat has been successfully deactivated.");
    }

    private void registerListeners() {
        chatListener = new ChatListener(this);
        getServer().getPluginManager().registerEvents(chatListener, this);
        getServer().getPluginManager().registerEvents(new GUIListener(this), this);
        getServer().getPluginManager().registerEvents(this, this);
    }

    private void registerCommands() {
        net.aquiles.neochat.commands.ViewInvCommand viewInvCmd = new net.aquiles.neochat.commands.ViewInvCommand(this);
        getCommand("viewinv").setExecutor(viewInvCmd);
        getCommand("viewinv").setTabCompleter(viewInvCmd);

        AdminChatCommand adminCommand = new AdminChatCommand(this);
        getCommand("neochat").setExecutor(adminCommand);
        getCommand("neochat").setTabCompleter(adminCommand);
        getCommand("chatmute").setExecutor(adminCommand);
        getCommand("chatunmute").setExecutor(adminCommand);

        MessageCommand msgCommand = new MessageCommand(this);
        getCommand("msg").setExecutor(msgCommand);
        getCommand("msg").setTabCompleter(msgCommand);
        getCommand("reply").setExecutor(msgCommand);
        getCommand("togglemsg").setExecutor(msgCommand);
        getCommand("socialspy").setExecutor(msgCommand);

        IgnoreCommand ignoreCmd = new IgnoreCommand(this);
        getCommand("ignore").setExecutor(ignoreCmd);
        getCommand("ignore").setTabCompleter(ignoreCmd);
        getCommand("ignoreall").setExecutor(ignoreCmd);
    }

    private void saveDefaultMessages() {
        messagesFile = new File(getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(messagesFile);
    }

    public void reloadPlugin() {
        reloadConfig();
        messages = YamlConfiguration.loadConfiguration(messagesFile);
        formats = YamlConfiguration.loadConfiguration(formatsFile);
        if (chatListener != null) {
            chatListener.loadSettings();
        }
    }

    private void printLogo() {
        org.bukkit.command.ConsoleCommandSender console = Bukkit.getConsoleSender();
        MiniMessage mm = MiniMessage.miniMessage();

        String version = getDescription().getVersion();
        String serverName = getServer().getName();

        console.sendMessage(mm.deserialize(" "));
        console.sendMessage(mm.deserialize("<aqua>  _   _             ____ _           _   "));
        console.sendMessage(mm.deserialize("<aqua> | \\ | | ___  ___  / ___| |__   __ _| |_   <white>NeoChat v" + version));
        console.sendMessage(mm.deserialize("<aqua> |  \\| |/ _ \\/ _ \\| |   | '_ \\ / _` | __|  <gray>Running on <white>" + serverName));
        console.sendMessage(mm.deserialize("<aqua> | |\\  |  __/ (_) | |___| | | | (_| | |_  "));
        console.sendMessage(mm.deserialize("<aqua> |_| \\_|\\___|\\___/ \\____|_| |_|\\__,_|\\__| <gray>By Aquiles"));
        console.sendMessage(mm.deserialize(" "));
    }

    private void sendConsoleLegacyMessage(String message) {
        Bukkit.getConsoleSender().sendMessage(MiniMessage.miniMessage().deserialize(translateLegacy(message)));
    }

    private Component buildUpdateAvailableMessage(String newVersion, boolean includeClickableUrl) {
        String parsedLine = getMessages()
                .getString(
                        "update-available-join",
                        "<dark_gray>[<aqua>NeoChat<dark_gray>] <green>Update available! <gray>(<white>%current% <gray>-> <white>%new%<gray>)"
                )
                .replace("%current%", this.getDescription().getVersion())
                .replace("%new%", newVersion);

        Component baseMessage = MiniMessage.miniMessage().deserialize(parsedLine);
        if (!includeClickableUrl) {
            return baseMessage;
        }

        Component downloadButton = Component.text("[Download here]", NamedTextColor.AQUA)
                .decorate(TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.openUrl(UPDATE_URL))
                .hoverEvent(HoverEvent.showText(Component.text("Open NeoChat on Modrinth", NamedTextColor.GRAY)));

        return baseMessage
                .append(Component.space())
                .append(downloadButton);
    }

    private void sendConsoleUpdateAvailable(String newVersion) {
        sendConsoleLegacyMessage(
                "&8[&bNeoChat&8] &aNew update is available &7Version: &f"
                        + this.getDescription().getVersion()
                        + " &7| &7New version: &f"
                        + newVersion
        );
        sendConsoleLegacyMessage("&b" + UPDATE_URL);
    }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    public YamlConfiguration getMessages() {
        return messages;
    }

    public YamlConfiguration getFormats() {
        return formats;
    }

    public boolean isChatMuted() {
        return chatMuted;
    }

    public void setChatMuted(boolean chatMuted) {
        this.chatMuted = chatMuted;
    }

    public boolean isPapiEnabled() {
        return papiEnabled;
    }

    @org.bukkit.event.EventHandler
    public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (latestVersion != null && player.hasPermission("neochat.admin")) {
            sendMessage(player, buildUpdateAvailableMessage(latestVersion, true));
        }
    }

    public String translateLegacy(String text) {
        if (text == null) {
            return "";
        }

        text = text.replace("\u00A7", "&");

        return text.replace("&0", "<black>").replace("&1", "<dark_blue>").replace("&2", "<dark_green>")
                .replace("&3", "<dark_aqua>").replace("&4", "<dark_red>").replace("&5", "<dark_purple>")
                .replace("&6", "<gold>").replace("&7", "<gray>").replace("&8", "<dark_gray>")
                .replace("&9", "<blue>").replace("&a", "<green>").replace("&b", "<aqua>")
                .replace("&c", "<red>").replace("&d", "<light_purple>").replace("&e", "<yellow>")
                .replace("&f", "<white>").replace("&l", "<bold>").replace("&m", "<strikethrough>")
                .replace("&n", "<underlined>").replace("&o", "<italic>").replace("&r", "<reset>");
    }

    public <T> T callSync(Callable<T> task) {
        return foliaSupport.callGlobal(task);
    }

    public void runSync(Runnable task) {
        foliaSupport.runGlobal(task);
    }

    public void runAsync(Runnable task) {
        foliaSupport.runAsync(task);
    }

    public <T> T callForEntity(Entity entity, Callable<T> task) {
        return foliaSupport.callForEntity(entity, task);
    }

    public <T> T callForEntityOrDefault(Entity entity, Callable<T> task, T fallback) {
        return foliaSupport.callForEntityOrDefault(entity, task, fallback);
    }

    public void runForEntity(Entity entity, Runnable task) {
        foliaSupport.runForEntity(entity, task);
    }

    public String applyPapiPlaceholders(Player player, String text) {
        if (!papiEnabled || text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }

        return callForEntity(player, () -> {
            String resolved = text;

            for (int pass = 0; pass < 2; pass++) {
                String parsed = PlaceholderAPI.setPlaceholders(player, resolved);
                if (parsed.equals(resolved) || !containsPapiPlaceholders(parsed)) {
                    return parsed;
                }

                resolved = parsed;
            }

            return resolved;
        });
    }

    private boolean containsPapiPlaceholders(String text) {
        return text != null && PAPI_PLACEHOLDER_PATTERN.matcher(text).find();
    }

    public void sendMessage(CommandSender sender, Component message) {
        if (sender instanceof Player player) {
            sendMessage(player, message);
            return;
        }

        runSync(() -> sender.sendMessage(message));
    }

    public void sendMessage(Player player, Component message) {
        runForEntity(player, () -> player.sendMessage(message));
    }

    public void playSound(Player player, Sound sound, float volume, float pitch) {
        runForEntity(player, () -> player.playSound(player.getLocation(), sound, volume, pitch));
    }

    public void openInventory(Player player, Inventory inventory) {
        runForEntity(player, () -> player.openInventory(inventory));
    }

    public void broadcast(Component message) {
        runSync(() -> Bukkit.broadcast(message));
    }

    public void broadcast(Component message, String permission) {
        runSync(() -> Bukkit.broadcast(message, permission));
    }

    public boolean isFolia() {
        return foliaSupport != null && foliaSupport.isFolia();
    }

    public net.aquiles.neochat.managers.TownyManager getTownyManager() {
        return townyManager;
    }

    public Set<UUID> getTownyChatToggled() {
        return townyChatToggled;
    }

    public boolean isTownyChatEnabled() {
        return townyChatEnabled;
    }

    public net.aquiles.neochat.utils.DiscordManager getDiscordManager() {
        return discordManager;
    }
}
