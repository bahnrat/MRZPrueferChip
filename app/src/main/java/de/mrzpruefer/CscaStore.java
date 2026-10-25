package de.mrzpruefer;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Selbst importierte Wurzelzertifikate (CSCA) im app-eigenen Speicher.
 *
 * Kein Download: Der Nutzer lädt die Liste selbst herunter (z. B. BSI-Masterliste,
 * ICAO-PKD) und wählt die Datei über die Android-Dateiauswahl aus. Die App braucht dafür
 * weder Internet- noch Speicherberechtigung. Die Dateien werden unverändert abgelegt
 * und bei jedem Start neu eingelesen.
 *
 * Unterstützte Formate:
 *  - ZIP (z. B. GermanMasterList.zip) – alle enthaltenen Dateien werden gelesen
 *  - CSCA-Masterliste (CMS, z. B. .ml vom BSI)
 *  - ICAO-PKD-LDIF (.ldif) mit Masterlisten bzw. Zertifikaten
 *  - einzelne Zertifikate (.cer/.crt/.der/.pem, auch mehrere PEM in einer Datei)
 *
 * Die Signatur der Masterlisten selbst wird nicht geprüft – deshalb nur Dateien
 * aus vertrauenswürdiger Quelle importieren.
 */
public final class CscaStore {

    private CscaStore() {}

    private static final long MAX_SIZE = 50L * 1024 * 1024;

    public static final class FileInfo {
        public final String name;
        public final int count;
        FileInfo(String name, int count) { this.name = name; this.count = count; }
    }

    public static final class Loaded {
        public final List<X509Certificate> certs = new ArrayList<>();
        public final List<FileInfo> files = new ArrayList<>();
    }

    private static File dir(Context ctx) {
        File d = new File(ctx.getFilesDir(), "csca");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    /** Alle abgelegten Dateien einlesen; doppelte Zertifikate werden zusammengefasst. */
    public static Loaded load(Context ctx) {
        Loaded l = new Loaded();
        Set<String> seen = new HashSet<>();
        File[] files = dir(ctx).listFiles();
        if (files == null) return l;
        Arrays.sort(files);
        for (File f : files) {
            int n = 0;
            try {
                for (X509Certificate c : parseAny(Files.readAllBytes(f.toPath()), f.getName())) {
                    n++;
                    if (seen.add(fingerprint(c))) l.certs.add(c);
                }
            } catch (Exception ignored) { }
            l.files.add(new FileInfo(f.getName(), n));
        }
        return l;
    }

    /**
     * Übernimmt eine vom Nutzer gewählte Datei. Liefert die Zahl der gefundenen Zertifikate;
     * bei 0 wird die Datei nicht abgelegt.
     */
    public static int importUri(Context ctx, Uri uri) throws IOException {
        String name = displayName(ctx, uri);
        byte[] data;
        try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("Datei nicht lesbar");
            data = readAll(in, MAX_SIZE);
        }
        int n;
        try {
            n = parseAny(data, name).size();
        } catch (Exception e) {
            n = 0;
        }
        if (n == 0) return 0;
        File target = new File(dir(ctx), safeName(name));
        try (FileOutputStream out = new FileOutputStream(target)) {
            out.write(data);
        }
        return n;
    }

    public static void clear(Context ctx) {
        File[] files = dir(ctx).listFiles();
        if (files != null) for (File f : files) f.delete();
    }

    public static String displayName(Context ctx, Uri uri) {
        String name = null;
        try (Cursor c = ctx.getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) name = c.getString(0);
        } catch (Exception ignored) { }
        if (name == null || name.isEmpty()) name = uri.getLastPathSegment();
        return name == null ? "liste" : name;
    }

    // ------------------------------------------------------------------
    // Formate
    // ------------------------------------------------------------------

    /** Erkennt das Format am Inhalt (nicht nur an der Endung). */
    static List<X509Certificate> parseAny(byte[] data, String name) throws Exception {
        if (data.length >= 4 && data[0] == 'P' && data[1] == 'K' && data[2] == 3 && data[3] == 4) {
            return parseZip(data);
        }
        String head = new String(data, 0, Math.min(data.length, 4096), StandardCharsets.ISO_8859_1);
        if (head.startsWith("dn:") || head.contains("\ndn:") || head.startsWith("version:")) {
            return parseLdif(data);
        }
        if (head.contains("-----BEGIN CERTIFICATE-----")) {
            return parseCertificates(data);
        }
        // DER: erst als Masterliste (CMS) versuchen, sonst als einzelnes Zertifikat
        try {
            List<X509Certificate> ml = ChipReader.parseMasterList(data);
            if (!ml.isEmpty()) return ml;
        } catch (Exception ignored) { }
        return parseCertificates(data);
    }

    private static List<X509Certificate> parseZip(byte[] data) throws IOException {
        List<X509Certificate> list = new ArrayList<>();
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(data))) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                byte[] entry = readAll(zin, MAX_SIZE);
                try {
                    list.addAll(parseAny(entry, e.getName()));
                } catch (Exception ignored) { }
            }
        }
        return list;
    }

    /** ICAO-PKD-LDIF: base64-kodierte Masterlisten und Zertifikate. */
    private static List<X509Certificate> parseLdif(byte[] data) {
        List<X509Certificate> list = new ArrayList<>();
        String text = new String(data, StandardCharsets.UTF_8).replace("\r\n", "\n");
        // Fortsetzungszeilen (beginnen mit einem Leerzeichen) anhängen
        String unfolded = text.replace("\n ", "");
        for (String line : unfolded.split("\n")) {
            int sep = line.indexOf(":: ");
            if (sep <= 0) continue;
            String attr = line.substring(0, sep).toLowerCase();
            byte[] value;
            try {
                value = Base64.getMimeDecoder().decode(line.substring(sep + 3).trim());
            } catch (IllegalArgumentException e) {
                continue;
            }
            try {
                if (attr.startsWith("pkdmasterlistcontent")) {
                    list.addAll(ChipReader.parseMasterList(value));
                } else if (attr.startsWith("usercertificate") || attr.startsWith("cacertificate")) {
                    list.addAll(parseCertificates(value));
                }
            } catch (Exception ignored) { }
        }
        return list;
    }

    private static List<X509Certificate> parseCertificates(byte[] data) throws Exception {
        List<X509Certificate> list = new ArrayList<>();
        CertificateFactory cf = CertificateFactory.getInstance("X.509", ChipReader.BC);
        for (Certificate c : cf.generateCertificates(new ByteArrayInputStream(data))) {
            if (c instanceof X509Certificate) list.add((X509Certificate) c);
        }
        return list;
    }

    // ------------------------------------------------------------------

    private static String fingerprint(X509Certificate c) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(c.getEncoded());
            StringBuilder b = new StringBuilder();
            for (byte x : d) b.append(String.format("%02x", x));
            return b.toString();
        } catch (Exception e) {
            return String.valueOf(System.identityHashCode(c));
        }
    }

    private static String safeName(String name) {
        String s = name.replaceAll("[^A-Za-z0-9._-]", "_");
        return s.isEmpty() ? "liste" : s;
    }

    private static byte[] readAll(InputStream in, long max) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        long total = 0;
        int n;
        while ((n = in.read(buf)) > 0) {
            total += n;
            if (total > max) throw new IOException("Datei zu groß");
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }
}
