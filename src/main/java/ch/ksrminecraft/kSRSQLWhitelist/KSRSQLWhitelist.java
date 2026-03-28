package ch.ksrminecraft.kSRSQLWhitelist;

import ch.ksrminecraft.kSRSQLWhitelist.listeners.PreLoginListener;
import ch.ksrminecraft.kSRSQLWhitelist.listeners.WhitelistCommandInterceptor;
import ch.ksrminecraft.kSRSQLWhitelist.listeners.WorldAccessListener;
import ch.ksrminecraft.kSRSQLWhitelist.utils.Database;
import ch.ksrminecraft.kSRSQLWhitelist.utils.LocalFallbackDatabase;
import ch.ksrminecraft.kSRSQLWhitelist.utils.ProtectedAccessBlockService;
import ch.ksrminecraft.kSRSQLWhitelist.utils.WhitelistService;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

public class KSRSQLWhitelist extends JavaPlugin {

    private Database database;
    private LocalFallbackDatabase localFallbackDatabase;
    private WhitelistService whitelistService;
    private ProtectedAccessBlockService protectedAccessBlockService;

    private final AtomicBoolean mysqlUnavailable = new AtomicBoolean(false);

    @Override
    public void onEnable() {
        saveDefaultConfig();

        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        database = new Database(this);
        localFallbackDatabase = new LocalFallbackDatabase(this);
        whitelistService = new WhitelistService(this, database, localFallbackDatabase);
        protectedAccessBlockService = new ProtectedAccessBlockService(this, database);

        try {
            localFallbackDatabase.ensureTable();
            getLogger().info("Local fallback whitelist database ready.");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to initialize local fallback database", e);
        }

        try {
            database.ensureTable();
            protectedAccessBlockService.ensureTable();
            protectedAccessBlockService.purgeExpired();
            handleMysqlRecovery();
        } catch (Exception e) {
            getLogger().log(Level.WARNING,
                    "MySQL whitelist table could not be reached during startup. Local fallback cache will be used.",
                    e);
            mysqlUnavailable.set(true);
        }

        if (getConfig().getBoolean("fallback.sync-on-startup", true)) {
            try {
                whitelistService.syncMysqlToLocalFallback();
                handleMysqlRecovery();
            } catch (Exception e) {
                getLogger().log(Level.WARNING,
                        "Startup sync MySQL -> local fallback failed. Existing local cache will still be used.",
                        e);
                mysqlUnavailable.set(true);
            }
        }

        startFallbackResyncTask();

        getServer().getPluginManager().registerEvents(new PreLoginListener(this, whitelistService), this);
        getServer().getPluginManager().registerEvents(new WhitelistCommandInterceptor(this, whitelistService), this);
        getServer().getPluginManager().registerEvents(new WorldAccessListener(this), this);

        if (getCommand("whitelist") != null) {
            getCommand("whitelist").setTabCompleter(
                    new ch.ksrminecraft.kSRSQLWhitelist.commands.WhitelistTabCompleter(this)
            );
        }

        getLogger().info(getDescription().getName() + " v" + getDescription().getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info(getDescription().getName() + " v" + getDescription().getVersion() + " disabled.");
    }

    public WhitelistService getWhitelistService() {
        return whitelistService;
    }

    public ProtectedAccessBlockService getProtectedAccessBlockService() {
        return protectedAccessBlockService;
    }

    private void startFallbackResyncTask() {
        boolean fallbackEnabled = getConfig().getBoolean("fallback.enabled", true);
        boolean resyncEnabled = getConfig().getBoolean("fallback.resync.enabled", true);
        int intervalHours = Math.max(1, getConfig().getInt("fallback.resync.interval-hours", 24));

        if (!fallbackEnabled || !resyncEnabled) {
            getLogger().info("Fallback whitelist resync task is disabled.");
            return;
        }

        long intervalTicks = intervalHours * 60L * 60L * 20L;

        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            try {
                whitelistService.syncMysqlToLocalFallback();
                protectedAccessBlockService.purgeExpired();
                handleMysqlRecovery();
                getLogger().info("Scheduled fallback whitelist resync completed successfully.");
            } catch (Exception e) {
                handleMysqlFailure("Scheduled fallback whitelist resync failed. Keeping existing local cache.", e);
            }
        }, intervalTicks, intervalTicks);

        getLogger().info("Started fallback whitelist resync task (every " + intervalHours + "h / " + intervalTicks + " ticks).");
    }

    public void handleMysqlFailure(String message, Exception exception) {
        if (mysqlUnavailable.compareAndSet(false, true)) {
            getLogger().log(Level.WARNING, message, exception);
        } else {
            getLogger().info("MySQL still unavailable, using local whitelist fallback.");
        }
    }

    public void handleMysqlRecovery() {
        if (mysqlUnavailable.compareAndSet(true, false)) {
            getLogger().info("MySQL connection restored.");
        }
    }
}