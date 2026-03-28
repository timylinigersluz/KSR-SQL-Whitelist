package ch.ksrminecraft.kSRSQLWhitelist.utils;

import ch.ksrminecraft.kSRSQLWhitelist.KSRSQLWhitelist;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


/**
 * Lokale SQLite-Fallback-Datenbank für die Whitelist.
 *
 * Zweck:
 * - Hält eine lokale Kopie der SQL-Whitelist vor
 * - Kann bei Ausfall der Hauptdatenbank für Join-Prüfungen genutzt werden
 */
public class LocalFallbackDatabase {

    private final KSRSQLWhitelist plugin;
    private final File dbFile;

    public LocalFallbackDatabase(KSRSQLWhitelist plugin) {
        this.plugin = plugin;
        String fileName = plugin.getConfig().getString("fallback.file", "fallback-whitelist.db");
        this.dbFile = new File(plugin.getDataFolder(), fileName);
    }

    public Connection openConnection() throws SQLException {
        String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
        return DriverManager.getConnection(url);
    }

    public void ensureTable() throws SQLException {
        String sql = """
                CREATE TABLE IF NOT EXISTS whitelist_cache (
                    uuid TEXT,
                    name TEXT,
                    PRIMARY KEY (uuid)
                );
                """;

        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.execute();
        }
    }

    public void replaceAll(List<WhitelistEntry> entries) throws SQLException {
        try (Connection c = openConnection()) {
            c.setAutoCommit(false);

            try (PreparedStatement del = c.prepareStatement("DELETE FROM whitelist_cache")) {
                del.executeUpdate();
            }

            try (PreparedStatement ins = c.prepareStatement(
                    "INSERT OR REPLACE INTO whitelist_cache (uuid, name) VALUES (?, ?)")) {
                for (WhitelistEntry entry : entries) {
                    ins.setString(1, entry.uuid());
                    ins.setString(2, entry.name());
                    ins.addBatch();
                }
                ins.executeBatch();
            }

            c.commit();
        }
    }

    public boolean isWhitelisted(UUID uuid, String name) throws SQLException {
        String uuidDashed = uuid.toString();
        String uuidRaw = uuidDashed.replace("-", "");

        String sqlByUuid = """
                SELECT uuid FROM whitelist_cache
                WHERE uuid = ? OR REPLACE(uuid, '-', '') = ?
                LIMIT 1
                """;

        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement(sqlByUuid)) {
            ps.setString(1, uuidDashed);
            ps.setString(2, uuidRaw);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    // Namen lokal aktuell halten
                    try (PreparedStatement up = c.prepareStatement(
                            "UPDATE whitelist_cache SET name = ? WHERE uuid = ?")) {
                        up.setString(1, name);
                        up.setString(2, uuidDashed);
                        up.executeUpdate();
                    }
                    return true;
                }
            }
        }

        String sqlByName = """
                SELECT 1 FROM whitelist_cache
                WHERE LOWER(name) = LOWER(?)
                LIMIT 1
                """;

        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement(sqlByName)) {
            ps.setString(1, name);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    // UUID nachziehen
                    try (PreparedStatement up = c.prepareStatement(
                            "UPDATE whitelist_cache SET uuid = ? WHERE LOWER(name) = LOWER(?)")) {
                        up.setString(1, uuidDashed);
                        up.setString(2, name);
                        up.executeUpdate();
                    }
                    return true;
                }
            }
        }

        return false;
    }

    public void upsert(UUID uuid, String name) throws SQLException {
        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT OR REPLACE INTO whitelist_cache (uuid, name) VALUES (?, ?)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.executeUpdate();
        }
    }

    public void upsert(String uuid, String name) throws SQLException {
        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT OR REPLACE INTO whitelist_cache (uuid, name) VALUES (?, ?)")) {
            ps.setString(1, uuid);
            ps.setString(2, name);
            ps.executeUpdate();
        }
    }

    public int deleteByUUID(UUID uuid) throws SQLException {
        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM whitelist_cache WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            return ps.executeUpdate();
        }
    }

    public int deleteByName(String name) throws SQLException {
        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM whitelist_cache WHERE LOWER(name) = LOWER(?)")) {
            ps.setString(1, name);
            return ps.executeUpdate();
        }
    }

    public List<String> listWhitelistedNames() throws SQLException {
        List<String> out = new ArrayList<>();

        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT DISTINCT name
                     FROM whitelist_cache
                     WHERE name IS NOT NULL AND name <> ''
                     ORDER BY name COLLATE NOCASE
                     """);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                out.add(rs.getString("name"));
            }
        }

        return out;
    }

    public record WhitelistEntry(String uuid, String name) {}
}