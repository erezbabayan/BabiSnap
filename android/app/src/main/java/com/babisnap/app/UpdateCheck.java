package com.babisnap.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Free update check: GitHub Releases "latest" only. Runs when Settings opens, throttled; no background polling. */
final class UpdateCheck {
    static final String API = "https://api.github.com/repos/erezbabayan/BabiSnap/releases/latest";
    static final String FALLBACK_APK = "https://github.com/erezbabayan/BabiSnap/releases/latest/download/BabiSnap.apk";
    private static final long THROTTLE_MS = 6L * 3600 * 1000;
    private UpdateCheck() {}

    /** "v2.16" > "2.15" → true. Numeric, dot-separated; non-digits ignored. */
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

    static void maybeCheck(Activity a) {
        long now = System.currentTimeMillis();
        if (now - Core.prefs(a).getLong("upd_last_check", 0) < THROTTLE_MS) return;
        Core.prefs(a).edit().putLong("upd_last_check", now).apply();
        String installed;
        try { installed = a.getPackageManager().getPackageInfo(a.getPackageName(), 0).versionName; } catch (Exception e) { return; }
        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(API).openConnection();
                c.setConnectTimeout(8000); c.setReadTimeout(8000);
                c.setRequestProperty("Accept", "application/vnd.github+json");
                if (c.getResponseCode() != 200) return;
                String body;
                try (InputStream in = c.getInputStream()) { body = new String(in.readAllBytes(), "UTF-8"); }
                JSONObject j = new JSONObject(body);
                String tag = j.optString("tag_name", "");
                if (!isNewer(tag, installed)) return;
                String url = FALLBACK_APK;
                JSONArray as = j.optJSONArray("assets");
                for (int i = 0; as != null && i < as.length(); i++) {
                    JSONObject o = as.getJSONObject(i);
                    if (o.optString("name").toLowerCase().endsWith(".apk")) { url = o.optString("browser_download_url", url); break; }
                }
                String u = url, latest = clean(tag);
                a.runOnUiThread(() -> { if (!a.isFinishing() && !a.isDestroyed()) show(a, installed, latest, u); });
            } catch (Exception ignored) { /* offline / rate-limited: stay silent */ }
        }, "update-check").start();
    }

    private static void show(Activity a, String installed, String latest, String url) {
        float dp = a.getResources().getDisplayMetrics().density;
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL); box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        int p = (int) (22 * dp); box.setPadding(p, (int) (8 * dp), p, 0);
        TextView m = new TextView(a);
        m.setText("זמין עדכון ל־BabiSnap. ההורדה מהריליס האחרון ב־GitHub — בלי חנות Play.");
        m.setTextSize(15.5f); m.setTextColor(0xFF45434F); m.setLineSpacing(0, 1.2f);
        box.addView(m);
        TextView v = new TextView(a);
        v.setText("מותקן: " + installed + "\nזמין: " + latest);
        v.setTextSize(14.5f); v.setTextColor(0xFF2A1B7A); v.setLineSpacing(0, 1.2f);
        int q = (int) (12 * dp); v.setPadding(q, q, q, q);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFFFFBEA); bg.setStroke((int) (1.5f * dp), 0xFFEADFB5); bg.setCornerRadius(14 * dp);
        v.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = (int) (12 * dp);
        box.addView(v, lp);
        AlertDialog d = new AlertDialog.Builder(a).setTitle("יש גרסה חדשה").setView(box)
            .setPositiveButton("הורד עדכון", (x, w) -> {
                try { a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception ignored) {}
            })
            .setNegativeButton("לא עכשיו", null).create();
        if (d.getWindow() != null) d.getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        d.show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(0xFF2A1B7A);
        d.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(0xFF2A1B7A);
    }
}
