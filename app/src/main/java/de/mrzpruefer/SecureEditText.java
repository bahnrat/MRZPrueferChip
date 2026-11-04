package de.mrzpruefer;

import android.content.Context;
import android.util.AttributeSet;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;

import androidx.appcompat.widget.AppCompatEditText;

/**
 * Eingabefeld ohne Weitergabe: Kopieren, Ausschneiden, Teilen sowie die Übergabe an andere Apps
 * (Übersetzen, Assistenten, KI) sind gesperrt – im Markier-Menü und über die Tastatur.
 * Einfügen und „Alles markieren“ bleiben erlaubt.
 */
public class SecureEditText extends AppCompatEditText {

    public SecureEditText(Context c) { super(c); init(); }
    public SecureEditText(Context c, AttributeSet a) { super(c, a); init(); }
    public SecureEditText(Context c, AttributeSet a, int s) { super(c, a, s); init(); }

    private void init() {
        ActionMode.Callback cb = new ActionMode.Callback() {
            @Override public boolean onCreateActionMode(ActionMode m, Menu menu) { strip(menu); return true; }
            @Override public boolean onPrepareActionMode(ActionMode m, Menu menu) { strip(menu); return true; }
            @Override public boolean onActionItemClicked(ActionMode m, MenuItem item) { return false; }
            @Override public void onDestroyActionMode(ActionMode m) { }
        };
        setCustomSelectionActionModeCallback(cb);
        setCustomInsertionActionModeCallback(cb);
    }

    /** Nur Einfügen und „Alles markieren“ im Menü lassen. */
    private static void strip(Menu menu) {
        for (int i = menu.size() - 1; i >= 0; i--) {
            int id = menu.getItem(i).getItemId();
            if (id != android.R.id.paste && id != android.R.id.pasteAsPlainText && id != android.R.id.selectAll) {
                menu.removeItem(id);
            }
        }
    }

    /** Auch Kopier-Befehle der Tastatur (Gboard, Samsung) abweisen. */
    @Override
    public boolean onTextContextMenuItem(int id) {
        if (id == android.R.id.copy || id == android.R.id.cut || id == android.R.id.shareText) {
            return false;
        }
        return super.onTextContextMenuItem(id);
    }
}