package ch.ksrminecraft.kSRSQLWhitelist.listeners;

import ch.ksrminecraft.kSRSQLWhitelist.KSRSQLWhitelist;
import ch.ksrminecraft.kSRSQLWhitelist.utils.MessageUtil;
import com.destroystokyo.paper.profile.PlayerProfile;
import io.papermc.paper.event.player.AsyncPlayerSpawnLocationEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Sperrt definierte Welten für normale Spieler.
 *
 * Unterstützte Muster:
 * - "staffworld"  -> exakter Weltname
 * - "mm_*"        -> Prefix-Match
 * - "*_nether"    -> Suffix-Match
 * - "test*arena"  -> allgemeiner Wildcard-Match
 * - "*"           -> alle Welten
 *
 * Verhalten:
 * - mit fallback-world -> Spieler wird dorthin umgeleitet
 * - ohne fallback-world -> LiteBans-Kick + kurzfristige clusterweite Sperre
 */
public class WorldAccessListener implements Listener {

    private final KSRSQLWhitelist plugin;

    /**
     * Spieler, deren Spawn bereits als unzulässig erkannt wurde.
     * Wird nur verwendet, wenn keine fallback-world konfiguriert ist.
     */
    private final Set<UUID> flaggedOnSpawn = ConcurrentHashMap.newKeySet();

    /**
     * Originale geschützte Spawnpositionen, falls wir im Async-Spawn-Event
     * zuerst auf die fallback-world umleiten mussten.
     *
     * Grund:
     * AsyncPlayerSpawnLocationEvent hat bewusst keinen normalen Player.
     * Permissions wie whitelist.staff können daher erst im Join-Event
     * zuverlässig geprüft werden.
     */
    private final Map<UUID, Location> redirectedProtectedSpawnTargets = new ConcurrentHashMap<>();

    /**
     * Schutz gegen doppelte Verarbeitung innerhalb desselben sehr kurzen Zeitfensters.
     */
    private final Set<UUID> handlingInProgress = ConcurrentHashMap.newKeySet();

    public WorldAccessListener(KSRSQLWhitelist plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAsyncSpawnLocation(AsyncPlayerSpawnLocationEvent event) {
        if (!isProtectionEnabled()) {
            return;
        }

        Location spawn = event.getSpawnLocation();
        if (spawn == null || spawn.getWorld() == null) {
            return;
        }

        String targetWorldName = spawn.getWorld().getName();
        if (!isProtectedWorld(targetWorldName)) {
            return;
        }

        PlayerProfile profile = event.getConnection().getProfile();
        UUID uuid = profile.getId();
        String playerName = profile.getName() != null ? profile.getName() : "unknown";

        Location fallback = getFallbackLocation();

        if (fallback != null) {
            event.setSpawnLocation(fallback);

            if (uuid != null) {
                redirectedProtectedSpawnTargets.put(uuid, spawn.clone());
            }

            plugin.getLogger().info("Protected-world redirect on async spawn for "
                    + playerName + " -> " + fallback.getWorld().getName());
        } else {
            if (uuid != null) {
                flaggedOnSpawn.add(uuid);
            }

            plugin.getLogger().warning("Protected-world access detected on async spawn for "
                    + playerName + " in world '" + targetWorldName
                    + "' without fallback-world configured.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        Location originalProtectedSpawn = redirectedProtectedSpawnTargets.remove(uuid);
        if (originalProtectedSpawn != null) {
            handleRedirectedSpawnJoin(player, originalProtectedSpawn);
        }

        if (flaggedOnSpawn.remove(uuid)) {
            if (!canEnterProtectedWorlds(player)) {
                punishProtectedWorldAccess(player, "join-after-protected-spawn");
            }
            return;
        }

        if (!isProtectionEnabled()) {
            return;
        }

        if (canEnterProtectedWorlds(player)) {
            return;
        }

        World currentWorld = player.getWorld();
        if (currentWorld == null) {
            return;
        }

        if (!isProtectedWorld(currentWorld.getName())) {
            return;
        }

        Location fallback = getFallbackLocation();
        if (fallback != null) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                player.teleport(fallback);
                player.sendMessage(getProtectedWorldMessage());
            }, 1L);

            plugin.getLogger().warning("Player " + player.getName()
                    + " joined protected world '" + currentWorld.getName()
                    + "' and was redirected to fallback-world.");
        } else {
            punishProtectedWorldAccess(player, "join-in-protected-world");
        }
    }

