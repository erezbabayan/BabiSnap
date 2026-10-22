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

    static SharedPreferences secure(Context c) throws Exception {
        MasterKey k = new MasterKey.Builder(c).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build();
        return EncryptedSharedPreferences.create(c, "babitk_secure", k,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
    }
    public static String token(Context c) {
        try { String t = secure(c).getString("device_token", null); return t == null || t.trim().isEmpty() ? null : t.trim(); }
        catch (Exception e) { return null; }
    }
    public static boolean setToken(Context c, String t) {
        try {
            SharedPreferences.Editor e = secure(c).edit();
            if (t == null || t.trim().isEmpty()) e.remove("device_token"); else e.putString("device_token", t.trim());
            boolean ok = e.commit();
            if (ok && t != null) kick(c);
            return ok;
        } catch (Exception e) { return false; }
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
    static void lastError(Context c, String e) { Core.prefs(c).edit().putString("babitk_last_error", e).apply(); }

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
            if (tok == null) return Result.success(); // stays queued until a token is saved (setToken kicks again)
            JSONArray a = queue(c); boolean retry = false;
            for (int i = 0; i < a.length(); i++) {
                JSONObject it = a.optJSONObject(i); if (it == null) continue;
                int code;
                try { code = Core.http("POST", BabiTkJson.URL, tok, it.optString("body")).code; }
                catch (Exception e) { code = -1; }
                switch (BabiTkJson.outcome(code)) {
                    case DONE: remove(c, it.optString("id")); break;
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
