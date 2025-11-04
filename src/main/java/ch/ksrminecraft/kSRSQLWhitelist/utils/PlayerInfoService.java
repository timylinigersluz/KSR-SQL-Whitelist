package ch.ksrminecraft.kSRSQLWhitelist.utils;

import ch.ksrminecraft.kSRSQLWhitelist.KSRSQLWhitelist;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

/**
 * ----------------------------------------------------------------------------
 *  🌍 PlayerInfoService
 *  -------------------
 *  Ruft öffentliche Informationen zu Minecraft-Spielern über die Ashcon-API ab.
 *
 *  Datenquelle:
 *   - https://api.ashcon.app/mojang/v2/user/<username>
 *
 *  Bereitgestellte Informationen:
 *   ✅ Mojang-UUID
 *   ✅ Name
 *   ✅ Erstellungsdatum (created_at)
 *   ✅ Namenshistorie
 *   ✅ Skin-URL (als klickbarer Link)
 *   ✅ Verifikationsstatus
 *
 *  Autor: Timy Liniger (KSR Minecraft)
 *  Projekt: KSR-SQL-Whitelist
 * ----------------------------------------------------------------------------
 */
public class PlayerInfoService {

    private final KSRSQLWhitelist plugin;

    public PlayerInfoService(KSRSQLWhitelist plugin) {
        this.plugin = plugin;
    }

    /**
     * Holt Spielerinformationen über die öffentliche Ashcon-API.
     *
     * @param playerName Der Minecraft-Benutzername
     * @return PlayerInfo-Objekt oder null, wenn Spieler unbekannt/nicht gefunden
     */
    public PlayerInfo fetchInfo(String playerName) {
        try {
            playerName = playerName.trim().toLowerCase(Locale.ROOT);
            URL url = new URL("https://api.ashcon.app/mojang/v2/user/" + playerName);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "KSR-SQL-Whitelist");
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            conn.setRequestMethod("GET");

            if (conn.getResponseCode() != 200) {
                plugin.getLogger().warning("[KSR-SQL-Whitelist] Ashcon API returned " +
                        conn.getResponseCode() + " for " + playerName);
                return null;
            }

            try (InputStream is = conn.getInputStream();
                 Scanner scanner = new Scanner(is).useDelimiter("\\A")) {

                String json = scanner.hasNext() ? scanner.next() : "";
                if (json.isEmpty()) return null;

                JSONObject obj = new JSONObject(json);

                String uuid = obj.optString("uuid", null);
                String username = obj.optString("username", playerName);
                String createdAt = obj.optString("created_at", "—");

                List<String> history = new ArrayList<>();
                if (obj.has("username_history")) {
                    JSONArray arr = obj.getJSONArray("username_history");
                    for (int i = 0; i < arr.length(); i++) {
                        history.add(arr.getJSONObject(i).getString("username"));
                    }
                }

                String skin = null;
                if (obj.has("textures")) {
                    JSONObject textures = obj.getJSONObject("textures");
                    if (textures.has("skin")) {
                        skin = textures.getJSONObject("skin").optString("url", null);
                    }
                }

                return new PlayerInfo(username, uuid, createdAt, history, skin, true);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[KSR-SQL-Whitelist] Failed to fetch Ashcon info for " +
                    playerName + ": " + e.getMessage());
            return null;
        }
    }

    // ------------------------------------------------------------------------
    // 🧩 Innere Datenklasse für API-Ergebnisse
    // ------------------------------------------------------------------------
    public static class PlayerInfo {
        public final String name;
        public final String uuid;
        public final String createdAt;
        public final List<String> history;
        public final String skin;
        public final boolean verified;

        public PlayerInfo(String name, String uuid, String createdAt,
                          List<String> history, String skin, boolean verified) {
            this.name = name;
            this.uuid = uuid;
            this.createdAt = createdAt;
            this.history = history;
            this.skin = skin;
            this.verified = verified;
        }

        /**
         * Gibt eine klickbare Chat-Komponente zurück, die auf die Skin-URL verweist.
         */
        public Component getClickableSkinComponent() {
            if (skin == null) {
                return Component.text("—", NamedTextColor.GRAY);
            }
            return Component.text("Click to view skin")
                    .color(NamedTextColor.AQUA)
                    .decorate(TextDecoration.UNDERLINED)
                    .clickEvent(ClickEvent.openUrl(skin));
        }
    }
}
