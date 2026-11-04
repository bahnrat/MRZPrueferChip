package de.mrzpruefer;

import android.content.Context;
import android.util.AttributeSet;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;

import androidx.appcompat.widget.AppCompatEditText;

/**
 * Eingabefeld ohne Weitergabe: Text lässt sich nicht markieren, kopieren, ausschneiden, teilen
 * oder herausziehen (auch nicht per Strg+C, Tastatur-Knopf oder Drag & Drop).
 * Tippen, Cursor setzen, Löschen und Einfügen bleiben möglich.
 */
public class SecureEditText extends AppCompatEditText {

    public SecureEditText(Context c) { super(c); init(); }
    public SecureEditText(Context c, AttributeSet a) { super(c, a); init(); }
    public SecureEditText(Context c, AttributeSet a, int s) { super(c, a, s); init(); }

    private void init() {
        // Langes Drücken startet keine Markierung mehr
        setLongClickable(false);
        // Markier-Menü gar nicht erst öffnen
        setCustomSelectionActionModeCallback(new ActionMode.Callback() {
            @Override public boolean onCreateActionMode(ActionMode m, Menu menu) { return false; }
            @Override public boolean onPrepareActionMode(ActionMode m, Menu menu) { return false; }
            @Override public boolean onActionItemClicked(ActionMode m, MenuItem item) { return false; }
            @Override public void onDestroyActionMode(ActionMode m) { }
        });
        // Menü am Cursor: nur „Einfügen“
        setCustomInsertionActionModeCallback(new ActionMode.Callback() {
            @Override public boolean onCreateActionMode(ActionMode m, Menu menu) { strip(menu); return true; }
            @Override public boolean onPrepareActionMode(ActionMode m, Menu menu) { strip(menu); return true; }
            @Override public boolean onActionItemClicked(ActionMode m, MenuItem item) { return false; }
            @Override public void onDestroyActionMode(ActionMode m) { }
        });
    }

    private static void strip(Menu menu) {
        for (int i = menu.size() - 1; i >= 0; i--) {
            int id = menu.getItem(i).getItemId();
            if (id != android.R.id.paste && id != android.R.id.pasteAsPlainText) menu.removeItem(id);
        }
    }

    /** Jede Markierung sofort auf den Cursor zusammenziehen (Doppeltippen, Umschalt+Pfeil, Strg+A …). */
    @Override
    protected void onSelectionChanged(int selStart, int selEnd) {
        super.onSelectionChanged(selStart, selEnd);
        if (selStart != selEnd && getText() != null) setSelection(selEnd);
    }

    /** Kopier-Befehle der Tastatur (Gboard, Samsung, Strg+C/X) abweisen. */
    @Override
    public boolean onTextContextMenuItem(int id) {
        if (id == android.R.id.copy || id == android.R.id.cut || id == android.R.id.shareText
                || id == android.R.id.selectAll) {
            return false;
        }
        return super.onTextContextMenuItem(id);
    }
}
