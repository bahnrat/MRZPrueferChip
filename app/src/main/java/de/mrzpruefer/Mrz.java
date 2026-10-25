package de.mrzpruefer;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Prüflogik für maschinenlesbare Zonen nach ICAO 9303.
 * <ul>
 *   <li>TD1 – 3 × 30 Zeichen (Personalausweise, ID-Karten, Aufenthaltstitel)</li>
 *   <li>TD2 – 2 × 36 Zeichen (ältere Ausweise, manche Aufenthaltstitel, Visum Typ B)</li>
 *   <li>TD3 – 2 × 44 Zeichen (Reisepässe, Visum Typ A)</li>
 * </ul>
 * Zusätzliche Plausibilitätsregeln für deutsche Dokumente greifen nur beim Aussteller D.
 * Reines Java ohne Android-Abhängigkeiten, damit es sich auch am PC testen lässt.
 */
public final class Mrz {

    private Mrz() {}

    public enum Status { OK, WARN, FAIL }

    public enum Format {
        TD1(3, 30, "TD1 (3 × 30)"),
        TD2(2, 36, "TD2 (2 × 36)"),
        TD3(2, 44, "TD3 (2 × 44)");

        public final int lines, length;
        public final String label;
        Format(int lines, int length, String label) {
            this.lines = lines; this.length = length; this.label = label;
        }

        /** Format anhand der Zeilenzahl und der Länge der ersten Zeile. */
        public static Format of(int lineCount, int firstLength) {
            if (lineCount == 3) return TD1;
            if (lineCount == 2) return Math.abs(firstLength - 44) < Math.abs(firstLength - 36) ? TD3 : TD2;
            return null;
        }
    }

    /** Eingabemodus der manuellen Erfassung. */
    public enum InputMode {
        DE_ID(Format.TD1), TD1(Format.TD1), TD2(Format.TD2), TD3(Format.TD3);
        public final Format format;
        InputMode(Format f) { format = f; }
    }

    /** Ein einzelner Prüfpunkt für die Ergebnisanzeige. */
    public static final class Item {
        public final Status status;
        public final String label;
        public final String detail;
        Item(Status status, String label, String detail) {
            this.status = status; this.label = label; this.detail = detail;
        }
    }

    /** Gesamtergebnis mit ausgelesenen Daten zum Abgleich mit der Vorderseite. */
    public static final class Result {
        public final List<Item> items = new ArrayList<>();
        public Format format;
        public String docType = "", docNumber = "", issuer = "", nationality = "";
        public String surname = "", givenNames = "", docVersion = "";
        public LocalDate birth, expiry;
        public boolean checkDigitsOk;

        public Status overall() {
            Status s = Status.OK;
            for (Item i : items) {
                if (i.status == Status.FAIL) return Status.FAIL;
                if (i.status == Status.WARN) s = Status.WARN;
            }
            return s;
        }
        void add(Status st, String label, String detail) { items.add(new Item(st, label, detail)); }
    }

    // ------------------------------------------------------------------
    // Prüfziffer nach ICAO 9303: Gewichte 7-3-1, "<" = 0, A = 10 … Z = 35
    // ------------------------------------------------------------------

