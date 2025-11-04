package ch.ksrminecraft.kSRSQLWhitelist.utils;

import ch.ksrminecraft.kSRSQLWhitelist.KSRSQLWhitelist;
import org.bukkit.entity.Player;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.*;
import java.util.Scanner;
import java.util.UUID;

/**
 * ----------------------------------------------------------------------------
 *  🔐 WhitelistService
 *  -------------------
 *  Zentrale Service-Klasse für alle SQL-basierten Whitelist-Operationen.
 *
 *  Hauptfunktionen:
 *   - Prüft, ob Spieler in der Datenbank whitelisted sind
 *   - Fügt neue Spieler hinzu (online oder offline)
 *   - Entfernt Spieler aus der Datenbank
 *   - Listet alle gespeicherten Whitelist-Namen auf
 *   - Synchronisiert UUIDs und Namen automatisch
 *
 *  💡 Besonderheit:
 *   Diese Klasse arbeitet mit der {@link Database}-Klasse zusammen,
 *   welche Tabellen- und Spaltennamen dynamisch aus der config.yml liest.
 *
 *  Autor: Timy Liniger (KSR Minecraft)
 *  Projekt: KSR-SQL-Whitelist
 * ----------------------------------------------------------------------------
 */
public class WhitelistService {

    /** Hauptinstanz des Plugins (für Logging, Config, etc.) */
    private final KSRSQLWhitelist plugin;

    /** Datenbank-Verwaltung (stellt Verbindungen & Tabelleninfos bereit) */
    private final Database db;

    /**
     * Konstruktor zur Initialisierung des Whitelist-Service.
     *
     * @param plugin Plugin-Instanz
     * @param db     Datenbank-Verwaltungsklasse
     */
    public WhitelistService(KSRSQLWhitelist plugin, Database db) {
        this.plugin = plugin;
        this.db = db;
    }

    // ------------------------------------------------------------------------
    // ✅ Prüfung, ob Spieler whitelisted ist
    // ------------------------------------------------------------------------

    /**
     * Prüft, ob ein Spieler in der Datenbank whitelisted ist.
     * Führt außerdem automatische Synchronisierung von UUID und Name durch.
     *
     * @param uuid UUID des Spielers
     * @param name Aktueller Spielername
     * @return true, wenn der Spieler whitelisted ist
     * @throws SQLException bei Datenbankfehlern
     */
    public boolean isWhitelisted(UUID uuid, String name) throws SQLException {
        String colUUID = db.columnUUID();
        String colName = db.columnName();

        final String selectByUUID = "SELECT `" + colUUID + "` FROM `" + db.table() + "` " +
                "WHERE `" + colUUID + "` = ? OR REPLACE(`" + colUUID + "`, '-', '') = ? LIMIT 1";
        final String selectByNameNoUUID = "SELECT 1 FROM `" + db.table() + "` " +
                "WHERE `" + colName + "` = ? AND (`" + colUUID + "` IS NULL OR `" + colUUID + "` = '') LIMIT 1";
        final String updateSetName = "UPDATE `" + db.table() + "` SET `" + colName + "` = ? WHERE `" + colUUID + "` = ?";
        final String updateAttachUUID = "UPDATE `" + db.table() + "` SET `" + colUUID + "` = ? " +
                "WHERE `" + colName + "` = ? AND (`" + colUUID + "` IS NULL OR `" + colUUID + "` = '')";

        String uuidDashed = uuid.toString();
        String uuidRaw = uuidDashed.replace("-", "");

        try (Connection c = db.openConnection();
             PreparedStatement ps = c.prepareStatement(selectByUUID)) {

            ps.setString(1, uuidDashed);
            ps.setString(2, uuidRaw);

            // 🔍 Treffer über UUID prüfen
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String found = rs.getString(colUUID);
                    // korrigiere ggf. fehlerhafte UUID-Formate
                    if (found != null && found.length() == 32) {
                        try (PreparedStatement fix = c.prepareStatement(
                                "UPDATE `" + db.table() + "` SET `" + colUUID + "` = ? WHERE `" + colUUID + "` = ?")) {
                            fix.setString(1, uuidDashed);
                            fix.setString(2, found);
                            fix.executeUpdate();
                            plugin.getLogger().warning("Fixed malformed UUID for " + name + " (" + found + " → " + uuidDashed + ")");
                        }
                    }

                    // Namen immer aktuell halten
                    try (PreparedStatement up = c.prepareStatement(updateSetName)) {
                        up.setString(1, name);
                        up.setString(2, uuidDashed);
                        up.executeUpdate();
                    }
                    return true;
                }
            }

