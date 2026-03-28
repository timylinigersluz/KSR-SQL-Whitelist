package ch.ksrminecraft.kSRSQLWhitelist.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * ----------------------------------------------------------------------------
 *  🎨 MessageUtil
 *  ---------------
 *  Dienstklasse für die Verarbeitung von Chat-Nachrichten und Kick-Messages.
 *
 *  Hauptaufgabe:
 *  - Konvertiert Minecraft-Farbcodes (&c, &7, etc.) korrekt in Adventure-Components.
 *
 *  💡 Hinweis:
 *  Diese Variante verwendet den LegacyComponentSerializer von Adventure.
 *  Dadurch werden klassische Minecraft-Farbcodes mit "&" zuverlässig
 *  in formatierte Components umgewandelt.
 *
 *  ⚙️ Beispiel:
 *  <pre>
 *  String raw = "&cFehler! &7Besuche &bhttps://ksrminecraft.ch";
 *  Component msg = MessageUtil.parse(raw);
 *  </pre>
 *
 *  Ergebnis:
 *   - „Fehler!“ wird rot angezeigt
 *   - „Besuche“ wird grau angezeigt
 *   - Die URL wird aqua angezeigt, sofern der Farbcode davor gesetzt wurde
 *
 *  Autor: Timy Liniger (KSR Minecraft)
 *  Projekt: KSR-SQL-Whitelist
 * ----------------------------------------------------------------------------
 */
public class MessageUtil {

    /**
     * Serializer für Legacy-Minecraft-Farbcodes mit '&'.
     */
    private static final LegacyComponentSerializer LEGACY_SERIALIZER =
            LegacyComponentSerializer.legacyAmpersand();

    /**
     * Wandelt einen Rohtext mit Minecraft-Farbcodes (&c, &7, &e, ...)
     * in ein {@link Component} um, das vom Paper Adventure-System korrekt
     * angezeigt werden kann.
     *
     * @param raw Originaltext mit optionalen Farbcodes (&)
     * @return Fertig formatierte {@link Component} für Chat oder Kicknachricht
     */
    public static Component parse(String raw) {
        if (raw == null) {
            return Component.empty();
        }

        return LEGACY_SERIALIZER.deserialize(raw);
    }
}