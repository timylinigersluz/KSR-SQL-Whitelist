package ch.ksrminecraft.kSRSQLWhitelist;

import ch.ksrminecraft.kSRSQLWhitelist.listeners.PreLoginListener;
import ch.ksrminecraft.kSRSQLWhitelist.listeners.WhitelistCommandInterceptor;
import ch.ksrminecraft.kSRSQLWhitelist.utils.Database;
import ch.ksrminecraft.kSRSQLWhitelist.utils.WhitelistService;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public class KSRSQLWhitelist extends JavaPlugin {

    private Database database;
    private WhitelistService whitelistService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        database = new Database(this);
        whitelistService = new WhitelistService(this, database);

        try {
            database.ensureTable();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to ensure DB table", e);
        }

        getServer().getPluginManager().registerEvents(new PreLoginListener(this, whitelistService), this);
        getServer().getPluginManager().registerEvents(new WhitelistCommandInterceptor(this, whitelistService), this);

        getLogger().info(getDescription().getName() + " v" + getDescription().getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info(getDescription().getName() + " v" + getDescription().getVersion() + " disabled.");
    }

    public WhitelistService getWhitelistService() {
        return whitelistService;
    }
}
