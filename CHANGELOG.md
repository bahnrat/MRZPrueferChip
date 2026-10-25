# Änderungen seit Version 1.0

Legende: **[beide]** = für öffentliche Version und Chip-Version · **[Chip]** = nur Chip-Version

---

## 1.0 – erste Version

- MRZ-Scan per Kamera (CameraX + Google ML Kit, Modell in der App, offline)
- nur deutscher Personalausweis (TD1, 3 × 30)
- vier Prüfziffern, Zeichenvorrat der Ausweisnummer, Geburts- und Ablaufdatum
- manuelle Eingabe aller drei Zeilen (Füllzeichen `<` selbst tippen)
- keine Internet-Berechtigung

---

## Eingabe

- **[beide]** Füllzeichen und feste Teile werden beim deutschen Ausweis automatisch ergänzt
  - Zeile 1: nur Nummer + Prüfziffer tippen
  - Zeile 2: nur Ziffern tippen (15 bzw. 19 mit Dokumentversion)
  - Zeile 3: „Name Vorname“ oder „Name, Vorname(n)“; Umlaute → AE/OE/UE, ß → SS, Bindestrich → `<`
- **[beide]** Dokumentversion (z. B. 2108) in Zeile 2 erkannt; Gesamtprüfziffer wird nicht mehr verschluckt
- **[beide]** Formatauswahl bei der Eingabe (dt. Personalausweis, TD1, TD3, TD2); bei zweizeiligen Formaten verschwindet Zeile 3
- **[beide]** Datenzeilen: lange `<`-Kette darf als ein Leerzeichen getippt werden

*Dateien:* `Mrz.java`, `ResultActivity.java`, `activity_result.xml`, `strings.xml`

## Prüfung nach ICAO 9303

- **[beide]** Formate TD1 (3 × 30), TD2 (2 × 36, alte Ausweise), TD3 (2 × 44, Pässe) und Visa
- **[beide]** Dokumentarten A, C, I, P, V passend zum Format
- **[beide]** verlängerte Dokumentnummern (> 9 Zeichen, Rest im optionalen Feld)
- **[beide]** Prüfziffer der persönlichen Nummer (Pass); Visa ohne Gesamtprüfziffer
- **[beide]** ausländische Dokumente grün statt orange; deutsche Zusatzregeln nur bei Aussteller D (Ausweis und Pass)
- **[beide]** Scanner findet zweizeilige MRZ; gleicht kleine Längenfehler in `<`-Ketten aus; OCR-Korrekturen nur, wenn danach die Prüfziffern stimmen

*Dateien:* `Mrz.java` (neu aufgebaut), `MainActivity.java`, `MrzTest.java`

## Datenschutz und Sicherheit

- **[beide]** Bildschirmfotos/-aufnahmen auf Kamera- und Ergebnisseite gesperrt, kein Vorschaubild in „Zuletzt verwendet“ (`FLAG_SECURE`)
- **[beide]** Statistik-Übertragungsweg von ML Kit entfernt (`TransportBackendDiscovery` im Manifest)
- **[beide]** Tastatur angewiesen, Eingaben nicht zu lernen (`flagNoPersonalizedLearning`), kein Autofill
- **[beide]** Info-Seite mit Impressum, Datenschutzerklärung und Lizenzhinweisen (HTML in `res/raw/`)

*Dateien:* `AndroidManifest.xml`, `MainActivity.java`, `ResultActivity.java`, `InfoActivity.java`, `activity_info.xml`, `themes.xml`, `res/raw/*.html`

## Sprachen

- **[beide]** Deutsch, Englisch, Französisch – nach Gerätesprache, sonst Deutsch
- **[beide]** Prüfmeldungen über `Tx.java` (reines Java, testbar); Datums- und Anführungszeichen je Sprache
- **[beide]** App-Sprache pro App einstellbar (`res/xml/locales_config.xml`, `android:localeConfig` im Manifest)
- **[beide]** `app_name` mit `translatable="false"`
- **[beide]** Info-Seiten auf Englisch und Französisch (`res/raw-en/`, `res/raw-fr/`), deutsche Fassung maßgeblich

*Dateien:* `Tx.java` (neu), `Mrz.java`, `values-en/strings.xml`, `values-fr/strings.xml`, `res/xml/locales_config.xml`, `AndroidManifest.xml`, `res/raw-en/*`, `res/raw-fr/*`

> Für die öffentliche Version die Chip-Texte (`chip_check`, `nfc_*`, `csca_*`) aus den
> `strings.xml` weglassen und in `Tx.java` die Chip-Schlüssel ignorieren (stören nicht).

## Chipprüfung per NFC

- **[Chip]** Knopf „Chip prüfen (CAN, NFC)“ auf der Ergebnisseite, eigene Seite `NfcActivity`
- **[Chip]** Zugang mit CAN oder automatisch über die gescannte MRZ (PACE, ersatzweise BAC)
- **[Chip]** passive Authentisierung: Hash DG1, Signatur des SOD, Wurzelzertifikat (CSCA)
- **[Chip]** Abgleich Chip ↔ gedruckte MRZ
- **[Chip]** deutscher Personalausweis: „nur für Behörden lesbar“ statt Fehler
- **[Chip]** genaue Fehleranzeige (Schritt + Meldung, ohne Ausweisdaten)
- **[Chip]** NFC-Berechtigung (kein Internet)
- **[Chip]** Bibliotheken: JMRTD 0.8.0, scuba-sc-android, Bouncy Castle (bcpkix); `packaging`-Block gegen doppelte Metadateien

*Dateien:* `ChipReader.java`, `NfcActivity.java`, `activity_nfc.xml`, `ResultActivity.java` (Knopf), `activity_result.xml`, `AndroidManifest.xml`, `app/build.gradle`

## Wurzelzertifikate

- **[Chip]** keine Listen mehr in der App, kein Download
- **[Chip]** Import über die Android-Dateiauswahl („Liste importieren …“), Ablage im App-Speicher, „Alle entfernen“
- **[Chip]** Formate: ZIP (BSI-Masterliste direkt), `.ml`, ICAO-PKD `.ldif`, `.cer`/`.der`/`.pem`
- **[Chip]** doppelte Zertifikate werden zusammengefasst; Suche zuerst nach Name, dann nach Land

*Dateien:* `CscaStore.java` (neu), `NfcActivity.java`, `activity_nfc.xml`, `ChipReader.java`; `assets/csca/` entfällt

## Projekt

- **[beide]** eigene Kennung für die Testversion: `de.mrzpruefer.chip`, Name „MRZ-Prüfer (Chip)“
- **[beide]** `.gitignore` (Schlüssel, Build-Ordner, `.idea`, Zertifikatslisten)
- **[beide]** `README.md` überarbeitet
- **[beide]** Tests: 16 Unit-Tests der Prüflogik, auch in allen drei Sprachen

---

## Für die öffentliche Version übernehmen

Alles mit **[beide]**. Die öffentliche Version behält:

- `applicationId 'de.mrzpruefer'`, App-Name „MRZ-Prüfer“
- keine NFC-Berechtigung, keine Bibliotheken JMRTD/SCUBA/Bouncy Castle, kein `packaging`-Block
- keinen Knopf „Chip prüfen“, keine `NfcActivity`, `ChipReader`, `CscaStore`
- in Datenschutzerklärung und Lizenzhinweisen keine Abschnitte zur Chipprüfung
