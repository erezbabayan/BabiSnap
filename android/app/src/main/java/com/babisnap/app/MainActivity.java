package com.babisnap.app;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import com.google.android.gms.auth.GoogleAuthUtil;

import org.json.JSONObject;

/** Settings screen (Hebrew, RTL): account, sign in/out, default duration, preview toggle, parser test. */
public class MainActivity extends Activity {
    static final int REQ_AUTH = 10;
    private TextView account, out; private Button signBtn;
    private static final int[] DURS = {15, 30, 45, 60, 90, 120};

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL); l.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        int p = (int) (16 * getResources().getDisplayMetrics().density); l.setPadding(p, p, p, p);

        Button mic = new Button(this); mic.setText("  הכתבה עכשיו"); mic.setTextSize(22);
        android.graphics.drawable.Drawable wb = getDrawable(R.drawable.widget_logo);
        int ip = (int) (40 * getResources().getDisplayMetrics().density); wb.setBounds(0, 0, ip, ip);
        mic.setCompoundDrawablesRelative(wb, null, null, null); mic.setCompoundDrawablePadding(ip / 4);
        mic.setOnClickListener(v -> startActivity(new Intent(this, VoiceActivity.class)));
        l.addView(mic);

        account = text(l, ""); account.setTextSize(16);
        signBtn = new Button(this); l.addView(signBtn); refreshAccount();

        Button btk = new Button(this); btk.setText("חיבור BabiTk (משימות והערות)"); l.addView(btk);
        btk.setOnClickListener(v -> startActivity(new Intent(this, BabiTkSettingsActivity.class)));

        text(l, "משך ברירת מחדל לפגישה:");
        Spinner sp = new Spinner(this);
        String[] labels = {"רבע שעה", "חצי שעה", "45 דקות", "שעה", "שעה וחצי", "שעתיים"};
        sp.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        int cur = Core.defaultDuration(this);
        for (int i = 0; i < DURS.length; i++) if (DURS[i] == cur) sp.setSelection(i);
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> a, View v, int pos, long id) { Core.prefs(MainActivity.this).edit().putInt("dur", DURS[pos]).apply(); }
            public void onNothingSelected(AdapterView<?> a) {}
        });
        l.addView(sp);


        text(l, "\nבדיקת פענוח (לא שומר ביומן):");
        EditText in = new EditText(this); in.setHint("פגישה עם דני מחר ב-14:00"); in.setInputType(InputType.TYPE_CLASS_TEXT);
        l.addView(in);
        Button test = new Button(this); test.setText("בדוק"); l.addView(test);
        out = text(l, "");
        test.setOnClickListener(v -> {
            try { JSONObject r = Core.parse(this, in.getText().toString());
                out.setText(r.getString("confirm") + "\nמשך: " + r.getInt("durationMin") + " דקות" + (r.optBoolean("timeDefaulted") ? "\n(לא נאמרה שעה, נקבע 09:00)" : ""));
            } catch (Exception e) { out.setText("שגיאה: " + e); }
        });

        text(l, "\nכפתור במסך הבית: לחיצה ארוכה על מסך הבית ← ווידג'טים ← BabiSnap.");
        Button pin = new Button(this); pin.setText("הוסף כפתור למסך הבית"); l.addView(pin);
        pin.setOnClickListener(v -> {
            AppWidgetManager m = getSystemService(AppWidgetManager.class);
            if (m == null || !m.isRequestPinAppWidgetSupported()) {
                Toast.makeText(this, "הוסיפו ידנית מתפריט הווידג'טים", Toast.LENGTH_LONG).show(); return;
            }
            showWidgetPicker(m);
        });

        ScrollView sv = new ScrollView(this); sv.addView(l); setContentView(sv);
    }

    private TextView text(LinearLayout l, String s) {
        TextView t = new TextView(this); t.setText(s); t.setGravity(Gravity.START); t.setTextSize(15); l.addView(t); return t;
    }

    private void refreshAccount() {
        String email = Core.prefs(this).getString("email", null);
        account.setText(email == null ? "\nלא מחובר" : "\nמחובר: " + email);
        signBtn.setText(email == null ? "התחבר עם Google" : "התנתק");
        signBtn.setOnClickListener(v -> { if (email == null) signIn(); else signOut(); });
    }

    private void signIn() {
        Core.requestAuth(this, REQ_AUTH, this::gotToken, e -> Toast.makeText(this, "שגיאת התחברות: " + e, Toast.LENGTH_LONG).show());
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        if (req != REQ_AUTH) return;
        if (res != RESULT_OK) { Toast.makeText(this, "ההתחברות בוטלה", Toast.LENGTH_SHORT).show(); return; }
        try { gotToken(Core.tokenFromResult(this, data)); } catch (Exception e) { Toast.makeText(this, "שגיאה: " + e, Toast.LENGTH_LONG).show(); }
    }

    private void gotToken(String token) {
        new Thread(() -> {
            String email; try { email = Core.fetchEmail(token); } catch (Exception e) { email = null; }
            String fe = email == null ? "(חשבון Google)" : email;
            Core.prefs(this).edit().putString("email", fe).putString("tok", token).apply();
            runOnUiThread(this::refreshAccount);
        }).start();
    }

    private void signOut() {
        String tok = Core.prefs(this).getString("tok", null);
        Core.prefs(this).edit().remove("email").remove("tok").apply();
        refreshAccount();
        if (tok != null) new Thread(() -> {
            Core.revoke(tok);
            try { GoogleAuthUtil.clearToken(this, tok); } catch (Exception ignored) {}
        }).start();
    }

    private void showWidgetPicker(AppWidgetManager m) {
        float dp = getResources().getDisplayMetrics().density;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL); row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        row.setGravity(Gravity.CENTER); int pad = (int) (16 * dp); row.setPadding(pad, pad, pad, (int) (4 * dp));
        final android.app.AlertDialog[] dlg = new android.app.AlertDialog[1];
        int[] imgs = {R.drawable.record_tile, R.drawable.widget_preview};
        String[] desc = {"כפתור הקלטה (ריבוע)", "יישומון רחב עם שם"};
        Class<?>[] providers = {ButtonWidget.class, MicWidget.class};
        int[] w = {88, 176};
        for (int i = 0; i < 2; i++) {
            ImageView iv = new ImageView(this);
            iv.setImageResource(imgs[i]); iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setContentDescription(desc[i]); iv.setAdjustViewBounds(true);
            int ip = (int) (10 * dp); iv.setPadding(ip, ip, ip, ip);
            android.graphics.drawable.GradientDrawable card = new android.graphics.drawable.GradientDrawable();
            card.setCornerRadius(20 * dp); card.setColor(0x1A808080); card.setStroke((int) dp, 0x40808080);
            android.util.TypedValue tv = new android.util.TypedValue();
            getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
            iv.setBackground(new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{card, getDrawable(tv.resourceId)}));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int) (w[i] * dp), (int) (88 * dp));
            lp.setMargins((int) (6 * dp), 0, (int) (6 * dp), 0);
            final Class<?> prov = providers[i];
            iv.setOnClickListener(v -> {
                if (dlg[0] != null) dlg[0].dismiss();
                boolean ok = m.requestPinAppWidget(new ComponentName(this, prov), null, null);
                if (!ok) Toast.makeText(this, "המסך הראשי לא אישר הוספה – הוסיפו ידנית מתפריט הווידג'טים", Toast.LENGTH_LONG).show();
            });
            row.addView(iv, lp);
        }
        dlg[0] = new android.app.AlertDialog.Builder(this).setTitle("איזה יישומון להוסיף?")
            .setView(row).setNegativeButton("ביטול", null).create();
        dlg[0].show();
    }
}
