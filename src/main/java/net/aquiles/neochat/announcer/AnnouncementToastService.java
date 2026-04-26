package net.aquiles.neochat.announcer;

import net.aquiles.neochat.NeoChat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

final class AnnouncementToastService {

    private static final String ROOT_CRITERION = "root";
    private static final String TOAST_CRITERION = "toast";
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("%[^%\\s]+%");

    private final NeoChat plugin;
    private final MiniMessage miniMessage;
    private final GsonComponentSerializer gsonSerializer;
    private final Map<String, ToastAdvancement> toastAdvancements = new ConcurrentHashMap<>();

    AnnouncementToastService(NeoChat plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
        this.gsonSerializer = GsonComponentSerializer.gson();
    }

    void reload(Collection<AnnouncementManager.AnnouncementDefinition> definitions) {
        toastAdvancements.clear();
        if (definitions == null || definitions.isEmpty()) {
            return;
        }

        for (AnnouncementManager.AnnouncementDefinition definition : definitions) {
            AnnouncementManager.ToastSpec toastSpec = definition.toast();
            if (toastSpec == null || !toastSpec.enabled()) {
                continue;
            }

            ToastAdvancement advancement = registerToast(definition.id(), toastSpec);
            if (advancement != null) {
                toastAdvancements.put(definition.id().toLowerCase(Locale.ROOT), advancement);
            }
        }
    }

    void shutdown() {
        toastAdvancements.clear();
    }

    void showToast(Player player, AnnouncementManager.AnnouncementDefinition definition) {
        if (player == null || definition == null || !player.isOnline()) {
            return;
        }

        AnnouncementManager.ToastSpec toastSpec = definition.toast();
        if (toastSpec == null || !toastSpec.enabled()) {
            return;
        }

        ToastAdvancement advancement = toastAdvancements.get(definition.id().toLowerCase(Locale.ROOT));
        if (advancement == null || advancement.rootAdvancement() == null || advancement.toastAdvancement() == null) {
            return;
        }

        plugin.runForEntity(player, () -> {
            awardRootIfNeeded(player, advancement.rootAdvancement());

            AdvancementProgress toastProgress = player.getAdvancementProgress(advancement.toastAdvancement());
            if (toastProgress.getAwardedCriteria().contains(TOAST_CRITERION)) {
                toastProgress.revokeCriteria(TOAST_CRITERION);
            }
            toastProgress.awardCriteria(TOAST_CRITERION);
        });
    }

    private void awardRootIfNeeded(Player player, Advancement rootAdvancement) {
        AdvancementProgress rootProgress = player.getAdvancementProgress(rootAdvancement);
        if (!rootProgress.getAwardedCriteria().contains(ROOT_CRITERION)) {
            rootProgress.awardCriteria(ROOT_CRITERION);
        }
    }

    @SuppressWarnings("deprecation")
    private ToastAdvancement registerToast(String announcementId, AnnouncementManager.ToastSpec toastSpec) {
        try {
            String sanitizedId = sanitizeId(announcementId);
            String specHash = hashToastSpec(toastSpec);
            NamespacedKey rootKey = new NamespacedKey(plugin, "announcer/toast/" + sanitizedId + "/" + specHash + "/root");
            NamespacedKey toastKey = new NamespacedKey(plugin, "announcer/toast/" + sanitizedId + "/" + specHash + "/toast");

            Advancement rootAdvancement = Bukkit.getAdvancement(rootKey);
            if (rootAdvancement == null) {
                rootAdvancement = Bukkit.getUnsafe().loadAdvancement(rootKey, buildRootAdvancementJson());
            }

            Advancement toastAdvancement = Bukkit.getAdvancement(toastKey);
            if (toastAdvancement == null) {
                toastAdvancement = loadToastAdvancement(toastKey, rootKey, toastSpec);
            }

            if (rootAdvancement == null || toastAdvancement == null) {
                plugin.getLogger().warning("Could not register announcer toast advancement for '" + announcementId + "'.");
                return null;
            }

            if (containsPlaceholders(toastSpec.title()) || containsPlaceholders(toastSpec.description())) {
                plugin.getLogger().warning("Announcement toast '" + announcementId + "' contains placeholders. Vanilla advancement toasts are static, so those placeholders will not be resolved per-player.");
            }

            return new ToastAdvancement(rootAdvancement, toastAdvancement);
        } catch (Exception exception) {
            plugin.getLogger().warning("Failed to register announcer toast for '" + announcementId + "': " + exception.getMessage());
            return null;
        }
    }

