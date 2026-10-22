package com.babisnap.app;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.*;

/** One-time "BabiTk connection" screen: paste the device token; stored only in EncryptedSharedPreferences, never shown back. */
public class BabiTkSettingsActivity extends Activity {
    private TextView state;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setTitle("חיבור BabiTk");
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL); l.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        int p = (int) (16 * getResources().getDisplayMetrics().density); l.setPadding(p, p, p, p);
        TextView info = new TextView(this); info.setTextSize(15);
        info.setText("משימות והערות שמוקלטות נשלחות ללוח \"מחברת\" ב-BabiTk.\nהדרך הקלה: פתחו בטלפון את קישור החיבור מ-BabiTk (babisnap://pair…).\nלחלופין: הדביקו כאן את טוקן המכשיר (פעם אחת). הטוקן נשמר מוצפן במכשיר בלבד.");
        l.addView(info);
        state = new TextView(this); state.setTextSize(16); l.addView(state);
        EditText in = new EditText(this); in.setHint("טוקן מכשיר");
        in.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        l.addView(in);
        Button save = new Button(this); save.setText("שמור וחבר"); l.addView(save);
        save.setOnClickListener(v -> {
            String t = in.getText().toString().trim();
            if (t.isEmpty()) { Toast.makeText(this, "הדביקו טוקן", Toast.LENGTH_SHORT).show(); return; }
            boolean ok = BabiTk.setToken(this, t); in.setText("");
            Toast.makeText(this, ok ? "מחובר BabiSnap ✓" : "שמירה נכשלה", Toast.LENGTH_LONG).show(); refresh();
        });
        Button off = new Button(this); off.setText("נתק BabiTk"); l.addView(off);
        off.setOnClickListener(v -> { BabiTk.setToken(this, null); refresh(); });
        Button retry = new Button(this); retry.setText("שלח עכשיו פריטים ממתינים"); l.addView(retry);
        retry.setOnClickListener(v -> { BabiTk.kick(this); Toast.makeText(this, "נשלח לתור", Toast.LENGTH_SHORT).show(); });
        ScrollView sv = new ScrollView(this); sv.addView(l); setContentView(sv);
        refresh();
        handlePair(getIntent());
    }

    @Override protected void onNewIntent(android.content.Intent i) { super.onNewIntent(i); handlePair(i); }

    /** babisnap://pair?token=..&url=.. ; url must be the exact BabiTk endpoint. Token is never logged or displayed. */
    private void handlePair(android.content.Intent i) {
        if (i == null || i.getData() == null) return;
        String tok = PairLink.token(i.getDataString());
        setIntent(new android.content.Intent(this, BabiTkSettingsActivity.class)); // drop the link (and token) from the intent
        if (tok == null) { Toast.makeText(this, "קישור חיבור לא תקין – לא נשמר", Toast.LENGTH_LONG).show(); return; }
        boolean ok = BabiTk.setToken(this, tok);
        Toast.makeText(this, ok ? "מחובר BabiSnap ✓" : "שמירה נכשלה", Toast.LENGTH_LONG).show();
        refresh();
    }

    private void refresh() {
        String err = Core.prefs(this).getString("babitk_last_error", null);
        state.setText("\nמצב: " + (BabiTk.hasToken(this) ? "מחובר ✓" : "לא מחובר") + "\nפריטים ממתינים לשליחה: " + BabiTk.pending(this)
            + (err != null ? "\nשגיאה אחרונה: " + err : "") + "\n");
    }
    @Override protected void onResume() { super.onResume(); refresh(); }
}
