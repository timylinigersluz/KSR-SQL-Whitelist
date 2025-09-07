// src/main/java/ch/ksrminecraft/kSRSQLWhitelist/listeners/WhitelistCommandInterceptor.java
package ch.ksrminecraft.kSRSQLWhitelist.listeners;

import ch.ksrminecraft.kSRSQLWhitelist.KSRSQLWhitelist;
import ch.ksrminecraft.kSRSQLWhitelist.utils.WhitelistService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

public class WhitelistCommandInterceptor implements Listener {
    private final KSRSQLWhitelist plugin;
    private final WhitelistService service;

    public WhitelistCommandInterceptor(KSRSQLWhitelist plugin, WhitelistService service) {
        this.plugin = plugin;
        this.service = service;
    }

    // Spielerbefehle (/whitelist ...)
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent e) {
        String raw = e.getMessage().trim();
        if (!raw.toLowerCase(Locale.ROOT).startsWith("/whitelist")) return;
        String[] parts = raw.substring(1).split("\\s+"); // ["whitelist", "sub", "args"...]
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
                e.setCancelled(true);
                dispatch(e.getPlayer(), parts);
                break;
            default:
                // andere Subcommands unangetastet lassen
        }
    }

    // Konsolenbefehle / RCON (ohne führenden Slash)
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
                e.setCancelled(true);
                dispatch(e.getSender(), parts);
                break;
            default:
        }
    }

    private void dispatch(CommandSender sender, String[] parts) {
        String sub = parts[1].toLowerCase(Locale.ROOT);
        switch (sub) {
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
                            sender.sendMessage(ChatColor.GREEN + target + " is now whitelisted (pending UUID).");
                        }
                    } catch (Exception ex) {
                        sender.sendMessage(ChatColor.RED + "Error while whitelisting player. Check console log.");
                        ex.printStackTrace();
                    }
                });
                break;
            }
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
            case "on": {
                if (!has(sender, "KSRSQLWhitelist.on")) return;
                plugin.getConfig().set("enabled", true);
                plugin.saveConfig();
                sender.sendMessage(ChatColor.GREEN + "Whitelist enabled.");
                break;
            }
            case "off": {
                if (!has(sender, "KSRSQLWhitelist.off")) return;
                plugin.getConfig().set("enabled", false);
                plugin.saveConfig();
                sender.sendMessage(ChatColor.YELLOW + "Whitelist disabled.");
                break;
            }
            case "list": {
                if (!has(sender, "minecraft.command.whitelist")) return; // Vanilla-Permission
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {
                        var names = service.listWhitelistedNames();
                        AtomicInteger onlineCount = new AtomicInteger(0);
                        names.forEach(name -> {
                            Player p = plugin.getServer().getPlayerExact(name);
                            if (p != null && p.isOnline()) onlineCount.incrementAndGet();
                        });
                        sender.sendMessage(ChatColor.GRAY + "There are " + onlineCount.get() + " (of " + names.size() + ") whitelisted players online:");
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
            }
            case "reload": {
                if (!has(sender, "minecraft.command.whitelist")) return;
                plugin.reloadConfig(); // neue DB-Settings greifen beim nächsten Connection-Open
                sender.sendMessage(ChatColor.GREEN + "Whitelist configuration reloaded.");
                break;
            }
            default:
                usage(sender, "whitelist <add|remove|on|off|list|reload> ...");
        }
    }

    private boolean has(CommandSender s, String perm) {
        // Erlaube sowohl deine Plugin-Permissions als auch die Vanilla-Permission
        return s.hasPermission(perm) || s.hasPermission("KSRSQLWhitelist.*") || s.hasPermission("minecraft.command.whitelist") || s.isOp();
    }

    private void usage(CommandSender sender, String u) {
        sender.sendMessage(ChatColor.RED + "Usage: " + ChatColor.WHITE + "/" + u);
    }
}
