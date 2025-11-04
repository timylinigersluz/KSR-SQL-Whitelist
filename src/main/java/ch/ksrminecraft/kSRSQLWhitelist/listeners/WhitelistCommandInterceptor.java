package ch.ksrminecraft.kSRSQLWhitelist.listeners;

import ch.ksrminecraft.kSRSQLWhitelist.KSRSQLWhitelist;
import ch.ksrminecraft.kSRSQLWhitelist.utils.PlayerInfoService;
import ch.ksrminecraft.kSRSQLWhitelist.utils.WhitelistService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ----------------------------------------------------------------------------
 *  🎮 WhitelistCommandInterceptor
 *  -------------------------------
 *  Ersetzt die Vanilla-/whitelist-Kommandos durch SQL-gestützte Logik.
 *
 *  Unterstützte Subcommands:
 *   - add, remove, on, off, list, reload, info
 *
 *  Erweiterung:
 *   - /whitelist info <Spieler> → zeigt Mojang-/Skin-Infos + Online-Status
 *
 *  Autor: Timy Liniger (KSR Minecraft)
 *  Projekt: KSR-SQL-Whitelist
 * ----------------------------------------------------------------------------
 */
public class WhitelistCommandInterceptor implements Listener {

    private final KSRSQLWhitelist plugin;
    private final WhitelistService service;
    private final PlayerInfoService infoService;

    public WhitelistCommandInterceptor(KSRSQLWhitelist plugin, WhitelistService service) {
        this.plugin = plugin;
        this.service = service;
        this.infoService = new PlayerInfoService(plugin);
    }

