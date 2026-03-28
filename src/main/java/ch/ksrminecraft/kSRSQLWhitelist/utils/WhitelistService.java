package ch.ksrminecraft.kSRSQLWhitelist.utils;

import ch.ksrminecraft.kSRSQLWhitelist.KSRSQLWhitelist;
import org.bukkit.entity.Player;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.*;
import java.util.*;
import java.util.logging.Level;

public class WhitelistService {

    private final KSRSQLWhitelist plugin;
    private final Database db;
    private final LocalFallbackDatabase localDb;

    public WhitelistService(KSRSQLWhitelist plugin, Database db, LocalFallbackDatabase localDb) {
        this.plugin = plugin;
        this.db = db;
        this.localDb = localDb;
    }

    public boolean isWhitelisted(UUID uuid, String name) throws SQLException {
        String table = plugin.getConfig().getString("mysql.table", "mysql_whitelist");
        String colUUID = plugin.getConfig().getString("mysql.column_uuid", "UUID");
        String colName = plugin.getConfig().getString("mysql.column_name", "user");

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

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String found = rs.getString(colUUID);

                    if (found != null && found.length() == 32) {
                        try (PreparedStatement fix = c.prepareStatement(
                                "UPDATE `" + table + "` SET `" + colUUID + "` = ? WHERE `" + colUUID + "` = ?")) {
                            fix.setString(1, uuidDashed);
                            fix.setString(2, found);
                            fix.executeUpdate();
                            plugin.getLogger().warning("Fixed malformed UUID for " + name + " (" + found + " -> " + uuidDashed + ")");
                        }
                    }

                    try (PreparedStatement up = c.prepareStatement(updateSetName)) {
                        up.setString(1, name);
                        up.setString(2, uuidDashed);
                        up.executeUpdate();
                    }

                    // lokal spiegeln
                    try {
                        localDb.upsert(uuid, name);
                    } catch (SQLException ex) {
                        plugin.getLogger().warning("Could not update local fallback cache for " + name + ": " + ex.getMessage());
                    }

                    return true;
                }
            }

            try (PreparedStatement ps2 = c.prepareStatement(selectByNameNoUUID)) {
                ps2.setString(1, name);
                try (ResultSet rs2 = ps2.executeQuery()) {
                    if (rs2.next()) {
                        try (PreparedStatement up2 = c.prepareStatement(updateAttachUUID)) {
                            up2.setString(1, uuidDashed);
                            up2.setString(2, name);
                            up2.executeUpdate();
                        }

                        try {
                            localDb.upsert(uuid, name);
                        } catch (SQLException ex) {
                            plugin.getLogger().warning("Could not update local fallback cache for " + name + ": " + ex.getMessage());
                        }

                        return true;
                    }
                }
            }
        }

        return false;
    }

    public boolean isWhitelistedLocal(UUID uuid, String name) throws SQLException {
        return localDb.isWhitelisted(uuid, name);
    }

    public boolean existsInWhitelist(String playerName) {
        String table = plugin.getConfig().getString("mysql.table", "mysql_whitelist");
        String colUUID = plugin.getConfig().getString("mysql.column_uuid", "UUID");
        String colName = plugin.getConfig().getString("mysql.column_name", "user");

        boolean whitelisted = false;

        try (Connection c = db.openConnection()) {
            String sqlByName = "SELECT 1 FROM `" + table + "` WHERE `" + colName + "` = ? LIMIT 1";
            try (PreparedStatement ps = c.prepareStatement(sqlByName)) {
                ps.setString(1, playerName);
                try (ResultSet rs = ps.executeQuery()) {
                    whitelisted = rs.next();
                }
            }

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

    public void addOrUpdateOnline(Player online) throws SQLException {
        addOrUpdateWhitelist(online.getUniqueId(), online.getName());
        localDb.upsert(online.getUniqueId(), online.getName());
    }

    public void addOfflineName(String name) throws SQLException {
        String table = plugin.getConfig().getString("mysql.table", "mysql_whitelist");
        String colUUID = plugin.getConfig().getString("mysql.column_uuid", "UUID");
        String colName = plugin.getConfig().getString("mysql.column_name", "user");

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

        localDb.upsert(uuid, name);
    }

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

    public int deleteByUUID(UUID uuid) throws SQLException {
        String table = plugin.getConfig().getString("mysql.table", "mysql_whitelist");
        String colUUID = plugin.getConfig().getString("mysql.column_uuid", "UUID");

        final String del = "DELETE FROM `" + table + "` WHERE `" + colUUID + "` = ?";
        int affected;
        try (Connection c = db.openConnection();
             PreparedStatement ps = c.prepareStatement(del)) {
            ps.setString(1, uuid.toString());
            affected = ps.executeUpdate();
        }

        localDb.deleteByUUID(uuid);
        return affected;
    }

    public int deleteByName(String name) throws SQLException {
        String table = plugin.getConfig().getString("mysql.table", "mysql_whitelist");
        String colName = plugin.getConfig().getString("mysql.column_name", "user");

        final String del = "DELETE FROM `" + table + "` WHERE `" + colName + "` = ?";
        int affected;
        try (Connection c = db.openConnection();
             PreparedStatement ps = c.prepareStatement(del)) {
            ps.setString(1, name);
            affected = ps.executeUpdate();
        }

        localDb.deleteByName(name);
        return affected;
    }

    private void addOrUpdateWhitelist(UUID uuid, String name) throws SQLException {
        String table = plugin.getConfig().getString("mysql.table", "mysql_whitelist");
        String colUUID = plugin.getConfig().getString("mysql.column_uuid", "UUID");
        String colName = plugin.getConfig().getString("mysql.column_name", "user");

        final String upsert = "INSERT INTO `" + table + "` (`" + colUUID + "`, `" + colName + "`) VALUES (?, ?) " +
                "ON DUPLICATE KEY UPDATE `" + colName + "` = VALUES(`" + colName + "`)";

        try (Connection c = db.openConnection();
             PreparedStatement ps = c.prepareStatement(upsert)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.executeUpdate();
        }
    }

    public List<String> listWhitelistedNames() throws SQLException {
        String table = plugin.getConfig().getString("mysql.table", "mysql_whitelist");
        String colName = plugin.getConfig().getString("mysql.column_name", "user");

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

    public List<String> listWhitelistedNamesLocal() throws SQLException {
        return localDb.listWhitelistedNames();
    }

    public void syncMysqlToLocalFallback() throws SQLException {
        String table = plugin.getConfig().getString("mysql.table", "mysql_whitelist");
        String colUUID = plugin.getConfig().getString("mysql.column_uuid", "UUID");
        String colName = plugin.getConfig().getString("mysql.column_name", "user");

        final String sql = "SELECT `" + colUUID + "`, `" + colName + "` FROM `" + table + "`";

        List<LocalFallbackDatabase.WhitelistEntry> entries = new ArrayList<>();

        try (Connection c = db.openConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String uuid = rs.getString(colUUID);
                String name = rs.getString(colName);

                if (uuid == null || uuid.isBlank()) {
                    continue;
                }
                if (name == null || name.isBlank()) {
                    continue;
                }

                entries.add(new LocalFallbackDatabase.WhitelistEntry(uuid, name));
            }
        }

        localDb.replaceAll(entries);
        plugin.getLogger().info("Local whitelist fallback cache synchronized successfully (" + entries.size() + " entries).");
    }

    public Database getDatabase() {
        return db;
    }

    public LocalFallbackDatabase getLocalDatabase() {
        return localDb;
    }
}