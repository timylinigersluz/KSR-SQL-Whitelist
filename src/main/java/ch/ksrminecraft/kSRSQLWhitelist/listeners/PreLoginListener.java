package ch.ksrminecraft.kSRSQLWhitelist.listeners;

import ch.ksrminecraft.kSRSQLWhitelist.KSRSQLWhitelist;
import ch.ksrminecraft.kSRSQLWhitelist.utils.MessageUtil;
import ch.ksrminecraft.kSRSQLWhitelist.utils.WhitelistService;
import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.util.logging.Level;

public class PreLoginListener implements Listener {
    private final KSRSQLWhitelist plugin;
    private final WhitelistService service;

    public PreLoginListener(KSRSQLWhitelist plugin, WhitelistService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @EventHandler
    public void onAsyncPreLogin(AsyncPlayerPreLoginEvent event) {
        if (!plugin.getConfig().getBoolean("enabled", true)) {
            return;
        }

        try {
            boolean ok = service.isWhitelisted(event.getUniqueId(), event.getName());
            if (!ok) {
                String raw = plugin.getConfig().getString(
                        "kick.not_whitelisted",
                        "&cYou're not on our whitelist."
                );
                Component msg = MessageUtil.parse(raw);
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, msg);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE,
                    "Whitelist check failed for " + event.getName(), e);

            String raw = plugin.getConfig().getString(
                    "kick.db_error",
                    "&cWhitelist check failed. Please try again later."
            );
            Component msg = MessageUtil.parse(raw);
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, msg);
        }
    }
}
