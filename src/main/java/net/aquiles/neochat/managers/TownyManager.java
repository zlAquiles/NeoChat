package net.aquiles.neochat.managers;

import net.aquiles.neochat.NeoChat;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;

public class TownyManager {

    private final NeoChat plugin;

    public TownyManager(NeoChat plugin) {
        this.plugin = plugin;
    }

    public boolean hasTown(Player player) {
        Resident resident = TownyAPI.getInstance().getResident(player);
        return resident != null && resident.hasTown();
    }

    public void sendTownMessage(Player sender, String message) {
        Resident resident = TownyAPI.getInstance().getResident(sender);
        if (resident == null || !resident.hasTown()) {
            sender.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(plugin.getMessages().getString("towny-need-town", "<red>¡No tienes ciudad!")));
            plugin.getTownyChatToggled().remove(sender.getUniqueId());
            return;
        }

        try {
            Town town = resident.getTown();
            String format = plugin.getConfig().getString("towny-chat.format", "<yellow>👑 <white>%townyadvanced_town% %player_name% <dark_gray>» <green><message>");
            String hover = plugin.getConfig().getString("towny-chat.hover-message", "");

            format = format.replace("%player_name%", sender.getName());

            if (plugin.isPapiEnabled()) {
                format = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(sender, format);
                hover = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(sender, hover);
            }

            format = plugin.translateLegacy(format);
            hover = plugin.translateLegacy(hover).replaceAll("\\n$", "").replace("\n", "<newline>");

            net.kyori.adventure.text.Component finalMessage = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                    format,
                    net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.parsed("hover_text", hover),
                    net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("message", message)
            );

            for (Player p : Bukkit.getOnlinePlayers()) {
                Resident targetRes = TownyAPI.getInstance().getResident(p);
                if (targetRes != null && targetRes.hasTown() && targetRes.getTown().equals(town)) {
                    p.sendMessage(finalMessage);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}