package net.aquiles.neochat.utils;

import net.aquiles.neochat.NeoChat;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class DiscordManager {

    private final NeoChat plugin;
    private final HttpClient httpClient;

    public DiscordManager(NeoChat plugin) {
        this.plugin = plugin;
        this.httpClient = HttpClient.newHttpClient();
    }

    public void sendMessage(String playerName, String message) {
        if (!plugin.getConfig().getBoolean("discord-webhook.enable", false)) return;

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
}