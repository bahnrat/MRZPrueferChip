package de.mrzpruefer;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Size;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.lifecycle.Lifecycle;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.core.resolutionselector.ResolutionStrategy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Kamerabild → ML Kit (läuft auf dem Gerät, Modell ist in der App enthalten) → MRZ suchen
 * (TD1 mit 3 Zeilen, TD2/TD3 mit 2 Zeilen).
 * Erst wenn zwei Bilder hintereinander dieselbe MRZ mit stimmigen Prüfziffern liefern,
 * geht es zur Ergebnisseite.
 */
public class MainActivity extends AppCompatActivity {

    private PreviewView preview;
    private TextView status;
    private Button btnTorch, btnTakeOver;
    private ExecutorService analysisExecutor;
    private TextRecognizer recognizer;
    private Camera camera;
    private boolean torchOn = false;

    private volatile boolean done = false;
    private String lastValid = null;          // zuletzt gefundene stimmige MRZ
    private String[] lastSeen = null;         // zuletzt gelesene MRZ, auch fehlerhaft
    private boolean clearOnReturn = false;    // nach der Ergebnisseite leer weitermachen

    private final ActivityResultLauncher<String> permission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) startCamera();
                else status.setText(R.string.no_camera_permission);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Keine Bildschirmfotos/-aufnahmen, kein Vorschaubild in der App-Übersicht
        getWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE,
                android.view.WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_main);
        preview = findViewById(R.id.preview);
        status = findViewById(R.id.status);
        btnTorch = findViewById(R.id.btnTorch);
        btnTakeOver = findViewById(R.id.btnTakeOver);

        findViewById(R.id.btnInfo).setOnClickListener(v ->
                startActivity(new android.content.Intent(this, InfoActivity.class)));
        findViewById(R.id.btnManual).setOnClickListener(v -> {
            clearOnReturn = true;
            startActivity(new Intent(this, ResultActivity.class));
        });
        btnTakeOver.setOnClickListener(v -> {
            if (lastSeen != null) openResult(lastSeen);
        });
        btnTorch.setOnClickListener(v -> {
            if (camera != null && camera.getCameraInfo().hasFlashUnit()) {
                torchOn = !torchOn;
                camera.getCameraControl().enableTorch(torchOn);
                btnTorch.setText(torchOn ? R.string.torch_off : R.string.torch_on);
            }
        });

        analysisExecutor = Executors.newSingleThreadExecutor();
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            permission.launch(Manifest.permission.CAMERA);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (clearOnReturn) clearSession();
        done = false;
        lastValid = null;
        status.setText(R.string.hint_scan);
    }

    /**
     * Speicher leeren: letzte Lesung vergessen, „Übernehmen“ ausblenden, Licht aus.
     * Aufgerufen nach „Neu scannen“ bzw. Rückkehr von der Ergebnisseite und vom
     * Auto-Reset ({@link App}) bei Bildschirmsperre oder Wechsel in den Hintergrund.
     */
    void clearSession() {
        clearOnReturn = false;
        lastSeen = null;
        lastValid = null;
        done = false;
        if (btnTakeOver == null) return;     // Seite noch im Aufbau
        btnTakeOver.setVisibility(View.INVISIBLE);
        if (torchOn && camera != null) {
            camera.getCameraControl().enableTorch(false);
            torchOn = false;
            btnTorch.setText(R.string.torch_on);
        }
        status.setText(R.string.hint_scan);
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();

                Preview pv = new Preview.Builder().build();
                pv.setSurfaceProvider(preview.getSurfaceProvider());

                ResolutionSelector res = new ResolutionSelector.Builder()
                        .setResolutionStrategy(new ResolutionStrategy(new Size(1920, 1080),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                        .build();
                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setResolutionSelector(res)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                analysis.setAnalyzer(analysisExecutor, this::analyze);

                provider.unbindAll();
                camera = provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, pv, analysis);
            } catch (Exception e) {
                status.setText(getString(R.string.camera_error, e.getMessage()));
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void analyze(ImageProxy proxy) {
        if (done || proxy.getImage() == null) {
            proxy.close();
            return;
        }
        InputImage img = InputImage.fromMediaImage(proxy.getImage(),
                proxy.getImageInfo().getRotationDegrees());
        recognizer.process(img)
                .addOnSuccessListener(this::handleText)
                .addOnCompleteListener(t -> proxy.close());
    }

    private void handleText(Text text) {
        // Späte Ergebnisse nach dem Verlassen der Seite verwerfen (Auto-Reset)
        if (done || !getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED)) return;

        // Alle erkannten Zeilen von oben nach unten sortieren
        List<Text.Line> lines = new ArrayList<>();
        for (Text.TextBlock b : text.getTextBlocks()) lines.addAll(b.getLines());
        Collections.sort(lines, (a, b) -> {
            int ya = a.getBoundingBox() == null ? 0 : a.getBoundingBox().top;
            int yb = b.getBoundingBox() == null ? 0 : b.getBoundingBox().top;
            return Integer.compare(ya, yb);
        });
        List<String> raw = new ArrayList<>();
        for (Text.Line l : lines) raw.add(l.getText());

        String[] found = Mrz.findMrz(raw);
        if (found == null) return;

        String[] best = Mrz.bestReading(found);
        lastSeen = best != null ? best : Mrz.ocrCorrect(found);
        btnTakeOver.setVisibility(View.VISIBLE);

        if (best == null) {
            status.setText(R.string.hint_digits);
            lastValid = null;
            return;
        }
        String[] fixed = best;
        String key = String.join("|", fixed);
        if (key.equals(lastValid)) {
            done = true;
            openResult(fixed);
        } else {
            lastValid = key;
            status.setText(R.string.hint_hold);
        }
    }

    private void openResult(String[] mrz) {
        done = true;
        clearOnReturn = true;
        if (torchOn && camera != null) {
            camera.getCameraControl().enableTorch(false);
            torchOn = false;
            btnTorch.setText(R.string.torch_on);
        }
        Intent i = new Intent(this, ResultActivity.class);
        i.putExtra(ResultActivity.EXTRA_MRZ, mrz);
        startActivity(i);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        analysisExecutor.shutdown();
        recognizer.close();
    }
}
