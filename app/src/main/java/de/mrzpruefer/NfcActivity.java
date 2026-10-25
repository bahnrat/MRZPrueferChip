package de.mrzpruefer;

import android.graphics.Typeface;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.jmrtd.lds.icao.MRZInfo;

import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

/**
 * Chipprüfung per NFC mit der CAN (6 Ziffern, Vorderseite des Ausweises unten rechts).
 * Vergleicht die Chipdaten zusätzlich mit der zuvor gescannten oder eingetippten MRZ.
 */
public class NfcActivity extends AppCompatActivity implements NfcAdapter.ReaderCallback {

    public static final String EXTRA_MRZ = "mrz";

    private NfcAdapter nfc;
    private EditText canField;
    private TextView status, verdict;
    private LinearLayout items;
    private String[] ocrLines;
    private volatile String can = "";
    private volatile boolean busy = false;
    private volatile List<X509Certificate> cscas = new ArrayList<>();
    private TextView cscaInfo;
    private Button btnClear;

    /** Android-Dateiauswahl: der Nutzer wählt eine selbst heruntergeladene Liste aus. */
    private final ActivityResultLauncher<String[]> pickFile =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::importFile);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_nfc);

        // Android bringt eine abgespeckte Bouncy-Castle-Version mit; die vollständige registrieren
        Security.removeProvider("BC");
        Security.insertProviderAt(ChipReader.BC, 1);

        canField = findViewById(R.id.can);
        status = findViewById(R.id.nfcStatus);
        verdict = findViewById(R.id.nfcVerdict);
        items = findViewById(R.id.nfcItems);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        ocrLines = getIntent().getStringArrayExtra(EXTRA_MRZ);
        canField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable e) {
                can = e.toString().trim();
                updateHint();
            }
        });

        nfc = NfcAdapter.getDefaultAdapter(this);

        cscaInfo = findViewById(R.id.cscaInfo);
        btnClear = findViewById(R.id.btnCscaClear);
        findViewById(R.id.btnCscaImport).setOnClickListener(v -> pickFile.launch(new String[]{"*/*"}));
        btnClear.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setMessage(R.string.csca_clear_confirm)
                .setPositiveButton(R.string.csca_clear, (d, w) -> { CscaStore.clear(this); reloadCscas(); })
                .setNegativeButton(R.string.cancel, null)
                .show());

        // Listen können einige hundert Zertifikate enthalten – im Hintergrund laden
        reloadCscas();
        updateHint();
    }

    /** Zugang aus CAN (falls eingegeben) oder aus der gescannten/eingetippten MRZ. */
    private ChipReader.Access access() {
        if (can.matches("\\d{6}")) return ChipReader.Access.can(can);
        if (ocrLines == null) return null;
        try {
            Mrz.Layout y = Mrz.layout(ocrLines);
            if (y == null || !Mrz.checkDigitsValid(ocrLines)) return null;
            return ChipReader.Access.mrz(clean(y.docNumber), y.birth, y.expiry);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void updateHint() {
        if (nfc == null) { status.setText(R.string.nfc_none); return; }
        if (!nfc.isEnabled()) { status.setText(R.string.nfc_off); return; }
        ChipReader.Access a = access();
        if (a == null) status.setText(R.string.nfc_enter_can);
        else status.setText(a.isCan() ? R.string.nfc_hold_can : R.string.nfc_hold_mrz);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (nfc != null && nfc.isEnabled()) {
            nfc.enableReaderMode(this, this,
                    NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_NFC_B
                            | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, null);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (nfc != null) nfc.disableReaderMode(this);
    }

    /** Wird von Android in einem Hintergrund-Thread aufgerufen. */
    @Override
    public void onTagDiscovered(Tag tag) {
        IsoDep iso = IsoDep.get(tag);
        if (iso == null || busy) return;
        ChipReader.Access a = access();
        if (a == null) {
            runOnUiThread(() -> status.setText(R.string.nfc_enter_can));
            return;
        }
        busy = true;
        runOnUiThread(() -> status.setText(R.string.nfc_reading));
        ChipReader.Result r = ChipReader.read(iso, a, cscas);
        if (r.mrz != null && ocrLines != null) compareWithOcr(r);
        runOnUiThread(() -> show(r));
        busy = false;
    }

    private void compareWithOcr(ChipReader.Result r) {
        Mrz.Layout y;
        try { y = Mrz.layout(ocrLines); } catch (RuntimeException e) { y = null; }
        if (y == null) return;
        MRZInfo m = r.mrz;
        boolean nr = clean(m.getDocumentNumber()).equals(clean(y.docNumber));
        boolean birth = m.getDateOfBirth().equals(y.birth);
        boolean exp = m.getDateOfExpiry().equals(y.expiry);
        String chipName = clean(m.getPrimaryIdentifier());          // JMRTD: primary = Familienname
        String ocrName = clean(y.name.split("<<")[0]);
        boolean name = chipName.equals(ocrName);
        boolean all = nr && birth && exp && name;
        r.add(all ? Mrz.Status.OK : Mrz.Status.FAIL, Tx.t("lbl.compare"),
                all ? Tx.t("msg.compareOk") : Tx.t("msg.compareBad", diff(nr, name, birth, exp)));
    }

    private static String diff(boolean nr, boolean name, boolean birth, boolean exp) {
        List<String> d = new ArrayList<>();
        if (!nr) d.add(Tx.t("lbl.docNr"));
        if (!name) d.add(Tx.t("word.surname"));
        if (!birth) d.add(Tx.t("lbl.birth"));
        if (!exp) d.add(Tx.t("word.expiry"));
        return String.join(", ", d);
    }

    private static String clean(String s) {
        return s == null ? "" : s.replace("<", "").replace(" ", "").trim();
    }

    private void show(ChipReader.Result r) {
        items.removeAllViews();
        for (Mrz.Item i : r.items) items.addView(row(i));
        Mrz.Status s = r.overall();
        int text = s == Mrz.Status.OK ? R.string.nfc_verdict_ok
                : s == Mrz.Status.WARN ? R.string.nfc_verdict_warn : R.string.nfc_verdict_fail;
        int color = s == Mrz.Status.OK ? R.color.ok : s == Mrz.Status.WARN ? R.color.warn : R.color.fail;
        verdict.setText(text);
        verdict.setBackgroundColor(ContextCompat.getColor(this, color));
        verdict.setVisibility(View.VISIBLE);
        status.setText(R.string.nfc_again);
    }

    // ------------------------------------------------------------------
    // Wurzelzertifikate: manuell importiert, kein Download
    // ------------------------------------------------------------------

    private void reloadCscas() {
        runOnUiThread(() -> cscaInfo.setText(R.string.csca_loading));
        new Thread(() -> {
            CscaStore.Loaded l = CscaStore.load(this);
            cscas = l.certs;
            runOnUiThread(() -> showCscaInfo(l));
        }).start();
    }

    private void showCscaInfo(CscaStore.Loaded l) {
        if (l.files.isEmpty()) {
            cscaInfo.setText(R.string.csca_none);
            btnClear.setEnabled(false);
            return;
        }
        StringBuilder b = new StringBuilder();
        for (CscaStore.FileInfo f : l.files) {
            if (b.length() > 0) b.append('\n');
            b.append("• ").append(f.name).append(" (").append(f.count).append(')');
        }
        cscaInfo.setText(getString(R.string.csca_loaded, l.certs.size(), l.files.size(), b.toString()));
        btnClear.setEnabled(true);
    }

    private void importFile(Uri uri) {
        if (uri == null) return;
        new Thread(() -> {
            String name = CscaStore.displayName(this, uri);
            int n;
            try {
                n = CscaStore.importUri(this, uri);
            } catch (Exception e) {
                n = 0;
            }
            final int count = n;
            runOnUiThread(() -> Toast.makeText(this,
                    count > 0 ? getString(R.string.csca_import_ok, count, name)
                              : getString(R.string.csca_import_fail, name),
                    Toast.LENGTH_LONG).show());
            reloadCscas();
        }).start();
    }

    private View row(Mrz.Item i) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(6), 0, dp(6));
        TextView icon = new TextView(this);
        icon.setTextSize(18);
        icon.setTypeface(Typeface.DEFAULT_BOLD);
        icon.setGravity(Gravity.CENTER);
        icon.setMinWidth(dp(32));
        int color;
        switch (i.status) {
            case OK:   icon.setText("✓"); color = R.color.ok; break;
            case WARN: icon.setText("!"); color = R.color.warn; break;
            default:   icon.setText("✗"); color = R.color.fail;
        }
        icon.setTextColor(ContextCompat.getColor(this, color));
        row.addView(icon);
        TextView t = new TextView(this);
        t.setText(i.label + "\n" + i.detail);
        t.setTextSize(15);
        t.setPadding(dp(8), 0, 0, 0);
        row.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
