 

# MRZ-Prüfer (Chip)

Offline-Android-App zum Prüfen von Ausweisen, Pässen und Visa nach ICAO 9303.
Keine Internet-Berechtigung, keine Speicherung von Ausweisdaten, kein Tracking.
Sprachen: Deutsch, Englisch, Französisch (nach Geräte- bzw. App-Sprache).

**Hinweis**:  
Der deutsche Personalausweis kann technisch bedingt nicht offline auf Echtheit geprüft werden. Der Zugriff auf **DG1/SOD** ist den Behörden vorbehalten. Offline-Echtheitsprüfung ist nur bei Ausweisdokumenten möglich, die den ICAO-Standard nutzen. Zur Echtheitsprüfung des deutschen Ausweises ist **eID-Integration** oder ein Behördenzertifikat mit Public-Key-Server erforderlich, diese Funktionen sind jedoch aus Datenschutzgründen nicht integriert. Die App folgt dem Datenschutz-Grundsatz "**Edge computing - local only and temporary only**".  

Es handelt sich lediglich um Plausibilitätsprüfungen. Fälschungen können nicht zwangsläufig erkannt werden.

## Rechtliches
Die App dient Test- und Übungszwecken. Sie soll die Funktionsweise der Plausibilitäts- und Echtheitsprüfung von Ausweisdokumenten zeigen. Die App ist ein Hobby-Projekt, ohne Anspruch auf Garantie und Gewährleistung. **Die Nutzung erfolgt auf eigene Gefahr und Eigenverantwortung.**  

**Wenn Sie das Projekt kommerziell nutzen wollen, z.B. Einlasskontrolle, sind zwingend die einschlägigen Rechtsvorschriften der Länder zu beachten! Das Erfassen ist i.d.R. an sehr enge Zweckbindungen geknüpft. Ohne Zweckbindung kein Scannen zulässig.**


## Funktionen

**MRZ-Prüfung** (Kamera mit Texterkennung auf dem Gerät oder manuelle Eingabe)
- Formate TD1 (3 × 30), TD2 (2 × 36), TD3 (2 × 44), Visa
- alle Prüfziffern inkl. Gesamtprüfziffer, verlängerte Dokumentnummern
- Plausibilität von Datum, Ländercodes, Namensfeld
- Zusatzregeln für deutsche Ausweise und Pässe (Zeichenvorrat, Dokumentversion u. a.)

**Chipprüfung per NFC** (optional)
- Zugang mit CAN oder mit den Daten der gescannten MRZ (PACE, ersatzweise BAC)
- passive Authentisierung: Hashwert der digitalen MRZ, Signatur des Sicherheitsobjekts,
  Wurzelzertifikat (CSCA) des Ausstellerstaates
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

**Hinweis**:  
Android kann App-Daten, z.B. Anzahl der Aufrufe und Absturzdaten, speichern und an Google senden. Das ist eine Systemfunktion, keine Funktion der App. Ist dies nicht gewünscht, kann die Funktion in den Android-Einstellungen unter "Daten und Nutzung" (oder ähnlich) deaktiviert werden, diese Einstellung gilt dann Systemweit.

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

## Einsatz der App

Im Ordner "MRZPruefer_App" ist eine fertig aufgebaute App enthalten. Diese kann direkt eingesetzt werden. 

APK herunterladen, oder selbsterstellte App nutzen, auf das Smartphone kopieren und dort installieren. **Bei aktivierten "Google Play Protect" wird die App vor der Installation auf schädliches Verhalten geprüft. Dies ist normal.**

## Hinweis zum Android-Sideloading ab 2027

Aufgrund von Systemänderungen seitens Google ab 2027 gelten für die Installation der APK folgende Besonderheiten:

* **Internetverbindung erforderlich:** Android erlaubt die Installation nicht verifizierter Apps nur noch, wenn das Gerät währenddessen online ist. Eine reine Offline-Installation wird blockiert.
* **Sicherheitswarnungen:** Android wird die App als "nicht verifiziert" blockieren. Du musst den erweiterten Warnprozess manuell bestätigen ("Trotzdem installieren") und die Installation ggf. per PIN oder Biometrie freigeben.
* **24 Stunden Wartezeit:** Nach der Freigabe im System kann es bis zu 24 Stunden dauern, bis Google die App im Hintergrund geprüft hat und die Installation bzw. Ausführung endgültig freigeschaltet wird.

Die App selbst enthält weiterhin keinerlei Tracker oder Schadsoftware – der Quellcode ist hier auf GitHub komplett transparent einsehbar.