            // 🔍 Fallback: Spielername vorhanden, aber keine UUID
            try (PreparedStatement ps2 = c.prepareStatement(selectByNameNoUUID)) {
                ps2.setString(1, name);
                try (ResultSet rs2 = ps2.executeQuery()) {
                    if (rs2.next()) {
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

        return false;
    }

    // ------------------------------------------------------------------------
    // ➕ Spieler hinzufügen
    // ------------------------------------------------------------------------

    /**
     * Fügt einen online verbundenen Spieler zur Whitelist hinzu oder aktualisiert ihn.
     *
     * @param online Online-Spielerobjekt
     * @throws SQLException Wenn ein Datenbankfehler auftritt
     */
    public void addOrUpdateOnline(Player online) throws SQLException {
        addOrUpdateWhitelist(online.getUniqueId(), online.getName());
    }

    /**
     * Fügt einen Offline-Spieler zur Whitelist hinzu, sofern ein offizieller Mojang-Account existiert.
     * Die UUID wird automatisch über die Mojang-API ermittelt.
     *
     * @param name Spielername
     * @throws SQLException Wenn kein offizieller Account gefunden oder DB-Fehler auftritt
     */
    public void addOfflineName(String name) throws SQLException {
        String colUUID = db.columnUUID();
        String colName = db.columnName();
        final String exists = "SELECT 1 FROM `" + db.table() + "` WHERE `" + colName + "` = ? LIMIT 1";
        final String insert = "INSERT INTO `" + db.table() + "` (`" + colUUID + "`, `" + colName + "`) VALUES (?, ?)";

        // Prüfen, ob Spieler schon existiert
        try (Connection c = db.openConnection();
             PreparedStatement sel = c.prepareStatement(exists)) {
            sel.setString(1, name);
            try (ResultSet rs = sel.executeQuery()) {
                if (rs.next()) {
                    return; // bereits vorhanden
                }
            }
        }

        // UUID über Mojang-API abrufen
        String uuid = fetchUUIDFromMojang(name);
        if (uuid == null) {
            plugin.getLogger().warning("[KSR-SQL-Whitelist] Could not find Mojang UUID for player '" + name + "'.");
            throw new SQLException("Player '" + name + "' is not a valid Mojang account.");
        }

        // Einfügen
        try (Connection c = db.openConnection();
             PreparedStatement ins = c.prepareStatement(insert)) {
            ins.setString(1, uuid);
            ins.setString(2, name);
            ins.executeUpdate();
            plugin.getLogger().info("[KSR-SQL-Whitelist] Added Mojang-verified player: " + name + " (" + uuid + ")");
        }
    }

    /**
     * Fragt die UUID eines Spielers über die Ashcon-API ab.
     *
     * @param playerName Minecraft-Name
     * @return UUID mit Bindestrichen oder null, falls nicht gefunden
     */
    private String fetchUUIDFromMojang(String playerName) {
        try {
            URL url = new URL("https://api.ashcon.app/mojang/v2/user/" + playerName);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "KSR-SQL-Whitelist");
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            conn.setRequestMethod("GET");

            if (conn.getResponseCode() != 200) {
                plugin.getLogger().warning("[KSR-SQL-Whitelist] Ashcon API returned " +
                        conn.getResponseCode() + " for " + playerName);
                return null;
            }

            try (InputStream is = conn.getInputStream();
                 Scanner scanner = new Scanner(is).useDelimiter("\\A")) {
                String json = scanner.hasNext() ? scanner.next() : "";
                if (json.isEmpty()) return null;

                org.json.JSONObject obj = new org.json.JSONObject(json);
                String rawUUID = obj.optString("uuid", null);
                if (rawUUID == null || rawUUID.isEmpty()) return null;
                return rawUUID;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[KSR-SQL-Whitelist] Failed to fetch UUID via Ashcon for " +
                    playerName + ": " + e.getMessage());
            return null;
        }
    }

    // ------------------------------------------------------------------------
    // ❌ Spieler löschen
    // ------------------------------------------------------------------------

    public int deleteByUUID(UUID uuid) throws SQLException {
        String colUUID = db.columnUUID();
        final String del = "DELETE FROM `" + db.table() + "` WHERE `" + colUUID + "` = ?";
        try (Connection c = db.openConnection();
             PreparedStatement ps = c.prepareStatement(del)) {
            ps.setString(1, uuid.toString());
            return ps.executeUpdate();
        }
    }

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
    // 🧩 Upsert (Insert or Update)
    // ------------------------------------------------------------------------

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
    // 📋 Whitelist abrufen
    // ------------------------------------------------------------------------

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

    // ------------------------------------------------------------------------
    // 🧱 Getter für Database
    // ------------------------------------------------------------------------

    /**
     * Gibt die aktuelle {@link Database}-Instanz zurück.
     * Wird u.a. von Command-Klassen genutzt, um SQL-Abfragen auszuführen.
     *
     * @return die aktive Database-Instanz
     */
    public Database getDatabase() {
        return db;
    }
}
