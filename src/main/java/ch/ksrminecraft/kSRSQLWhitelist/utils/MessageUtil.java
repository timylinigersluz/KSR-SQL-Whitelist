package ch.ksrminecraft.kSRSQLWhitelist.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageUtil {

    private static final Pattern URL_PATTERN =
            Pattern.compile("(https?://\\S+)", Pattern.CASE_INSENSITIVE);

    /**
     * Wandelt einen String mit Farbcodes (&c, &7, ...) und evtl. Links in ein Adventure-Component um.
     * Links werden farblich hervorgehoben, aber nicht klickbar.
     */
    public static Component parse(String raw) {
        if (raw == null) return Component.empty();

        // Farb-Codes (&c etc.) konvertieren
        String colored = org.bukkit.ChatColor.translateAlternateColorCodes('&', raw);

        // Jetzt nach URLs suchen
        Matcher m = URL_PATTERN.matcher(colored);
        Component out = Component.empty();

        int last = 0;
        while (m.find()) {
            String before = colored.substring(last, m.start());
            if (!before.isEmpty()) {
                out = out.append(Component.text(before));
            }

            String url = m.group(1);
            Component link = Component.text(url)
                    .color(NamedTextColor.BLUE)
                    .decorate(TextDecoration.UNDERLINED); // nur optisch, kein ClickEvent
            out = out.append(link);

            last = m.end();
        }

        // Rest anhängen
        if (last < colored.length()) {
            out = out.append(Component.text(colored.substring(last)));
        }

        return out;
    }
}
