

# MRZ-Prüfer (Chip)

Offline-Android-App zum Prüfen von Ausweisen, Pässen und Visa nach ICAO 9303.
Keine Internet-Berechtigung, keine Speicherung von Ausweisdaten, kein Tracking.
Sprachen: Deutsch, Englisch, Französisch (nach Geräte- bzw. App-Sprache).

**Hinweis**:  
Der deutsche Personalausweis kann technisch bedingt nicht offline auf Echtheit geprüft werden. Der Zugriff auf **DG1/SOD** ist den Behörden vorbehalten. Echtheitsprüfung ist nur bei Ausweisdokumenten möglich, die den ICAO-Standard nutzen. Zur Echtheitsprüfung des deutschen Ausweises ist **eID-Integration** erforderlich, diese Funktion ist aus Datenschutzgründen nicht integriert. Die App folgt dem Grundsatz "**Edge computing - local only - temporary only**".

## Rechtliches
Die App dient Test- und Übungszwecken. Sie soll die Funktionsweise der Plausibilitäts- und Echtheitsprüfung von Ausweisdokumenten zeigen.  
```diff
 - **Für den kommerziellen Einsatz sind zwingend die einschlägigen Rechtsvorschriften der Länder zu beachten**. Die App ist ein Hobby-Projekt, ohne Anspruch auf Garantie und Gewährleistung. **Die Nutzung erfolgt auf eigene Gefahr.**
```

## Funktionen

**MRZ-Prüfung** (Kamera mit Texterkennung auf dem Gerät oder manuelle Eingabe)
- Formate TD1 (3 × 30), TD2 (2 × 36), TD3 (2 × 44), Visa
- alle Prüfziffern inkl. Gesamtprüfziffer, verlängerte Dokumentnummern
- Plausibilität von Datum, Ländercodes, Namensfeld
- Zusatzregeln für deutsche Ausweise und Pässe (Zeichenvorrat, Dokumentversion u. a.)

**Chipprüfung per NFC** (optional)
- Zugang mit CAN oder mit den Daten der gescannten MRZ (PACE, ersatzweise BAC)
- passive Authentisierung: Hashwert der digitalen MRZ, Signatur des Sicherheitsobjekts,
  Wurzelzertifikat (CSCA) des Ausstellerstaats
- Abgleich Chip ↔ gedruckte MRZ
- Beim deutschen Personalausweis sind die Chipdaten Behörden vorbehalten; die App meldet das.
- Nicht geprüft: Klonschutz des Chips, Sperr- und Fahndungslisten. Lichtbild und
  Fingerabdrücke werden nicht gelesen.

## Wurzelzertifikate (CSCA)

Die App enthält **keine** Zertifikatslisten und lädt auch keine herunter.
Jeder Nutzer lädt eine Liste selbst aus vertrauenswürdiger Quelle herunter und
importiert sie in der App über „Liste importieren …“ (Android-Dateiauswahl,
keine Speicherberechtigung nötig). Unterstützt werden:

- ZIP-Archive (z. B. die Masterliste des BSI, ohne Entpacken)
- CSCA-Masterlisten (CMS, `.ml`)
- ICAO-PKD-Dateien (`.ldif`)
- einzelne Zertifikate (`.cer`, `.der`, `.pem`)

Die Signatur der Masterliste selbst wird nicht geprüft. Dieses Projekt steht in
keiner Verbindung zum BSI oder zur ICAO.

## Datenschutz

- Keine Internet-Berechtigung (im Manifest ausdrücklich entfernt)
- Statistik-Übertragungsweg von Google ML Kit entfernt
- Es erfolgt keine Speicherung von Ausweisdaten (Android-Backup deaktiviert)
- Bildschirmfotos und -aufnahmen auf den Prüfseiten gesperrt
- Tastatur angewiesen, Eingaben nicht zu lernen
- Gespeichert werden nur selbst importierte Zertifikatslisten (öffentliche Daten)

## Bauen

Android Studio (aktuelle Version), JDK 17, Android SDK 34. Mindestens Android 8.0.

- Projekt öffnen, Gradle-Sync abwarten
- **Build → Generate Signed App Bundle / APK → APK** mit eigenem Signierschlüssel
- Tests der Prüflogik: `./gradlew test`

Wer die App selbst veröffentlicht, muss Impressum und Datenschutzerklärung
(`app/src/main/res/raw*/`) mit den eigenen Angaben füllen.

## Aufbau

| Datei | Aufgabe |
|---|---|
| `Mrz.java` | MRZ-Prüflogik (reines Java, mit Unit-Tests) |
| `Tx.java` | Texte der Prüfmeldungen in drei Sprachen |
| `MainActivity.java` | Kamera und Texterkennung |
| `ResultActivity.java` | Ergebnis und manuelle Eingabe |
| `NfcActivity.java` | Chipprüfung und Zertifikatsimport |
| `ChipReader.java` | NFC-Zugang und passive Authentisierung (JMRTD) |
| `CscaStore.java` | Import und Ablage der Zertifikatslisten |
| `InfoActivity.java` | Impressum, Datenschutz, Lizenzen |

## Verwendete Bibliotheken

AndroidX, CameraX, Material Components (Apache 2.0) · Google ML Kit Text
Recognition (Google-Nutzungsbedingungen, nicht frei) · JMRTD, SCUBA (LGPL 2.1) ·
Bouncy Castle (MIT)
