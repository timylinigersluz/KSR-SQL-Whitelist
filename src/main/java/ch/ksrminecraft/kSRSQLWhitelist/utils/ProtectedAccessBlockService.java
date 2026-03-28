package ch.ksrminecraft.kSRSQLWhitelist.utils;

import ch.ksrminecraft.kSRSQLWhitelist.KSRSQLWhitelist;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Verwaltet kurzfristige clusterweite Zugriffssperren nach einem unerlaubten
 * Betreten geschützter Welten.
 *
 * Zweck:
 * - Ein LiteBans-Kick dokumentiert den Vorfall
 * - Diese Tabelle verhindert zusätzlich direkte Rejoins auf andere Backends
 *
 * Speicherung:
 * - UUID des Spielers
 * - Spielername
 * - Grund
 * - Ablaufzeitpunkt als Unix-Millis
 */
public class ProtectedAccessBlockService {

    private final KSRSQLWhitelist plugin;
    private final Database database;

    public ProtectedAccessBlockService(KSRSQLWhitelist plugin, Database database) {
        this.plugin = plugin;
        this.database = database;
    }

    public void ensureTable() throws SQLException {
        String table = tableName();

        String sql = "CREATE TABLE IF NOT EXISTS `" + table + "` ("
                + "`uuid` varchar(36) NOT NULL,"
                + "`player_name` varchar(100) DEFAULT NULL,"
                + "`reason` varchar(255) DEFAULT NULL,"
                + "`blocked_until` bigint NOT NULL,"
                + "PRIMARY KEY (`uuid`),"
                + "KEY `idx_blocked_until` (`blocked_until`)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;";

        try (Connection c = database.openConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.execute();
        }
    }

    public void upsertBlock(UUID uuid, String playerName, String reason, long blockedUntil) throws SQLException {
        String table = tableName();

        String sql = "INSERT INTO `" + table + "` (`uuid`, `player_name`, `reason`, `blocked_until`) "
                + "VALUES (?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE "
                + "`player_name` = VALUES(`player_name`), "
                + "`reason` = VALUES(`reason`), "
                + "`blocked_until` = VALUES(`blocked_until`)";

        try (Connection c = database.openConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, playerName);
            ps.setString(3, reason);
            ps.setLong(4, blockedUntil);
            ps.executeUpdate();
        }
    }

    public boolean isBlocked(UUID uuid) throws SQLException {
        String table = tableName();
        long now = System.currentTimeMillis();

        String sql = "SELECT `blocked_until` FROM `" + table + "` WHERE `uuid` = ? LIMIT 1";

        try (Connection c = database.openConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return false;
                }

                long blockedUntil = rs.getLong("blocked_until");

                if (blockedUntil > now) {
                    return true;
                }
            }
        }

        return false;
    }

    public void purgeExpired() {
        String table = tableName();
        long now = System.currentTimeMillis();

        String sql = "DELETE FROM `" + table + "` WHERE `blocked_until` <= ?";

        try (Connection c = database.openConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, now);
            ps.executeUpdate();
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING,
                    "Failed to purge expired protected-world blocks.", ex);
        }
    }

    private String tableName() {
        return plugin.getConfig().getString(
                "protected-worlds.block-table",
                "ksr_protected_world_blocks"
        );
    }
}