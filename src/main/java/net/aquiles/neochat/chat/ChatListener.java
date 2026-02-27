package net.aquiles.neochat.chat;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.aquiles.neochat.NeoChat;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatListener implements Listener {

    private final NeoChat plugin;
    private final MiniMessage strictMiniMessage;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, String> lastMessages = new HashMap<>();
    private final Map<UUID, Long> lastMessageTimes = new HashMap<>();

    public ChatListener(NeoChat plugin) {
        this.plugin = plugin;
        this.strictMiniMessage = MiniMessage.miniMessage();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerChat(AsyncChatEvent event) {

        Player player = event.getPlayer();

        if (plugin.getConfig().getBoolean("chat-cooldown.enable", true)) {
            String bypassPerm = plugin.getConfig().getString("chat-cooldown.permission-bypass", "neochat.bypass.cooldown");
            if (!player.hasPermission(bypassPerm)) {
                int cooldownSeconds = plugin.getConfig().getInt("chat-cooldown.time", 3);
                long lastTime = cooldowns.getOrDefault(player.getUniqueId(), 0L);
                long currentTime = System.currentTimeMillis();
                long timeLeft = (lastTime + (cooldownSeconds * 1000L)) - currentTime;

                if (timeLeft > 0) {
                    event.setCancelled(true);
                    String msg = plugin.getMessages().getString("chat-cooldown-message", "<red>¡Espera <time> segundos!")
                            .replace("<time>", String.valueOf((float) timeLeft / 1000).replaceAll("\\.?0*$", ""));
                    player.sendMessage(strictMiniMessage.deserialize(msg));
                    return;
                }
                cooldowns.put(player.getUniqueId(), currentTime);
            }
        }

        String plainMessage = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(event.message());

        if (plugin.getConfig().getBoolean("anti-flood.enable", true) && !player.hasPermission(plugin.getConfig().getString("anti-flood.permission-bypass", "neochat.bypass.flood"))) {
            if (plainMessage.length() > plugin.getConfig().getInt("anti-flood.max-message-length", 80)) {
                event.setCancelled(true);
                player.sendMessage(strictMiniMessage.deserialize(plugin.getMessages().getString("chat-flood-length", "<red>Mensaje muy largo.")));
                return;
            }
            if (plugin.getConfig().getBoolean("anti-flood.block-repeated-chars", true) && plainMessage.matches(".*(.)\\1{4,}.*")) {
                event.setCancelled(true);
                player.sendMessage(strictMiniMessage.deserialize(plugin.getMessages().getString("chat-flood-chars", "<red>No repitas caracteres.")));
                if (plugin.getConfig().getBoolean("anti-flood.alert-staff", true)) {
                    String alert = plugin.getMessages().getString("staff-alert-flood", "<red>%player% hizo flood.").replace("%player%", player.getName());
                    Bukkit.broadcast(strictMiniMessage.deserialize(alert), "neochat.admin");
                }
                return;
            }
        }

        if (plugin.getConfig().getBoolean("anti-caps.enable", true) && !player.hasPermission(plugin.getConfig().getString("anti-caps.permission-bypass", "neochat.bypass.caps"))) {
            int capsCount = 0;
            for (char c : plainMessage.toCharArray()) {
                if (Character.isUpperCase(c)) capsCount++;
            }
            if (capsCount > plugin.getConfig().getInt("anti-caps.max-uppercase", 5)) {
                event.setCancelled(true);
                player.sendMessage(strictMiniMessage.deserialize(plugin.getMessages().getString("chat-caps", "<red>Demasiadas mayúsculas.")));
                return;
            }
        }

        if (plugin.getConfig().getBoolean("similarity-check.enable", true) && !player.hasPermission(plugin.getConfig().getString("similarity-check.permission-bypass", "neochat.bypass.similarity"))) {
            long cacheTime = plugin.getConfig().getInt("similarity-check.cache-time-seconds", 15) * 1000L;
            if (lastMessages.containsKey(player.getUniqueId()) && (System.currentTimeMillis() - lastMessageTimes.get(player.getUniqueId()) < cacheTime)) {
                String lastMsg = lastMessages.get(player.getUniqueId());
                double similarity = calculateSimilarity(plainMessage.toLowerCase(), lastMsg.toLowerCase());
                if ((similarity * 100) >= plugin.getConfig().getInt("similarity-check.max-similarity-percent", 80)) {
                    event.setCancelled(true);
                    player.sendMessage(strictMiniMessage.deserialize(plugin.getMessages().getString("chat-similar", "<red>Mensaje muy similar al anterior.")));
                    return;
                }
            }
            lastMessages.put(player.getUniqueId(), plainMessage);
            lastMessageTimes.put(player.getUniqueId(), System.currentTimeMillis());
        }

        if (plugin.getConfig().getBoolean("anti-swear.enable", true)) {
            String bypassPerm = plugin.getConfig().getString("anti-swear.permission-bypass", "neochat.bypass.swear");
            if (!player.hasPermission(bypassPerm)) {
                java.util.List<String> blockedWords = plugin.getConfig().getStringList("anti-swear.blocked-words");
                String replacement = plugin.getConfig().getString("anti-swear.replace-with", "***");

                net.kyori.adventure.text.Component filteredMessage = event.message();

                for (String word : blockedWords) {
                    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(?i)\\b" + java.util.regex.Pattern.quote(word) + "\\b");

                    filteredMessage = filteredMessage.replaceText(net.kyori.adventure.text.TextReplacementConfig.builder()
                            .match(pattern)
                            .replacement(replacement)
                            .build());
                }
                event.message(filteredMessage);
            }
        }

        if (plugin.isChatMuted() && !player.hasPermission("neochat.bypass.mute")) {
            event.setCancelled(true);
            String mutedMsg = plugin.getMessages().getString("chat-is-muted", "<red>El chat está silenciado.");
            player.sendMessage(strictMiniMessage.deserialize(mutedMsg));
            return;
        }

        String rawMessage = PlainTextComponentSerializer.plainText().serialize(event.message());
        if (plugin.isTownyChatEnabled() && plugin.getTownyChatToggled().contains(player.getUniqueId())) {
            event.setCancelled(true);
            String finalMsg = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(event.message());
            plugin.getTownyManager().sendTownMessage(player, finalMsg);
            return;
        }

        String bypassPermAlhpa = plugin.getConfig().getString("alphanumeric-permission", "neochat.bypass.alphanumeric");
        if (!player.hasPermission(bypassPermAlhpa)) {
            String alphaRegex = plugin.getConfig().getString("alphanumeric-regex", "^[a-zA-Z0-9_.?!^¨%ù*&é\"#'{(\\[\\-|èêë`\\\\çà)\\]=}ûî+<>:²€$/\\\\\\,\\-â@;ô ]+$");
            if (!rawMessage.matches(alphaRegex)) {
                event.setCancelled(true);
                player.sendMessage(strictMiniMessage.deserialize(plugin.getMessages().getString("chat-invalid-characters", "<red>Tu mensaje contiene caracteres no permitidos.")));
                return;
            }
        }

        Set<Player> mentionedPlayers = new HashSet<>();
        String pingPrefix = plugin.getConfig().getString("ping.prefix", "@");

        if (plugin.getConfig().getBoolean("ping.enable", true)) {
            Pattern pattern = Pattern.compile(Pattern.quote(pingPrefix) + "([a-zA-Z0-9_]{3,16})");
            Matcher matcher = pattern.matcher(rawMessage);

            while (matcher.find()) {
                String name = matcher.group(1);
                Player target = Bukkit.getPlayerExact(name);

                if (target != null && target.isOnline() && player.canSee(target)) {
                    mentionedPlayers.add(target);
                }
            }

            if (!mentionedPlayers.isEmpty() && plugin.getConfig().getBoolean("ping.sound.enable", true)) {
                String soundName = plugin.getConfig().getString("ping.sound.type", "ENTITY_EXPERIENCE_ORB_PICKUP");
                float volume = (float) plugin.getConfig().getDouble("ping.sound.volume", 1.0);
                float pitch = (float) plugin.getConfig().getDouble("ping.sound.pitch", 1.0);

                try {
                    Sound sound = Sound.valueOf(soundName.toUpperCase());
                    for (Player p : mentionedPlayers) {
                        p.playSound(p.getLocation(), sound, volume, pitch);
                    }
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Sonido invalido en config.yml: " + soundName + " - usando default.");
                    for (Player p : mentionedPlayers) {
                        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                    }
                }
            }
        }

        TagResolver.Builder resolverBuilder = TagResolver.builder();
        if (player.hasPermission("neochat.chat.color")) resolverBuilder.resolver(StandardTags.color());
        if (player.hasPermission("neochat.chat.decoration")) resolverBuilder.resolver(StandardTags.decorations());
        if (player.hasPermission("neochat.chat.gradient")) resolverBuilder.resolver(StandardTags.gradient());
        if (player.hasPermission("neochat.chat.rainbow")) resolverBuilder.resolver(StandardTags.rainbow());
        if (player.hasPermission("neochat.chat.reset")) resolverBuilder.resolver(StandardTags.reset());
        if (player.hasPermission("neochat.chat.click")) resolverBuilder.resolver(StandardTags.clickEvent());
        if (player.hasPermission("neochat.chat.hover")) resolverBuilder.resolver(StandardTags.hoverEvent());
        if (player.hasPermission("neochat.chat.font")) resolverBuilder.resolver(StandardTags.font());

        MiniMessage playerMessageParser = MiniMessage.builder().tags(resolverBuilder.build()).build();
        Component playerMessageComponent = playerMessageParser.deserialize(rawMessage);

        playerMessageComponent = applyPlaceholders(player, playerMessageComponent);

        int highestPriority = Integer.MAX_VALUE;
        String groupFormat = "<gray>%player_name% <dark_gray>» <reset><message>";
        String hoverText = "";

        if (plugin.getFormats() != null && plugin.getFormats().contains("formats")) {
            for (String key : plugin.getFormats().getConfigurationSection("formats").getKeys(false)) {
                String perm = plugin.getFormats().getString("formats." + key + ".permission", "neochat.format." + key);
                int priority = plugin.getFormats().getInt("formats." + key + ".priority", 999);
                java.util.List<String> groups = plugin.getFormats().getStringList("formats." + key + ".groups");

                boolean hasAccess = player.hasPermission(perm);

                if (!hasAccess && groups != null && !groups.isEmpty()) {
                    for (String group : groups) {
                        if (player.hasPermission("group." + group.toLowerCase())) {
                            hasAccess = true;
                            break;
                        }
                    }
                }

                if (hasAccess && priority < highestPriority) {
                    highestPriority = priority;
                    groupFormat = plugin.getFormats().getString("formats." + key + ".chat-format", groupFormat);
                    hoverText = plugin.getFormats().getString("formats." + key + ".hover-message", "");
                }
            }
        }

        groupFormat = groupFormat.replace("%player_name%", player.getName());

        if (plugin.isPapiEnabled()) {
            groupFormat = PlaceholderAPI.setPlaceholders(player, groupFormat);
            if (!hoverText.isEmpty()) {
                hoverText = PlaceholderAPI.setPlaceholders(player, hoverText);
            }
        }

        groupFormat = plugin.translateLegacy(groupFormat);
        if (!hoverText.isEmpty()) hoverText = plugin.translateLegacy(hoverText);

        String finalHoverText = hoverText.replace("\n", "<newline>");

        final String finalGroupFormat = groupFormat;
        final Component finalPlayerMessage = playerMessageComponent;

        if (plugin.getDiscordManager() != null) {
            String textForDiscord = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(finalPlayerMessage);
            plugin.getDiscordManager().sendMessage(player.getName(), textForDiscord);
        }

        event.renderer(new ChatRenderer() {
            @Override
            public @NotNull Component render(@NotNull Player source, @NotNull Component sourceDisplayName, @NotNull Component message, @NotNull Audience viewer) {

                Component viewerMessage = finalPlayerMessage;

                if (!mentionedPlayers.isEmpty()) {
                    for (Player mentioned : mentionedPlayers) {
                        String rawMention = pingPrefix + mentioned.getName();
                        boolean isReceiver = viewer instanceof Player && ((Player) viewer).getUniqueId().equals(mentioned.getUniqueId());

                        String formatConfig = isReceiver
                                ? plugin.getConfig().getString("ping.format.receiver", "<red>%name%</red>")
                                : plugin.getConfig().getString("ping.format.others", "<white>%name%</white>");

                        Component replacement = strictMiniMessage.deserialize(formatConfig.replace("%name%", rawMention));

                        viewerMessage = viewerMessage.replaceText(TextReplacementConfig.builder()
                                .matchLiteral(rawMention)
                                .replacement(replacement)
                                .build());
                    }
                }

                return strictMiniMessage.deserialize(
                        finalGroupFormat,
                        Placeholder.parsed("hover_text", finalHoverText),
                        Placeholder.component("message", viewerMessage)
                );
            }
        });
    }

    private double calculateSimilarity(String s1, String s2) {
        String longer = s1.length() > s2.length() ? s1 : s2;
        String shorter = s1.length() > s2.length() ? s2 : s1;
        if (longer.length() == 0) return 1.0;

        int[] costs = new int[shorter.length() + 1];
        for (int i = 0; i <= shorter.length(); i++) costs[i] = i;
        for (int i = 1; i <= longer.length(); i++) {
            int nw = costs[0];
            costs[0] = i;
            for (int j = 1; j <= shorter.length(); j++) {
                int cj = Math.min(1 + Math.min(costs[j], costs[j - 1]), s1.charAt(i - 1) == s2.charAt(j - 1) ? nw : nw + 1);
                nw = costs[j];
                costs[j] = cj;
            }
        }
        return (longer.length() - costs[shorter.length()]) / (double) longer.length();
    }

    private Component applyPlaceholders(Player player, Component message) {
        Component result = message;
        List<Map<?, ?>> placeholders = plugin.getConfig().getMapList("chat-placeholders");

        for (Map<?, ?> phMap : placeholders) {
            String name = (String) phMap.get("name");

            Object enableObj = phMap.get("enable");
            boolean enabled = enableObj instanceof Boolean ? (Boolean) enableObj : true;
            if (!enabled) continue;

            Object regexObj = phMap.get("regex");
            Object resultObj = phMap.get("result");
            Object permObj = phMap.get("permission");

            if (!(regexObj instanceof String) || !(resultObj instanceof String) || !(permObj instanceof String)) continue;

            String regex = (String) regexObj;
            String resultFormat = (String) resultObj;
            String permission = (String) permObj;

            if (!player.hasPermission(permission)) continue;

            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(regex);
            if (!pattern.matcher(PlainTextComponentSerializer.plainText().serialize(result)).find()) continue;

            if ("item".equalsIgnoreCase(name)) {
                ItemStack item = player.getInventory().getItemInMainHand();
                if (item != null && !item.getType().isAir()) {
                    int amount = item.getAmount();
                    Component itemName = item.getItemMeta() != null && item.getItemMeta().hasDisplayName() ?
                            item.getItemMeta().displayName() : Component.translatable(item.getType().translationKey());
                    Component itemReplacement = strictMiniMessage.deserialize(
                            resultFormat.replace("<amount>", String.valueOf(amount)),
                            Placeholder.component("item", itemName)
                    ).hoverEvent(item.asHoverEvent());
                    result = result.replaceText(TextReplacementConfig.builder().match(regex).replacement(itemReplacement).build());
                }
            } else if ("inv".equalsIgnoreCase(name)) {
                UUID snapshotId = UUID.randomUUID();
                plugin.getPlayerDataManager().addSnapshot(snapshotId, new net.aquiles.neochat.utils.InventorySnapshot(
                        player.getName(), player.getInventory().getStorageContents(), player.getInventory().getArmorContents(), player.getInventory().getItemInOffHand()));
                String finalResultText = resultFormat.replace("%id%", snapshotId.toString()).replace("%player%", player.getName());
                result = result.replaceText(TextReplacementConfig.builder().match(regex).replacement(strictMiniMessage.deserialize(finalResultText)).build());

            } else if ("ender".equalsIgnoreCase(name)) {
                UUID snapshotId = UUID.randomUUID();
                plugin.getPlayerDataManager().addSnapshot(snapshotId, new net.aquiles.neochat.utils.InventorySnapshot(
                        player.getName(), net.aquiles.neochat.utils.InventorySnapshot.SnapshotType.ENDERCHEST, player.getEnderChest().getContents()));
                String finalResultText = resultFormat.replace("%id%", snapshotId.toString()).replace("%player%", player.getName());
                result = result.replaceText(TextReplacementConfig.builder().match(regex).replacement(strictMiniMessage.deserialize(finalResultText)).build());

            } else if ("shulker".equalsIgnoreCase(name)) {
                ItemStack handItem = player.getInventory().getItemInMainHand();
                if (handItem != null && handItem.getType().name().endsWith("SHULKER_BOX")) {
                    if (handItem.getItemMeta() instanceof org.bukkit.inventory.meta.BlockStateMeta meta && meta.getBlockState() instanceof org.bukkit.block.ShulkerBox shulkerBox) {
                        UUID snapshotId = UUID.randomUUID();
                        plugin.getPlayerDataManager().addSnapshot(snapshotId, new net.aquiles.neochat.utils.InventorySnapshot(
                                player.getName(), net.aquiles.neochat.utils.InventorySnapshot.SnapshotType.SHULKER, shulkerBox.getInventory().getContents()));
                        String finalResultText = resultFormat.replace("%id%", snapshotId.toString()).replace("%player%", player.getName());
                        result = result.replaceText(TextReplacementConfig.builder().match(regex).replacement(strictMiniMessage.deserialize(finalResultText)).build());
                    }
                }
            } else {
                String finalResultText = resultFormat
                        .replace("%player_ping%", String.valueOf(player.getPing()))
                        .replace("%player_world%", player.getWorld().getName())
                        .replace("%player_x%", String.valueOf(player.getLocation().getBlockX()))
                        .replace("%player_y%", String.valueOf(player.getLocation().getBlockY()))
                        .replace("%player_z%", String.valueOf(player.getLocation().getBlockZ()))
                        .replace("%player%", player.getName());

                if (plugin.isPapiEnabled()) {
                    finalResultText = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, finalResultText);
                }

                result = result.replaceText(TextReplacementConfig.builder()
                        .match(regex)
                        .replacement(strictMiniMessage.deserialize(finalResultText))
                        .build());
            }
        }

        if (plugin.getConfig().getBoolean("links.enable", true)) {
            String linkPerm = plugin.getConfig().getString("links.permission", "neochat.chat.link");
            if (player.hasPermission(linkPerm)) {
                String linkRegex = plugin.getConfig().getString("links.regex", "[-a-zA-Z0-9@:%._+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b([-a-zA-Z0-9()@:%_+.~#?&/=]*)");
                String linkTransform = plugin.getConfig().getString("links.transform", "<hover:show_text:'<white>Click para abrir enlace'><click:open_url:'%url%'><aqua>%url%</aqua></click></hover>");

                java.util.regex.Pattern linkPattern = java.util.regex.Pattern.compile(linkRegex);

                result = result.replaceText(TextReplacementConfig.builder()
                        .match(linkPattern)
                        .replacement((java.util.regex.MatchResult match, net.kyori.adventure.text.TextComponent.Builder builder) -> {
                            String url = match.group();
                            String finalUrl = (!url.startsWith("http://") && !url.startsWith("https://")) ? "https://" + url : url;
                            return strictMiniMessage.deserialize(linkTransform.replace("%url%", finalUrl));
                        })
                        .build());
            }
        }

        return result;
    }
}