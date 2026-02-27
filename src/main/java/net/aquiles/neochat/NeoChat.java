package net.aquiles.neochat;

import net.aquiles.neochat.chat.ChatListener;
import net.aquiles.neochat.commands.AdminChatCommand;
import net.aquiles.neochat.commands.IgnoreCommand;
import net.aquiles.neochat.commands.MessageCommand;
import net.aquiles.neochat.gui.GUIListener;
import net.aquiles.neochat.managers.PlayerDataManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class NeoChat extends JavaPlugin implements org.bukkit.event.Listener {

    private PlayerDataManager playerDataManager;
    private YamlConfiguration messages;
    private File messagesFile;
    private java.io.File formatsFile;
    private org.bukkit.configuration.file.YamlConfiguration formats;
    private boolean chatMuted = false;
    private boolean papiEnabled = false;
    private String latestVersion = null;
    private net.aquiles.neochat.managers.TownyManager townyManager;
    private final java.util.Set<java.util.UUID> townyChatToggled = new java.util.HashSet<>();
    private boolean townyChatEnabled = false;
    private net.aquiles.neochat.utils.DiscordManager discordManager;

    @Override
    public void onEnable() {
        long startTime = System.currentTimeMillis();

        saveDefaultConfig();
        saveDefaultMessages();

        formatsFile = new java.io.File(getDataFolder(), "formats.yml");
        if (!formatsFile.exists()) {
            saveResource("formats.yml", false);
        }
        formats = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(formatsFile);

        playerDataManager = new PlayerDataManager(this);

        discordManager = new net.aquiles.neochat.utils.DiscordManager(this);

        if (getConfig().getBoolean("update-checker.enable", true)) {
            String gistUrl = "https://gist.githubusercontent.com/zlAquiles/4f2a1a36bcb16aa7d15cfb7f785ea071/raw/version.txt";

            new net.aquiles.neochat.utils.UpdateChecker(this, gistUrl).getVersion(version -> {
                if (!this.getDescription().getVersion().equals(version)) {
                    this.latestVersion = version;

                    String updateMsg = getMessages().getString("update-available", "<green>¡Nueva actualizacion disponible! <gray>(Actual: %current% | Nueva: %new%)");
                    String parsedLine = updateMsg.replace("%current%", this.getDescription().getVersion()).replace("%new%", version);

                    org.bukkit.command.ConsoleCommandSender console = org.bukkit.Bukkit.getConsoleSender();
                    net.kyori.adventure.text.minimessage.MiniMessage mm = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage();

                    console.sendMessage(mm.deserialize(parsedLine));
                }
            });
        }

        int pluginId = 29796;
        try {
            new org.bstats.bukkit.Metrics(this, pluginId);
            getLogger().info("Metrics (bStats) enabled.");
        } catch (Exception e) {
            getLogger().warning("No se pudo inicializar bStats.");
        }

        try {
            org.apache.logging.log4j.core.Logger coreLogger = (org.apache.logging.log4j.core.Logger) org.apache.logging.log4j.LogManager.getRootLogger();
            coreLogger.addFilter(new net.aquiles.neochat.utils.LogFilter());
        } catch (Exception e) {
            getLogger().warning("No se pudo inyectar el filtro de consola (Log4j).");
        }

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            papiEnabled = true;
            getLogger().info("PlaceholderAPI detectado y activado.");
        }

        registerListeners();
        registerCommands();

        if (getConfig().getBoolean("towny-chat.enable", false) && Bukkit.getPluginManager().getPlugin("Towny") != null) {
            townyChatEnabled = true;
            townyManager = new net.aquiles.neochat.managers.TownyManager(this);
            getCommand("tc").setExecutor(new net.aquiles.neochat.commands.TownyChatCommand(this));
            getLogger().info("Towny Chat detectado y activado correctamente.");
        }
        printLogo();
        long took = System.currentTimeMillis() - startTime;
        getLogger().info("Successfully enabled. (took " + took + "ms)");
    }

    @Override
    public void onDisable() {
        getLogger().info("NeoChat ha sido desactivado correctamente.");
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getPluginManager().registerEvents(new GUIListener(this), this);
        getServer().getPluginManager().registerEvents(this, this);
    }

    private void registerCommands() {
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
    }

    private void printLogo() {
        org.bukkit.command.ConsoleCommandSender console = Bukkit.getConsoleSender();
        net.kyori.adventure.text.minimessage.MiniMessage mm = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage();

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
        org.bukkit.entity.Player player = event.getPlayer();
        if (latestVersion != null && player.hasPermission("neochat.admin")) {
            String updateMsg = getMessages().getString("update-available", "<green>¡Nueva actualizacion disponible para NeoChat! <gray>Version actual: <white>%current% <gray>| Nueva version: <white>%new%");
            String parsedLine = updateMsg.replace("%current%", this.getDescription().getVersion()).replace("%new%", latestVersion);

            player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(parsedLine));
        }
    }

    public String translateLegacy(String text) {
        if (text == null) return "";

        text = text.replace("§", "&");

        return text.replace("&0", "<black>").replace("&1", "<dark_blue>").replace("&2", "<dark_green>")
                .replace("&3", "<dark_aqua>").replace("&4", "<dark_red>").replace("&5", "<dark_purple>")
                .replace("&6", "<gold>").replace("&7", "<gray>").replace("&8", "<dark_gray>")
                .replace("&9", "<blue>").replace("&a", "<green>").replace("&b", "<aqua>")
                .replace("&c", "<red>").replace("&d", "<light_purple>").replace("&e", "<yellow>")
                .replace("&f", "<white>").replace("&l", "<bold>").replace("&m", "<strikethrough>")
                .replace("&n", "<underlined>").replace("&o", "<italic>").replace("&r", "<reset>");
    }
    public net.aquiles.neochat.managers.TownyManager getTownyManager() { return townyManager; }
    public java.util.Set<java.util.UUID> getTownyChatToggled() { return townyChatToggled; }
    public boolean isTownyChatEnabled() { return townyChatEnabled; }
    public net.aquiles.neochat.utils.DiscordManager getDiscordManager() { return discordManager; }
}