package de.mrzpruefer;

import static org.junit.Assert.*;

import java.time.LocalDate;
import java.util.Arrays;
import org.junit.Test;

public class MrzTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 20);

    /** Baut eine stimmige MRZ mit berechneten Prüfziffern. */
    static String[] build(String nr, String birth, String expiry) {
        String l1 = "IDD<<" + nr + Mrz.checkDigit(nr) + "<<<<<<<<<<<<<<<";
        String b = birth + Mrz.checkDigit(birth) + "<" + expiry + Mrz.checkDigit(expiry) + "D<<<<<<<<<<<<<";
        String l2 = b + Mrz.checkDigit(Mrz.compositeData(l1, b + "0"));
        return new String[]{l1, l2, "MUSTERMANN<<ERIKA<<<<<<<<<<<<<"};
    }

    static String[] valid() { return build("T22000129", "640812", "310315"); }

    @Test public void beispielAusDemChat() {
        assertEquals(3, Mrz.checkDigit("T22000129"));
        assertEquals(4, Mrz.checkDigit("LZ6KR4FX7"));
        // Original-Musterausweis: Gesamtprüfziffer 4, aber abgelaufen 2020
        Mrz.Result r = Mrz.check("IDD<<T220001293<<<<<<<<<<<<<<<",
                "6408125<2010315D<<<<<<<<<<<<<4", "MUSTERMANN<<ERIKA<<<<<<<<<<<<<", TODAY);
        assertTrue(r.checkDigitsOk);
        assertEquals(Mrz.Status.FAIL, r.overall()); // abgelaufen
    }

    @Test public void gueltigerAusweis() {
        String[] v = valid();
        assertEquals(30, v[1].length());
        Mrz.Result r = Mrz.check(v[0], v[1], v[2], TODAY);
        for (Mrz.Item i : r.items) System.out.println(i.status + "  " + i.label + ": " + i.detail);
        assertEquals(Mrz.Status.OK, r.overall());
        assertEquals("MUSTERMANN", r.surname);
        assertEquals("ERIKA", r.givenNames);
        assertEquals(LocalDate.of(1964, 8, 12), r.birth);
        assertEquals(LocalDate.of(2031, 3, 15), r.expiry);
    }

    @Test public void falscheZeichenInNummer() {
        // S in der Nummer, Prüfziffern trotzdem korrekt gerechnet → Plausibilität schlägt an
        String[] v = build("LZ6KS4FX7", "640812", "310315");
        Mrz.Result r = Mrz.check(v[0], v[1], v[2], TODAY);
        assertTrue(r.checkDigitsOk);
        assertEquals(Mrz.Status.FAIL, r.overall());
    }

    @Test public void tippfehler() {
        String[] v = valid();
        String l2 = "6408135" + v[1].substring(7);
        assertFalse(Mrz.check(v[0], l2, v[2], TODAY).checkDigitsOk);
    }

    @Test public void ocrTextFinden() {
        String[] v = valid();
        // OCR liest Füllzeichen als K, 0 als O, mit Leerzeichen
        String l1 = v[0].replace("<<<<<<<", "<<KK<<<").replace("T22000", "T22OOO");
        String l2 = v[1].substring(0, 20) + "K" + v[1].substring(21);
        String[] found = Mrz.findTd1(Arrays.asList("BUNDESREPUBLIK", l1.substring(0, 10) + " " + l1.substring(10), l2, v[2]));
        assertNotNull(found);
        String[] fixed = Mrz.ocrCorrect(found[0], found[1], found[2]);
        assertEquals(v[0], fixed[0]);
        assertEquals(v[1], fixed[1]);
        assertTrue(Mrz.checkDigitsValid(fixed[0], fixed[1]));
    }

    @Test public void fuellzeichenErgaenzen() {
        String[] v = build("LZ6KR4FX7", "640812", "310315");
        assertEquals(v[0], Mrz.autoFill("lz6kr4fx74", 1));
        assertEquals(v[0], Mrz.autoFill("IDD LZ6KR4FX74", 1));
        String digits = v[1].substring(0, 7) + v[1].substring(8, 15) + v[1].charAt(29);
        assertEquals(v[1], Mrz.autoFill(digits, 2));
        assertEquals(v[1], Mrz.autoFill(digits.substring(0, 7) + " " + digits.substring(7, 14) + " " + digits.charAt(14), 2));
        assertEquals(v[2], Mrz.autoFill("Mustermann Erika", 3));
        assertEquals(v[2], Mrz.autoFill("MUSTERMANN, ERIKA", 3));
        assertEquals("MUELLER<LUEDENSCHEIDT<<ANNA<MA", Mrz.autoFill("Müller-Lüdenscheidt, Anna Maria", 3).substring(0, 30));
        assertEquals("GOESSLING<<HANS<PETER<<<<<<<<<", Mrz.autoFill("Gößling Hans-Peter", 3));
        // vollständige Zeilen bleiben, wie sie sind
        for (int i = 0; i < 3; i++) assertEquals(v[i], Mrz.autoFill(v[i], i + 1));
        // fehlende Gesamtprüfziffer: Platz bleibt <, Prüfung meldet den Fehler
        String l2 = Mrz.autoFill(digits.substring(0, 14), 2);
        assertEquals(30, l2.length());
        assertFalse(Mrz.check(v[0], l2, v[2], TODAY).checkDigitsOk);
        Mrz.Result ok = Mrz.check(Mrz.autoFill("LZ6KR4FX74", 1), Mrz.autoFill(digits, 2),
                Mrz.autoFill("Mustermann Erika", 3), TODAY);
        assertEquals(Mrz.Status.OK, ok.overall());
    }

    @Test public void dokumentversion() {
        String nr = "LZ6KR4FX7";
        String l1 = "IDD<<" + nr + Mrz.checkDigit(nr) + "<<<<<<<<<<<<<<<";
        String b = "6408125<3103153D<<2108<<<<<<<";
        String l2 = b + Mrz.checkDigit(Mrz.compositeData(l1, b + "0"));
        String l3 = "MUSTERMANN<<ERIKA<<<<<<<<<<<<<";
        Mrz.Result r = Mrz.check(l1, l2, l3, TODAY);
        assertTrue(r.checkDigitsOk);
        assertEquals(Mrz.Status.OK, r.overall());
        assertEquals("2108", r.docVersion);

        // Eintippen mit Version: 19 Ziffern, mit oder ohne Leerzeichen
        String total = l2.substring(29);
        assertEquals(l2, Mrz.autoFill("6408125 3103153 2108 " + total, 2));
        assertEquals(l2, Mrz.autoFill("640812531031532108" + total, 2));
        // Version getippt, Gesamtprüfziffer vergessen
        assertEquals(b + "<", Mrz.autoFill("640812531031532108", 2));
        // OCR liest 2108 als Z1O8
        String[] fixed = Mrz.ocrCorrect(l1, l2.replace("2108", "Z1O8"), l3);
        assertEquals(l2, fixed[1]);
    }

    // ---------------- ICAO allgemein ----------------

    /** Setzt alle Prüfziffern einer Zeile 2 (TD2/TD3) passend. */
    static String[] build2(String l1, String doc, String nat, String birth, char sex, String exp, String opt, int len) {
        String d = doc + Mrz.checkDigit(doc);
        String b = d + nat + birth + Mrz.checkDigit(birth) + sex + exp + Mrz.checkDigit(exp);
        if (len == 44) {
            String pn = opt;
            char pc = pn.matches("<+") ? '<' : (char) ('0' + Mrz.checkDigit(pn));
            b = b + pn + pc;
            String comp = b.substring(0, 10) + b.substring(13, 20) + b.substring(21, 43);
            return new String[]{l1, b + Mrz.checkDigit(comp)};
        }
        b = b + opt;
        String comp = b.substring(0, 10) + b.substring(13, 20) + b.substring(21, 35);
        return new String[]{l1, b + Mrz.checkDigit(comp)};
    }

    @Test public void deutscherReisepass() {
        String l1 = "P<D<<MUSTERMANN<<ERIKA<<<<<<<<<<<<<<<<<<<<<<";
        String[] p = build2(l1, "C01X00T47", "D<<", "640812", 'F', "310315", "<<<<<<<<<<<<<<", 44);
        assertEquals(44, p[1].length());
        Mrz.Result r = Mrz.check(p, TODAY);
        for (Mrz.Item i : r.items) System.out.println("  " + i.status + "  " + i.label + ": " + i.detail);
        assertEquals(Mrz.Format.TD3, r.format);
        assertTrue(r.checkDigitsOk);
        assertEquals(Mrz.Status.OK, r.overall());
        assertEquals("ERIKA", r.givenNames);
    }

    @Test public void auslaendischerPassMitPersonennummer() {
        String l1 = "P<NLDDE<BRUIJN<<WILLEKE<LISELOTTE<<<<<<<<<<<";
        String[] p = build2(l1, "SPECI2014", "NLD", "650310", 'F', "300309", "999999990<<<<<", 44);
        Mrz.Result r = Mrz.check(p, TODAY);
        assertTrue(r.checkDigitsOk);
        assertEquals(Mrz.Status.OK, r.overall());   // ausländisch, aber grün
        assertEquals("DE BRUIJN", r.surname);
        // Tippfehler in der persönlichen Nummer fällt auf
        String bad = p[1].substring(0, 30) + "8" + p[1].substring(31);
        assertFalse(Mrz.check(new String[]{p[0], bad}, TODAY).checkDigitsOk);
    }

    @Test public void alterAusweisTd2() {
        String l1 = "IDD<<MUSTERMANN<<ERIKA<<<<<<<<<<<<<<";
        String[] p = build2(l1, "122000129", "D<<", "640812", '<', "200315", "<<<<<<<", 36);
        Mrz.Result r = Mrz.check(p, TODAY);
        assertEquals(Mrz.Format.TD2, r.format);
        assertTrue(r.checkDigitsOk);
        assertEquals(Mrz.Status.FAIL, r.overall());  // nur weil abgelaufen
        long fails = r.items.stream().filter(i -> i.status == Mrz.Status.FAIL).count();
        assertEquals(1, fails);
    }

    @Test public void auslaendischeIdKarteGruen() {
        String doc = "SPEC12014";
        String l1 = "I<NLD" + doc + Mrz.checkDigit(doc) + "999999990<<<<<<";
        String b = "650310" + Mrz.checkDigit("650310") + "F" + "300309" + Mrz.checkDigit("300309") + "NLD<<<<<<<<<<<";
        String l2 = b + Mrz.checkDigit(l1.substring(5, 30) + b.substring(0, 7) + b.substring(8, 15) + b.substring(18, 29));
        Mrz.Result r = Mrz.check(new String[]{l1, l2, "DE<BRUIJN<<WILLEKE<<<<<<<<<<<<"}, TODAY);
        assertEquals(Mrz.Status.OK, r.overall());
    }

    @Test public void langeDokumentnummerTd1() {
        // 12-stellige Nummer: 9 Zeichen + "<" + Rest im optionalen Feld, dahinter die Prüfziffer
        String full = "D23145890734";
        String l1 = "I<UTO" + full.substring(0, 9) + "<" + full.substring(9) + Mrz.checkDigit(full) + "<<<<<<<<<<<";
        String b = "7408122F1204159UTO<<<<<<<<<<<";
        String l2 = b + Mrz.checkDigit(l1.substring(5, 30) + b.substring(0, 7) + b.substring(8, 15) + b.substring(18, 29));
        Mrz.Result r = Mrz.check(new String[]{l1, l2, "ERIKSSON<<ANNA<MARIA<<<<<<<<<<"}, LocalDate.of(2011, 1, 1));
        assertTrue(r.checkDigitsOk);
        assertEquals(full, r.docNumber);
    }

    @Test public void visumOhneGesamtpruefziffer() {
        String l1 = "V<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<";
        String doc = "L8988901C", birth = "740812", exp = "300415";
        String l2 = doc + Mrz.checkDigit(doc) + "XXX" + birth + Mrz.checkDigit(birth) + "F" + exp + Mrz.checkDigit(exp) + "6ZE184226B<<<<<<";
        assertEquals(44, l2.length());
        Mrz.Result r = Mrz.check(new String[]{l1, l2}, TODAY);
        assertTrue(r.checkDigitsOk);
        assertEquals(Mrz.Status.OK, r.overall());
    }

    @Test public void passScannenMitLaengenfehlern() {
        String l1 = "P<D<<MUSTERMANN<<ERIKA<<<<<<<<<<<<<<<<<<<<<<";
        String[] p = build2(l1, "C01X00T47", "D<<", "640812", 'F', "310315", "<<<<<<<<<<<<<<", 44);
        // OCR: zwei "<" zu wenig im Namen, eins zu viel in der Datenzeile, K statt <, O statt 0
        String o1 = l1.substring(0, 40) + "K<";
        String o2 = p[1].substring(0, 30) + "<" + p[1].substring(30).replace("<<<<", "<K<<");
        o2 = o2.replace("C01X00", "C01XO0");
        String[] found = Mrz.findMrz(Arrays.asList("REISEPASS", o1, o2));
        assertNotNull(found);
        assertEquals(2, found.length);
        String[] best = Mrz.bestReading(found);
        assertNotNull(best);
        assertArrayEquals(p, best);
    }

    @Test public void eingabeErgaenzenAllgemein() {
        String l1 = "P<D<<MUSTERMANN<<ERIKA<<<<<<<<<<<<<<<<<<<<<<";
        String[] p = build2(l1, "C01X00T47", "D<<", "640812", 'F', "310315", "<<<<<<<<<<<<<<", 44);
        assertEquals(l1, Mrz.autoFill("P D Mustermann, Erika", Mrz.InputMode.TD3, 1));
        assertEquals(l1, Mrz.autoFill("P<D<<MUSTERMANN<<ERIKA", Mrz.InputMode.TD3, 1));
        // lange <-Kette vor der letzten Ziffer durch Leerzeichen ersetzt
        String typed = p[1].substring(0, 28) + " " + p[1].substring(43);
        assertEquals(p[1], Mrz.autoFill(typed, Mrz.InputMode.TD3, 2));
        assertEquals("I<NLDSPEC120144<<<<<<<<<<<<<<<", Mrz.autoFill("I NLD SPEC120144", Mrz.InputMode.TD1, 1));
        assertEquals("DE<BRUIJN<<WILLEKE<<<<<<<<<<<<", Mrz.autoFill("de Bruijn, Willeke", Mrz.InputMode.TD1, 3));
    }

    @Test public void sprachen() {
        String[][] docs = {
            valid(),
            build("LZ6KS4FX7", "640812", "200315"),   // falsche Zeichen + abgelaufen
            {"P<D<<MUSTERMANN<<ERIKA<<<<<<<<<<<<<<<<<<<<<<", "C01X00T478D<<6408125F3103153<<<<<<<<<<<<<<<9"},
            {"IDD<<X", "6408125", "MUSTERMANN<<ERIKA"}       // Formatfehler
        };
        try {
            for (String lang : new String[]{"de", "en", "fr"}) {
                Tx.forcedLang = lang;
                for (String[] d : docs) {
                    for (Mrz.Item i : Mrz.check(d, TODAY).items) {
                        assertFalse(lang + ": " + i.label, i.label.startsWith("lbl.") || i.label.startsWith("msg."));
                        assertFalse(lang + ": " + i.detail, i.detail.contains("msg.") || i.detail.contains("{0}"));
                    }
                }
            }
            Tx.forcedLang = "fr";
            Mrz.Result r = Mrz.check(valid(), TODAY);
            assertTrue(r.items.get(r.items.size() - 1).label.contains("nom"));
            assertEquals("15/03/2031", Mrz.fmt(r.expiry));
            Tx.forcedLang = "en";
            for (Mrz.Item i : Mrz.check(build("LZ6KS4FX7", "640812", "200315"), TODAY).items)
                System.out.println("  EN " + i.status + "  " + i.label + ": " + i.detail);
            Tx.forcedLang = "fr";
            for (Mrz.Item i : Mrz.check(build("LZ6KS4FX7", "640812", "200315"), TODAY).items)
                System.out.println("  FR " + i.status + "  " + i.label + ": " + i.detail);
        } finally {
            Tx.forcedLang = null;
        }
    }
}
