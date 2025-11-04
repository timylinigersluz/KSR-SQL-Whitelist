# 🧩 KSR-SQL-Whitelist

Ein leistungsstarkes **Minecraft Paper/Spigot-Plugin**, das die **Vanilla-Whitelist vollständig durch eine SQL-basierte Lösung** ersetzt.  
Ideal für **Servernetzwerke** oder **mehrere Server-Instanzen**, die dieselbe zentrale Whitelist verwenden möchten.

---

## 🚀 Features

- 🔄 **Intercepts alle Vanilla-Whitelist-Kommandos**  
  (`/whitelist add/remove/on/off/list/reload`)
- 🗄️ **Speichert Whitelist-Einträge in einer MySQL / MariaDB-Datenbank**
- 🧱 **Automatische Tabellenerstellung** bei Pluginstart (`mysql.table`)
- 🔗 **UUID- und Spielernamen-Synchronisation**, auch für Offline-Spieler
- ⚙️ **Kompatibel mit Minecraft 1.21** (Paper / Spigot)
- 🧩 **Erweitertes Rechtesystem** (`KSRSQLWhitelist.*`) analog zur Vanilla-Whitelist
- 📜 **Konfigurierbare Spaltennamen** (`column_uuid`, `column_name`) für flexible DB-Strukturen

---

## 🧰 Installation

1. **Baue das Plugin mit Maven**
   ```bash
   mvn clean package
   ```
   ➜ Die fertige JAR-Datei befindet sich unter:  
   `target/KSR-SQL-Whitelist-1.0-SNAPSHOT.jar`

2. **Kopiere das JAR nach**
   ```
   plugins/
   ```

3. **Starte den Server neu**  
   → Die Standard-`config.yml` wird automatisch erstellt.

---

## ⚙️ Konfiguration (`config.yml`)

```yaml
########################################################
# 🧩 KSR-SQL-Whitelist - Configuration
# Plugin by Timy Liniger (https://ksrminecraft.ch/)
########################################################

mysql:
  host: localhost
  port: 3306
  database: minecraft
  user: root
  password: example
  table: ksr_sql_whitelist
  column_uuid: UUID       # Spaltenname für die UUID
  column_name: user       # Spaltenname für den Spielernamen
  useSSL: false
  serverTimezone: UTC

# Aktiviert/Deaktiviert die Whitelist beim Serverstart
enabled: true

# Kick-Nachrichten
kick:
  not_whitelisted: "&cDu bist nicht auf unserer Whitelist.&r\n&7Registriere dich auf https://ksrminecraft.ch."
  db_error: "&cEin Datenbankfehler ist aufgetreten.&r\n&7Bitte versuche es später erneut."
```

💡 **Tipp:**  
Mit `column_uuid` und `column_name` kannst du das Plugin flexibel an verschiedene Tabellen-Layouts anpassen  
(z. B. für Test- oder Produktionsserver mit unterschiedlichen Feldnamen).

---

## 🔐 Permissions

| Permission | Beschreibung | Standard |
|-------------|--------------|-----------|
| `KSRSQLWhitelist.add` | Spieler hinzufügen | `op` |
| `KSRSQLWhitelist.del` | Spieler entfernen | `op` |
| `KSRSQLWhitelist.on`  | Whitelist aktivieren | `op` |
| `KSRSQLWhitelist.off` | Whitelist deaktivieren | `op` |
| `KSRSQLWhitelist.*`   | Zugriff auf alle Befehle | – |

Vanilla-Rechte (`minecraft.command.whitelist`) werden weiterhin unterstützt.

---

## 💬 Commands

Das Plugin ersetzt automatisch alle Vanilla-Subcommands:

| Befehl | Beschreibung |
|--------|---------------|
| `/whitelist add <Spieler>` | Spieler zur Whitelist hinzufügen |
| `/whitelist remove <Spieler>` | Spieler entfernen |
| `/whitelist on` | Whitelist aktivieren |
| `/whitelist off` | Whitelist deaktivieren |
| `/whitelist list` | Liste aller Whitelist-Einträge anzeigen |
| `/whitelist reload` | Konfiguration neu laden |

Alle Operationen erfolgen **asynchron**, um den Hauptthread nicht zu blockieren.

---

## 🧱 Datenbankstruktur

Das Plugin erstellt automatisch eine einfache Tabelle:

```sql
CREATE TABLE IF NOT EXISTS `ksr_sql_whitelist` (
  `UUID` varchar(36) DEFAULT NULL,
  `user` varchar(100) DEFAULT NULL,
  KEY `idx_uuid` (`UUID`),
  KEY `idx_user` (`user`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

🔸 **Empfohlen:**  
Füge einen **UNIQUE KEY** auf die UUID hinzu, um saubere Upserts zu ermöglichen:
```sql
ALTER TABLE ksr_sql_whitelist
  ADD UNIQUE KEY uniq_uuid (UUID);
```

---

## 🧠 Internes Verhalten

- Beim **Login** prüft das Plugin asynchron, ob der Spieler in der SQL-Whitelist steht.
- Falls nicht: Kick mit konfigurierbarer Nachricht.
- Bei DB-Fehlern: Fallback-Kick mit neutraler Meldung.
- `/whitelist`-Befehle (egal ob von Spieler oder Konsole) werden abgefangen und  
  direkt mit der Datenbank synchronisiert.

---

## 🧑‍💻 Entwicklung

**Java-Version:** 21  
**Paper-API:** 1.21.8-R0.1-SNAPSHOT  
**Buildsystem:** Maven (Shade-Plugin)

```xml
<dependency>
    <groupId>io.papermc.paper</groupId>
    <artifactId>paper-api</artifactId>
    <version>1.21.8-R0.1-SNAPSHOT</version>
    <scope>provided</scope>
</dependency>
```

---

## 🧾 Credits

- **Autor:** Timy Liniger
- **Projektseite:** [https://ksrminecraft.ch](https://ksrminecraft.ch)
- **Lizenz:** Private / Education Use Only
- **Kompatibel mit:** Paper, Spigot, Purpur (1.21+)

---

> © 2025 KSR Minecraft – SQL-Whitelist Plugin  
> Entwickelt zur zentralen Verwaltung von Spieler-Zugängen im KSR-Netzwerk.
