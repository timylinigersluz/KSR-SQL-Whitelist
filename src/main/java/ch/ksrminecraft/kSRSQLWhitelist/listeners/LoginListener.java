package ch.ksrminecraft.kSRSQLWhitelist.listeners;


import ch.ksrminecraft.kSRSQLWhitelist.KSRSQLWhitelist;
import ch.ksrminecraft.kSRSQLWhitelist.utils.WhitelistService;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerLoginEvent.Result;


import java.util.logging.Level;


public class LoginListener implements Listener {
    private final KSRSQLWhitelist plugin;
    private final WhitelistService service;


    public LoginListener(KSRSQLWhitelist plugin, WhitelistService service) {
        this.plugin = plugin;
        this.service = service;
    }


    @EventHandler
    public void onPlayerLogin(PlayerLoginEvent event) {
        if (!plugin.getConfig().getBoolean("enabled", true)) return; // Whitelist global aus
        Player player = event.getPlayer();
        try {
            boolean ok = service.isWhitelisted(player.getUniqueId(), player.getName());
            if (!ok) {
                event.setKickMessage(ChatColor.RED + "You're not on our whitelist");
                event.setResult(Result.KICK_WHITELIST);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Whitelist check failed for " + player.getName(), e);
            event.setKickMessage(ChatColor.RED + "Whitelist check failed. Please try again later.");
            event.setResult(Result.KICK_OTHER);
        }
    }
}