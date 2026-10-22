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
        EditText in = new EditText(this); in.setHint("טוקן מכשיר או קישור babisnap://pair…");
        in.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        l.addView(in);
        Button save = new Button(this); save.setText("שמור וחבר"); l.addView(save);
        save.setOnClickListener(v -> {
            String t = in.getText().toString().trim();
            if (t.isEmpty()) { Toast.makeText(this, "הדביקו טוקן", Toast.LENGTH_SHORT).show(); return; }
            if (t.toLowerCase(java.util.Locale.ROOT).startsWith("babisnap:") || t.toLowerCase(java.util.Locale.ROOT).startsWith("intent:")) {
                in.setText(""); applyLink(t, "הדבקה"); return; // a whole pairing link was pasted
            }
            if (t.matches(".*\\s.*")) { Toast.makeText(this, "הטוקן לא יכול להכיל רווחים", Toast.LENGTH_LONG).show(); return; }
            boolean ok = BabiTk.setToken(this, t); in.setText("");
            BabiTk.note(this, "babitk_last_pair", ok ? "טוקן הודבק ונשמר ✓" : "הדבקה – שמירה נכשלה");
            Toast.makeText(this, ok ? "מחובר BabiSnap ✓" : "שמירה נכשלה: " + Core.prefs(this).getString("babitk_store_error", ""), Toast.LENGTH_LONG).show(); refresh();
        });
        Button off = new Button(this); off.setText("נתק BabiTk"); l.addView(off);
        off.setOnClickListener(v -> { BabiTk.setToken(this, null); refresh(); });
        Button test = new Button(this); test.setText("שלח בדיקה"); l.addView(test);
        test.setOnClickListener(v -> {
            test.setEnabled(false); state.append("\nשולח בדיקה…");
            new Thread(() -> {
                String res = BabiTk.sendTest(getApplicationContext());
                runOnUiThread(() -> { test.setEnabled(true); refresh(); state.append("\nתוצאת בדיקה: " + res);
                    Toast.makeText(this, res, Toast.LENGTH_LONG).show(); });
            }).start();
        });
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
        String link = i.getDataString();
        setIntent(new android.content.Intent(this, BabiTkSettingsActivity.class)); // drop the link (and token) from the intent
        applyLink(link, "קישור");
    }

    private void applyLink(String link, String via) {
        PairLink.Result r = PairLink.parse(link);
        if (r.token == null) {
            BabiTk.note(this, "babitk_last_pair", via + " נדחה: " + r.error);
            Toast.makeText(this, "קישור החיבור נדחה: " + r.error, Toast.LENGTH_LONG).show(); refresh(); return;
        }
        boolean ok = BabiTk.setToken(this, r.token);
        BabiTk.note(this, "babitk_last_pair", via + (ok ? " התקבל ונשמר ✓" : " התקבל אבל השמירה נכשלה"));
        Toast.makeText(this, ok ? "מחובר BabiSnap ✓" : "שמירת החיבור נכשלה: " + Core.prefs(this).getString("babitk_store_error", ""), Toast.LENGTH_LONG).show();
        refresh();
    }

    private void refresh() {
        android.content.SharedPreferences p = Core.prefs(this);
        StringBuilder sb = new StringBuilder("\nמצב: ").append(BabiTk.hasToken(this) ? "מחובר ✓" : "לא מחובר")
            .append("\nפריטים ממתינים לשליחה: ").append(BabiTk.pending(this));
        line(sb, p, "babitk_last_attempt", "ניסיון שליחה אחרון");
        line(sb, p, "babitk_last_error", "שגיאה אחרונה");
        line(sb, p, "babitk_last_pair", "חיבור אחרון");
        line(sb, p, "babitk_store_error", "אחסון");
        state.setText(sb.append("\n"));
    }
    private void line(StringBuilder sb, android.content.SharedPreferences p, String key, String label) {
        String v = p.getString(key, null);
        if (v != null) sb.append("\n").append(label).append(": ").append(v).append(" (").append(BabiTk.when(this, key)).append(")");
    }
    @Override protected void onResume() { super.onResume(); refresh(); }
}