    public static int charValue(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'A' && c <= 'Z') return c - 'A' + 10;
        if (c == '<') return 0;
        throw new IllegalArgumentException("Ungültiges MRZ-Zeichen: " + c);
    }

    public static int checkDigit(String s) {
        final int[] w = {7, 3, 1};
        int sum = 0;
        for (int i = 0; i < s.length(); i++) sum += charValue(s.charAt(i)) * w[i % 3];
        return sum % 10;
    }

    private static boolean digitOk(String data, char check) {
        return check >= '0' && check <= '9' && checkDigit(data) == check - '0';
    }

    // ------------------------------------------------------------------
    // Feldaufteilung je Format
    // ------------------------------------------------------------------

    /** Lage aller Felder einer MRZ, unabhängig vom Format. */
    static final class Layout {
        Format format;
        String type, issuer, name, nationality, sex;
        String docNumber;            // ggf. inkl. Verlängerung im optionalen Feld
        char docCheck;
        boolean longDocNumber;
        String birth, expiry;
        char birthCheck, expiryCheck;
        String optional1 = "";       // TD1 Zeile 1 / TD2 / TD3 (bei TD3: persönliche Nummer)
        String optional2 = "";       // nur TD1 Zeile 2
        Character optionalCheck;     // nur TD3: Prüfziffer der persönlichen Nummer
        String compositeData;        // null = keine Gesamtprüfziffer (Visum)
        char compositeCheck;
        // Positionen für die OCR-Korrektur: {Zeile, von, bis}
        final List<int[]> digitFields = new ArrayList<>();
        final List<int[]> fillerFields = new ArrayList<>();
        int[] docField, nationalityField, issuerField, nameField;
    }

    static Layout layout(String[] l) {
        Format f = Format.of(l.length, l[0].length());
        if (f == null) return null;
        for (String s : l) if (s.length() != f.length) return null;
        Layout y = new Layout();
        y.format = f;
        y.type = l[0].substring(0, 2);
        y.issuer = l[0].substring(2, 5);
        y.issuerField = new int[]{0, 2, 5};
        boolean visa = l[0].charAt(0) == 'V';

        if (f == Format.TD1) {
            String a = l[0], b = l[1];
            y.docNumber = a.substring(5, 14);
            y.docCheck = a.charAt(14);
            y.optional1 = a.substring(15, 30);
            y.birth = b.substring(0, 6);   y.birthCheck = b.charAt(6);
            y.sex = b.substring(7, 8);
            y.expiry = b.substring(8, 14); y.expiryCheck = b.charAt(14);
            y.nationality = b.substring(15, 18);
            y.optional2 = b.substring(18, 29);
            y.compositeData = a.substring(5, 30) + b.substring(0, 7) + b.substring(8, 15) + b.substring(18, 29);
            y.compositeCheck = b.charAt(29);
            y.name = l[2];
            y.docField = new int[]{0, 5, 14};
            y.nationalityField = new int[]{1, 15, 18};
            y.nameField = new int[]{2, 0, 30};
            y.digitFields.add(new int[]{1, 0, 7});
            y.digitFields.add(new int[]{1, 8, 15});
            y.digitFields.add(new int[]{1, 29, 30});
            y.fillerFields.add(new int[]{0, 15, 30});
            y.fillerFields.add(new int[]{1, 18, 29});
        } else {
            String a = l[0], b = l[1];
            int n = f.length;
            y.name = a.substring(5, n);
            y.docNumber = b.substring(0, 9);
            y.docCheck = b.charAt(9);
            y.nationality = b.substring(10, 13);
            y.birth = b.substring(13, 19);  y.birthCheck = b.charAt(19);
            y.sex = b.substring(20, 21);
            y.expiry = b.substring(21, 27); y.expiryCheck = b.charAt(27);
            y.docField = new int[]{1, 0, 9};
            y.nationalityField = new int[]{1, 10, 13};
            y.nameField = new int[]{0, 5, n};
            y.digitFields.add(new int[]{1, 13, 20});
            y.digitFields.add(new int[]{1, 21, 28});
            if (visa) {
                y.optional1 = b.substring(28, n);
                y.compositeData = null;
                y.fillerFields.add(new int[]{1, 28, n});
            } else if (f == Format.TD3) {
                y.optional1 = b.substring(28, 42);
                y.optionalCheck = b.charAt(42);
                y.compositeData = b.substring(0, 10) + b.substring(13, 20) + b.substring(21, 43);
                y.compositeCheck = b.charAt(43);
                y.fillerFields.add(new int[]{1, 28, 42});
                y.digitFields.add(new int[]{1, 43, 44});
            } else {
                y.optional1 = b.substring(28, 35);
                y.compositeData = b.substring(0, 10) + b.substring(13, 20) + b.substring(21, 35);
                y.compositeCheck = b.charAt(35);
                y.fillerFields.add(new int[]{1, 28, 35});
                y.digitFields.add(new int[]{1, 35, 36});
            }
        }

        // Ausweisnummer länger als 9 Zeichen (nur TD1/TD2): an Stelle der Prüfziffer steht "<",
        // die Nummer läuft im optionalen Feld weiter, ihr letztes Zeichen ist die Prüfziffer.
        if (f != Format.TD3 && y.docCheck == '<' && !y.optional1.isEmpty() && y.optional1.charAt(0) != '<') {
            int end = y.optional1.indexOf('<');
            String ext = end < 0 ? y.optional1 : y.optional1.substring(0, end);
            if (ext.length() >= 1) {
                y.docNumber = y.docNumber + ext.substring(0, ext.length() - 1);
                y.docCheck = ext.charAt(ext.length() - 1);
                y.longDocNumber = true;
            }
        } else if (y.docField != null) {
            int[] d = y.docField;
            y.digitFields.add(new int[]{d[0], d[2], d[2] + 1});   // Prüfziffer der Nummer
        }
        return y;
    }

    // ------------------------------------------------------------------
    // Aufbereitung von OCR-Text
    // ------------------------------------------------------------------

    /** Leerzeichen entfernen, typische OCR-Verwechslungen des Füllzeichens bereinigen. */
    public static String cleanLine(String raw) {
        String s = raw.toUpperCase()
                .replace("«", "<<")
                .replace("‹", "<")
                .replace("〈", "<")
                .replace("＜", "<");
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            if ((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '<') b.append(c);
        }
        return b.toString();
    }

    /**
     * Gleicht kleine Längenfehler der OCR aus, die fast immer in langen Ketten von "<" stecken.
     * Bei Namenszeilen wird am Ende ergänzt bzw. gekürzt, sonst in der letzten langen Kette.
     */
    static String normalizeLength(String s, int len, boolean fillerAtEnd) {
        int diff = len - s.length();
        if (diff == 0) return s;
        if (Math.abs(diff) > 3) return null;
        if (fillerAtEnd) {
            if (diff > 0) return s + fillers(diff);
            return s.substring(s.length() + diff).matches("<+") ? s.substring(0, len) : null;
        }
        // letzte Kette aus mindestens 3 "<" suchen
        int end = -1, start = -1;
        for (int i = s.length() - 1; i >= 0; i--) {
            if (s.charAt(i) == '<') {
                int j = i;
                while (j >= 0 && s.charAt(j) == '<') j--;
                if (i - j >= 3 && i - j + diff >= 1) { start = j + 1; end = i + 1; break; }
                i = j;
            }
        }
        if (start < 0) return null;
        int newRun = end - start + diff;
        return s.substring(0, start) + fillers(newRun) + s.substring(end);
    }

    private static boolean digitLike(char c) {
        return Character.isDigit(fixDigit(c));
    }

    /**
     * Sucht in den (von oben nach unten sortierten) Textzeilen eine MRZ:
     * drei Zeilen TD1 oder zwei Zeilen TD2/TD3. Liefert null, wenn nichts passt.
     */
    public static String[] findMrz(List<String> rawLines) {
        List<String> c = new ArrayList<>();
        for (String r : rawLines) {
            String s = cleanLine(r);
            if (s.length() >= 25) c.add(s);
        }
        // TD1: Zeile 1 beginnt mit A, C oder I, Zeile 2 mit dem Geburtsdatum
        for (int i = 0; i + 2 < c.size(); i++) {
            String a = c.get(i);
            if ("ACI".indexOf(a.charAt(0)) < 0) continue;
            String l1 = normalizeLength(a, 30, true);
            String l2 = normalizeLength(c.get(i + 1), 30, false);
            String l3 = normalizeLength(c.get(i + 2), 30, true);
            if (l1 != null && l2 != null && l3 != null && digitLike(l2.charAt(0)) && digitLike(l2.charAt(5))) {
                return new String[]{l1, l2, l3};
            }
        }
        // TD2/TD3: Zeile 1 beginnt mit Dokumentart, Zeile 2 trägt ab Pos. 14 das Geburtsdatum
        for (int i = 0; i + 1 < c.size(); i++) {
            String a = c.get(i);
            if ("ACIPV".indexOf(a.charAt(0)) < 0) continue;
            int len = Math.abs(a.length() - 44) <= 3 ? 44 : Math.abs(a.length() - 36) <= 3 ? 36 : 0;
            if (len == 0) continue;
            String l1 = normalizeLength(a, len, true);
            String l2 = normalizeLength(c.get(i + 1), len, false);
            if (l1 != null && l2 != null && digitLike(l2.charAt(13)) && digitLike(l2.charAt(18))) {
                return new String[]{l1, l2};
            }
        }
        return null;
    }

    /** Für ältere Aufrufer: nur TD1. */
    public static String[] findTd1(List<String> rawLines) {
        String[] m = findMrz(rawLines);
        return m != null && m.length == 3 ? m : null;
    }

    // Buchstaben, die die OCR häufig statt einer Ziffer liest
    static char fixDigit(char c) {
        switch (c) {
            case 'O': case 'Q': case 'D': case 'U': return '0';
            case 'I': case 'L': return '1';
            case 'Z': return '2';
            case 'S': return '5';
            case 'G': return '6';
            case 'B': return '8';
            default: return c;
        }
    }

    // In deutschen Dokumentnummern nicht vorkommende Buchstaben sind fast immer Ziffern
    static char fixGermanDocChar(char c) {
        switch (c) {
            case 'O': case 'Q': case 'D': return '0';
            case 'I': return '1';
            case 'S': return '5';
            case 'B': return '8';
            default: return c;
        }
    }

    private interface CharFix { char apply(char c); }

    private static String fixRange(String s, int from, int to, CharFix f) {
        char[] a = s.toCharArray();
        for (int i = from; i < to; i++) a[i] = f.apply(a[i]);
        return new String(a);
    }

    /** "<" wird oft als K gelesen: K am Ende eines Feldes nach dem Inhalt wird zu "<". */
    private static String fixFiller(String s, int from, int to) {
        int lastContent = from - 1;
        for (int i = from; i < to; i++) {
            char c = s.charAt(i);
            if (c != '<' && c != 'K') lastContent = i;
        }
        return fixRange(s, lastContent + 1, to, c -> c == 'K' ? '<' : c);
    }

    /** In Namen: K in der abschließenden Füllkette (nach mindestens 3 "<") wird zu "<". */
    private static String fixNameTail(String s, int from, int to) {
        String f = s.substring(from, to);
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("<{3}[<K]*$").matcher(f);
        if (!m.find()) return s;
        return s.substring(0, from + m.start()) + m.group().replace('K', '<') + s.substring(to);
    }

    /**
     * Korrigiert typische OCR-Fehler feldweise. Ob die Korrektur stimmt, entscheiden
     * anschließend die Prüfziffern – siehe {@link #bestReading(String[])}.
     */
    public static String[] ocrCorrect(String[] in) {
        String[] l = in.clone();
        // Ausstellerstaat D: "D<<", auch wenn ein Füllzeichen als K gelesen wurde
        if (l[0].length() >= 5 && l[0].substring(2, 5).matches("D[<K][<K]")) {
            l[0] = l[0].substring(0, 2) + "D<<" + l[0].substring(5);
        }
        Layout y = layout(l);
        if (y == null) return l;
        boolean german = y.issuer.equals("D<<");

        for (int[] f : y.fillerFields) l[f[0]] = fixFiller(l[f[0]], f[1], f[2]);
        for (int[] f : y.digitFields) l[f[0]] = fixRange(l[f[0]], f[1], f[2], Mrz::fixDigit);
        if (y.nameField != null) l[y.nameField[0]] = fixNameTail(l[y.nameField[0]], y.nameField[1], y.nameField[2]);

        int[] nf = y.nationalityField;
        if (l[nf[0]].substring(nf[1], nf[2]).matches("D[<K][<K]")) {
            l[nf[0]] = l[nf[0]].substring(0, nf[1]) + "D<<" + l[nf[0]].substring(nf[2]);
        }
        if (german && !y.longDocNumber) {
            int[] d = y.docField;
            l[d[0]] = fixRange(l[d[0]], d[1], d[2], Mrz::fixGermanDocChar);
        }
        // Dokumentversion deutscher Ausweise (z. B. 2108) in Zeile 2, Pos. 19–22
        if (german && y.format == Format.TD1 && l[1].substring(18, 22).matches(".*[0-9].*")) {
            l[1] = fixRange(l[1], 18, 22, Mrz::fixDigit);
        }
        return l;
    }

    /** Für ältere Aufrufer: TD1 mit drei Einzelzeilen. */
    public static String[] ocrCorrect(String l1, String l2, String l3) {
        return ocrCorrect(new String[]{l1, l2, l3});
    }

    /** Korrigierte Lesung, wenn deren Prüfziffern stimmen, sonst die unveränderte, sonst null. */
    public static String[] bestReading(String[] raw) {
        String[] fixed = ocrCorrect(raw);
        if (checkDigitsValid(fixed)) return fixed;
        if (checkDigitsValid(raw)) return raw;
        return null;
    }

    /** Schnelltest für den Kamerabetrieb: stimmen alle Prüfziffern? */
    public static boolean checkDigitsValid(String[] l) {
        Layout y;
        try {
            for (String s : l) if (!s.matches("[A-Z0-9<]+")) return false;
            y = layout(l);
        } catch (RuntimeException e) {
            return false;
        }
        if (y == null) return false;
        boolean ok = digitOk(y.docNumber, y.docCheck)
                && digitOk(y.birth, y.birthCheck)
                && digitOk(y.expiry, y.expiryCheck);
        if (y.compositeData != null) ok &= digitOk(y.compositeData, y.compositeCheck);
        if (y.optionalCheck != null) ok &= optionalCheckOk(y);
        return ok;
    }

    public static boolean checkDigitsValid(String l1, String l2) {
        return checkDigitsValid(new String[]{l1, l2, fillers(30)});
    }

    private static boolean optionalCheckOk(Layout y) {
        char c = y.optionalCheck;
        if (y.optional1.matches("<+")) return c == '<' || c == '0';
        return digitOk(y.optional1, c);
    }

    static String compositeData(String l1, String l2) {
        return l1.substring(5, 30) + l2.substring(0, 7) + l2.substring(8, 15) + l2.substring(18, 29);
    }

    // ------------------------------------------------------------------
    // Manuelle Eingabe: feste Teile und Füllzeichen ergänzen
    // ------------------------------------------------------------------

    private static String pad(String s, int len) {
        StringBuilder b = new StringBuilder(s.length() > len ? s.substring(0, len) : s);
        while (b.length() < len) b.append('<');
        return b.toString();
    }

    private static String pad(String s) { return pad(s, 30); }

    static String fillers(int n) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n; i++) b.append('<');
        return b.toString();
    }

    private static String translit(String s) {
        return s.toUpperCase().trim()
                .replace("Ä", "AE").replace("Ö", "OE").replace("Ü", "UE").replace("ß", "SS")
                .replace("Å", "AA").replace("Ø", "OE").replace("Æ", "AE");
    }

    /** „NAME VORNAME“ / „NAME, VORNAME VORNAME2“ / „NAME<<VORNAME“ → MRZ-Namensfeld. */
    static String nameField(String raw, int len) {
        String t = translit(raw);
        String s = cleanLine(t.replace(' ', '<').replace(',', '<'));
        if (s.length() == len && s.contains("<<")) return s;
        String name;
        if (t.contains(",")) {
            int c = t.indexOf(',');
            name = cleanLine(t.substring(0, c).trim().replace(' ', '<').replace('-', '<'))
                    + "<<" + cleanLine(t.substring(c + 1).trim().replace(' ', '<').replace('-', '<'));
        } else if (t.contains("<<")) {
            name = cleanLine(t.replace(' ', '<').replace('-', '<'));
        } else {
            String u = t.replace('-', ' ').replaceAll("\\s+", " ");
            int sp = u.indexOf(' ');
            name = sp < 0 ? cleanLine(u) + "<<"
                    : cleanLine(u.substring(0, sp)) + "<<" + cleanLine(u.substring(sp + 1).replace(' ', '<'));
        }
        return pad(name, len);
    }

    /**
     * Datenzeile: Leerzeichen werden zu "<". Ist die Zeile zu kurz, wird das letzte
     * Leerzeichen bzw. die letzte "<"-Kette so verlängert, dass die Länge stimmt.
     */
    static String dataLine(String raw, int len) {
        String t = translit(raw);
        String s = cleanLine(t.replace(' ', '<'));
        if (s.length() >= len) return s;
        int last = s.lastIndexOf('<');
        if (last < 0) return s;   // nichts zum Auffüllen – Prüfung meldet die Länge
        return s.substring(0, last) + fillers(len - s.length()) + s.substring(last);
    }

    /** Erste Zeile von TD2/TD3: „P D MUSTERMANN, ERIKA“ oder vollständig mit "<". */
    static String headerNameLine(String raw, int len) {
        String t = translit(raw);
        if (!t.contains(" ") && !t.contains(",")) return pad(cleanLine(t), len);
        String[] p = t.split("\\s+", 3);
        if (p.length < 3) return pad(cleanLine(t.replace(' ', '<')), len);
        String type = pad(cleanLine(p[0]), 2);
        String issuer = pad(cleanLine(p[1]), 3);
        return type + issuer + nameField(p[2], len - 5);
    }

    /** Ergänzt alle Zeilen auf einmal entsprechend dem Eingabemodus. */
    public static String[] autoFill(String[] in, InputMode mode) {
        String[] out = new String[in.length];
        for (int i = 0; i < in.length; i++) {
            String s = in[i] == null ? "" : in[i];
            if (s.trim().isEmpty()) { out[i] = ""; continue; }
            out[i] = autoFill(s, mode, i + 1);
        }
        return out;
    }

    public static String autoFill(String input, InputMode mode, int line) {
        if (input == null || input.trim().isEmpty()) return "";
        int len = mode.format.length;
        switch (mode) {
            case DE_ID:
                return autoFill(input, line);
            case TD1:
                if (line == 1) {
                    String t = translit(input);
                    String[] p = t.split("\\s+");
                    if (p.length >= 3 && p[0].length() <= 2 && p[1].length() <= 3) {
                        StringBuilder rest = new StringBuilder();
                        for (int i = 2; i < p.length; i++) { if (i > 2) rest.append('<'); rest.append(p[i]); }
                        return pad(pad(cleanLine(p[0]), 2) + pad(cleanLine(p[1]), 3) + cleanLine(rest.toString()), len);
                    }
                    return pad(cleanLine(t.replace(' ', '<')), len);
                }
                if (line == 2) return dataLine(input, len);
                return nameField(input, len);
            default: // TD2, TD3
                return line == 1 ? headerNameLine(input, len) : dataLine(input, len);
        }
    }

    /**
     * Deutscher Personalausweis (TD1): ergänzt eine von Hand getippte Zeile.
     * Eine bereits vollständige 30-Zeichen-Zeile bleibt unverändert.
     * <ul>
     *   <li>Zeile 1: nur Ausweisnummer + Prüfziffer, z. B. „LZ6KR4FX74“</li>
     *   <li>Zeile 2: Geburtsdatum+PZ, Ablaufdatum+PZ, ggf. Version (2108), Gesamt-PZ</li>
     *   <li>Zeile 3: „NAME VORNAME“ oder „NAME, VORNAME VORNAME2“</li>
     * </ul>
     */
    public static String autoFill(String input, int line) {
        String raw = input == null ? "" : translit(input);
        if (raw.isEmpty()) return "";
        String s = cleanLine(raw.replace(' ', '<').replace(',', '<'));
        if (line != 3 && s.length() == 30) return s;

        switch (line) {
            case 1: {
                String body = s.replace("<", "");
                if (body.startsWith("IDD")) body = body.substring(3);
                else if (body.startsWith("ID")) body = body.substring(2);
                return pad("IDD<<" + body);
            }
            case 2: {
                // 14/15 Ziffern: ohne Dokumentversion, 18/19 Ziffern: mit (z. B. 2108)
                String d = s.replaceAll("[^0-9]", "");
                if (d.length() < 7) return pad(d);
                String birth = d.substring(0, 7);
                String rest = d.substring(7);
                if (rest.length() < 7) return pad(birth + "<" + rest);
                String expiry = rest.substring(0, 7);
                String tail = rest.substring(7);
                String version = "", total = "<";
                if (tail.length() >= 4) {
                    version = tail.substring(0, 4);
                    if (tail.length() >= 5) total = tail.substring(4, 5);
                } else if (tail.length() >= 1) {
                    total = tail.substring(0, 1);
                }
                return birth + "<" + expiry + "D<<" + version + fillers(11 - version.length()) + total;
            }
            default:
                return nameField(input, 30);
        }
    }

    // ------------------------------------------------------------------
    // Vollständige Prüfung
    // ------------------------------------------------------------------

    static final String GERMAN_DOC_CHARS = "0123456789CFGHJKLMNPRTVWXYZ";
    private static final DateTimeFormatter DE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public static String fmt(LocalDate d) { return d == null ? "–" : d.format(DateTimeFormatter.ofPattern(Tx.datePattern())); }

    private static String typeName(char c) {
        switch (c) {
            case 'I': return Tx.t("type.I");
            case 'P': return Tx.t("type.P");
            case 'V': return Tx.t("type.V");
            case 'A': case 'C': return Tx.t("type.A");
            default:  return Tx.t("type.x");
        }
    }

    /** Für ältere Aufrufer: TD1 mit drei Einzelzeilen. */
    public static Result check(String l1, String l2, String l3, LocalDate today) {
        return check(new String[]{l1, l2, l3}, today);
    }

    public static Result check(String[] in, LocalDate today) {
        Result r = new Result();
        List<String> ls = new ArrayList<>();
        for (String s : in) {
            String t = s == null ? "" : s.trim().toUpperCase();
            if (!t.isEmpty() || ls.size() < 2) ls.add(t);
        }
        String[] l = ls.toArray(new String[0]);

        Format f = l.length >= 1 ? Format.of(l.length, l[0].length()) : null;
        if (f == null) {
            r.add(Status.FAIL, Tx.t("lbl.format"), Tx.t("msg.lines"));
            return r;
        }
        r.format = f;
        for (int i = 0; i < l.length; i++) {
            if (!l[i].matches("[A-Z0-9<]*")) {
                r.add(Status.FAIL, Tx.t("lbl.formatLine", i + 1), Tx.t("msg.badChars"));
            } else if (l[i].length() != f.length) {
                r.add(Status.FAIL, Tx.t("lbl.formatLine", i + 1),
                        Tx.t("msg.lineLen", l[i].length(), f.length, f.label));
            }
        }
        if (!r.items.isEmpty()) return r;

        Layout y = layout(l);
        char t0 = y.type.charAt(0);
        boolean visa = t0 == 'V';
        r.docType = y.type.replace("<", "");
        r.issuer = y.issuer.replace("<", "");
        r.nationality = y.nationality.replace("<", "");
        r.docNumber = y.docNumber.replace("<", "");
        boolean german = r.issuer.equals("D");
        boolean germanOwnDoc = german && (t0 == 'I' || t0 == 'P');   // nur an Deutsche ausgegeben

        // Dokumentart und Format
        String allowed = f == Format.TD1 ? "ACI" : f == Format.TD2 ? "ACIPV" : "PV";
        if (allowed.indexOf(t0) < 0) {
            r.add(Status.FAIL, Tx.t("lbl.docType"), Tx.t("msg.typeNotInFormat", Tx.q(y.type), f.label));
        } else {
            r.add(Status.OK, Tx.t("lbl.docType"), r.docType + " – " + typeName(t0) + ", " + f.label);
        }
        if (!y.issuer.matches("[A-Z]{1,3}<*")) {
            r.add(Status.FAIL, Tx.t("lbl.issuer"), Tx.t("msg.noCountry", Tx.q(y.issuer)));
        } else {
            r.add(Status.OK, Tx.t("lbl.issuer"), r.issuer + (german ? Tx.t("msg.germany") : ""));
        }

        // Prüfziffern
        boolean c1 = digitOk(y.docNumber, y.docCheck);
        boolean c2 = digitOk(y.birth, y.birthCheck);
        boolean c3 = digitOk(y.expiry, y.expiryCheck);
        boolean ok = c1 && c2 && c3;
        addDigit(r, Tx.t("lbl.cdDoc") + (y.longDocNumber ? Tx.t("msg.longNr") : ""),
                y.docNumber, y.docCheck, c1);
        addDigit(r, Tx.t("lbl.cdBirth"), y.birth, y.birthCheck, c2);
        addDigit(r, Tx.t("lbl.cdExpiry"), y.expiry, y.expiryCheck, c3);
        if (y.optionalCheck != null) {
            boolean c5 = optionalCheckOk(y);
            ok &= c5;
            if (y.optional1.matches("<+")) {
                r.add(c5 ? Status.OK : Status.FAIL, Tx.t("lbl.cdPersonal"),
                        c5 ? Tx.t("msg.fieldEmpty") : Tx.t("msg.fieldEmptyBad", Tx.q(y.optionalCheck)));
            } else {
                addDigit(r, Tx.t("lbl.cdPersonal"), y.optional1, y.optionalCheck, c5);
            }
        }
        if (y.compositeData != null) {
            boolean c4 = digitOk(y.compositeData, y.compositeCheck);
            ok &= c4;
            addDigit(r, Tx.t("lbl.cdComposite"), y.compositeData, y.compositeCheck, c4);
        } else if (visa) {
            r.add(Status.OK, Tx.t("lbl.cdComposite"), Tx.t("msg.visaNoComposite"));
        }
        r.checkDigitsOk = ok;

        // Dokumentnummer
        if (r.docNumber.isEmpty()) {
            r.add(Status.FAIL, Tx.t("lbl.docNr"), Tx.t("msg.empty"));
        } else if (germanOwnDoc && !y.longDocNumber) {
            StringBuilder bad = new StringBuilder();
            for (char c : r.docNumber.toCharArray()) {
                if (GERMAN_DOC_CHARS.indexOf(c) < 0 && bad.indexOf(String.valueOf(c)) < 0) {
                    if (bad.length() > 0) bad.append(", ");
                    bad.append(c);
                }
            }
            if (bad.length() == 0) {
                r.add(Status.OK, Tx.t("lbl.docChars"), Tx.t("msg.docCharsOk"));
            } else {
                r.add(Status.FAIL, Tx.t("lbl.docChars"), Tx.t("msg.docCharsBad", bad));
            }
        }

        // Geburtsdatum (unbekannte Teile dürfen mit < angegeben sein)
        r.birth = parseDate(y.birth, today, true);
        if (r.birth == null && y.birth.contains("<")) {
            r.add(Status.WARN, Tx.t("lbl.birth"), Tx.t("msg.partUnknown", Tx.q(y.birth)));
        } else if (r.birth == null) {
            r.add(Status.FAIL, Tx.t("lbl.birth"), Tx.t("msg.noDate", Tx.q(y.birth)));
        } else if (r.birth.isAfter(today)) {
            r.add(Status.FAIL, Tx.t("lbl.birth"), Tx.t("msg.future", fmt(r.birth)));
        } else {
            int age = Period.between(r.birth, today).getYears();
            if (age > 110) {
                r.add(Status.WARN, Tx.t("lbl.birth"), Tx.t("msg.ageUnlikely", fmt(r.birth), age));
            } else {
                r.add(Status.OK, Tx.t("lbl.birth"), Tx.t("msg.age", fmt(r.birth), age));
            }
        }

        // Ablaufdatum
        r.expiry = parseDate(y.expiry, today, false);
        if (r.expiry == null && y.expiry.matches("<{6}")) {
            r.add(Status.WARN, Tx.t("lbl.expiry"), Tx.t("msg.noExpiry"));
        } else if (r.expiry == null) {
            r.add(Status.FAIL, Tx.t("lbl.expiry"), Tx.t("msg.noDate", Tx.q(y.expiry)));
        } else if (r.expiry.isBefore(today)) {
            r.add(Status.FAIL, Tx.t("lbl.expiry"), Tx.t("msg.expired", fmt(r.expiry)));
        } else if (germanOwnDoc && r.expiry.isAfter(today.plusYears(10).plusMonths(1))) {
            r.add(Status.WARN, Tx.t("lbl.expiry"), Tx.t("msg.tooLong", fmt(r.expiry)));
        } else {
            r.add(Status.OK, Tx.t("lbl.expiry"), fmt(r.expiry));
        }
        if (r.birth != null && r.expiry != null && !r.expiry.isAfter(r.birth)) {
            r.add(Status.FAIL, Tx.t("lbl.dateOrder"), Tx.t("msg.expBeforeBirth"));
        }

        // Geschlecht
        if ("MFX<".indexOf(y.sex.charAt(0)) < 0) {
            r.add(Status.FAIL, Tx.t("lbl.sex"), Tx.t("msg.badValue", Tx.q(y.sex)));
        }

        // Staatsangehörigkeit
        if (!y.nationality.matches("[A-Z]{1,3}<*")) {
            r.add(Status.FAIL, Tx.t("lbl.nationality"), Tx.t("msg.noCountry", Tx.q(y.nationality)));
        } else if (germanOwnDoc && !r.nationality.equals("D")) {
            r.add(Status.WARN, Tx.t("lbl.nationality"), Tx.t("msg.germanOnly", r.nationality));
        } else {
            r.add(Status.OK, Tx.t("lbl.nationality"), r.nationality);
        }

        // Besonderheiten deutscher Personalausweise (TD1)
        if (german && t0 == 'I' && f == Format.TD1) {
            String opt2 = y.optional2;
            if (opt2.matches("\\d{4}<{7}")) {
                r.docVersion = opt2.substring(0, 4);
                int yy = Integer.parseInt(r.docVersion.substring(0, 2));
                int mm = Integer.parseInt(r.docVersion.substring(2, 4));
                if (mm >= 1 && mm <= 12 && yy >= 10 && 2000 + yy <= today.getYear()) {
                    r.add(Status.OK, Tx.t("lbl.docVersion"),
                            Tx.t("msg.docVersion", r.docVersion, String.format("%02d/%d", mm, 2000 + yy)));
                } else {
                    r.add(Status.WARN, Tx.t("lbl.docVersion"), Tx.t("msg.docVersionBad", Tx.q(r.docVersion)));
                }
            } else if (!opt2.matches("<+")) {
                r.add(Status.WARN, Tx.t("lbl.filler2"), Tx.t("msg.filler2", Tx.q(opt2)));
            }
            if (!y.optional1.matches("<+")) {
                r.add(Status.WARN, Tx.t("lbl.filler1"), Tx.t("msg.filler1"));
            }
        }

        // Name
        String nm = y.name;
        if (!nm.matches("[A-Z<]+")) {
            r.add(Status.FAIL, Tx.t("lbl.name"), Tx.t("msg.nameDigits"));
        } else if (nm.startsWith("<")) {
            r.add(Status.FAIL, Tx.t("lbl.name"), Tx.t("msg.nameFiller"));
        } else {
            int sep = nm.indexOf("<<");
            if (sep < 0) {
                // Name passt nicht ganz in die Zeile oder nur ein Familienname
                r.surname = nm.replace('<', ' ').trim();
                r.add(Status.WARN, Tx.t("lbl.name"), Tx.t("msg.nameNoSep"));
            } else {
                r.surname = nm.substring(0, sep).replace('<', ' ').trim();
                r.givenNames = nm.substring(sep + 2).replaceAll("<+$", "").replace('<', ' ').trim();
                r.add(r.givenNames.isEmpty() ? Status.WARN : Status.OK, Tx.t("lbl.name"),
                        r.givenNames.isEmpty() ? Tx.t("msg.noGiven") : Tx.t("msg.nameOk"));
            }
        }
        return r;
    }

    private static void addDigit(Result r, String label, String data, char found, boolean ok) {
        int calc = checkDigit(data);
        r.add(ok ? Status.OK : Status.FAIL, label,
                ok ? Tx.t("msg.cdOk", calc) : Tx.t("msg.cdBad", Tx.q(found), calc));
    }

    /** YYMMDD → Datum. Geburtsdaten in der Zukunft rutschen ins vorige Jahrhundert. */
    static LocalDate parseDate(String yymmdd, LocalDate today, boolean birth) {
        if (!yymmdd.matches("\\d{6}")) return null;
        int yy = Integer.parseInt(yymmdd.substring(0, 2));
        int mm = Integer.parseInt(yymmdd.substring(2, 4));
        int dd = Integer.parseInt(yymmdd.substring(4, 6));
        int century = (today.getYear() / 100) * 100;
        int year = century + yy;
        if (birth && year > today.getYear()) year -= 100;
        if (!birth && year < today.getYear() - 50) year += 100;
        try {
            return LocalDate.of(year, mm, dd);
        } catch (DateTimeException e) {
            return null;
        }
    }
}
