package ch.ksrminecraft.kSRSQLWhitelist.utils;

import ch.ksrminecraft.kSRSQLWhitelist.KSRSQLWhitelist;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;
import java.util.logging.Level;

public class Database {
    private final KSRSQLWhitelist plugin;

    public Database(KSRSQLWhitelist plugin) {
        this.plugin = plugin;
    }

    public Connection openConnection() throws SQLException {
        String host = req("mysql.host");
        int port = plugin.getConfig().getInt("mysql.port", 3306);
        String db   = req("mysql.database");
        String user = req("mysql.user");
        String pass = req("mysql.password");

        // SSL standardmäßig deaktivieren, außer in der Config explizit true
        boolean useSSL = plugin.getConfig().getBoolean("mysql.useSSL", false);
        String serverTimezone = plugin.getConfig().getString("mysql.serverTimezone", "UTC");

        String params = String.join("&",
                "useUnicode=true",
                "characterEncoding=UTF-8",
                "useSSL=" + useSSL,
                "serverTimezone=" + serverTimezone
        );

        String url = "jdbc:mysql://" + host + ":" + port + "/" + db + "?" + params;
        return DriverManager.getConnection(url, user, pass);
    }

    public String table() {
        return req("mysql.table");
    }

    public void ensureTable() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS `" + table() + "` ("
                + "`UUID` varchar(36) DEFAULT NULL,"
                + "`user` varchar(100) DEFAULT NULL,"
                + "KEY `idx_uuid` (`UUID`),"
                + "KEY `idx_user` (`user`)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;";
        try (Connection c = openConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.execute();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to ensure whitelist table", e);
            throw e;
        }
    }

    private String req(String path) {
        return Objects.requireNonNull(plugin.getConfig().getString(path), path);
    }
}
