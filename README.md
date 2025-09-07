# KSR-SQL-Whitelist

Ein Minecraft Paper/Spigot Plugin, das die Vanilla-Whitelist vollständig durch eine SQL-basierte Whitelist ersetzt.

## Features
- Fängt alle Vanilla-Whitelist-Kommandos ab (`/whitelist add/remove/on/off/list/reload`).
- Speichert Whitelist-Einträge in einer MySQL/MariaDB-Datenbank.
- Automatische Tabellenerstellung (`mysql.table`).
- UUID- und Spielername-Synchronisation (auch für Offline-Adds).
- Kompatibel mit Minecraft 1.21 (Paper/Spigot).
- Permissions wie beim Original, ergänzt um eigene (`KSRSQLWhitelist.*`).

## Installation
1. Baue das Plugin mit Maven:  
   ```bash
   mvn package
   ```
   Das JAR liegt danach unter `target/KSR-SQL-Whitelist-1.0-SNAPSHOT.jar`.
2. Kopiere das JAR nach `plugins/` auf deinem Server.
3. Starte den Server neu.  
   -> Die Standard-`config.yml` wird erstellt.

## Konfiguration
```yaml
mysql:
  host: localhost
  port: 3306
  database: minecraft
  user: root
  password: example
  table: ksr_sql_whitelist

# Status der Whitelist beim Serverstart
enabled: true
```

## Permissions
- `KSRSQLWhitelist.add` – Spieler hinzufügen
- `KSRSQLWhitelist.del` – Spieler entfernen
- `KSRSQLWhitelist.on` – Whitelist aktivieren
- `KSRSQLWhitelist.off` – Whitelist deaktivieren
- `KSRSQLWhitelist.*` – Zugriff auf alles

Vanilla-Permissions wie `minecraft.command.whitelist` funktionieren weiterhin.

## Commands
Das Plugin fängt **alle** Vanilla-Subcommands ab:  
- `/whitelist add <Spieler>`  
- `/whitelist remove <Spieler>`  
- `/whitelist on`  
- `/whitelist off`  
- `/whitelist list`  
- `/whitelist reload`  

## Hinweise
- Für sauberes Upsert in MySQL empfiehlt sich ein **UNIQUE KEY** auf `UUID`:
  ```sql
  ALTER TABLE ksr_sql_whitelist
    ADD UNIQUE KEY uniq_uuid (UUID);
  ```
- Standard-Datei `whitelist.json` wird nicht mehr genutzt.

---

Autor: Timy Liniger  
Website: [https://ksrminecraft.ch](https://ksrminecraft.ch)
