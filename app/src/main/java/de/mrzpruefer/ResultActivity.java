package de.mrzpruefer;

import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.time.LocalDate;

/**
 * Zeigt die MRZ-Zeilen zum Nachkorrigieren, das Prüfergebnis und die Daten
 * zum Abgleich mit der Vorderseite. Ohne Scan-Daten dient sie als manuelle Eingabe.
 */
public class ResultActivity extends AppCompatActivity {

    public static final String EXTRA_MRZ = "mrz";

    // Reihenfolge wie im Array R.array.input_modes
    private static final Mrz.InputMode[] MODES = {
            Mrz.InputMode.DE_ID, Mrz.InputMode.TD1, Mrz.InputMode.TD3, Mrz.InputMode.TD2
    };

    private EditText e1, e2, e3;
    private EditText[] fields;
    private Spinner modeSpinner;
    private LinearLayout items;
    private TextView verdict, compare;
    private Mrz.InputMode mode = Mrz.InputMode.DE_ID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Keine Bildschirmfotos/-aufnahmen, kein Vorschaubild in der App-Übersicht
        getWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE,
                android.view.WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_result);

        e1 = findViewById(R.id.line1);
        e2 = findViewById(R.id.line2);
        e3 = findViewById(R.id.line3);
        fields = new EditText[]{e1, e2, e3};
        modeSpinner = findViewById(R.id.mode);
        items = findViewById(R.id.items);
        verdict = findViewById(R.id.verdict);
        compare = findViewById(R.id.compare);

        ArrayAdapter<CharSequence> ad = ArrayAdapter.createFromResource(this,
                R.array.input_modes, android.R.layout.simple_spinner_item);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        modeSpinner.setAdapter(ad);
        modeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                setMode(MODES[pos]);
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        // Länge großzügig, da Komma und Leerzeichen beim Ergänzen wegfallen
        InputFilter[] f = {new InputFilter.LengthFilter(60), new InputFilter.AllCaps()};
        for (int i = 0; i < 3; i++) {
            final int line = i + 1;
            final EditText e = fields[i];
            e.setFilters(f);
            // Beim Verlassen des Feldes feste Teile und Füllzeichen ergänzen
            e.setOnFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus) fill(e, line);
            });
        }

        findViewById(R.id.btnCheck).setOnClickListener(v -> runCheck());
        findViewById(R.id.btnInfo).setOnClickListener(v ->
                startActivity(new android.content.Intent(this, InfoActivity.class)));
        findViewById(R.id.btnScan).setOnClickListener(v -> finish());
        findViewById(R.id.btnChip).setOnClickListener(v -> {
            for (int i = 0; i < mode.format.lines; i++) fill(fields[i], i + 1);
            android.content.Intent in = new android.content.Intent(this, NfcActivity.class);
            in.putExtra(NfcActivity.EXTRA_MRZ, currentLines());
            startActivity(in);
        });

        String[] mrz = getIntent().getStringArrayExtra(EXTRA_MRZ);
        if (mrz != null && (mrz.length == 2 || mrz.length == 3)) {
            modeSpinner.setSelection(indexOf(modeFor(mrz)), false);
            setMode(modeFor(mrz));
            for (int i = 0; i < 3; i++) fields[i].setText(i < mrz.length ? mrz[i] : "");
            runCheck();
        } else {
            setMode(Mrz.InputMode.DE_ID);
            e1.requestFocus();
        }
    }

    private static Mrz.InputMode modeFor(String[] mrz) {
        if (mrz.length == 3) return mrz[0].startsWith("IDD<<") ? Mrz.InputMode.DE_ID : Mrz.InputMode.TD1;
        return mrz[0].length() == 44 ? Mrz.InputMode.TD3 : Mrz.InputMode.TD2;
    }

    private static int indexOf(Mrz.InputMode m) {
        for (int i = 0; i < MODES.length; i++) if (MODES[i] == m) return i;
        return 0;
    }

    private void setMode(Mrz.InputMode m) {
        mode = m;
        boolean three = m.format.lines == 3;
        e3.setVisibility(three ? View.VISIBLE : View.GONE);
        switch (m) {
            case DE_ID:
                e1.setHint(R.string.hint_de_1);
                e2.setHint(R.string.hint_de_2);
                e3.setHint(R.string.hint_name);
                break;
            case TD1:
                e1.setHint(R.string.hint_td1_1);
                e2.setHint(R.string.hint_data);
                e3.setHint(R.string.hint_name);
                break;
            default:
                e1.setHint(R.string.hint_head);
                e2.setHint(R.string.hint_data);
        }
    }

    private String[] currentLines() {
        int n = mode.format.lines;
        String[] l = new String[n];
        for (int i = 0; i < n; i++) l[i] = text(fields[i]);
        return l;
    }

    private void runCheck() {
        for (int i = 0; i < mode.format.lines; i++) fill(fields[i], i + 1);
        Mrz.Result r = Mrz.check(currentLines(), LocalDate.now());

        items.removeAllViews();
        for (Mrz.Item i : r.items) items.addView(row(i));

        switch (r.overall()) {
            case OK:
                verdict.setText(R.string.verdict_ok);
                verdict.setBackgroundColor(ContextCompat.getColor(this, R.color.ok));
                break;
            case WARN:
                verdict.setText(R.string.verdict_warn);
                verdict.setBackgroundColor(ContextCompat.getColor(this, R.color.warn));
                break;
            default:
                verdict.setText(R.string.verdict_fail);
                verdict.setBackgroundColor(ContextCompat.getColor(this, R.color.fail));
        }
        verdict.setVisibility(View.VISIBLE);

        if (!r.surname.isEmpty() || r.birth != null) {
            compare.setText(getString(R.string.compare_text,
                    r.surname, r.givenNames, Mrz.fmt(r.birth), r.docNumber, Mrz.fmt(r.expiry),
                    r.issuer, r.nationality));
            compare.setVisibility(View.VISIBLE);
        } else {
            compare.setVisibility(View.GONE);
        }
    }

    private static String text(EditText e) {
        return e.getText().toString();
    }

    private void fill(EditText e, int line) {
        String in = text(e);
        String out = Mrz.autoFill(in, mode, line);
        if (!out.equals(in)) e.setText(out);
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
