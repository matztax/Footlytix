# Futlytix

Taktik- und Teamverwaltung für Amateurfußball-Trainer als native Android-App (Kotlin, Jetpack Compose).
Alles läuft offline: kein Server, kein Konto, kein Tracking. Die Daten bleiben auf dem Gerät.

## Funktionen

- **Spiele:** Saisons, Spiele mit Gegner, Ergebnis, Aufstellung und Einsätzen. Der Spieltag-Modus hat eine Uhr und Tasten für Tor, Gegentor, Karten, Wechsel und Verletzung und rechnet beim Beenden die Statistik aus.
- **Datenbank:** Spieler, eigene und gegnerische Vereine, Attribut-Katalog, Analysen, Bewertungsverlauf, Verfügbarkeit und Transfers.
- **Taktik:** Aufstellungen mit mehreren Phasen und Laufwegen, Standards mit Rollenverteilung, Gegner-Briefings.
- **Training:** Übungen auf einem Spielfeld zeichnen, Einheiten planen, Anwesenheit erfassen, PDF-Export.
- **Video:** Bibliothek, Zeichnungen an Zeitmarken, Marker und Clips. Die Videodatei bleibt, wo sie ist.
- **Import:** Spielerliste (CSV) und Spielplan (.ics).
- **Sicherheit & Backup:** App-Sperre, tägliches Auto-Backup, optional in einen eigenen Ordner, verschlüsselte Backups mit Passwort.

## Bauen

**Ohne eigenen Rechner (GitHub Actions):** Jeder Push startet den Workflow `.github/workflows/blank.yml`. Unter *Actions → letzter Lauf → Artifacts* liegen danach:

| Artifact | Wofür |
|---|---|
| `Futlytix-debug-apk` | Entwicklungs-Build zum Ausprobieren |
| `Futlytix-r8test-apk` | Optimierter Release-Build (R8) zum Testen auf dem Handy. Er installiert sich als eigene App neben der normalen und hat eigene Daten. |
| `Futlytix-release-aab` | App Bundle für Google Play (nur, wenn die Signatur-Secrets gesetzt sind) |
| `Futlytix-release-mapping` | Für lesbare Absturzberichte in der Play Console |
| `Testbericht`, `Lint-Bericht` | Ergebnisse der automatischen Prüfungen |
| `Datenbankschema` | Das von Room erzeugte Schema, siehe unten |

**Mit Android Studio:** Den Ordner öffnen und *Run* ausführen, oder `gradle assembleDebug`. Tests laufen mit `gradle testDebugUnitTest`.

Der Workflow baut das Projekt aus dem Repo-Hauptordner. Liegt dort keine `settings.gradle.kts`, entpackt er übergangsweise wie früher die neueste Projekt-ZIP.

## Google Play

- Paketname `de.futlytix.app`, Ziel-API 36.
- Signiertes App Bundle: die Secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` und `KEY_PASSWORD` im Repo setzen.
- `versionCode` ist die GitHub-Laufnummer, dadurch ist jeder Upload höher als der vorige.
- Vor einem Play-Upload die `Futlytix-r8test-apk` installieren und kurz durchklicken. Sie entspricht dem Release-Build inklusive R8-Optimierung.

> **Hinweis zum Debug-Schlüssel:** `app/debug.keystore` liegt öffentlich im Repo. Jeder kann damit eine APK signieren, die Android als Update der Debug-Version akzeptiert. Debug-APKs deshalb nicht an andere Trainer weitergeben, dafür ist der Play-Store-Build da.

## Aufbau des Codes

```
app/src/main/java/de/tactics/app/
├── MainActivity.kt          App-Sperre, Edge-to-Edge
├── MainViewModel.kt         Schnittstelle der Oberfläche (StateFlows + Aktionen)
├── BackupRepository.kt      Export/Import/Auto-Backup
├── BackupCrypto.kt          Passwort-Verschlüsselung der Backups (AES-256-GCM)
├── AutoBackup.kt            Sicherungsdateien und Backup-Ordner
├── Importer.kt / Exporter.kt  CSV/ICS-Import, PDF/Bild-Export
├── data/
│   ├── Models.kt            Tabellen (Room) und Backup-Format
│   ├── Database.kt          DAO, Migrationen
│   ├── MatchLogic.kt        Spieltag-Auswertung (reine Funktion, getestet)
│   └── *Repository.kt       Kader, Spiele, Training, Taktik, Video
└── ui/                      Compose-Oberflächen
app/src/test/                Unit-Tests (Backup-Verschlüsselung, Import, Spielauswertung, Backup-Format)
```

Änderungen, die mehrere Tabellen betreffen (z. B. Spieler löschen), laufen in den Repositories als Transaktion: entweder alles oder nichts.

## Datenbank ändern (wichtig!)

Die App löscht bei einem Update **keine** Daten mehr. Fehlt eine Migration, bricht sie stattdessen ab. Bei jeder Änderung an einer Tabelle also:

1. In `data/Database.kt` die Version erhöhen, sowohl `version = …` als auch `AppDatabase.VERSION`.
2. Eine `MIGRATION_X_Y` schreiben und in `ALL_MIGRATIONS` anhängen.
3. Das neue Schema aus dem Artifact `Datenbankschema` nach `app/schemas/` ins Repo legen.

## Datenschutz

Die Datenschutzerklärung liegt unter [`docs/datenschutz.html`](docs/datenschutz.html).

## Änderungen

### 1.7
- Kein stilles Löschen aller Daten mehr bei Datenbank-Updates
- Backup importieren fragt immer nach und legt vorher eine Sicherheitskopie an. Leere, fremde oder zu neue Dateien werden abgelehnt.
- Löschen und Speichern über mehrere Tabellen läuft als Transaktion
- Backup-Passwort: Backups im Ordner sowie geteilte und exportierte Backups werden verschlüsselt
- Fehler beim Kopieren in den Backup-Ordner werden angezeigt
- App-Sperre blockiert Bildschirmfotos und die Vorschau in „Zuletzt verwendet“
- Keine Übertragung in die Google-Cloud-Sicherung mehr (der Umzug von Gerät zu Gerät bleibt möglich)
- Videos, deren Datei fehlt (z. B. nach einem Gerätewechsel), lassen sich neu verknüpfen
- Kotlin 2.0, aktuelle Bibliotheken, R8 im Release-Build, Unit-Tests in der CI

### 1.6
- Spieltag-Modus, Import von Spielerlisten (CSV) und Spielplänen (.ics), Schutz vor Datenverlust beim Bearbeiten

### 1.5
- Spiele und Saisonstatistik, Verfügbarkeit, Trainingsanwesenheit, Bewertungsverlauf, PDF-/Bild-Export, Video-Marker, App-Sperre und Auto-Backup
