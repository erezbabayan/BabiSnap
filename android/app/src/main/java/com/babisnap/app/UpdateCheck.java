package com.babisnap.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Free update check: one anonymous GET of the public GitHub "releases/latest" JSON.
 * Automatic: when Settings opens, at most once a day, silent when offline / up to date.
 * Manual: «בדוק עדכונים» in Settings, always checks and always answers with a short toast.
 * No user data is sent (no account, no install id, generic User-Agent). No new permissions:
 * «עדכן» opens the permanent APK link in the browser; Android's installer does the rest.
 */
final class UpdateCheck {
    static final String API = "https://api.github.com/repos/erezbabayan/BabiSnap/releases/latest";
    static final String APK_URL = "https://github.com/erezbabayan/BabiSnap/releases/latest/download/BabiSnap.apk";
    static final String PREF_LAST = "upd_last_check";
    static final long DAY_MS = 24L * 3600 * 1000;
    static final String MSG_UP_TO_DATE = "יש לך את הגרסה האחרונה";
    static final String MSG_OFFLINE = "לא הצלחנו לבדוק עדכונים. נסו שוב כשיש אינטרנט.";
    static final String MSG_CHECKING = "בודק עדכונים…";
    private UpdateCheck() {}

    /** "v2.16" > "2.15" → true. Numeric, dot-separated; a trailing suffix ("2.39-debug") is ignored. */
    static boolean isNewer(String latest, String installed) {
        int[] a = parts(latest), b = parts(installed);
        if (a.length == 0 || b.length == 0) return false;
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length ? a[i] : 0, y = i < b.length ? b[i] : 0;
            if (x != y) return x > y;
        }
        return false;
    }
    static String clean(String v) { return v == null ? "" : v.trim().replaceFirst("^[vV]", ""); }
    private static int[] parts(String v) {
        String c = clean(v); if (c.isEmpty()) return new int[0];
        String[] s = c.split("\\.");
        int[] r = new int[s.length];
        for (int i = 0; i < s.length; i++) {
            String d = s[i].replaceAll("\\D.*$", "");
            if (d.isEmpty()) return new int[0];
            try { r[i] = Integer.parseInt(d); } catch (NumberFormatException e) { return new int[0]; }
        }
        return r;
    }

    /** Automatic check is due when a day has passed since the last one (or the clock went backwards). */
    static boolean due(long last, long now) { return last <= 0 || now < last || now - last >= DAY_MS; }

    /** tag_name from the releases/latest JSON, or "" when missing / not JSON. */
    static String tagFrom(String json) {
        try { return new JSONObject(json).optString("tag_name", "").trim(); } catch (Exception e) { return ""; }
    }

    static String title(String latest) { return "יש גרסה חדשה " + clean(latest); }
    static String upToDate(String installed) { return MSG_UP_TO_DATE + " (" + clean(installed) + ")"; }

    /** On Settings open: once a day, silent unless a newer version exists. */
    static void maybeCheck(Activity a) {
        long now = System.currentTimeMillis();
        if (!due(Core.prefs(a).getLong(PREF_LAST, 0), now)) return;
        Core.prefs(a).edit().putLong(PREF_LAST, now).apply();
        run(a, false);
    }

    /** «בדוק עדכונים»: always checks and always tells the result. */
    static void checkNow(Activity a) {
        Core.prefs(a).edit().putLong(PREF_LAST, System.currentTimeMillis()).apply();
        Toast.makeText(a, MSG_CHECKING, Toast.LENGTH_SHORT).show();
        run(a, true);
    }

    private static void run(Activity a, boolean manual) {
        String installed = BuildConfig.VERSION_NAME;
        new Thread(() -> {
            String tag = fetchTag();
            a.runOnUiThread(() -> {
                if (a.isFinishing() || a.isDestroyed()) return;
                if (tag.isEmpty()) { if (manual) Toast.makeText(a, MSG_OFFLINE, Toast.LENGTH_LONG).show(); return; }
                if (isNewer(tag, installed)) show(a, installed, clean(tag));
                else if (manual) Toast.makeText(a, upToDate(installed), Toast.LENGTH_LONG).show();
            });
        }, "update-check").start();
    }

    /** "" on any failure (offline, timeout, rate limit, bad JSON). */
    private static String fetchTag() {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(API).openConnection();
            c.setConnectTimeout(8000); c.setReadTimeout(8000);
            c.setRequestProperty("Accept", "application/vnd.github+json");
            c.setRequestProperty("User-Agent", "BabiSnap-update-check"); // generic, no device / user info
            if (c.getResponseCode() != 200) return "";
            try (InputStream in = c.getInputStream()) { return tagFrom(new String(in.readAllBytes(), "UTF-8")); }
        } catch (Exception ignored) {
            return "";
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static void show(Activity a, String installed, String latest) {
        float dp = a.getResources().getDisplayMetrics().density;
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL); box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        int p = (int) (22 * dp); box.setPadding(p, (int) (8 * dp), p, 0);
        TextView m = new TextView(a);
        m.setText("«עדכן» מוריד את הגרסה החדשה. פותחים את הקובץ ומאשרים התקנה. היא תותקן מעל הקיימת, וההגדרות נשמרות.");
        m.setTextSize(15.5f); m.setTextColor(0xFF45434F); m.setLineSpacing(0, 1.2f);
        box.addView(m);
        TextView v = new TextView(a);
        v.setText("מותקן: " + clean(installed) + "\nחדש: " + latest);
        v.setTextSize(14.5f); v.setTextColor(0xFF2A1B7A); v.setLineSpacing(0, 1.2f);
        int q = (int) (12 * dp); v.setPadding(q, q, q, q);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFFFFBEA); bg.setStroke((int) (1.5f * dp), 0xFFEADFB5); bg.setCornerRadius(14 * dp);
        v.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = (int) (12 * dp);
        box.addView(v, lp);
        AlertDialog d = new AlertDialog.Builder(a).setTitle(title(latest)).setView(box)
            .setPositiveButton("עדכן", (x, w) -> {
                try { a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(APK_URL))); }
                catch (Exception e) { Toast.makeText(a, "לא ניתן לפתוח את הקישור", Toast.LENGTH_SHORT).show(); }
            })
            .setNegativeButton("אחר כך", null).create();
        if (d.getWindow() != null) d.getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        d.show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(0xFF2A1B7A);
        d.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(0xFF2A1B7A);
    }
}
