package de.mrzpruefer;

import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Impressum, Datenschutzerklärung und Lizenzhinweise.
 * Die Texte stehen in res/raw/*.html und lassen sich dort direkt bearbeiten.
 * Links (Webseite, E-Mail) öffnen Browser bzw. Mailprogramm – dafür braucht
 * die App selbst keine Internet-Berechtigung.
 */
public class InfoActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_info);

        String version = "";
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) { }

        String html = raw(R.raw.impressum) + "<br>" + raw(R.raw.datenschutz) + "<br>" + raw(R.raw.lizenzen)
                + "<p><small>" + getString(R.string.app_name) + " " + version + "</small></p>";
        TextView t = findViewById(R.id.infoText);
        t.setText(Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT));
        t.setMovementMethod(LinkMovementMethod.getInstance());

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    private String raw(int id) {
        try (InputStream in = getResources().openRawResource(id)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        } catch (IOException e) {
            return "";
        }
    }
}
