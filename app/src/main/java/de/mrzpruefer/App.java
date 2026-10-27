package de.mrzpruefer;

import android.app.Activity;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * Auto-Reset: Sobald die App in den Hintergrund geht (Home, App-Wechsel, Anruf …)
 * oder der Bildschirm gesperrt wird, werden alle angezeigten Daten verworfen.
 * Ergebnis-, Chip- und Info-Seite werden geschlossen, der Scanner vergisst die
 * letzte Lesung. Beim Zurückkehren erscheint die leere Kameraansicht.
 *
 * Wechsel innerhalb der App (Ergebnis → Chip, Ergebnis → Info) und Drehen des Geräts
 * lösen keinen Reset aus. Für die Android-Dateiauswahl (Zertifikatsimport), die kurz
 * eine andere App öffnet, gibt es {@link #allowLeaveOnce()}.
 */
public class App extends Application implements Application.ActivityLifecycleCallbacks {

    private static App instance;

    private final List<Activity> alive = new ArrayList<>();
    private int started = 0;
    private boolean allowLeave = false;

    private final BroadcastReceiver screenOff = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            allowLeave = false;          // Sperre gilt immer, auch während der Dateiauswahl
            reset();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        registerActivityLifecycleCallbacks(this);
        // Nur zur Laufzeit registrierbar; kein Eintrag im Manifest nötig.
        // NOT_EXPORTED: nur das System darf diese Meldung schicken, keine fremde App.
        ContextCompat.registerReceiver(this, screenOff,
                new IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    /** Nächstes Verlassen der App (z. B. für die Dateiauswahl) nicht als Hintergrund werten. */
    public static void allowLeaveOnce() {
        if (instance != null) instance.allowLeave = true;
    }

    /** Alles verwerfen: Unterseiten schließen, Scanner leeren. */
    private void reset() {
        for (Activity a : new ArrayList<>(alive)) {
            if (a instanceof MainActivity) ((MainActivity) a).clearSession();
            else a.finish();
        }
    }

    // ------------------------------------------------------------------

    @Override public void onActivityCreated(@NonNull Activity a, @Nullable Bundle b) { alive.add(a); }
    @Override public void onActivityDestroyed(@NonNull Activity a) { alive.remove(a); }

    @Override public void onActivityStarted(@NonNull Activity a) {
        started++;
        allowLeave = false;
    }

    @Override public void onActivityStopped(@NonNull Activity a) {
        started--;
        if (started > 0 || a.isChangingConfigurations()) return;
        if (allowLeave) return;          // Dateiauswahl; Ausnahme endet beim Zurückkommen
        reset();
    }

    @Override public void onActivityResumed(@NonNull Activity a) {}
    @Override public void onActivityPaused(@NonNull Activity a) {}
    @Override public void onActivitySaveInstanceState(@NonNull Activity a, @NonNull Bundle b) {}
}
