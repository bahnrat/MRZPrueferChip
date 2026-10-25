package de.mrzpruefer;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Texte der Prüfmeldungen auf Deutsch, Englisch und Französisch.
 * Reines Java (ohne Android), damit die Prüflogik weiter am PC testbar bleibt.
 * Die Sprache folgt der Spracheinstellung des Geräts; alles außer en/fr → Deutsch.
 * Platzhalter: {0}, {1}, … (bewusst kein MessageFormat – das stolpert über Apostrophe im Französischen).
 */
public final class Tx {

    private Tx() {}

    private static final Map<String, String[]> T = new HashMap<>();
    /** Zum Testen fest einstellbar; null = Gerätesprache. */
    static String forcedLang = null;

    private static void p(String key, String de, String en, String fr) {
        T.put(key, new String[]{de, en, fr});
    }

    static String lang() {
        String l = forcedLang != null ? forcedLang : Locale.getDefault().getLanguage();
        return "en".equals(l) || "fr".equals(l) ? l : "de";
    }

    private static int idx() {
        switch (lang()) {
            case "en": return 1;
            case "fr": return 2;
            default:   return 0;
        }
    }

    /** Text mit Platzhaltern {0}, {1}, … */
    public static String t(String key, Object... args) {
        String[] v = T.get(key);
        String s = v == null ? key : v[idx()];
        for (int i = 0; i < args.length; i++) s = s.replace("{" + i + "}", String.valueOf(args[i]));
        return s;
    }

    /** Wert in landesüblichen Anführungszeichen. */
    public static String q(Object x) {
        switch (lang()) {
            case "en": return "“" + x + "”";
            case "fr": return "« " + x + " »";
            default:   return "„" + x + "“";
        }
    }

    /** Datumsformat: de 31.12.2030, en/fr 31/12/2030. */
    static String datePattern() {
        return "de".equals(lang()) ? "dd.MM.yyyy" : "dd/MM/yyyy";
    }

