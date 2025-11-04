package ch.ksrminecraft.kSRSQLWhitelist.utils;

import ch.ksrminecraft.kSRSQLWhitelist.KSRSQLWhitelist;
import org.bukkit.entity.Player;

import java.sql.*;
import java.util.UUID;

/**
 * ----------------------------------------------------------------------------
 *  🔐 WhitelistService
 *  -------------------
 *  Zentrale Service-Klasse für alle SQL-basierten Whitelist-Operationen.
 *
 *  Hauptfunktionen:
 *   - Prüft, ob ein Spieler in der Whitelist-Datenbank eingetragen ist
 *   - Fügt neue Spieler hinzu (online oder offline)
 *   - Entfernt Spieler aus der Datenbank
 *   - Listet alle Whitelist-Einträge auf
 *   - Aktualisiert Namen oder UUIDs bei Bedarf (Synchronisierung)
 *
 *  💡 Besonderheit:
 *   Diese Klasse arbeitet mit der {@link Database}-Klasse zusammen,
 *   die Tabellennamen und Spaltennamen dynamisch aus der config.yml liest.
 *
 *  Autor: Timy Liniger (KSR Minecraft)
 *  Projekt: KSR-SQL-Whitelist
 * ----------------------------------------------------------------------------
 */
public class WhitelistService {

    /** Hauptinstanz des Plugins (für Logging und Config-Zugriff). */
    private final KSRSQLWhitelist plugin;

    /** Hilfsklasse für Datenbankverbindungen und Tabelleninformationen. */
    private final Database db;

    /**
     * Konstruktor.
     *
     * @param plugin Plugin-Instanz
     * @param db     Datenbank-Verwaltungsklasse
     */
    public WhitelistService(KSRSQLWhitelist plugin, Database db) {
        this.plugin = plugin;
        this.db = db;
    }

    // ------------------------------------------------------------------------
    // ✅ Whitelist-Abfrage
    // ------------------------------------------------------------------------

    /**
     * Prüft, ob ein Spieler whitelisted ist.
     * <p>
     * Dabei werden folgende Fälle berücksichtigt:
     * 1️⃣ Direkter Treffer per UUID (mit oder ohne Bindestriche)
     * 2️⃣ Eintrag mit passendem Namen, aber ohne UUID → wird aktualisiert
     * 3️⃣ Name- oder UUID-Felder werden automatisch synchronisiert
     *
     * @param uuid UUID des Spielers (mit Bindestrichen)
     * @param name Aktueller Spielername
     * @return true, wenn der Spieler in der Whitelist ist
     * @throws SQLException bei Datenbankfehlern
     */
    public boolean isWhitelisted(UUID uuid, String name) throws SQLException {
        String colUUID = db.columnUUID();
        String colName = db.columnName();

        // SQL-Statements dynamisch auf Basis der Config-Spaltennamen erstellen
        final String selectByUUID = "SELECT `" + colUUID + "` FROM `" + db.table() + "` " +
                "WHERE `" + colUUID + "` = ? OR REPLACE(`" + colUUID + "`, '-', '') = ? LIMIT 1";
        final String selectByNameNoUUID = "SELECT 1 FROM `" + db.table() + "` " +
                "WHERE `" + colName + "` = ? AND (`" + colUUID + "` IS NULL OR `" + colUUID + "` = '') LIMIT 1";
        final String updateSetName = "UPDATE `" + db.table() + "` SET `" + colName + "` = ? WHERE `" + colUUID + "` = ?";
        final String updateAttachUUID = "UPDATE `" + db.table() + "` SET `" + colUUID + "` = ? " +
                "WHERE `" + colName + "` = ? AND (`" + colUUID + "` IS NULL OR `" + colUUID + "` = '')";

        String uuidDashed = uuid.toString();
        String uuidRaw = uuidDashed.replace("-", "");

        // Verbindung zur Datenbank öffnen
        try (Connection c = db.openConnection();
             PreparedStatement ps = c.prepareStatement(selectByUUID)) {

            // UUID in beiden Formaten (mit/ohne Bindestriche) prüfen
            ps.setString(1, uuidDashed);
            ps.setString(2, uuidRaw);

            // 🔍 1. Prüfung: Eintrag mit passender UUID vorhanden?
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String found = rs.getString(colUUID);

                    // Wenn UUID ohne Bindestriche gespeichert war → korrigieren
                    if (found != null && found.length() == 32) {
                        try (PreparedStatement fix = c.prepareStatement(
                                "UPDATE `" + db.table() + "` SET `" + colUUID + "` = ? WHERE `" + colUUID + "` = ?")) {
                            fix.setString(1, uuidDashed);
                            fix.setString(2, found);
                            fix.executeUpdate();
                            plugin.getLogger().warning("Fixed malformed UUID for " + name +
                                    " (" + found + " → " + uuidDashed + ")");
                        }
                    }

                    // Namen aktualisieren (z. B. wenn Spieler sich umbenannt hat)
                    try (PreparedStatement up = c.prepareStatement(updateSetName)) {
                        up.setString(1, name);
                        up.setString(2, uuidDashed);
                        up.executeUpdate();
                    }
                    return true;
                }
            }

