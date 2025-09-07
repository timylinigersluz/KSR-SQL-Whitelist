package ch.ksrminecraft.kSRSQLWhitelist.utils;

import ch.ksrminecraft.kSRSQLWhitelist.KSRSQLWhitelist;
import org.bukkit.entity.Player;

import java.sql.*;
import java.util.UUID;

public class WhitelistService {
    private final KSRSQLWhitelist plugin;
    private final Database db;

    public WhitelistService(KSRSQLWhitelist plugin, Database db) {
        this.plugin = plugin;
        this.db = db;
    }

    /** Prüft, ob Spieler whitelisted ist und synchronisiert ggf. Name/UUID. */
    public boolean isWhitelisted(UUID uuid, String name) throws SQLException {
        final String selectByUUID =
                "SELECT 1 FROM `" + db.table() + "` WHERE `UUID` = ? LIMIT 1";
        final String selectByNameNoUUID =
                "SELECT 1 FROM `" + db.table() + "` WHERE `user` = ? AND (`UUID` IS NULL OR `UUID` = '') LIMIT 1";
        final String updateSetName =
                "UPDATE `" + db.table() + "` SET `user` = ? WHERE `UUID` = ?";
        final String updateAttachUUID =
                "UPDATE `" + db.table() + "` SET `UUID` = ? WHERE `user` = ? AND (`UUID` IS NULL OR `UUID` = '')";

        try (Connection c = db.openConnection()) {
            // 1) UUID vorhanden?
            try (PreparedStatement ps = c.prepareStatement(selectByUUID)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        try (PreparedStatement up = c.prepareStatement(updateSetName)) {
                            up.setString(1, name);
                            up.setString(2, uuid.toString());
                            up.executeUpdate();
                        }
                        return true;
                    }
                }
            }
            // 2) Namenseintrag ohne UUID → UUID nachtragen
            try (PreparedStatement ps2 = c.prepareStatement(selectByNameNoUUID)) {
                ps2.setString(1, name);
                try (ResultSet rs2 = ps2.executeQuery()) {
                    if (rs2.next()) {
                        try (PreparedStatement up2 = c.prepareStatement(updateAttachUUID)) {
                            up2.setString(1, uuid.toString());
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

    /** Online-Spieler mit UUID+Name eintragen/aktualisieren. */
    public void addOrUpdateOnline(Player online) throws SQLException {
        addOrUpdateWhitelist(online.getUniqueId(), online.getName());
    }

    /** Offline-Namen vormerken, UUID wird beim ersten Login ergänzt. */
    public void addOfflineName(String name) throws SQLException {
        final String exists =
                "SELECT 1 FROM `" + db.table() + "` WHERE `user` = ? AND (`UUID` IS NULL OR `UUID` = '') LIMIT 1";
        final String insert =
                "INSERT INTO `" + db.table() + "` (`user`) VALUES (?)";
        try (Connection c = db.openConnection();
             PreparedStatement sel = c.prepareStatement(exists)) {
            sel.setString(1, name);
            try (ResultSet rs = sel.executeQuery()) {
                if (!rs.next()) {
                    try (PreparedStatement ins = c.prepareStatement(insert)) {
                        ins.setString(1, name);
                        ins.executeUpdate();
                    }
                }
            }
        }
    }

    /** Spieler per UUID löschen. Gibt die Anzahl betroffener Zeilen zurück. */
    public int deleteByUUID(UUID uuid) throws SQLException {
        final String del = "DELETE FROM `" + db.table() + "` WHERE `UUID` = ?";
        try (Connection c = db.openConnection();
             PreparedStatement ps = c.prepareStatement(del)) {
            ps.setString(1, uuid.toString());
            return ps.executeUpdate();
        }
    }

    /** Spieler per Name löschen. Gibt die Anzahl betroffener Zeilen zurück. */
    public int deleteByName(String name) throws SQLException {
        final String del = "DELETE FROM `" + db.table() + "` WHERE `user` = ?";
        try (Connection c = db.openConnection();
             PreparedStatement ps = c.prepareStatement(del)) {
            ps.setString(1, name);
            return ps.executeUpdate();
        }
    }

    /** Upsert für UUID+Name. Nutzt ON DUPLICATE KEY, fallback ohne UNIQUE-Constraint. */
    private void addOrUpdateWhitelist(UUID uuid, String name) throws SQLException {
        final String upsert =
                "INSERT INTO `" + db.table() + "` (`UUID`, `user`) VALUES (?, ?) " +
                        "ON DUPLICATE KEY UPDATE `user` = VALUES(`user`)";
        try (Connection c = db.openConnection();
             PreparedStatement ps = c.prepareStatement(upsert)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.executeUpdate();
        } catch (SQLSyntaxErrorException e) {
            // Fallback: ohne UNIQUE KEY auf UUID
            final String select =
                    "SELECT 1 FROM `" + db.table() + "` WHERE `UUID` = ? LIMIT 1";
            try (Connection c2 = db.openConnection();
                 PreparedStatement sel = c2.prepareStatement(select)) {
                sel.setString(1, uuid.toString());
                try (ResultSet rs = sel.executeQuery()) {
                    if (rs.next()) {
                        final String update =
                                "UPDATE `" + db.table() + "` SET `user` = ? WHERE `UUID` = ?";
                        try (PreparedStatement up = c2.prepareStatement(update)) {
                            up.setString(1, name);
                            up.setString(2, uuid.toString());
                            up.executeUpdate();
                        }
                    } else {
                        final String insert =
                                "INSERT INTO `" + db.table() + "` (`UUID`, `user`) VALUES (?, ?)";
                        try (PreparedStatement ins = c2.prepareStatement(insert)) {
                            ins.setString(1, uuid.toString());
                            ins.setString(2, name);
                            ins.executeUpdate();
                        }
                    }
                }
            }
        }
    }
    // Liste der whitelisted Namen (alle Einträge; ohne Duplikate)
    public java.util.List<String> listWhitelistedNames() throws SQLException {
        final String sql = "SELECT DISTINCT `user` FROM `" + db.table() + "` WHERE `user` IS NOT NULL AND `user` <> ''";
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
