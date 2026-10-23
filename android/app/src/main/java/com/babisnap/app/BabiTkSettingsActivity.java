package com.babisnap.app;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.*;

/** One-time "BabiTk connection" screen: paste the device token; stored only in EncryptedSharedPreferences, never shown back. */
public class BabiTkSettingsActivity extends Activity {
    private TextView state; private Button test, retry;
    /** 2.3: a new token waiting for the user's "replace?" answer. Memory only: never persisted, never put in savedInstanceState. */
    private String pendingToken, pendingVia;
    private android.app.AlertDialog confirm;
    private static final String KEY_CONFIRM_OPEN = "repair_confirm_open"; // only a flag, never the token

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE); // no screenshots / recents thumbnail of this screen
        setTitle("חיבור BabiTk");
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL); l.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        int p = (int) (16 * getResources().getDisplayMetrics().density); l.setPadding(p, p, p, p);
        TextView info = new TextView(this); info.setTextSize(15);
        info.setText("משימות והערות שמוקלטות נשלחות ללוח \"מחברת\" ב-BabiTk.\nהדרך הקלה: פתחו בטלפון את קישור החיבור מ-BabiTk (babisnap://pair…).\nלחלופין: הדביקו כאן את טוקן המכשיר (פעם אחת). הטוקן נשמר מוצפן במכשיר בלבד.");
        l.addView(info);
        state = new TextView(this); state.setTextSize(16); l.addView(state);
        EditText in = new EditText(this); in.setHint("טוקן מכשיר או קישור babisnap://pair…");
        in.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        in.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        l.addView(in);
        Button save = new Button(this); save.setText("שמור וחבר"); l.addView(save);
        save.setOnClickListener(v -> {
            String t = in.getText().toString().trim();
            if (t.isEmpty()) { Toast.makeText(this, "הדביקו טוקן", Toast.LENGTH_SHORT).show(); return; }
            if (t.toLowerCase(java.util.Locale.ROOT).startsWith("babisnap:") || t.toLowerCase(java.util.Locale.ROOT).startsWith("intent:")) {
                in.setText(""); applyLink(t, "הדבקה"); return; // a whole pairing link was pasted
            }
            if (!PairLink.validToken(t)) { in.setText(""); Toast.makeText(this, "זה לא טוקן BabiTk תקין (babi_…)", Toast.LENGTH_LONG).show(); return; }
            in.setText(""); offer(t, "הדבקה");
        });
        Button off = new Button(this); off.setText("נתק BabiTk"); l.addView(off);
        off.setOnClickListener(v -> { BabiTk.disconnect(this); Toast.makeText(this, "נותק – הטוקן נמחק מהמכשיר", Toast.LENGTH_SHORT).show(); refresh(); });
        test = new Button(this); test.setText("שלח בדיקה"); l.addView(test);
        test.setOnClickListener(v -> {
            test.setEnabled(false); state.append("\nשולח בדיקה…");
            new Thread(() -> {
                String res = BabiTk.sendTest(getApplicationContext());
                runOnUiThread(() -> { test.setEnabled(true); refresh(); state.append("\nתוצאת בדיקה: " + res);
                    Toast.makeText(this, res, Toast.LENGTH_LONG).show(); });
            }).start();
        });
        retry = new Button(this); retry.setText("שלח עכשיו פריטים ממתינים"); l.addView(retry);
        retry.setOnClickListener(v -> { BabiTk.kick(this); Toast.makeText(this, "נשלח לתור", Toast.LENGTH_SHORT).show(); });
        ScrollView sv = new ScrollView(this); sv.addView(l); setContentView(sv);
        refresh();
        if (b == null) {
            // Launched from Recents/history: Android re-delivers the original link intent – never re-apply it.
            if ((getIntent().getFlags() & android.content.Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0) handlePair(getIntent());
            else setIntent(new android.content.Intent(this, BabiTkSettingsActivity.class));
        } else {
            // Recreated (rotation etc.): the original intent may still carry the link – drop it. An unanswered
            // "replace?" dialog is treated as Cancel; its token was only in memory and is gone.
            setIntent(new android.content.Intent(this, BabiTkSettingsActivity.class));
            if (b.getBoolean(KEY_CONFIRM_OPEN, false)) cancelled(false);
        }
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putBoolean(KEY_CONFIRM_OPEN, pendingToken != null);
    }

    @Override protected void onDestroy() {
        if (confirm != null) { confirm.setOnCancelListener(null); confirm.dismiss(); confirm = null; }
        pendingToken = null; pendingVia = null;
        super.onDestroy();
    }

    @Override protected void onNewIntent(android.content.Intent i) { super.onNewIntent(i); handlePair(i); }

    /** babisnap://pair?token=..&url=.. ; url must be the exact BabiTk endpoint. Token is never logged or displayed. */
    private void handlePair(android.content.Intent i) {
        if (i == null || i.getData() == null || !android.content.Intent.ACTION_VIEW.equals(i.getAction())) return;
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
        offer(r.token, via);
    }

    /** A valid token arrived (link or paste). 2.3: ask before replacing a working connection. */
    private void offer(String token, String via) {
        dropPending(); // a newer link/paste supersedes an unanswered one
        switch (RePair.decide(BabiTk.token(this), BabiTk.needsRepair(this), token)) {
            case SAME:
                BabiTk.note(this, "babitk_last_pair", via + " התקבל – כבר מחובר עם אותו טוקן ✓");
                Toast.makeText(this, "מחובר BabiSnap ✓", Toast.LENGTH_LONG).show(); refresh(); return;
            case SAVE: save(token, via); return;
            case CONFIRM: default: askReplace(token, via);
        }
    }

    private void save(String token, String via) {
        boolean ok = BabiTk.setToken(this, token);
        BabiTk.note(this, "babitk_last_pair", via + (ok ? " התקבל ונשמר ✓" : " התקבל אבל השמירה נכשלה"));
        Toast.makeText(this, ok ? "מחובר BabiSnap ✓" : "שמירת החיבור נכשלה: " + Core.prefs(this).getString("babitk_store_error", ""), Toast.LENGTH_LONG).show();
        refresh();
    }

    private void askReplace(String token, String via) {
        pendingToken = token; pendingVia = via;
        confirm = new android.app.AlertDialog.Builder(this)
            .setTitle("להחליף את חיבור BabiTk?")
            .setMessage("הטלפון כבר מחובר. אם לא לחצת עכשיו על “חבר את BabiSnap” ב-BabiTk, בחר ביטול.")
            .setPositiveButton("החלף", (d, w) -> {
                String t = pendingToken, v = pendingVia; confirm = null; pendingToken = null; pendingVia = null;
                if (t != null) save(t, v + " (הוחלף)");
            })
            .setNegativeButton("ביטול", (d, w) -> { confirm = null; cancelled(true); })
            .setOnCancelListener(d -> { confirm = null; cancelled(true); }) // back button = cancel
            .create();
        confirm.setCanceledOnTouchOutside(false);
        if (confirm.getWindow() != null) {
            confirm.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
            confirm.getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        }
        confirm.show();
    }

    /** Cancel: keep the old token, forget the new one. */
    private void cancelled(boolean byUser) {
        pendingToken = null; pendingVia = null;
        BabiTk.note(this, "babitk_last_pair", byUser ? "בקשת החלפת חיבור בוטלה – החיבור הקיים נשמר" : "בקשת החלפת חיבור לא אושרה – החיבור הקיים נשמר");
        Toast.makeText(this, "החיבור לא הוחלף", Toast.LENGTH_LONG).show();
        refresh();
    }

    private void dropPending() {
        if (confirm != null) { confirm.setOnCancelListener(null); confirm.dismiss(); confirm = null; }
        pendingToken = null; pendingVia = null;
    }

    private void refresh() {
        android.content.SharedPreferences p = Core.prefs(this);
        boolean on = BabiTk.hasToken(this);
        StringBuilder sb = new StringBuilder("\nמצב: ").append(on ? "מחובר ✓" : BabiTk.needsRepair(this) ? BabiTk.NOT_CONNECTED : "לא מחובר");
        if (on) sb.append("\nטוקן: babi_•••••••• (מוסתר)"); // never shown, not even partially
        // 2.4: test / retry / diagnostics only when something is waiting or wrong
        boolean diag = BabiTk.showDiagnostics(this);
        test.setVisibility(diag && on ? View.VISIBLE : View.GONE);
        retry.setVisibility(diag && on && BabiTk.pending(this) > 0 ? View.VISIBLE : View.GONE);
        if (diag) {
            sb.append("\nפריטים ממתינים לשליחה: ").append(BabiTk.pending(this));
            int failed = BabiTk.failed(this);
            if (failed > 0) sb.append("\nפריטים שנדחו (נשמרו במכשיר): ").append(failed);
            line(sb, p, "babitk_last_attempt", "ניסיון שליחה אחרון");
            line(sb, p, "babitk_last_error", "שגיאה אחרונה");
            line(sb, p, "babitk_store_error", "אחסון");
        }
        line(sb, p, "babitk_last_pair", "חיבור אחרון");
        state.setText(sb.append("\n"));
    }
    private void line(StringBuilder sb, android.content.SharedPreferences p, String key, String label) {
        String v = p.getString(key, null);
        if (v != null) sb.append("\n").append(label).append(": ").append(v).append(" (").append(BabiTk.when(this, key)).append(")");
    }
    @Override protected void onResume() { super.onResume(); refresh(); }
}