    static {
        // ---------- Dokumentarten ----------
        p("type.I", "Personalausweis / ID-Karte", "identity card", "carte d'identité");
        p("type.P", "Reisepass", "passport", "passeport");
        p("type.V", "Visum", "visa", "visa");
        p("type.A", "amtliches Dokument (z. B. Aufenthaltstitel)", "official document (e.g. residence permit)",
                "document officiel (p. ex. titre de séjour)");
        p("type.x", "unbekannt", "unknown", "inconnu");

        // ---------- Format ----------
        p("lbl.format", "Format", "Format", "Format");
        p("lbl.formatLine", "Format Zeile {0}", "Format line {0}", "Format ligne {0}");
        p("msg.lines", "Zwei oder drei Zeilen erwartet", "Two or three lines expected", "Deux ou trois lignes attendues");
        p("msg.badChars", "Unzulässige Zeichen (erlaubt: A–Z, 0–9, <)", "Invalid characters (allowed: A–Z, 0–9, <)",
                "Caractères non autorisés (autorisés : A–Z, 0–9, <)");
        p("msg.lineLen", "Zeile hat {0} statt {1} Zeichen ({2})", "Line has {0} instead of {1} characters ({2})",
                "La ligne comporte {0} caractères au lieu de {1} ({2})");

        // ---------- Kopfdaten ----------
        p("lbl.docType", "Dokumentart", "Document type", "Type de document");
        p("msg.typeNotInFormat", "{0} ist im Format {1} nicht vorgesehen", "{0} is not provided for in format {1}",
                "{0} n'est pas prévu dans le format {1}");
        p("lbl.issuer", "Ausstellerstaat", "Issuing state", "État émetteur");
        p("msg.noCountry", "{0} ist kein Ländercode", "{0} is not a country code", "{0} n'est pas un code pays");
        p("msg.germany", " (Deutschland)", " (Germany)", " (Allemagne)");

        // ---------- Prüfziffern ----------
        p("lbl.cdDoc", "Prüfziffer Dokumentnummer", "Check digit document number", "Chiffre de contrôle du numéro de document");
        p("msg.longNr", " (verlängerte Nummer)", " (extended number)", " (numéro étendu)");
        p("lbl.cdBirth", "Prüfziffer Geburtsdatum", "Check digit date of birth", "Chiffre de contrôle de la date de naissance");
        p("lbl.cdExpiry", "Prüfziffer Ablaufdatum", "Check digit date of expiry", "Chiffre de contrôle de la date d'expiration");
        p("lbl.cdPersonal", "Prüfziffer persönliche Nummer", "Check digit personal number", "Chiffre de contrôle du numéro personnel");
        p("lbl.cdComposite", "Gesamtprüfziffer", "Composite check digit", "Chiffre de contrôle global");
        p("msg.cdOk", "stimmt ({0})", "correct ({0})", "correct ({0})");
        p("msg.cdBad", "gelesen {0}, berechnet {1}", "read {0}, calculated {1}", "lu {0}, calculé {1}");
        p("msg.fieldEmpty", "Feld leer", "field empty", "champ vide");
        p("msg.fieldEmptyBad", "Feld leer, erwartet < oder 0, gelesen {0}", "field empty, expected < or 0, read {0}",
                "champ vide, attendu < ou 0, lu {0}");
        p("msg.visaNoComposite", "beim Visum nicht vorgesehen", "not provided for visas", "non prévu pour les visas");

        // ---------- Dokumentnummer ----------
        p("lbl.docNr", "Dokumentnummer", "Document number", "Numéro de document");
        p("msg.empty", "leer", "empty", "vide");
        p("lbl.docChars", "Zeichen Dokumentnummer", "Characters of document number", "Caractères du numéro de document");
        p("msg.docCharsOk", "nur zulässige Ziffern und Buchstaben", "only permitted digits and letters",
                "uniquement des chiffres et lettres autorisés");
        p("msg.docCharsBad", "enthält {0} – kommt in deutschen Dokumentnummern nicht vor",
                "contains {0} – not used in German document numbers",
                "contient {0} – absent des numéros de documents allemands");

        // ---------- Datumsangaben ----------
        p("lbl.birth", "Geburtsdatum", "Date of birth", "Date de naissance");
        p("msg.partUnknown", "{0} – teilweise unbekannt", "{0} – partly unknown", "{0} – partiellement inconnue");
        p("msg.noDate", "{0} ist kein gültiges Datum", "{0} is not a valid date", "{0} n'est pas une date valide");
        p("msg.future", "{0} liegt in der Zukunft", "{0} is in the future", "{0} est dans le futur");
        p("msg.ageUnlikely", "{0} – Alter {1} Jahre ist unwahrscheinlich", "{0} – an age of {1} years is unlikely",
                "{0} – un âge de {1} ans est improbable");
        p("msg.age", "{0} ({1} Jahre)", "{0} ({1} years)", "{0} ({1} ans)");
        p("lbl.expiry", "Gültig bis", "Valid until", "Valable jusqu'au");
        p("msg.noExpiry", "kein Ablaufdatum angegeben", "no expiry date given", "aucune date d'expiration indiquée");
        p("msg.expired", "{0} – abgelaufen", "{0} – expired", "{0} – expiré");
        p("msg.tooLong", "{0} – mehr als 10 Jahre in der Zukunft", "{0} – more than 10 years in the future",
                "{0} – plus de 10 ans dans le futur");
        p("lbl.dateOrder", "Datumsfolge", "Date sequence", "Ordre des dates");
        p("msg.expBeforeBirth", "Ablaufdatum liegt vor dem Geburtsdatum", "expiry date is before date of birth",
                "la date d'expiration précède la date de naissance");

        // ---------- Weitere Felder ----------
        p("lbl.sex", "Geschlechtsfeld", "Sex field", "Champ sexe");
        p("msg.badValue", "{0} ist kein zulässiger Wert", "{0} is not a permitted value", "{0} n'est pas une valeur autorisée");
        p("lbl.nationality", "Staatsangehörigkeit", "Nationality", "Nationalité");
        p("msg.germanOnly", "{0} – deutsche Pässe und Ausweise gibt es nur für Deutsche (D)",
                "{0} – German passports and ID cards are issued only to German nationals (D)",
                "{0} – les passeports et cartes d'identité allemands ne sont délivrés qu'aux ressortissants allemands (D)");
        p("lbl.docVersion", "Dokumentversion", "Document version", "Version du document");
        p("msg.docVersion", "{0} (Ausweismuster {1})", "{0} (card model {1})", "{0} (modèle de carte {1})");
        p("msg.docVersionBad", "{0} ist kein plausibles Jahr/Monat", "{0} is not a plausible year/month",
                "{0} n'est pas une année/un mois plausible");
        p("lbl.filler2", "Füllbereich Zeile 2", "Filler area line 2", "Zone de remplissage ligne 2");
        p("msg.filler2", "{0} – erwartet leer oder Dokumentversion (4 Ziffern)",
                "{0} – expected empty or document version (4 digits)",
                "{0} – attendu vide ou version du document (4 chiffres)");
        p("lbl.filler1", "Füllbereich Zeile 1", "Filler area line 1", "Zone de remplissage ligne 1");
        p("msg.filler1", "Optionale Felder sind beim deutschen Ausweis leer (<)",
                "Optional fields are empty (<) on German ID cards",
                "Les champs optionnels sont vides (<) sur la carte d'identité allemande");

        // ---------- Name ----------
        p("lbl.name", "Namensfeld", "Name field", "Champ du nom");
        p("msg.nameDigits", "enthält Ziffern", "contains digits", "contient des chiffres");
        p("msg.nameFiller", "beginnt mit Füllzeichen – Familienname fehlt", "starts with a filler – surname missing",
                "commence par un caractère de remplissage – nom manquant");
        p("msg.nameNoSep", "keine Trennung Name/Vorname (<<) – evtl. gekürzt",
                "no separator between surname and given names (<<) – possibly truncated",
                "pas de séparation nom/prénoms (<<) – peut-être tronqué");
        p("msg.noGiven", "kein Vorname angegeben", "no given name", "aucun prénom indiqué");
        p("msg.nameOk", "Aufbau stimmt", "structure correct", "structure correcte");

        // ---------- Chipprüfung ----------
        p("lbl.chipData", "Chipdaten", "Chip data", "Données de la puce");
        p("msg.restricted", "nur für Behörden lesbar – beim deutschen Personalausweis so vorgesehen. "
                        + "Der Zugang hat geklappt, die Echtheit lässt sich privat aber nicht prüfen.",
                "readable by authorities only – intended this way for the German ID card. "
                        + "Access worked, but authenticity cannot be verified privately.",
                "lisibles uniquement par les autorités – prévu ainsi pour la carte d'identité allemande. "
                        + "L'accès a fonctionné, mais l'authenticité ne peut pas être vérifiée à titre privé.");
        p("msg.dg1Read", "digitale MRZ (Datengruppe 1) gelesen", "digital MRZ (data group 1) read",
                "ZLA numérique (groupe de données 1) lue");
        p("lbl.hash", "Hashwert Datengruppe 1", "Hash of data group 1", "Empreinte du groupe de données 1");
        p("msg.hashOk", "passt zum Sicherheitsobjekt ({0})", "matches the security object ({0})",
                "correspond à l'objet de sécurité ({0})");
        p("msg.hashBad", "passt NICHT zum Sicherheitsobjekt – Daten verändert",
                "does NOT match the security object – data altered",
                "ne correspond PAS à l'objet de sécurité – données modifiées");
        p("lbl.sig", "Signatur Sicherheitsobjekt", "Signature of security object", "Signature de l'objet de sécurité");
        p("msg.sigOk", "gültig – Signaturstelle: {0}", "valid – signer: {0}", "valide – signataire : {0}");
        p("msg.sigBad", "ungültig oder nicht prüfbar", "invalid or not verifiable", "invalide ou non vérifiable");
        p("lbl.csca", "Wurzelzertifikat (CSCA)", "Root certificate (CSCA)", "Certificat racine (CSCA)");
        p("msg.cscaNone", "keine Liste importiert – Herkunft der Signatur nicht geprüft",
                "no list imported – origin of the signature not verified",
                "aucune liste importée – origine de la signature non vérifiée");
        p("msg.cscaOk", "signiert von {0}", "signed by {0}", "signé par {0}");
        p("msg.cscaMissing", "für {0} kein passendes Wurzelzertifikat in den importierten Listen – Herkunft nicht geprüft",
                "no matching root certificate for {0} in the imported lists – origin not verified",
                "aucun certificat racine correspondant pour {0} dans les listes importées – origine non vérifiée");
        p("msg.thisIssuer", "diesen Aussteller", "this issuer", "cet émetteur");
        p("lbl.conn", "Verbindung", "Connection", "Connexion");
        p("msg.stepErr", "Fehler beim Schritt {0}", "Error at step {0}", "Erreur à l'étape {0}");
        p("lbl.access", "Zugang", "Access", "Accès");
        p("lbl.accessWith", "Zugang mit {0}", "Access with {0}", "Accès avec {0}");
        p("word.mrzData", "MRZ-Daten", "MRZ data", "données de la ZLA");
        p("msg.accessPace", "mit {0} (PACE) – sicherer Kanal aufgebaut", "with {0} (PACE) – secure channel established",
                "avec {0} (PACE) – canal sécurisé établi");
        p("msg.accessBac", "mit MRZ-Daten (BAC) – sicherer Kanal aufgebaut",
                "with MRZ data (BAC) – secure channel established",
                "avec les données de la ZLA (BAC) – canal sécurisé établi");
        p("msg.deniedCan", "abgelehnt – CAN falsch oder Dokument zu früh entfernt",
                "refused – wrong CAN or document removed too early",
                "refusé – CAN incorrect ou document retiré trop tôt");
        p("msg.deniedMrz", "abgelehnt – MRZ falsch gelesen (Nummer, Geburts- oder Ablaufdatum) oder Dokument zu früh entfernt",
                "refused – MRZ misread (number, date of birth or expiry) or document removed too early",
                "refusé – ZLA mal lue (numéro, date de naissance ou d'expiration) ou document retiré trop tôt");
        p("lbl.compare", "Abgleich Chip ↔ gedruckte MRZ", "Chip ↔ printed MRZ", "Puce ↔ ZLA imprimée");
        p("msg.compareOk", "Nummer, Name, Geburts- und Ablaufdatum stimmen überein",
                "number, name, date of birth and expiry match",
                "numéro, nom, date de naissance et d'expiration concordent");
        p("msg.compareBad", "Abweichung bei {0}", "mismatch in {0}", "divergence : {0}");
        p("word.surname", "Familienname", "surname", "nom");
        p("word.expiry", "Ablaufdatum", "date of expiry", "date d'expiration");
    }
}
