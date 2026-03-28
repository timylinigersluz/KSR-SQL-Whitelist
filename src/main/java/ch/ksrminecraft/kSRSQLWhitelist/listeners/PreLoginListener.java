package ch.ksrminecraft.kSRSQLWhitelist.listeners;

import ch.ksrminecraft.kSRSQLWhitelist.KSRSQLWhitelist;
import ch.ksrminecraft.kSRSQLWhitelist.utils.MessageUtil;
import ch.ksrminecraft.kSRSQLWhitelist.utils.WhitelistService;
import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

/**
 * ----------------------------------------------------------------------------
 *  📋 PreLoginListener
 *  --------------------
 *  Prüft beim Login eines Spielers:
 *
 *   1. Ob eine aktuelle clusterweite Protected-World-Sperre aktiv ist
 *   2. Ob der Spieler auf der Whitelist steht
 *   3. Ob bei MySQL-Ausfall die lokale Fallback-Whitelist greift
 *
 *  Autor: Timy Liniger (KSR Minecraft)
 *  Projekt: KSR-SQL-Whitelist
 * ----------------------------------------------------------------------------
 */
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

        String playerName = event.getName();

        // --------------------------------------------------------------
        // 1) Clusterweite kurzfristige Protected-World-Sperre prüfen
        // --------------------------------------------------------------
        try {
            if (plugin.getProtectedAccessBlockService().isBlocked(event.getUniqueId())) {
                event.disallow(
                        AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                        getProtectedWorldMessage()
                );
                return;
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to check protected-world block for " + playerName + ": " + ex.getMessage());
        }

        // --------------------------------------------------------------
        // 2) Normale SQL-Whitelist prüfen
        // --------------------------------------------------------------
        try {
            boolean whitelisted = service.isWhitelisted(event.getUniqueId(), playerName);

            // Wenn MySQL wieder funktioniert, Recovery loggen
            plugin.handleMysqlRecovery();

            if (!whitelisted) {
                event.disallow(
                        AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                        getNotWhitelistedMessage()
                );
            }

        } catch (Exception mysqlException) {
            plugin.handleMysqlFailure(
                    "MySQL whitelist check failed for " + playerName + ". Trying local fallback...",
                    mysqlException
            );

            try {
                boolean fallbackWhitelisted = service.isWhitelistedLocal(event.getUniqueId(), playerName);

                if (!fallbackWhitelisted) {
                    event.disallow(
                            AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                            getNotWhitelistedMessage()
                    );
                    return;
                }

            } catch (Exception fallbackException) {
                plugin.getLogger().log(
                        java.util.logging.Level.SEVERE,
                        "Local fallback whitelist check also failed for " + playerName,
                        fallbackException
                );

                event.disallow(
                        AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                        getDatabaseErrorMessage()
                );
            }
        }
    }

    private Component getNotWhitelistedMessage() {
        String raw = plugin.getConfig().getString(
                "kick.not_whitelisted",
                "&cYou're not on our whitelist."
        );
        return MessageUtil.parse(raw);
    }

    private Component getDatabaseErrorMessage() {
        String raw = plugin.getConfig().getString(
                "kick.db_error",
                "&cEs gab einen internen Fehler mit der Datenbank."
        );
        return MessageUtil.parse(raw);
    }

    private Component getProtectedWorldMessage() {
        String raw = plugin.getConfig().getString(
                "messages.protected_world",
                "&cNope! Du darfst diese Welt nicht betreten."
        );
        return MessageUtil.parse(raw);
    }
}