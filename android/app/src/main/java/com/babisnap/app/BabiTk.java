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

/** BabiTk: encrypted device token, local queue of pending items, WorkManager sender. The token is never logged, displayed or put in a URL. */
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
    /** Saves a new device token (must be babi_… format) — replaces any previous one; null/empty wipes it. */
    public static boolean setToken(Context c, String t) {
        String v = t == null ? null : t.trim();
        if (v != null && !v.isEmpty() && !PairLink.validToken(v)) { note(c, "babitk_store_error", "טוקן לא בפורמט של BabiTk – לא נשמר"); return false; }
        try {
            SharedPreferences.Editor e = secure(c).edit();
            if (v == null || v.isEmpty()) e.remove("device_token"); else e.putString("device_token", v);
            boolean ok = e.commit();
            if (ok && v != null && !v.isEmpty()) {
                ok = v.equals(token(c)); // read back to prove it was stored
                if (ok) {
                    Core.prefs(c).edit().remove(NEEDS_REPAIR).remove(N429).remove(NEXT_AT).remove("babitk_store_error").remove("babitk_last_error").apply();
                    kick(c); // anything queued while disconnected goes out now
                }
            }
            if (!ok) note(c, "babitk_store_error", "שמירת הטוקן לא נקראה בחזרה");
            return ok;
        } catch (Exception e) { note(c, "babitk_store_error", "שמירת הטוקן נכשלה: " + e.getClass().getSimpleName()); return false; }
    }
    public static boolean hasToken(Context c) { return token(c) != null; }
    /** User pressed 'נתק': wipe the local token. Queued items stay on the device. */
    public static void disconnect(Context c) {
        setToken(c, null);
        Core.prefs(c).edit().remove(NEEDS_REPAIR).apply();
        note(c, "babitk_last_pair", "נותק ידנית – הטוקן נמחק מהמכשיר");
    }
    /** Settings: test/retry/diagnostics are shown only when something is waiting or wrong. */
    public static boolean showDiagnostics(Context c) {
        SharedPreferences p = Core.prefs(c);
        return SettingsLogic.showBabiTkDiagnostics(pending(c), failed(c), hasToken(c),
            p.getString("babitk_last_error", null), p.getString("babitk_store_error", null));
    }
    /** "Delete all data from this device": stop sending, drop the token (and its encrypted store) and the queue. */
    public static void wipeAll(Context c) {
        try { WorkManager.getInstance(c).cancelUniqueWork("babitk-send"); WorkManager.getInstance(c).cancelUniqueWork("babitk-send-later"); }
        catch (Exception ignored) {}
        try { secure(c).edit().clear().commit(); } catch (Exception ignored) {}
        c.deleteSharedPreferences("babitk_secure");
        synchronized (BabiTk.class) { Core.prefs(c).edit().remove(QUEUE).commit(); }
    }
    /** Server said 401 for this token: wipe it (if still current), keep the queue, show 'reconnect'. */
    static void unauthorized(Context c, String rejected) {
        if (SendLoop.shouldWipe(token(c), rejected)) {
            setToken(c, null);
            Core.prefs(c).edit().putBoolean(NEEDS_REPAIR, true).apply();
            lastError(c, "HTTP 401 – " + NOT_CONNECTED);
        }
    }
    public static boolean needsRepair(Context c) { return Core.prefs(c).getBoolean(NEEDS_REPAIR, false) && !hasToken(c); }
    public static final String NOT_CONNECTED = "לא מחובר – חבר מחדש";
    private static final String NEEDS_REPAIR = "babitk_needs_repair", N429 = "babitk_429_n", NEXT_AT = "babitk_next_at";

    // ---- queue (item bodies only; no secrets). Items: {id, body, failed?, error?} ----
    static synchronized JSONArray queue(Context c) {
        try { return new JSONArray(Core.prefs(c).getString(QUEUE, "[]")); } catch (Exception e) { return new JSONArray(); }
    }
    static synchronized void saveQueue(Context c, JSONArray a) { Core.prefs(c).edit().putString(QUEUE, a.toString()).commit(); }
    public static synchronized void enqueue(Context c, String clientId, String body) {
        JSONArray a = queue(c);
        try { a.put(new JSONObject().put("id", clientId).put("body", body)); } catch (Exception ignored) {}
        saveQueue(c, a); kick(c);
    }
    /** Items still waiting to be sent (failed ones excluded). */
    public static int pending(Context c) { JSONArray a = queue(c); int n = 0; for (int i = 0; i < a.length(); i++) if (!a.optJSONObject(i).optBoolean("failed")) n++; return n; }
    /** Items the server rejected (400…); kept on the device, not retried. */
    public static int failed(Context c) { return queue(c).length() - pending(c); }
    static synchronized void remove(Context c, String id) {
        JSONArray a = queue(c), b = new JSONArray();
        for (int i = 0; i < a.length(); i++) if (!id.equals(a.optJSONObject(i).optString("id"))) b.put(a.opt(i));
        saveQueue(c, b);
    }
    static synchronized void markFailed(Context c, String id, String err) {
        JSONArray a = queue(c);
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && id.equals(o.optString("id"))) try { o.put("failed", true).put("error", err); } catch (Exception ignored) {}
        }
        saveQueue(c, a);
    }
    static void lastError(Context c, String e) { note(c, "babitk_last_error", e); }
    static void attempt(Context c, int code) { note(c, "babitk_last_attempt", code > 0 ? "HTTP " + code : "אין חיבור לשרת"); }

    /** Re-validates a queued body (also items queued by older versions) against the server limits. */
    static String sanitize(String bodyJson) {
        try {
            JSONObject o = new JSONObject(bodyJson);
            return BabiTkJson.build(o.optString("title", ""), o.optString("due_date", null), o.optString("due_time", null),
                o.optString("notes", ""), o.optString("location", ""), o.optString("type", "task"), o.optString("client_id", null), tagList(o.optJSONArray("tags")));
        } catch (Exception e) { return bodyJson; }
    }

    static java.util.List<String> tagList(JSONArray a) {
        java.util.List<String> l = new java.util.ArrayList<>();
        if (a != null) for (int i = 0; i < a.length(); i++) l.add(a.optString(i));
        return l;
    }

    /** Sends one real test note right now (blocking; call off the UI thread). Returns a Hebrew result line. */
    public static String sendTest(Context c) {
        String tok = token(c);
        if (tok == null) return needsRepair(c) ? NOT_CONNECTED : "לא מחובר – אין טוקן שמור";
        String body = BabiTkJson.build("בדיקת חיבור BabiSnap", null, null, "נשלח מכפתור 'שלח בדיקה'", null, "note", BabiTkJson.newClientId());
        try {
            Core.Resp r = Core.http("POST", BabiTkJson.URL, tok, body);
            attempt(c, r.code);
            switch (BabiTkJson.outcome(r.code)) {
                case DONE: lastError(c, "אין"); kick(c); return "HTTP " + r.code + " ✓ הערת בדיקה נשמרה ב-BabiTk";
                case UNAUTHORIZED: unauthorized(c, tok); return "HTTP 401 – " + NOT_CONNECTED;
                case RATE_LIMITED: lastError(c, "בדיקה: HTTP 429 – יותר מדי שליחות, ננסה שוב מאוחר יותר"); return "HTTP 429 – יותר מדי שליחות בשעה האחרונה";
                default: String err = BabiTkJson.errorText(r.code, r.body); lastError(c, "בדיקה: " + err); return err;
            }
        } catch (Exception e) { attempt(c, -1); lastError(c, "בדיקה: " + e.getClass().getSimpleName()); return "אין חיבור לשרת: " + e.getClass().getSimpleName(); }
    }

    /** Schedules a send of everything queued (needs network). While backing off after 429, waits until the backoff ends. */
    public static void kick(Context c) {
        long wait = Core.prefs(c).getLong(NEXT_AT, 0) - System.currentTimeMillis();
        if (wait > 0) { later(c, (wait + 999) / 1000); return; }
        OneTimeWorkRequest w = new OneTimeWorkRequest.Builder(SendWorker.class)
            .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build();
        WorkManager.getInstance(c).enqueueUniqueWork("babitk-send", ExistingWorkPolicy.APPEND_OR_REPLACE, w);
    }
    private static void later(Context c, long seconds) {
        OneTimeWorkRequest w = new OneTimeWorkRequest.Builder(SendWorker.class)
            .setInitialDelay(seconds, TimeUnit.SECONDS)
            .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build();
        WorkManager.getInstance(c).enqueueUniqueWork("babitk-send-later", ExistingWorkPolicy.REPLACE, w);
    }

    public static class SendWorker extends Worker {
        public SendWorker(Context c, WorkerParameters p) { super(c, p); }
        @Override public Result doWork() {
            Context c = getApplicationContext();
            String tok = token(c);
            if (tok == null) { lastError(c, needsRepair(c) ? NOT_CONNECTED : "לא מחובר – אין טוקן"); return Result.success(); } // stays queued; setToken kicks again
            if (Core.prefs(c).getLong(NEXT_AT, 0) > System.currentTimeMillis()) { kick(c); return Result.success(); } // still backing off
            JSONArray a = queue(c); java.util.List<SendLoop.Item> items = new java.util.ArrayList<>();
            for (int i = 0; i < a.length(); i++) {
                JSONObject it = a.optJSONObject(i);
                if (it != null && !it.optBoolean("failed")) items.add(new SendLoop.Item(it.optString("id"), sanitize(it.optString("body"))));
            }
            if (items.isEmpty()) return Result.success();
            SendLoop.Report r = SendLoop.run(items, body -> Core.http("POST", BabiTkJson.URL, tok, body));
            attempt(c, r.lastCode);
            for (String id : r.sent) remove(c, id);
            for (java.util.Map.Entry<String, String> f : r.failed.entrySet()) markFailed(c, f.getKey(), f.getValue());
            if (!r.sent.isEmpty()) Core.prefs(c).edit().remove(N429).remove(NEXT_AT).apply();
            if (!r.failed.isEmpty()) lastError(c, "BabiTk דחה פריט: " + r.failed.values().iterator().next());
            else if (!r.sent.isEmpty() && !r.unauthorized && !r.rateLimited && !r.retry) lastError(c, "אין");
            if (r.unauthorized) { unauthorized(c, tok); return Result.success(); }
            if (r.rateLimited) {
                int n = Core.prefs(c).getInt(N429, 0) + 1;
                long s = BabiTkJson.backoffSeconds(n, r.retryAfter, System.currentTimeMillis());
                Core.prefs(c).edit().putInt(N429, n).putLong(NEXT_AT, System.currentTimeMillis() + s * 1000).apply();
                lastError(c, "HTTP 429 – יותר מדי שליחות, ננסה שוב בעוד " + (s >= 60 ? (s / 60) + " דק'" : s + " שנ'"));
                later(c, s); return Result.success();
            }
            if (r.retry) { lastError(c, "BabiTk לא זמין (" + (r.lastCode > 0 ? r.lastCode : "אין רשת") + "), ננסה שוב"); return Result.retry(); }
            return Result.success();
        }
    }
    private BabiTk() {}
}
