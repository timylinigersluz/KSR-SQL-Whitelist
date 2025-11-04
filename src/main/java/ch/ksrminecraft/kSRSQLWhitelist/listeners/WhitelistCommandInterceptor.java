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

/**
 * ----------------------------------------------------------------------------
 *  🎮 WhitelistCommandInterceptor
 *  -------------------------------
 *  Listener, der alle Vanilla-Whitelist-Befehle abfängt und stattdessen die
 *  SQL-basierte Whitelist-Verwaltung verwendet.
 *
 *  Hauptaufgaben:
 *  - Fängt alle relevanten Subcommands von /whitelist ab
 *    (add, remove, on, off, list, reload)
 *  - Führt die entsprechenden Operationen über {@link WhitelistService} aus
 *  - Unterstützt sowohl Spieler- als auch Konsolenbefehle
 *  - Arbeitet asynchron, um keine Verzögerung im Hauptthread zu verursachen
 *
 *  ⚙️ Beispiele:
 *   /whitelist add <Spieler>
 *   /whitelist remove <Spieler>
 *   /whitelist on|off|list|reload
 *
 *  Autor: Timy Liniger (KSR Minecraft)
 *  Projekt: KSR-SQL-Whitelist
 * ----------------------------------------------------------------------------
 */
public class WhitelistCommandInterceptor implements Listener {

    /** Haupt-Plugin-Instanz (für Config, Logging, Scheduler). */
    private final KSRSQLWhitelist plugin;

    /** Whitelist-Dienst, der Datenbankzugriffe kapselt. */
    private final WhitelistService service;

    /**
     * Konstruktor zur Initialisierung des Interceptors.
     *
     * @param plugin  Hauptinstanz des Plugins
     * @param service Whitelist-Service für DB-Operationen
     */
    public WhitelistCommandInterceptor(KSRSQLWhitelist plugin, WhitelistService service) {
        this.plugin = plugin;
        this.service = service;
    }

    // ------------------------------------------------------------------------
    // 🎯 1. Spielerbefehle (z. B. /whitelist add ...)
    // ------------------------------------------------------------------------

