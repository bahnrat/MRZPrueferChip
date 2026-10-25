package de.mrzpruefer;

import android.nfc.tech.IsoDep;

import net.sf.scuba.smartcards.CardService;
import net.sf.scuba.smartcards.CardServiceException;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.ASN1Set;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jmrtd.AccessKeySpec;
import org.jmrtd.BACKey;
import org.jmrtd.PACEKeySpec;
import org.jmrtd.PassportService;
import org.jmrtd.lds.CardAccessFile;
import org.jmrtd.lds.PACEInfo;
import org.jmrtd.lds.SODFile;
import org.jmrtd.lds.SecurityInfo;
import org.jmrtd.lds.icao.DG1File;
import org.jmrtd.lds.icao.MRZInfo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Liest den Chip eines Ausweises/Passes/Visums per NFC und prüft die Echtheit der Daten
 * (ICAO 9303, gilt für TD1, TD2 und TD3 gleichermaßen).
 *
 * Zugang:
 *  - mit CAN (6 Ziffern, steht auf vielen ID-Karten), sonst
 *  - mit den Daten der gedruckten MRZ (Dokumentnummer, Geburts- und Ablaufdatum):
 *    zuerst PACE, falls der Chip es anbietet, sonst BAC (ältere Pässe).
 *
 * Prüfung (passive Authentisierung):
 *  a) Hashwert von DG1 gegen das Sicherheitsobjekt (EF.SOD),
 *  b) Signatur des Sicherheitsobjekts mit dem Document-Signer-Zertifikat,
 *  c) Document-Signer-Zertifikat gegen ein hinterlegtes CSCA-Wurzelzertifikat.
 *
 * Nicht geprüft: Klonschutz (Chip/Active Authentication), Sperr- und Fahndungslisten.
 * Beim deutschen Personalausweis sind die Datengruppen Behörden vorbehalten –
 * das wird erkannt und gemeldet.
 *
 * Blockierend – nur aus einem Hintergrund-Thread aufrufen. Es wird nichts protokolliert.
 */
public final class ChipReader {

    private ChipReader() {}

    static final BouncyCastleProvider BC = new BouncyCastleProvider();
    private static final int SW_SECURITY_STATUS_NOT_SATISFIED = 0x6982;

    /** Zugangsdaten: CAN oder MRZ-Daten (Dokumentnummer, YYMMDD, YYMMDD). */
    public static final class Access {
        final String can, docNumber, birth, expiry;
        private Access(String can, String docNumber, String birth, String expiry) {
            this.can = can; this.docNumber = docNumber; this.birth = birth; this.expiry = expiry;
        }
        public static Access can(String can) { return new Access(can, null, null, null); }
        public static Access mrz(String docNumber, String birth, String expiry) {
            return new Access(null, docNumber, birth, expiry);
        }
        boolean isCan() { return can != null; }
        BACKey bacKey() { return new BACKey(docNumber, birth, expiry); }
    }

    public static final class Result {
        public final List<Mrz.Item> items = new ArrayList<>();
        public MRZInfo mrz;
        public boolean genuine;
        /** true, wenn der Chip das Lesen der Daten verweigert (z. B. deutscher Personalausweis). */
        public boolean restricted;

        void add(Mrz.Status s, String label, String detail) { items.add(new Mrz.Item(s, label, detail)); }

        public Mrz.Status overall() {
            Mrz.Status st = Mrz.Status.OK;
            for (Mrz.Item i : items) {
                if (i.status == Mrz.Status.FAIL) return Mrz.Status.FAIL;
                if (i.status == Mrz.Status.WARN) st = Mrz.Status.WARN;
            }
            return st;
        }
    }

