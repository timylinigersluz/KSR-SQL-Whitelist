package ch.ksrminecraft.kSRSQLWhitelist.utils;

import ch.ksrminecraft.kSRSQLWhitelist.KSRSQLWhitelist;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;
import java.util.logging.Level;

/**
 * ----------------------------------------------------------------------------
 *  🗄️ Database
 *  ------------
 *  Diese Klasse verwaltet die Verbindung zur MySQL-Datenbank für das
 *  KSR-SQL-Whitelist-Plugin.
 *
 *  Hauptfunktionen:
 *   - Aufbau einer JDBC-Verbindung gemäß Konfiguration
 *   - Erstellung der Whitelist-Tabelle, falls sie noch nicht existiert
 *   - Zugriff auf konfigurierbare Tabellennamen und Spaltennamen
 *
 *  💡 Besonderheit:
 *   Über die Felder {@code mysql.column_uuid} und {@code mysql.column_name}
 *   kann in der config.yml definiert werden, wie die Spalten in der Datenbank heißen.
 *   Dadurch ist das Plugin flexibel gegenüber unterschiedlichen Datenbankschemata.
 *
 *  Autor: Timy Liniger (KSR Minecraft)
 *  Projekt: KSR-SQL-Whitelist
 * ----------------------------------------------------------------------------
 */
public class Database {

    /** Hauptinstanz des Plugins (Zugriff auf Config, Logger, etc.). */
    private final KSRSQLWhitelist plugin;

    /**
     * Konstruktor.
     *
     * @param plugin Hauptinstanz des KSR-SQL-Whitelist-Plugins.
     */
    public Database(KSRSQLWhitelist plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------------
    // 🔌 Verbindung zur Datenbank herstellen
    // ------------------------------------------------------------------------

    /**
     * Öffnet eine neue Verbindung zur MySQL-Datenbank auf Basis der Konfiguration.
     * <p>
     * ⚠️ Hinweis: Diese Methode erstellt jedes Mal eine neue Verbindung und sollte
     * daher nicht dauerhaft im Hauptthread verwendet werden. Für wiederholte
     * Abfragen empfiehlt sich ein Connection-Pool (z. B. HikariCP).
     *
     * @return Aktive {@link Connection} zur Datenbank
     * @throws SQLException Wenn der Verbindungsaufbau fehlschlägt
     */
    public Connection openConnection() throws SQLException {
        // Pflichtfelder aus der Konfiguration lesen
        String host = req("mysql.host");
        int port = plugin.getConfig().getInt("mysql.port", 3306);
        String db   = req("mysql.database");
        String user = req("mysql.user");
        String pass = req("mysql.password");

        // SSL und Zeitzone – optional konfigurierbar
        boolean useSSL = plugin.getConfig().getBoolean("mysql.useSSL", false);
        String serverTimezone = plugin.getConfig().getString("mysql.serverTimezone", "UTC");

        // Zusätzliche Verbindungsparameter
        String params = String.join("&",
                "useUnicode=true",
                "characterEncoding=UTF-8",
                "useSSL=" + useSSL,
                "serverTimezone=" + serverTimezone
        );

        // Aufbau der finalen JDBC-URL
        String url = "jdbc:mysql://" + host + ":" + port + "/" + db + "?" + params;

        // Verbindung herstellen
        return DriverManager.getConnection(url, user, pass);
    }

    // ------------------------------------------------------------------------
    // ⚙️ Tabellen- und Spaltennamen aus der Config lesen
    // ------------------------------------------------------------------------

    /**
     * Liefert den konfigurierten Tabellennamen.
     *
     * @return Tabellenname (z. B. "mysql_whitelist")
     */
    public String table() {
        return req("mysql.table");
    }

    /**
     * Liefert den Spaltennamen für die UUID.
     * Standardwert: {@code UUID}
     *
     * @return Name der Spalte, die die UUID speichert
     */
    public String columnUUID() {
        return plugin.getConfig().getString("mysql.column_uuid", "UUID");
    }

    /**
     * Liefert den Spaltennamen für den Spielernamen.
     * Standardwert: {@code user}
     *
     * @return Name der Spalte, die den Spielernamen enthält
     */
    public String columnName() {
        return plugin.getConfig().getString("mysql.column_name", "user");
    }

    // ------------------------------------------------------------------------
    // 🧱 Sicherstellen, dass die Tabelle existiert
    // ------------------------------------------------------------------------

    /**
     * Erstellt die Whitelist-Tabelle, falls sie noch nicht existiert.
     * <p>
     * Diese Methode nutzt die aktuell in der Config definierten Spaltennamen.
     * Sie wird typischerweise beim Plugin-Start aufgerufen.
     *
     * Beispielhafte SQL-Struktur:
     * <pre>
     * CREATE TABLE IF NOT EXISTS `mysql_whitelist` (
     *   `UUID` varchar(36) DEFAULT NULL,
     *   `user` varchar(100) DEFAULT NULL,
     *   KEY `idx_uuid` (`UUID`),
     *   KEY `idx_user` (`user`)
     * ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
     * </pre>
     *
     * @throws SQLException Falls die SQL-Ausführung fehlschlägt
     */
    public void ensureTable() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS `" + table() + "` ("
                + "`" + columnUUID() + "` varchar(36) DEFAULT NULL,"
                + "`" + columnName() + "` varchar(100) DEFAULT NULL,"
                + "KEY `idx_uuid` (`" + columnUUID() + "`),"
                + "KEY `idx_user` (`" + columnName() + "`)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;";

        try (Connection c = openConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.execute();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to ensure whitelist table", e);
            throw e;
        }
    }

    // ------------------------------------------------------------------------
    // 🧩 Hilfsmethode
    // ------------------------------------------------------------------------

    /**
     * Liest einen String-Wert aus der Config und stellt sicher, dass er vorhanden ist.
     * Wenn der Wert fehlt, wird eine {@link NullPointerException} mit Pfadangabe geworfen.
     *
     * @param path Pfad in der Config (z. B. "mysql.host")
     * @return Wert aus der Config
     */
    private String req(String path) {
        return Objects.requireNonNull(plugin.getConfig().getString(path), path);
    }
}