    /**
     * Fängt alle Spielerbefehle ab, die mit /whitelist beginnen,
     * und leitet die erlaubten Subcommands intern um.
     *
     * @param e PlayerCommandPreprocessEvent (wird vor Ausführung des Befehls ausgelöst)
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent e) {
        String raw = e.getMessage().trim();
        if (!raw.toLowerCase(Locale.ROOT).startsWith("/whitelist")) return;

        // Befehl in Argumente aufteilen (ohne führenden Slash)
        String[] parts = raw.substring(1).split("\\s+");
        if (parts.length < 2) return;

        String sub = parts[1].toLowerCase(Locale.ROOT);

        // Nur bekannte Subcommands abfangen
        switch (sub) {
            case "add":
            case "remove":
            case "rm":
            case "del":
            case "on":
            case "off":
            case "list":
            case "reload":
                e.setCancelled(true); // Vanilla-Verhalten unterdrücken
                dispatch(e.getPlayer(), parts);
                break;
            default:
                // Unbekannte Subcommands (z. B. /whitelist help) ignorieren
        }
    }

    // ------------------------------------------------------------------------
    // 🖥️ 2. Konsolen- oder RCON-Befehle (z. B. whitelist add ...)
    // ------------------------------------------------------------------------

    /**
     * Interzeptiert Whitelist-Befehle aus der Serverkonsole oder RCON.
     *
     * @param e ServerCommandEvent (kein führender Slash)
     */
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
                // Unbekannte Subcommands ignorieren
        }
    }

    // ------------------------------------------------------------------------
    // 🧠 3. Zentrale Dispatch-Logik
    // ------------------------------------------------------------------------

    /**
     * Führt den passenden SQL-basierten Whitelist-Befehl aus.
     *
     * @param sender Quelle des Befehls (Spieler oder Konsole)
     * @param parts  Argumente des Befehls
     */
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

                        // Wenn Spieler online → sofort mit UUID speichern
                        if (online != null) {
                            service.addOrUpdateOnline(online);
                            sender.sendMessage(ChatColor.GREEN + online.getName() + " is now whitelisted!");
                            online.sendMessage(ChatColor.GREEN + "You have been whitelisted!");
                        } else {
                            // Offline-Spieler → nur Name speichern (UUID wird beim Login ergänzt)
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

                        // Versuch, nach UUID zu löschen, falls Spieler online
                        if (online != null) affected = service.deleteByUUID(online.getUniqueId());
                        if (affected == 0) affected = service.deleteByName(target);

                        if (affected > 0) {
                            sender.sendMessage(ChatColor.RED + target + " is no longer whitelisted!");
                            // Wenn Spieler online → vom Server kicken
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
            // /whitelist on
            // --------------------------------------------------------------
            case "on": {
                if (!has(sender, "KSRSQLWhitelist.on")) return;
                plugin.getConfig().set("enabled", true);
                plugin.saveConfig();
                sender.sendMessage(ChatColor.GREEN + "Whitelist enabled.");
                break;
            }

            // --------------------------------------------------------------
            // /whitelist off
            // --------------------------------------------------------------
            case "off": {
                if (!has(sender, "KSRSQLWhitelist.off")) return;
                plugin.getConfig().set("enabled", false);
                plugin.saveConfig();
                sender.sendMessage(ChatColor.YELLOW + "Whitelist disabled.");
                break;
            }

            // --------------------------------------------------------------
            // /whitelist list
            // --------------------------------------------------------------
            case "list": {
                if (!has(sender, "minecraft.command.whitelist")) return; // Vanilla-Permission reicht
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {
                        var names = service.listWhitelistedNames();
                        AtomicInteger onlineCount = new AtomicInteger(0);

                        // Online-Spieler zählen
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
            }

            // --------------------------------------------------------------
            // /whitelist reload
            // --------------------------------------------------------------
            case "reload": {
                if (!has(sender, "minecraft.command.whitelist")) return;
                plugin.reloadConfig(); // neue DB-Settings greifen bei nächster Verbindung
                sender.sendMessage(ChatColor.GREEN + "Whitelist configuration reloaded.");
                break;
            }

            // --------------------------------------------------------------
            // Fallback: Unbekannte oder unvollständige Eingabe
            // --------------------------------------------------------------
            default:
                usage(sender, "whitelist <add|remove|on|off|list|reload> ...");
        }
    }

    // ------------------------------------------------------------------------
    // 🔒 4. Berechtigungsprüfung
    // ------------------------------------------------------------------------

    /**
     * Prüft, ob der Befehlssender die nötige Berechtigung besitzt.
     * Akzeptiert sowohl Plugin-eigene als auch Vanilla-Whitelist-Permissions.
     *
     * @param s    Sender (Spieler oder Konsole)
     * @param perm Plugin-spezifische Permission (z. B. KSRSQLWhitelist.add)
     * @return true, wenn erlaubt
     */
    private boolean has(CommandSender s, String perm) {
        return s.hasPermission(perm)
                || s.hasPermission("KSRSQLWhitelist.*")
                || s.hasPermission("minecraft.command.whitelist")
                || s.isOp();
    }

    // ------------------------------------------------------------------------
    // 📘 5. Hilfsmethode: Usage anzeigen
    // ------------------------------------------------------------------------

    /**
     * Gibt eine einheitliche Nutzungsanweisung für Befehle aus.
     *
     * @param sender Befehlssender
     * @param u      Beispielsyntax (ohne führenden Slash)
     */
    private void usage(CommandSender sender, String u) {
        sender.sendMessage(ChatColor.RED + "Usage: " + ChatColor.WHITE + "/" + u);
    }
}
