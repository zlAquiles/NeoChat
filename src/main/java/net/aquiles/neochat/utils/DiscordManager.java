package net.aquiles.neochat.utils;

import net.aquiles.neochat.NeoChat;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class DiscordManager {

    private static final String MODE_DISABLED = "disabled";
    private static final String MODE_WEBHOOK = "webhook";
    private static final String MODE_DISCORDSRV = "discordsrv";

    private final NeoChat plugin;
    private final HttpClient httpClient;

    public DiscordManager(NeoChat plugin) {
        this.plugin = plugin;
        this.httpClient = HttpClient.newHttpClient();
    }

    public void sendMessage(String playerName, String message) {
        if (!MODE_WEBHOOK.equals(getMode())) return;

        String url = plugin.getConfig().getString("discord-webhook.url", "");
        if (url == null || url.isEmpty() || url.equals("PON_TU_URL_AQUI")) return;

        String format = plugin.getConfig().getString("discord-webhook.format", "**%player%**: %message%");
        String content = format.replace("%player%", playerName).replace("%message%", message);

        JsonObject json = new JsonObject();
        json.addProperty("content", content);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                .build();

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .exceptionally(e -> {
                    plugin.getLogger().warning("No se pudo enviar el mensaje a Discord: " + e.getMessage());
                    return null;
                });
    }

    public void validateIntegration() {
        if (MODE_DISCORDSRV.equals(getMode()) && plugin.getServer().getPluginManager().getPlugin("DiscordSRV") == null) {
            plugin.getLogger().warning("Discord mode is set to 'discordsrv', but DiscordSRV is not installed. Discord bridge messages will be handled only after DiscordSRV is available.");
        }
    }

    private String getMode() {
        String configuredMode = plugin.getConfig().getString("discord.mode", "");
        if (configuredMode != null && !configuredMode.isBlank()) {
            String normalizedMode = configuredMode.trim().toLowerCase();
            return switch (normalizedMode) {
                case MODE_DISABLED, MODE_WEBHOOK, MODE_DISCORDSRV -> normalizedMode;
                default -> MODE_DISABLED;
            };
        }

        return plugin.getConfig().getBoolean("discord-webhook.enable", false) ? MODE_WEBHOOK : MODE_DISABLED;
    }
}
