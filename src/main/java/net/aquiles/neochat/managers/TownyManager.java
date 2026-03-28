package net.aquiles.neochat.managers;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import net.aquiles.neochat.NeoChat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class TownyManager {

    private final NeoChat plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public TownyManager(NeoChat plugin) {
        this.plugin = plugin;
    }

    public boolean hasTown(Player player) {
        Resident resident = TownyAPI.getInstance().getResident(player);
        return resident != null && resident.hasTown();
    }

    public void sendTownMessage(Player sender, String message) {
        plugin.runForEntity(sender, () -> sendTownMessageInternal(sender, message));
    }

    private void sendTownMessageInternal(Player sender, String message) {
        Resident resident = TownyAPI.getInstance().getResident(sender);
        if (resident == null || !resident.hasTown()) {
            plugin.sendMessage(sender, miniMessage.deserialize(plugin.getMessages().getString("towny-need-town", "<red>No tienes ciudad!")));
            plugin.getTownyChatToggled().remove(sender.getUniqueId());
            return;
        }

        try {
            Town town = resident.getTown();
            String format = plugin.getConfig().getString("towny-chat.format", "<yellow>[Town] <white>%townyadvanced_town% %player_name% <dark_gray>\u00BB <green><message>");
            String hover = plugin.getConfig().getString("towny-chat.hover-message", "");

            format = format.replace("%player_name%", sender.getName());

            if (plugin.isPapiEnabled()) {
                format = plugin.applyPapiPlaceholders(sender, format);
                hover = plugin.applyPapiPlaceholders(sender, hover);
            }

            format = plugin.translateLegacy(format);
            hover = plugin.translateLegacy(hover).replaceAll("\\n$", "").replace("\n", "<newline>");

            Component finalMessage = miniMessage.deserialize(
                    format,
                    Placeholder.parsed("hover_text", hover),
                    Placeholder.unparsed("message", message)
            );

            for (Player online : Bukkit.getOnlinePlayers()) {
                Resident targetResident = TownyAPI.getInstance().getResident(online);
                if (targetResident != null && targetResident.hasTown() && targetResident.getTown().equals(town)) {
                    plugin.sendMessage(online, finalMessage);
                }
            }
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }
}
