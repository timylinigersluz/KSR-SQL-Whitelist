package ch.ksrminecraft.kSRSQLWhitelist.utils;

import ch.ksrminecraft.kSRSQLWhitelist.KSRSQLWhitelist;
import org.bukkit.entity.Player;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.*;
import java.util.*;

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
 *   Arbeitet mit {@link Database}, die nur die Verbindungslogik kapselt.
 *   Tabellen- und Spaltennamen werden dynamisch aus "whitelist" in config.yml gelesen.
 *
 *  Autor: Timy Liniger (KSR Minecraft)
 *  Projekt: KSR-SQL-Whitelist
 * ----------------------------------------------------------------------------
 */
public class WhitelistService {

    private final KSRSQLWhitelist plugin;
    private final Database db;

    public WhitelistService(KSRSQLWhitelist plugin, Database db) {
        this.plugin = plugin;
        this.db = db;
    }

    // ------------------------------------------------------------------------
    // ✅ Prüfung, ob Spieler whitelisted ist (für Login-Checks)
    // ------------------------------------------------------------------------
    public boolean isWhitelisted(UUID uuid, String name) throws SQLException {
        String table = plugin.getConfig().getString("whitelist.table", "mysql_whitelist");
        String colUUID = plugin.getConfig().getString("whitelist.column_uuid", "UUID");
        String colName = plugin.getConfig().getString("whitelist.column_name", "user");

        final String selectByUUID = "SELECT `" + colUUID + "` FROM `" + table + "` " +
                "WHERE `" + colUUID + "` = ? OR REPLACE(`" + colUUID + "`, '-', '') = ? LIMIT 1";
        final String selectByNameNoUUID = "SELECT 1 FROM `" + table + "` " +
                "WHERE `" + colName + "` = ? AND (`" + colUUID + "` IS NULL OR `" + colUUID + "` = '') LIMIT 1";
        final String updateSetName = "UPDATE `" + table + "` SET `" + colName + "` = ? WHERE `" + colUUID + "` = ?";
        final String updateAttachUUID = "UPDATE `" + table + "` SET `" + colUUID + "` = ? " +
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
                                "UPDATE `" + table + "` SET `" + colUUID + "` = ? WHERE `" + colUUID + "` = ?")) {
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
    // 🧩 Öffentliche Prüfmethode für Commands (/whitelist info)
    // ------------------------------------------------------------------------
    public boolean existsInWhitelist(String playerName) {
        String table = plugin.getConfig().getString("whitelist.table", "mysql_whitelist");
        String colUUID = plugin.getConfig().getString("whitelist.column_uuid", "UUID");
        String colName = plugin.getConfig().getString("whitelist.column_name", "user");

        boolean whitelisted = false;

        try (Connection c = db.openConnection()) {
            // 🔹 Erst nach Name prüfen
            String sqlByName = "SELECT 1 FROM `" + table + "` WHERE `" + colName + "` = ? LIMIT 1";
            try (PreparedStatement ps = c.prepareStatement(sqlByName)) {
                ps.setString(1, playerName);
                try (ResultSet rs = ps.executeQuery()) {
                    whitelisted = rs.next();
                }
            }

            // 🔹 Fallback über UUID falls nötig
            if (!whitelisted) {
                String uuid = fetchUUIDFromMojang(playerName);
                if (uuid != null) {
                    String sqlByUuid = "SELECT 1 FROM `" + table + "` WHERE `" + colUUID + "` = ? OR REPLACE(`" + colUUID + "`, '-', '') = ? LIMIT 1";
                    try (PreparedStatement ps = c.prepareStatement(sqlByUuid)) {
                        ps.setString(1, uuid);
                        ps.setString(2, uuid.replace("-", ""));
                        try (ResultSet rs = ps.executeQuery()) {
                            whitelisted = rs.next();
                        }
                    }
                }
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("[KSR-SQL-Whitelist] Whitelist check failed for " + playerName + ": " + ex.getMessage());
        }

        return whitelisted;
    }

    // ------------------------------------------------------------------------
    // ➕ Spieler hinzufügen
    // ------------------------------------------------------------------------
    public void addOrUpdateOnline(Player online) throws SQLException {
        addOrUpdateWhitelist(online.getUniqueId(), online.getName());
    }

    public void addOfflineName(String name) throws SQLException {
        String table = plugin.getConfig().getString("whitelist.table", "mysql_whitelist");
        String colUUID = plugin.getConfig().getString("whitelist.column_uuid", "UUID");
        String colName = plugin.getConfig().getString("whitelist.column_name", "user");

        final String exists = "SELECT 1 FROM `" + table + "` WHERE `" + colName + "` = ? LIMIT 1";
        final String insert = "INSERT INTO `" + table + "` (`" + colUUID + "`, `" + colName + "`) VALUES (?, ?)";

        try (Connection c = db.openConnection();
             PreparedStatement sel = c.prepareStatement(exists)) {
            sel.setString(1, name);
            try (ResultSet rs = sel.executeQuery()) {
                if (rs.next()) return;
            }
        }

        String uuid = fetchUUIDFromMojang(name);
        if (uuid == null) {
            plugin.getLogger().warning("[KSR-SQL-Whitelist] Could not find Mojang UUID for player '" + name + "'.");
            throw new SQLException("Player '" + name + "' is not a valid Mojang account.");
        }

        try (Connection c = db.openConnection();
             PreparedStatement ins = c.prepareStatement(insert)) {
            ins.setString(1, uuid);
            ins.setString(2, name);
            ins.executeUpdate();
            plugin.getLogger().info("[KSR-SQL-Whitelist] Added Mojang-verified player: " + name + " (" + uuid + ")");
        }
    }

    // ------------------------------------------------------------------------
    // 🌍 Mojang API Helper
    // ------------------------------------------------------------------------
    private String fetchUUIDFromMojang(String playerName) {
        try {
            URL url = new URL("https://api.ashcon.app/mojang/v2/user/" + playerName);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "KSR-SQL-Whitelist");
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            conn.setRequestMethod("GET");

            if (conn.getResponseCode() != 200) {
                return null;
            }

            try (InputStream is = conn.getInputStream();
                 Scanner scanner = new Scanner(is).useDelimiter("\\A")) {
                String json = scanner.hasNext() ? scanner.next() : "";
                if (json.isEmpty()) return null;

                JSONObject obj = new JSONObject(json);
                String rawUUID = obj.optString("uuid", null);
                if (rawUUID == null || rawUUID.isEmpty()) return null;
                return rawUUID;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[KSR-SQL-Whitelist] Failed to fetch UUID for " + playerName + ": " + e.getMessage());
            return null;
        }
    }

    // ------------------------------------------------------------------------
    // ❌ Spieler löschen
    // ------------------------------------------------------------------------
    public int deleteByUUID(UUID uuid) throws SQLException {
        String table = plugin.getConfig().getString("whitelist.table", "mysql_whitelist");
        String colUUID = plugin.getConfig().getString("whitelist.column_uuid", "UUID");

        final String del = "DELETE FROM `" + table + "` WHERE `" + colUUID + "` = ?";
        try (Connection c = db.openConnection();
             PreparedStatement ps = c.prepareStatement(del)) {
            ps.setString(1, uuid.toString());
            return ps.executeUpdate();
        }
    }

    public int deleteByName(String name) throws SQLException {
        String table = plugin.getConfig().getString("whitelist.table", "mysql_whitelist");
        String colName = plugin.getConfig().getString("whitelist.column_name", "user");

        final String del = "DELETE FROM `" + table + "` WHERE `" + colName + "` = ?";
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
        String table = plugin.getConfig().getString("whitelist.table", "mysql_whitelist");
        String colUUID = plugin.getConfig().getString("whitelist.column_uuid", "UUID");
        String colName = plugin.getConfig().getString("whitelist.column_name", "user");

        final String upsert = "INSERT INTO `" + table + "` (`" + colUUID + "`, `" + colName + "`) VALUES (?, ?) " +
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
    public List<String> listWhitelistedNames() throws SQLException {
        String table = plugin.getConfig().getString("whitelist.table", "mysql_whitelist");
        String colName = plugin.getConfig().getString("whitelist.column_name", "user");

        final String sql = "SELECT DISTINCT `" + colName + "` FROM `" + table + "` " +
                "WHERE `" + colName + "` IS NOT NULL AND `" + colName + "` <> ''";

        List<String> out = new ArrayList<>();
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
    public Database getDatabase() {
        return db;
    }
}