    public static Result read(IsoDep isoDep, Access access, List<X509Certificate> cscas) {
        Result r = new Result();
        PassportService service = null;
        String step = "Verbindung öffnen";
        try {
            isoDep.setTimeout(10_000);
            CardService cardService = CardService.getInstance(isoDep);
            cardService.open();
            service = new PassportService(cardService,
                    PassportService.NORMAL_MAX_TRANCEIVE_LENGTH,
                    PassportService.DEFAULT_MAX_BLOCKSIZE,
                    false, false);
            step = "PassportService öffnen";
            service.open();

            // ---------- Zugang ----------
            step = "Zugang";
            if (!openAccess(service, access, r)) return r;

            // ---------- Daten lesen ----------
            step = "DG1 lesen";
            byte[] dg1Bytes;
            try {
                dg1Bytes = readAll(service.getInputStream(PassportService.EF_DG1, PassportService.DEFAULT_MAX_BLOCKSIZE));
            } catch (CardServiceException e) {
                if (isAccessDenied(e)) {
                    r.restricted = true;
                    r.add(Mrz.Status.WARN, Tx.t("lbl.chipData"), Tx.t("msg.restricted"));
                    return r;
                }
                throw e;
            }
            step = "SOD lesen";
            byte[] sodBytes = readAll(service.getInputStream(PassportService.EF_SOD, PassportService.DEFAULT_MAX_BLOCKSIZE));
            DG1File dg1 = new DG1File(new ByteArrayInputStream(dg1Bytes));
            SODFile sod = new SODFile(new ByteArrayInputStream(sodBytes));
            r.mrz = dg1.getMRZInfo();
            r.add(Mrz.Status.OK, Tx.t("lbl.chipData"), Tx.t("msg.dg1Read"));

            // ---------- a) Hash ----------
            step = "Prüfung Hashwert";
            byte[] expected = sod.getDataGroupHashes().get(1);
            MessageDigest md = MessageDigest.getInstance(sod.getDigestAlgorithm(), BC);
            boolean hashOk = expected != null && Arrays.equals(md.digest(dg1Bytes), expected);
            r.add(hashOk ? Mrz.Status.OK : Mrz.Status.FAIL, Tx.t("lbl.hash"),
                    hashOk ? Tx.t("msg.hashOk", sod.getDigestAlgorithm()) : Tx.t("msg.hashBad"));

            // ---------- b) Signatur ----------
            step = "Prüfung Signatur";
            boolean sigOk = false;
            X509Certificate ds = null;
            try {
                CMSSignedData cms = new CMSSignedData(unwrapTlv(sodBytes, 0x77));
                SignerInformation signer = cms.getSignerInfos().getSigners().iterator().next();
                @SuppressWarnings("unchecked")
                Collection<X509CertificateHolder> certs = cms.getCertificates().getMatches(signer.getSID());
                if (!certs.isEmpty()) {
                    X509CertificateHolder holder = certs.iterator().next();
                    ds = new JcaX509CertificateConverter().setProvider(BC).getCertificate(holder);
                    sigOk = signer.verify(new JcaSimpleSignerInfoVerifierBuilder().setProvider(BC).build(holder));
                }
            } catch (Exception e) {
                sigOk = false;
            }
            r.add(sigOk ? Mrz.Status.OK : Mrz.Status.FAIL, Tx.t("lbl.sig"),
                    sigOk ? Tx.t("msg.sigOk", cn(ds)) : Tx.t("msg.sigBad"));

            // ---------- c) Wurzelzertifikat ----------
            step = "Prüfung Wurzelzertifikat";
            boolean cscaOk = false;
            if (ds != null) {
                X509Certificate root = findCsca(ds, cscas);
                if (cscas.isEmpty()) {
                    r.add(Mrz.Status.WARN, Tx.t("lbl.csca"), Tx.t("msg.cscaNone"));
                } else if (root != null) {
                    cscaOk = true;
                    r.add(Mrz.Status.OK, Tx.t("lbl.csca"), Tx.t("msg.cscaOk", cn(root)));
                } else {
                    String c = country(ds);
                    r.add(Mrz.Status.WARN, Tx.t("lbl.csca"),
                            Tx.t("msg.cscaMissing", c.isEmpty() ? Tx.t("msg.thisIssuer") : Tx.q(c)));
                }
            }
            r.genuine = hashOk && sigOk && cscaOk;

        } catch (Throwable e) {
            r.add(Mrz.Status.FAIL, Tx.t("lbl.conn"), Tx.t("msg.stepErr", Tx.q(step)) + "\n" + err(e));
        } finally {
            if (service != null) try { service.close(); } catch (Exception ignored) { }
            try { isoDep.close(); } catch (Exception ignored) { }
        }
        return r;
    }

    /** PACE (mit CAN oder MRZ), bei Pässen ohne PACE ersatzweise BAC (nur mit MRZ). */
    private static boolean openAccess(PassportService service, Access access, Result r) {
        String how = access.isCan() ? "CAN" : Tx.t("word.mrzData");

        // PACE-Parameter aus EF.CardAccess (fehlt bei älteren Pässen)
        PACEInfo pace = null;
        try {
            CardAccessFile caf = new CardAccessFile(
                    service.getInputStream(PassportService.EF_CARD_ACCESS, PassportService.DEFAULT_MAX_BLOCKSIZE));
            for (SecurityInfo si : caf.getSecurityInfos()) {
                if (si instanceof PACEInfo) { pace = (PACEInfo) si; break; }
            }
        } catch (Exception ignored) {
            // kein EF.CardAccess → BAC versuchen
        }

        Exception paceError = null;
        if (pace != null) {
            try {
                AccessKeySpec key = access.isCan()
                        ? PACEKeySpec.createCANKey(access.can)
                        : PACEKeySpec.createMRZKey(access.bacKey());
                service.doPACE(key, pace.getObjectIdentifier(),
                        PACEInfo.toParameterSpec(pace.getParameterId()), null);
                service.sendSelectApplet(true);
                r.add(Mrz.Status.OK, Tx.t("lbl.access"), Tx.t("msg.accessPace", how));
                return true;
            } catch (Exception e) {
                paceError = e;
            }
        }

        // BAC: nur mit MRZ-Daten möglich
        if (!access.isCan()) {
            try {
                service.sendSelectApplet(false);
                service.doBAC(access.bacKey());
                r.add(Mrz.Status.OK, Tx.t("lbl.access"), Tx.t("msg.accessBac"));
                return true;
            } catch (Exception e) {
                if (paceError == null) paceError = e;
            }
        }

        r.add(Mrz.Status.FAIL, Tx.t("lbl.accessWith", how),
                (access.isCan() ? Tx.t("msg.deniedCan") : Tx.t("msg.deniedMrz"))
                + (paceError != null ? "\n" + err(paceError) : ""));
        return false;
    }

