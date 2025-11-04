package ch.ksrminecraft.kSRSQLWhitelist;

import ch.ksrminecraft.kSRSQLWhitelist.listeners.PreLoginListener;
import ch.ksrminecraft.kSRSQLWhitelist.listeners.WhitelistCommandInterceptor;
import ch.ksrminecraft.kSRSQLWhitelist.utils.Database;
import ch.ksrminecraft.kSRSQLWhitelist.utils.WhitelistService;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * ----------------------------------------------------------------------------
 *  🧩 KSRSQLWhitelist
 *  ------------------
 *  Hauptklasse des Plugins "KSR-SQL-Whitelist".
 *
 *  Verantwortlichkeiten:
 *   - Initialisierung aller Hauptkomponenten (Config, Datenbank, Services, Listener)
 *   - Registrierung von Event-Listenern
 *   - Sicherstellen, dass die SQL-Tabelle existiert
 *   - Lifecycle-Logging (enable/disable)
 *
 *  💡 Dieses Plugin ersetzt die Vanilla-Whitelist durch eine SQL-basierte Variante,
 *     um die Whitelist zentral für mehrere Server zu verwalten.
 *
 *  Autor: Timy Liniger (KSR Minecraft)
 *  Projekt: KSR-SQL-Whitelist
 * ----------------------------------------------------------------------------
 */
public class KSRSQLWhitelist extends JavaPlugin {

    /** Verwaltet den Zugriff auf die MySQL-Datenbank. */
    private Database database;

    /** Kapselt alle Whitelist-bezogenen Datenbankoperationen. */
    private WhitelistService whitelistService;

    // ------------------------------------------------------------------------
    // 🚀 Plugin-Start
    // ------------------------------------------------------------------------

    /**
     * Wird beim Aktivieren des Plugins aufgerufen (z. B. beim Serverstart oder Reload).
     * <p>
     * Initialisiert alle zentralen Komponenten und registriert Event-Listener.
     */
    @Override
    public void onEnable() {
        // 1️⃣ Standard-Config laden oder neu erzeugen, falls sie fehlt
        saveDefaultConfig();

        // 2️⃣ Datenbank-Manager und Whitelist-Service initialisieren
        database = new Database(this);
        whitelistService = new WhitelistService(this, database);

        // 3️⃣ Sicherstellen, dass die Whitelist-Tabelle existiert
        try {
            database.ensureTable();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to ensure DB table", e);
        }

        // 4️⃣ Event-Listener registrieren
        getServer().getPluginManager().registerEvents(new PreLoginListener(this, whitelistService), this);
        getServer().getPluginManager().registerEvents(new WhitelistCommandInterceptor(this, whitelistService), this);
        getCommand("whitelist").setTabCompleter(new ch.ksrminecraft.kSRSQLWhitelist.commands.WhitelistTabCompleter(this));

        // 5️⃣ Erfolgsnachricht ins Log schreiben
        getLogger().info(getDescription().getName() + " v" + getDescription().getVersion() + " enabled.");
    }

    // ------------------------------------------------------------------------
    // 📴 Plugin-Stopp
    // ------------------------------------------------------------------------

    /**
     * Wird beim Deaktivieren des Plugins aufgerufen (z. B. bei Serverstopp).
     * <p>
     * Da keine dauerhaften Verbindungen bestehen, ist kein spezielles Cleanup nötig.
     */
    @Override
    public void onDisable() {
        getLogger().info(getDescription().getName() + " v" + getDescription().getVersion() + " disabled.");
    }

    // ------------------------------------------------------------------------
    // 🧠 Getter
    // ------------------------------------------------------------------------

    /**
     * Gibt den aktiven WhitelistService zurück.
     * <p>
     * Kann von anderen Plugins oder Command-Klassen verwendet werden,
     * um z. B. Whitelist-Einträge programmgesteuert zu prüfen oder zu ändern.
     *
     * @return Instanz des {@link WhitelistService}
     */
    public WhitelistService getWhitelistService() {
        return whitelistService;
    }
}
