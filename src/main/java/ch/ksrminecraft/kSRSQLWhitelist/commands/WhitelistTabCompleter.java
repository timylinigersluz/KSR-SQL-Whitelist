package ch.ksrminecraft.kSRSQLWhitelist.commands;

import ch.ksrminecraft.kSRSQLWhitelist.KSRSQLWhitelist;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.sql.SQLException;
import java.util.*;

/**
 * ----------------------------------------------------------------------------
 *  🧩 WhitelistTabCompleter
 *  ------------------------
 *  Bietet intelligente Tab-Vervollständigung für den /whitelist-Befehl an.
 *  Synchronisiert Namen dynamisch mit der MySQL-Datenbank.
 *
 *  Unterstützte Befehle:
 *   - Subcommands: add, remove, del, rm, list, on, off, reload, info
 *   - Vorschläge:
 *       • Für remove/del/rm → Spieler aus der Whitelist-Datenbank
 *       • Für info → Whitelist-Spieler + aktuell Online-Spieler
 *
 *  Autor: Timy Liniger (KSR Minecraft)
 *  Projekt: KSR-SQL-Whitelist
 * ----------------------------------------------------------------------------
 */
public class WhitelistTabCompleter implements TabCompleter {

    private final KSRSQLWhitelist plugin;

    public WhitelistTabCompleter(KSRSQLWhitelist plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {

        // Nur für /whitelist relevant
        if (!command.getName().equalsIgnoreCase("whitelist")) return null;

        // --------------------------------------------------------------
        // /whitelist <subcommand>
        // --------------------------------------------------------------
        if (args.length == 1) {
            List<String> subs = List.of("add", "remove", "rm", "del", "on", "off", "list", "reload", "info");
            return subs.stream()
                    .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }

        // --------------------------------------------------------------
        // /whitelist <sub> <name>
        // --------------------------------------------------------------
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);

            // Spieler-Vorschläge für remove/rm/del
            if (sub.equals("remove") || sub.equals("rm") || sub.equals("del")) {
                try {
                    var names = plugin.getWhitelistService().listWhitelistedNames();
                    return names.stream()
                            .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                            .toList();
                } catch (SQLException e) {
                    plugin.getLogger().warning("[KSR-SQL-Whitelist] Could not fetch whitelist for tab completion: " + e.getMessage());
                    return Collections.emptyList();
                }
            }

            // Spieler-Vorschläge für info (Whitelist + Online-Spieler)
            if (sub.equals("info")) {
                Set<String> suggestions = new HashSet<>();
                try {
                    suggestions.addAll(plugin.getWhitelistService().listWhitelistedNames());
                } catch (SQLException e) {
                    plugin.getLogger().warning("[KSR-SQL-Whitelist] Could not fetch whitelist for tab completion: " + e.getMessage());
                }

                // Online-Spieler hinzufügen
                Bukkit.getOnlinePlayers().forEach(p -> suggestions.add(p.getName()));

                return suggestions.stream()
                        .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .toList();
            }
        }

        // Kein weiterer Vorschlag
        return Collections.emptyList();
    }
}