    // ------------------------------------------------------------------------
    // 🎯 Spielerbefehle (/whitelist ...)
    // ------------------------------------------------------------------------
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent e) {
        String raw = e.getMessage().trim();
        if (!raw.toLowerCase(Locale.ROOT).startsWith("/whitelist")) return;

        String[] parts = raw.substring(1).split("\\s+");
        if (parts.length < 2) return;

        String sub = parts[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "add":
            case "remove":
            case "rm":
            case "del":
            case "on":
            case "off":
            case "list":
            case "reload":
            case "info":
                e.setCancelled(true);
                dispatch(e.getPlayer(), parts);
                break;
        }
    }

    // ------------------------------------------------------------------------
    // 🖥️ Konsolen-/RCON-Befehle
    // ------------------------------------------------------------------------
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent e) {
        String raw = e.getCommand().trim();
        if (!raw.toLowerCase(Locale.ROOT).startsWith("whitelist")) return;

        String[] parts = raw.split("\\s+");
        if (parts.length < 2) return;

        String sub = parts[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "add":
            case "remove":
            case "rm":
            case "del":
            case "on":
            case "off":
            case "list":
            case "reload":
            case "info":
                e.setCancelled(true);
                dispatch(e.getSender(), parts);
                break;
        }
    }

    // ------------------------------------------------------------------------
    // 🧠 Zentrale Dispatch-Logik
    // ------------------------------------------------------------------------
    private void dispatch(CommandSender sender, String[] parts) {
        String sub = parts[1].toLowerCase(Locale.ROOT);

        switch (sub) {

            // --------------------------------------------------------------
            // /whitelist add <player>
            // --------------------------------------------------------------
            case "add": {
                if (!has(sender, "KSRSQLWhitelist.add")) return;
                if (parts.length < 3) { usage(sender, "whitelist add <player>"); return; }

                String target = parts[2];
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {
                        Player online = plugin.getServer().getPlayerExact(target);
                        if (online != null) {
                            service.addOrUpdateOnline(online);
                            sender.sendMessage(ChatColor.GREEN + online.getName() + " is now whitelisted!");
                            online.sendMessage(ChatColor.GREEN + "You have been whitelisted!");
                        } else {
                            service.addOfflineName(target);
                            sender.sendMessage(ChatColor.GREEN + target + " is now whitelisted and verified via Mojang!");
                        }
                    } catch (Exception ex) {
                        sender.sendMessage(ChatColor.RED + "Error while whitelisting player. Check console log.");
                        ex.printStackTrace();
                    }
                });
                break;
            }

            // --------------------------------------------------------------
            // /whitelist remove|rm|del <player>
            // --------------------------------------------------------------
            case "remove":
            case "rm":
            case "del": {
                if (!has(sender, "KSRSQLWhitelist.del")) return;
                if (parts.length < 3) { usage(sender, "whitelist remove <player>"); return; }

                String target = parts[2];
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {
                        int affected = 0;
                        Player online = plugin.getServer().getPlayerExact(target);
                        if (online != null) affected = service.deleteByUUID(online.getUniqueId());
                        if (affected == 0) affected = service.deleteByName(target);

                        if (affected > 0) {
                            sender.sendMessage(ChatColor.RED + target + " is no longer whitelisted!");
                            if (online != null) {
                                Bukkit.getScheduler().runTask(plugin,
                                        () -> online.kickPlayer("You have been removed from our whitelist"));
                            }
                        } else {
                            sender.sendMessage(ChatColor.YELLOW + "No whitelist entry found for " + target + ".");
                        }
                    } catch (Exception ex) {
                        sender.sendMessage(ChatColor.RED + "Error while deleting player. Check console log.");
                        ex.printStackTrace();
                    }
                });
                break;
            }

            // --------------------------------------------------------------
            // /whitelist on|off|list|reload
            // --------------------------------------------------------------
            case "on":
                if (has(sender, "KSRSQLWhitelist.on")) {
                    plugin.getConfig().set("enabled", true);
                    plugin.saveConfig();
                    sender.sendMessage(ChatColor.GREEN + "Whitelist enabled.");
                }
                break;

            case "off":
                if (has(sender, "KSRSQLWhitelist.off")) {
                    plugin.getConfig().set("enabled", false);
                    plugin.saveConfig();
                    sender.sendMessage(ChatColor.YELLOW + "Whitelist disabled.");
                }
                break;

            case "list":
                if (!has(sender, "minecraft.command.whitelist")) return;
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {
                        var names = service.listWhitelistedNames();
                        AtomicInteger onlineCount = new AtomicInteger(0);
                        names.forEach(name -> {
                            Player p = plugin.getServer().getPlayerExact(name);
                            if (p != null && p.isOnline()) onlineCount.incrementAndGet();
                        });
                        sender.sendMessage(ChatColor.GRAY + "There are " + onlineCount.get() +
                                " (of " + names.size() + ") whitelisted players online:");
                        if (names.isEmpty()) {
                            sender.sendMessage(ChatColor.GRAY + "[]");
                        } else {
                            sender.sendMessage(ChatColor.WHITE + String.join(", ", names));
                        }
                    } catch (Exception ex) {
                        sender.sendMessage(ChatColor.RED + "Error while listing whitelist. Check console log.");
                        ex.printStackTrace();
                    }
                });
                break;

            case "reload":
                if (has(sender, "minecraft.command.whitelist")) {
                    plugin.reloadConfig();
                    sender.sendMessage(ChatColor.GREEN + "Whitelist configuration reloaded.");
                }
                break;

            // --------------------------------------------------------------
            // /whitelist info <player>
            // --------------------------------------------------------------
            case "info": {
                if (!has(sender, "minecraft.command.whitelist")) return;
                if (parts.length < 3) { usage(sender, "whitelist info <player>"); return; }
                String target = parts[2];

                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {
                        // 1️⃣ Whitelist-Status prüfen (lokale DB)
                        String uuid = null;
                        boolean whitelisted = false;
                        try {
                            String colUUID = service.getDatabase().columnUUID();
                            String colName = service.getDatabase().columnName();
                            String table = service.getDatabase().table();

                            String sql = "SELECT `" + colUUID + "`, `" + colName + "` " +
                                    "FROM `" + table + "` WHERE `" + colName + "` = ? LIMIT 1";

                            try (Connection c = service.getDatabase().openConnection();
                                 PreparedStatement ps = c.prepareStatement(sql)) {
                                ps.setString(1, target);
                                try (ResultSet rs = ps.executeQuery()) {
                                    if (rs.next()) {
                                        uuid = rs.getString(colUUID);
                                        whitelisted = true;
                                    }
                                }
                            }
                        } catch (Exception ignored) {}

                        // 2️⃣ Öffentliche Mojang-/Ashcon-Daten
                        var info = infoService.fetchInfo(target);
                        if (info == null) {
                            sender.sendMessage(ChatColor.RED + "Player '" + target + "' not found via Mojang API.");
                            return;
                        }

                        // 3️⃣ Online-Status prüfen
                        boolean online = Bukkit.getPlayerExact(info.name) != null &&
                                Bukkit.getPlayerExact(info.name).isOnline();

                        // 4️⃣ Ausgabe formatieren
                        sender.sendMessage(ChatColor.GRAY + "------ Player Info ------");
                        sender.sendMessage(ChatColor.YELLOW + "Name: " + ChatColor.WHITE + info.name);
                        sender.sendMessage(ChatColor.YELLOW + "UUID: " + ChatColor.WHITE + info.uuid);
                        sender.sendMessage(ChatColor.YELLOW + "Mojang Account: " +
                                (info.verified ? ChatColor.GREEN + "✅ Verified" : ChatColor.RED + "❌ Unknown"));
                        sender.sendMessage(ChatColor.YELLOW + "Status: " +
                                (online ? ChatColor.GREEN + "🟢 Online" : ChatColor.RED + "🔴 Offline"));

                        if (info.createdAt != null && !info.createdAt.equals("—")) {
                            sender.sendMessage(ChatColor.YELLOW + "Created: " +
                                    ChatColor.WHITE + info.createdAt.replace("T", " ").replace("Z", ""));
                        }

                        if (info.history != null && info.history.size() > 1) {
                            String hist = String.join(" → ", info.history);
                            sender.sendMessage(ChatColor.YELLOW + "Name History: " + ChatColor.WHITE + hist);
                        }

                        sender.sendMessage(Component.text("Skin: ", NamedTextColor.YELLOW)
                                .append(info.getClickableSkinComponent()));

                        sender.sendMessage(ChatColor.YELLOW + "Whitelisted: " +
                                (whitelisted ? ChatColor.GREEN + "✅" : ChatColor.RED + "❌"));

                    } catch (Exception e) {
                        sender.sendMessage(ChatColor.RED + "Error while fetching info for " + target + ".");
                        e.printStackTrace();
                    }
                });
                break;
            }

            default:
                usage(sender, "whitelist <add|remove|on|off|list|reload|info> ...");
        }
    }

    // ------------------------------------------------------------------------
    // 🔒 Berechtigungsprüfung
    // ------------------------------------------------------------------------
    private boolean has(CommandSender s, String perm) {
        return s.hasPermission(perm)
                || s.hasPermission("KSRSQLWhitelist.*")
                || s.hasPermission("minecraft.command.whitelist")
                || s.isOp();
    }

    // ------------------------------------------------------------------------
    // 📘 Usage-Hilfe
    // ------------------------------------------------------------------------
    private void usage(CommandSender sender, String u) {
        sender.sendMessage(ChatColor.RED + "Usage: " + ChatColor.WHITE + "/" + u);
    }
}
