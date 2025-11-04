package ch.ksrminecraft.kSRSQLWhitelist.listeners;

import ch.ksrminecraft.kSRSQLWhitelist.KSRSQLWhitelist;
import ch.ksrminecraft.kSRSQLWhitelist.utils.MessageUtil;
import ch.ksrminecraft.kSRSQLWhitelist.utils.WhitelistService;
import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.util.logging.Level;

/**
 * ----------------------------------------------------------------------------
 *  📋 PreLoginListener
 *  --------------------
 *  Listener, der bei jedem Verbindungsversuch eines Spielers ausgelöst wird,
 *  noch bevor der eigentliche Login-Vorgang abgeschlossen ist.
 *
 *  Zweck:
 *  - Überprüft, ob die Whitelist-Funktion im Plugin aktiviert ist.
 *  - Fragt über den {@link WhitelistService} ab, ob der Spieler in der SQL-Datenbank eingetragen ist.
 *  - Kickt den Spieler mit einer konfigurierbaren Nachricht, falls er nicht whitelisted ist
 *    oder ein Datenbankfehler auftritt.
 *
 *  Vorteil dieser frühen Prüfung (AsyncPlayerPreLoginEvent):
 *  - Der Check läuft asynchron und blockiert daher nicht den Hauptthread.
 *  - Der Spieler wird noch vor dem vollständigen Loginprozess abgelehnt (effizient).
 *
 *  Autor: Timy Liniger (KSR Minecraft)
 *  Projekt: KSR-SQL-Whitelist
 * ----------------------------------------------------------------------------
 */
public class PreLoginListener implements Listener {

    /** Referenz auf das Haupt-Plugin (für Config und Logging). */
    private final KSRSQLWhitelist plugin;

    /** Service-Klasse für Whitelist-Abfragen (Datenbank-Zugriffe). */
    private final WhitelistService service;

    /**
     * Konstruktor – registriert Listener mit Plugin-Instanz und WhitelistService.
     *
     * @param plugin  Hauptinstanz des KSR-SQL-Whitelist-Plugins
     * @param service Logikklasse für Whitelist-Abfragen
     */
    public PreLoginListener(KSRSQLWhitelist plugin, WhitelistService service) {
        this.plugin = plugin;
        this.service = service;
    }

    /**
     * Event-Handler, der ausgelöst wird, wenn sich ein Spieler verbindet,
     * aber bevor der Login-Prozess vollständig abgeschlossen ist.
     *
     * Läuft asynchron (daher darf hier direkt mit der Datenbank gearbeitet werden).
     *
     * @param event Das PreLogin-Event mit Spielername und UUID
     */
    @EventHandler
    public void onAsyncPreLogin(AsyncPlayerPreLoginEvent event) {

        // 1️⃣ Prüfen, ob die Whitelist-Funktion im Plugin überhaupt aktiviert ist.
        // Wenn "enabled: false" in der config.yml steht → sofort durchlassen.
        if (!plugin.getConfig().getBoolean("enabled", true)) {
            return;
        }

        try {
            // 2️⃣ Whitelist prüfen: Ist dieser Spieler (UUID/Name) eingetragen?
            boolean ok = service.isWhitelisted(event.getUniqueId(), event.getName());

            // 3️⃣ Wenn nicht whitelisted → Spieler kicken mit konfigurierter Nachricht.
            if (!ok) {
                String raw = plugin.getConfig().getString(
                        "kick.not_whitelisted",
                        "&cYou're not on our whitelist."
                );
                // Farbcode (&c) → Adventure Component umwandeln
                Component msg = MessageUtil.parse(raw);

                // Spieler ablehnen (KICK_OTHER → normaler Kickgrund)
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, msg);
            }

        } catch (Exception e) {
            // 4️⃣ Fehlerbehandlung: Datenbankverbindung oder SQL-Abfrage ist fehlgeschlagen.
            plugin.getLogger().log(Level.SEVERE,
                    "Whitelist check failed for " + event.getName(), e);

            // Fallback-Nachricht bei DB-Fehler
            String raw = plugin.getConfig().getString(
                    "kick.db_error",
                    "&cWhitelist check failed. Please try again later."
            );
            Component msg = MessageUtil.parse(raw);

            // Spieler freundlich mit Fehlerhinweis kicken
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, msg);
        }
    }
}
