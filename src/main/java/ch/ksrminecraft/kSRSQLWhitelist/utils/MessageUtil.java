package ch.ksrminecraft.kSRSQLWhitelist.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ----------------------------------------------------------------------------
 *  🎨 MessageUtil
 *  ---------------
 *  Dienstklasse für die Verarbeitung von Chat-Nachrichten und Kick-Messages.
 *
 *  Hauptaufgaben:
 *  - Konvertiert Minecraft-Farbcodes (&c, &7, etc.) in Adventure-Components.
 *  - Erkannt Links (http/https) in Texten und hebt sie farblich hervor.
 *  - Erzeugt optisch ansprechende Nachrichten, die mit dem
 *    Adventure-API-System von Paper kompatibel sind.
 *
 *  ⚙️ Beispiel:
 *  <pre>
 *  String raw = "&cFehler! &7Besuche &bhttps://ksrminecraft.ch";
 *  Component msg = MessageUtil.parse(raw);
 *  </pre>
 *
 *  Ergebnis:
 *   - „Fehler!“ wird rot angezeigt
 *   - Der Link „https://ksrminecraft.ch“ erscheint blau und unterstrichen
 *   - Keine ClickEvents → rein visuelle Hervorhebung
 *
 *  Autor: Timy Liniger (KSR Minecraft)
 *  Projekt: KSR-SQL-Whitelist
 * ----------------------------------------------------------------------------
 */
public class MessageUtil {

    /**
     * Regulärer Ausdruck, um URLs (http/https) in einem Text zu erkennen.
     * Beispiel: https://ksrminecraft.ch
     */
    private static final Pattern URL_PATTERN =
            Pattern.compile("(https?://\\S+)", Pattern.CASE_INSENSITIVE);

    // ------------------------------------------------------------------------
    // 🧠 Hauptmethode: Text → Adventure Component
    // ------------------------------------------------------------------------

    /**
     * Wandelt einen Rohtext mit Farbcodes (&c, &7, &e, ...) und evtl. Links
     * in ein {@link Component} um, das vom Paper Adventure-System angezeigt
     * werden kann.
     * <p>
     * Links werden automatisch erkannt und blau/unterstrichen dargestellt,
     * sind jedoch **nicht klickbar** (rein optisch).
     *
     * @param raw Originaltext mit optionalen Farbcodes (&) und URLs
     * @return Fertig formatiertes {@link Component} für Chat oder Kicknachricht
     */
    public static Component parse(String raw) {
        if (raw == null) return Component.empty();

        // 1️⃣ Minecraft-Farbcodes (&c etc.) in echte Farben umwandeln
        String colored = org.bukkit.ChatColor.translateAlternateColorCodes('&', raw);

        // 2️⃣ Text mit regulärem Ausdruck nach URLs durchsuchen
        Matcher m = URL_PATTERN.matcher(colored);
        Component out = Component.empty();
        int last = 0;

        // 3️⃣ Alle Teile zwischen URLs und die URLs selbst zusammensetzen
        while (m.find()) {
            // Text vor der URL anhängen
            String before = colored.substring(last, m.start());
            if (!before.isEmpty()) {
                out = out.append(Component.text(before));
            }

            // URL hervorheben (blau + unterstrichen)
            String url = m.group(1);
            Component link = Component.text(url)
                    .color(NamedTextColor.BLUE)
                    .decorate(TextDecoration.UNDERLINED);

            out = out.append(link);
            last = m.end();
        }

        // 4️⃣ Letzten Rest des Textes (nach der letzten URL) hinzufügen
        if (last < colored.length()) {
            out = out.append(Component.text(colored.substring(last)));
        }

        return out;
    }
}