    // ------------------------------------------------------------------
    // Wurzelzertifikate
    // ------------------------------------------------------------------

    /** Passendes CSCA: erst über den Namen, dann über alle des gleichen Landes. */
    static X509Certificate findCsca(X509Certificate ds, List<X509Certificate> cscas) {
        for (X509Certificate c : cscas) {
            if (c.getSubjectX500Principal().equals(ds.getIssuerX500Principal()) && verifies(ds, c)) return c;
        }
        String country = country(ds);
        for (X509Certificate c : cscas) {
            if (!country.isEmpty() && country.equalsIgnoreCase(country(c)) && verifies(ds, c)) return c;
        }
        return null;
    }

    private static boolean verifies(X509Certificate cert, X509Certificate issuer) {
        try {
            cert.verify(issuer.getPublicKey(), BC);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Liest eine CSCA-Masterliste (CMS-Datei, z. B. vom BSI) und liefert alle enthaltenen
     * Zertifikate. Die Signatur der Liste selbst wird nicht geprüft.
     */
    static List<X509Certificate> parseMasterList(byte[] data) throws Exception {
        List<X509Certificate> list = new ArrayList<>();
        CMSSignedData cms = new CMSSignedData(data);
        byte[] content = (byte[]) cms.getSignedContent().getContent();
        ASN1Sequence seq = ASN1Sequence.getInstance(content);
        ASN1Set set = ASN1Set.getInstance(seq.getObjectAt(1));
        JcaX509CertificateConverter conv = new JcaX509CertificateConverter().setProvider(BC);
        for (ASN1Encodable e : set) {
            X509CertificateHolder h = new X509CertificateHolder(
                    org.bouncycastle.asn1.x509.Certificate.getInstance(e));
            list.add(conv.getCertificate(h));
        }
        return list;
    }

    // ------------------------------------------------------------------
    // Hilfsfunktionen
    // ------------------------------------------------------------------

    private static boolean isAccessDenied(CardServiceException e) {
        if (e.getSW() == SW_SECURITY_STATUS_NOT_SATISFIED) return true;
        String m = e.getMessage();
        return m != null && m.toLowerCase().contains("denied");
    }

    /** Fehlerklasse und Meldung, für die Fehlersuche (enthält keine Ausweisdaten). */
    private static String err(Throwable e) {
        Throwable c = e;
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 3 && c != null; i++, c = c.getCause()) {
            if (i > 0) b.append("\n← ");
            b.append(c.getClass().getSimpleName());
            if (c.getMessage() != null) b.append(": ").append(c.getMessage());
        }
        return b.toString();
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toByteArray();
    }

    /** Entfernt die äußere BER-TLV-Hülle (EF.SOD hat Tag 0x77) und liefert den Inhalt. */
    static byte[] unwrapTlv(byte[] d, int tag) throws IOException {
        if ((d[0] & 0xFF) != tag) return d;
        int i = 1;
        int len = d[i++] & 0xFF;
        if (len > 0x80) {
            int n = len & 0x7F;
            len = 0;
            for (int k = 0; k < n; k++) len = (len << 8) | (d[i++] & 0xFF);
        }
        if (i + len > d.length) throw new IOException("TLV zu kurz");
        return Arrays.copyOfRange(d, i, i + len);
    }

    private static String rdn(X509Certificate c, String key) {
        if (c == null) return "";
        for (String part : c.getSubjectX500Principal().getName().split(",")) {
            part = part.trim();
            if (part.startsWith(key + "=")) return part.substring(key.length() + 1);
        }
        return "";
    }

    private static String country(X509Certificate c) {
        if (c == null) return "";
        for (String part : c.getIssuerX500Principal().getName().split(",")) {
            part = part.trim();
            if (part.startsWith("C=")) return part.substring(2);
        }
        return rdn(c, "C");
    }

    private static String cn(X509Certificate c) {
        if (c == null) return "–";
        String cn = rdn(c, "CN");
        String co = rdn(c, "C");
        if (cn.isEmpty()) return c.getSubjectX500Principal().getName();
        return co.isEmpty() ? cn : cn + " (" + co + ")";
    }
}