    private String buildRootAdvancementJson() {
        return """
                {
                  "criteria": {
                    "root": {
                      "trigger": "minecraft:impossible"
                    }
                  },
                  "requirements": [["root"]]
                }
                """;
    }

    private String buildToastAdvancementJson(NamespacedKey rootKey, AnnouncementManager.ToastSpec toastSpec) {
        return buildToastAdvancementJson(rootKey, toastSpec, true);
    }

    private String buildToastAdvancementJson(NamespacedKey rootKey, AnnouncementManager.ToastSpec toastSpec, boolean modernIconFormat) {
        Component title = parseStaticComponent(toastSpec.title());
        Component description = parseStaticComponent(toastSpec.description());
        String iconItem = normalizeItemKey(toastSpec.icon());
        String frame = normalizeFrame(toastSpec.frame());
        String iconJson = modernIconFormat
                ? """
                  {
                    "id": "%s"
                  }
                  """.formatted(iconItem)
                : """
                  {
                    "item": "%s"
                  }
                  """.formatted(iconItem);

        return """
                {
                  "parent": "%s",
                  "display": {
                    "icon": %s,
                    "title": %s,
                    "description": %s,
                    "frame": "%s",
                    "show_toast": true,
                    "announce_to_chat": false,
                    "hidden": true
                  },
                  "criteria": {
                    "toast": {
                      "trigger": "minecraft:impossible"
                    }
                  },
                  "requirements": [["toast"]]
                }
                """.formatted(
                rootKey.asString(),
                iconJson,
                gsonSerializer.serialize(title),
                gsonSerializer.serialize(description),
                frame
        );
    }

    @SuppressWarnings("deprecation")
    private Advancement loadToastAdvancement(NamespacedKey toastKey, NamespacedKey rootKey, AnnouncementManager.ToastSpec toastSpec) {
        String modernJson = buildToastAdvancementJson(rootKey, toastSpec, true);
        try {
            return Bukkit.getUnsafe().loadAdvancement(toastKey, modernJson);
        } catch (IllegalArgumentException modernFailure) {
            String legacyJson = buildToastAdvancementJson(rootKey, toastSpec, false);
            try {
                return Bukkit.getUnsafe().loadAdvancement(toastKey, legacyJson);
            } catch (IllegalArgumentException legacyFailure) {
                modernFailure.addSuppressed(legacyFailure);
                throw modernFailure;
            }
        }
    }

    private Component parseStaticComponent(String raw) {
        String input = raw == null || raw.isBlank() ? "<white>Notification" : raw;
        return miniMessage.deserialize(plugin.translateLegacy(input));
    }

    private boolean containsPlaceholders(String text) {
        return text != null && PLACEHOLDER_PATTERN.matcher(text).find();
    }

    private String sanitizeId(String rawId) {
        String input = rawId == null ? "announcement" : rawId.toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder(input.length());
        for (int index = 0; index < input.length(); index++) {
            char current = input.charAt(index);
            if ((current >= 'a' && current <= 'z') || (current >= '0' && current <= '9') || current == '_' || current == '-' || current == '/' || current == '.') {
                builder.append(current);
            } else {
                builder.append('_');
            }
        }
        return builder.isEmpty() ? "announcement" : builder.toString();
    }

    private String hashToastSpec(AnnouncementManager.ToastSpec toastSpec) {
        String payload = String.join(
                "\u0000",
                toastSpec.title(),
                toastSpec.description(),
                toastSpec.icon(),
                toastSpec.frame()
        );

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(12);
            for (int index = 0; index < 6; index++) {
                builder.append(String.format("%02x", hash[index]));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            return Integer.toHexString(payload.hashCode());
        }
    }

    private String normalizeFrame(String rawFrame) {
        if (rawFrame == null || rawFrame.isBlank()) {
            return "task";
        }

        String normalized = rawFrame.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "goal", "challenge", "task" -> normalized;
            default -> "task";
        };
    }

    private String normalizeItemKey(String rawItem) {
        if (rawItem == null || rawItem.isBlank()) {
            return "minecraft:paper";
        }

        String normalized = rawItem.trim();
        if (normalized.contains(":")) {
            return normalized.toLowerCase(Locale.ROOT);
        }

        Material material = Material.matchMaterial(normalized);
        if (material == null) {
            return "minecraft:paper";
        }

        return material.getKey().asString();
    }

    private record ToastAdvancement(Advancement rootAdvancement, Advancement toastAdvancement) {
    }
}