    private void handleRedirectedSpawnJoin(Player player, Location originalProtectedSpawn) {
        if (!isProtectionEnabled()) {
            return;
        }

        if (canEnterProtectedWorlds(player)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }

                if (originalProtectedSpawn.getWorld() == null) {
                    return;
                }

                player.teleport(originalProtectedSpawn);
            }, 1L);

            plugin.getLogger().info("Allowed staff player " + player.getName()
                    + " to return to protected spawn world '"
                    + originalProtectedSpawn.getWorld().getName() + "'.");
            return;
        }

        player.sendMessage(getProtectedWorldMessage());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!isProtectionEnabled()) {
            return;
        }

        Player player = event.getPlayer();
        if (canEnterProtectedWorlds(player)) {
            return;
        }

        Location to = event.getTo();
        if (to == null || to.getWorld() == null) {
            return;
        }

        String targetWorldName = to.getWorld().getName();
        if (!isProtectedWorld(targetWorldName)) {
            return;
        }

        event.setCancelled(true);

        Location fallback = getFallbackLocation();
        if (fallback != null) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                player.teleport(fallback);
                player.sendMessage(getProtectedWorldMessage());
            }, 1L);

            plugin.getLogger().warning("Blocked teleport of " + player.getName()
                    + " into protected world '" + targetWorldName
                    + "' and redirected to fallback-world.");
        } else {
            punishProtectedWorldAccess(player, "teleport-into-protected-world");
        }
    }

    /**
     * Führt die Schutzreaktion aus:
     * - clusterweiten Kurzblock setzen
     * - LiteBans-Kick dokumentieren
     * - notfallmässig lokal kicken, falls der Command fehlschlägt
     */
    private void punishProtectedWorldAccess(Player player, String context) {
        UUID uuid = player.getUniqueId();

        if (!handlingInProgress.add(uuid)) {
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                if (!player.isOnline()) {
                    return;
                }

                long blockDurationSeconds = plugin.getConfig().getLong(
                        "protected-worlds.block-duration-seconds",
                        120L
                );
                long blockedUntil = System.currentTimeMillis() + (blockDurationSeconds * 1000L);

                String reason = getPunishmentReason();

                plugin.getProtectedAccessBlockService().upsertBlock(
                        uuid,
                        player.getName(),
                        reason,
                        blockedUntil
                );

                String command = buildLiteBansKickCommand(player.getName(), reason);
                ConsoleCommandSender console = Bukkit.getConsoleSender();
                boolean success = Bukkit.dispatchCommand(console, command);

                plugin.getLogger().warning(
                        "Protected world access detected for " + player.getName()
                                + " (" + uuid + "), context=" + context
                                + ", executedCommand=\"" + command + "\""
                                + ", success=" + success
                                + ", blockedUntil=" + blockedUntil
                );

                if (!success && player.isOnline()) {
                    player.kick(getProtectedWorldMessage());
                }

            } catch (Exception ex) {
                plugin.getLogger().warning("Failed to handle protected-world punishment for "
                        + player.getName() + ": " + ex.getMessage());

                if (player.isOnline()) {
                    player.kick(getProtectedWorldMessage());
                }
            } finally {
                Bukkit.getScheduler().runTaskLater(plugin,
                        () -> handlingInProgress.remove(uuid), 20L);
            }
        });
    }

    private String buildLiteBansKickCommand(String playerName, String reason) {
        return "litebans:kick " + playerName + " " + sanitizeReason(reason);
    }

    private String getPunishmentReason() {
        return plugin.getConfig().getString(
                "protected-worlds.punishment.reason",
                "Unerlaubter Beitritt zu geschuetzter Testumgebung"
        );
    }

    private String sanitizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "Unerlaubter_Beitritt_zu_geschuetzter_Testumgebung";
        }

        String cleaned = reason.replace("\"", "").trim();
        if (cleaned.isBlank()) {
            return "Unerlaubter_Beitritt_zu_geschuetzter_Testumgebung";
        }

        return cleaned;
    }

    private boolean isProtectionEnabled() {
        return plugin.getConfig().getBoolean("protected-worlds.enabled", true);
    }

    private boolean canEnterProtectedWorlds(Player player) {
        return player.hasPermission("whitelist.staff") || player.isOp();
    }

    private boolean isProtectedWorld(String worldName) {
        List<String> patterns = plugin.getConfig().getStringList("protected-worlds.worlds");
        String normalizedWorld = worldName.toLowerCase(Locale.ROOT);

        for (String entry : patterns) {
            if (entry == null || entry.isBlank()) {
                continue;
            }

            String pattern = entry.trim().toLowerCase(Locale.ROOT);

            if (pattern.equals("*")) {
                return true;
            }

            if (matchesWildcard(normalizedWorld, pattern)) {
                return true;
            }
        }

        return false;
    }

    private boolean matchesWildcard(String text, String wildcardPattern) {
        return Pattern.matches(wildcardToRegex(wildcardPattern), text);
    }

    private String wildcardToRegex(String wildcardPattern) {
        StringBuilder out = new StringBuilder("^");

        for (char c : wildcardPattern.toCharArray()) {
            if (c == '*') {
                out.append(".*");
            } else {
                if ("\\.[]{}()+-^$|?".indexOf(c) >= 0) {
                    out.append("\\");
                }
                out.append(c);
            }
        }

        out.append("$");
        return out.toString();
    }

    private Location getFallbackLocation() {
        String fallbackWorldName = plugin.getConfig().getString("protected-worlds.fallback-world", "");

        if (fallbackWorldName == null || fallbackWorldName.isBlank()) {
            return null;
        }

        World fallbackWorld = Bukkit.getWorld(fallbackWorldName);
        if (fallbackWorld == null) {
            plugin.getLogger().warning("Configured fallback world '" + fallbackWorldName + "' was not found.");
            return null;
        }

        return fallbackWorld.getSpawnLocation();
    }

    private Component getProtectedWorldMessage() {
        String raw = plugin.getConfig().getString(
                "messages.protected_world",
                "&cNope! Du darfst diese Welt nicht betreten."
        );
        return MessageUtil.parse(raw);
    }
}