            // 🔍 2. Prüfung: Nur Name bekannt, aber keine UUID vorhanden
            try (PreparedStatement ps2 = c.prepareStatement(selectByNameNoUUID)) {
                ps2.setString(1, name);
                try (ResultSet rs2 = ps2.executeQuery()) {
                    if (rs2.next()) {
                        // UUID beim ersten Login automatisch ergänzen
                        try (PreparedStatement up2 = c.prepareStatement(updateAttachUUID)) {
                            up2.setString(1, uuidDashed);
                            up2.setString(2, name);
                            up2.executeUpdate();
                        }
                        return true;
                    }
                }
            }
        }

        // Kein Treffer → Spieler nicht auf der Whitelist
        return false;
    }

    // ------------------------------------------------------------------------
    // ➕ Spieler hinzufügen (online/offline)
    // ------------------------------------------------------------------------

    /**
     * Fügt einen **online** verbundenen Spieler zur Whitelist hinzu oder aktualisiert ihn.
     * Verwendet UUID + Name.
     *
     * @param online Online-Spielerobjekt
     * @throws SQLException Wenn ein Datenbankfehler auftritt
     */
    public void addOrUpdateOnline(Player online) throws SQLException {
        addOrUpdateWhitelist(online.getUniqueId(), online.getName());
    }

    /**
     * Fügt einen **Offline-Spielernamen** zur Whitelist hinzu.
     * Die UUID wird später beim ersten Login automatisch ergänzt.
     *
     * @param name Spielername (Offline)
     * @throws SQLException Wenn ein Datenbankfehler auftritt
     */
    public void addOfflineName(String name) throws SQLException {
        String colUUID = db.columnUUID();
        String colName = db.columnName();

        final String exists = "SELECT 1 FROM `" + db.table() + "` WHERE `" + colName + "` = ? " +
                "AND (`" + colUUID + "` IS NULL OR `" + colUUID + "` = '') LIMIT 1";
        final String insert = "INSERT INTO `" + db.table() + "` (`" + colName + "`) VALUES (?)";

        try (Connection c = db.openConnection();
             PreparedStatement sel = c.prepareStatement(exists)) {
            sel.setString(1, name);
            try (ResultSet rs = sel.executeQuery()) {
                // Nur einfügen, wenn noch kein passender Eintrag existiert
                if (!rs.next()) {
                    try (PreparedStatement ins = c.prepareStatement(insert)) {
                        ins.setString(1, name);
                        ins.executeUpdate();
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------------
    // ❌ Spieler entfernen
    // ------------------------------------------------------------------------

    /**
     * Entfernt einen Spieler anhand seiner UUID aus der Whitelist.
     *
     * @param uuid UUID des Spielers
     * @return Anzahl der betroffenen Datensätze (0 = kein Treffer)
     * @throws SQLException Wenn ein Datenbankfehler auftritt
     */
    public int deleteByUUID(UUID uuid) throws SQLException {
        String colUUID = db.columnUUID();
        final String del = "DELETE FROM `" + db.table() + "` WHERE `" + colUUID + "` = ?";
        try (Connection c = db.openConnection();
             PreparedStatement ps = c.prepareStatement(del)) {
            ps.setString(1, uuid.toString());
            return ps.executeUpdate();
        }
    }

    /**
     * Entfernt einen Spieler anhand seines Namens aus der Whitelist.
     *
     * @param name Spielername
     * @return Anzahl der betroffenen Datensätze (0 = kein Treffer)
     * @throws SQLException Wenn ein Datenbankfehler auftritt
     */
    public int deleteByName(String name) throws SQLException {
        String colName = db.columnName();
        final String del = "DELETE FROM `" + db.table() + "` WHERE `" + colName + "` = ?";
        try (Connection c = db.openConnection();
             PreparedStatement ps = c.prepareStatement(del)) {
            ps.setString(1, name);
            return ps.executeUpdate();
        }
    }

    // ------------------------------------------------------------------------
    // 🧩 Eintrag hinzufügen oder aktualisieren
    // ------------------------------------------------------------------------

    /**
     * Fügt einen Eintrag hinzu oder aktualisiert ihn, falls bereits vorhanden.
     * <p>
     * Verwendet MySQLs "ON DUPLICATE KEY UPDATE", sofern ein UNIQUE KEY auf
     * der UUID-Spalte vorhanden ist.
     *
     * @param uuid UUID des Spielers
     * @param name Aktueller Spielername
     * @throws SQLException Wenn ein Datenbankfehler auftritt
     */
    private void addOrUpdateWhitelist(UUID uuid, String name) throws SQLException {
        String colUUID = db.columnUUID();
        String colName = db.columnName();

        final String upsert = "INSERT INTO `" + db.table() + "` (`" + colUUID + "`, `" + colName + "`) VALUES (?, ?) " +
                "ON DUPLICATE KEY UPDATE `" + colName + "` = VALUES(`" + colName + "`)";
        try (Connection c = db.openConnection();
             PreparedStatement ps = c.prepareStatement(upsert)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.executeUpdate();
        }
    }

    // ------------------------------------------------------------------------
    // 📋 Whitelist-Liste abrufen
    // ------------------------------------------------------------------------

    /**
     * Gibt eine Liste aller aktuell gespeicherten Spielernamen in der Whitelist zurück.
     *
     * @return Liste der Namen
     * @throws SQLException Wenn ein Datenbankfehler auftritt
     */
    public java.util.List<String> listWhitelistedNames() throws SQLException {
        String colName = db.columnName();
        final String sql = "SELECT DISTINCT `" + colName + "` FROM `" + db.table() + "` " +
                "WHERE `" + colName + "` IS NOT NULL AND `" + colName + "` <> ''";

        java.util.List<String> out = new java.util.ArrayList<>();
        try (Connection c = db.openConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(rs.getString(1));
            }
        }
        return out;
    }
}
