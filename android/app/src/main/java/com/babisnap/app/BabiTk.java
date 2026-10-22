package com.babisnap.app;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

/** BabiTk: encrypted device token, local queue of pending items, WorkManager sender. The token is never logged. */
public final class BabiTk {
    private static final String QUEUE = "babitk_queue";

    /** Opens the encrypted store; if it is unreadable (e.g. restored from a backup without its key), wipes it and retries once. */
    static SharedPreferences secure(Context c) throws Exception {
        try { return openSecure(c); }
        catch (Exception e) {
            note(c, "babitk_store_error", "אחסון מוצפן נפתח מחדש (" + e.getClass().getSimpleName() + ")");
            c.deleteSharedPreferences("babitk_secure");
            return openSecure(c);
        }
    }
    private static SharedPreferences openSecure(Context c) throws Exception {
        MasterKey k = new MasterKey.Builder(c).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build();
        return EncryptedSharedPreferences.create(c, "babitk_secure", k,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
    }
    public static String token(Context c) {
        try { String t = secure(c).getString("device_token", null); return t == null || t.trim().isEmpty() ? null : t.trim(); }
        catch (Exception e) { note(c, "babitk_store_error", "קריאת הטוקן נכשלה: " + e.getClass().getSimpleName()); return null; }
    }
    /** Small status notes for the connection screen (never contain the token). */
    static void note(Context c, String key, String v) {
        Core.prefs(c).edit().putString(key, v).putLong(key + "_at", System.currentTimeMillis()).apply();
    }
    static String when(Context c, String key) {
        long t = Core.prefs(c).getLong(key + "_at", 0);
        return t == 0 ? "" : new java.text.SimpleDateFormat("dd/MM HH:mm:ss", java.util.Locale.US).format(new java.util.Date(t));
    }
    public static boolean setToken(Context c, String t) {
        try {
            SharedPreferences.Editor e = secure(c).edit();
            if (t == null || t.trim().isEmpty()) e.remove("device_token"); else e.putString("device_token", t.trim());
            boolean ok = e.commit();
            if (ok && t != null) {
                ok = t.trim().equals(token(c)); // read back to prove it was stored
                if (ok) kick(c);
            }
            if (!ok) note(c, "babitk_store_error", "שמירת הטוקן לא נקראה בחזרה");
            return ok;
        } catch (Exception e) { note(c, "babitk_store_error", "שמירת הטוקן נכשלה: " + e.getClass().getSimpleName()); return false; }
    }
    public static boolean hasToken(Context c) { return token(c) != null; }

    // ---- queue (item bodies only; no secrets) ----
    static synchronized JSONArray queue(Context c) {
        try { return new JSONArray(Core.prefs(c).getString(QUEUE, "[]")); } catch (Exception e) { return new JSONArray(); }
    }
    static synchronized void saveQueue(Context c, JSONArray a) { Core.prefs(c).edit().putString(QUEUE, a.toString()).commit(); }
    public static synchronized void enqueue(Context c, String clientId, String body) {
        JSONArray a = queue(c);
        try { a.put(new JSONObject().put("id", clientId).put("body", body)); } catch (Exception ignored) {}
        saveQueue(c, a); kick(c);
    }
    public static int pending(Context c) { return queue(c).length(); }
    static synchronized void remove(Context c, String id) {
        JSONArray a = queue(c), b = new JSONArray();
        for (int i = 0; i < a.length(); i++) if (!id.equals(a.optJSONObject(i).optString("id"))) b.put(a.opt(i));
        saveQueue(c, b);
    }
    static void lastError(Context c, String e) { note(c, "babitk_last_error", e); }
    static void attempt(Context c, int code) { note(c, "babitk_last_attempt", code > 0 ? "HTTP " + code : "אין חיבור לשרת"); }

    /** Sends one real test note right now (blocking; call off the UI thread). Returns a Hebrew result line. */
    public static String sendTest(Context c) {
        String tok = token(c);
        if (tok == null) return "לא מחובר – אין טוקן שמור";
        String id = java.util.UUID.randomUUID().toString();
        String body = BabiTkJson.build("בדיקת חיבור BabiSnap", null, null, "נשלח מכפתור 'שלח בדיקה'", null, "note", id);
        try {
            Core.Resp r = Core.http("POST", BabiTkJson.URL, tok, body);
            attempt(c, r.code);
            if (r.code / 100 == 2) { lastError(c, "אין"); kick(c); return "HTTP " + r.code + " ✓ הערת בדיקה נשמרה ב-BabiTk"; }
            String err = r.body == null ? "" : r.body.replaceAll("\\s+", " ");
            if (err.length() > 160) err = err.substring(0, 160);
            lastError(c, "בדיקה: HTTP " + r.code + " " + err);
            return "HTTP " + r.code + (r.code == 401 ? " – הטוקן לא תקף, חברו מחדש" : "") + "\n" + err;
        } catch (Exception e) { attempt(c, -1); lastError(c, "בדיקה: " + e.getClass().getSimpleName()); return "אין חיבור לשרת: " + e.getClass().getSimpleName(); }
    }

    /** Schedules a send of everything queued (needs network; exponential backoff on retry). */
    public static void kick(Context c) {
        OneTimeWorkRequest w = new OneTimeWorkRequest.Builder(SendWorker.class)
            .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build();
        WorkManager.getInstance(c).enqueueUniqueWork("babitk-send", ExistingWorkPolicy.APPEND_OR_REPLACE, w);
    }

    public static class SendWorker extends Worker {
        public SendWorker(Context c, WorkerParameters p) { super(c, p); }
        @Override public Result doWork() {
            Context c = getApplicationContext();
            String tok = token(c);
            if (tok == null) { lastError(c, "לא מחובר – אין טוקן"); return Result.success(); } // stays queued until a token is saved (setToken kicks again)
            JSONArray a = queue(c); boolean retry = false;
            for (int i = 0; i < a.length(); i++) {
                JSONObject it = a.optJSONObject(i); if (it == null) continue;
                int code;
                try { code = Core.http("POST", BabiTkJson.URL, tok, it.optString("body")).code; }
                catch (Exception e) { code = -1; }
                attempt(c, code);
                switch (BabiTkJson.outcome(code)) {
                    case DONE: remove(c, it.optString("id")); lastError(c, "אין"); break;
                    case DROP: remove(c, it.optString("id")); lastError(c, "BabiTk דחה פריט (" + code + ")"); break;
                    case WAIT_TOKEN: lastError(c, "טוקן BabiTk לא תקף (401) – עדכנו בהגדרות"); return Result.success();
                    case RETRY: retry = true; lastError(c, "BabiTk לא זמין (" + code + "), ננסה שוב"); break;
                }
            }
            return retry ? Result.retry() : Result.success();
        }
    }
    private BabiTk() {}
}
