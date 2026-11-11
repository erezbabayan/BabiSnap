package com.babisnap.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.tasks.Tasks;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Durable calendar queue (Google + device). Survives process death — unlike the old fire-and-forget MeetingJob thread.
 * Unique WorkManager work per recording id (KEEP). Google needs network; device does not.
 * Token refresh is serialized so two rapid recordings cannot clearToken each other.
 */
public final class CalQueue {
    private static final String QUEUE = "cal_queue";
    private static final String SENT = "cal_sent_ids";
    private static final String LAST_ERR = "cal_last_error";
    private static final Object TOKEN_LOCK = new Object();
    private static final int NOTIF_FAIL = 7101, NOTIF_AUTH = 7102;

    /** One meeting ready to write. emails may be empty; set later for picker-mode invites. */
    public static void enqueue(Context c, String clientId, String route, JSONObject parsed, String spoken, List<String> emails) {
        enqueue(c, clientId, route, parsed, spoken, emails, true);
    }

    /** attendeesReady=false: insert now, patch invites later (contact picker). Meetings only — not WhatsApp. */
    public static void enqueue(Context c, String clientId, String route, JSONObject parsed, String spoken, List<String> emails, boolean attendeesReady) {
        if (clientId == null || clientId.isEmpty() || parsed == null) return;
        if (parsed.optBoolean("whatsapp", false) || "whatsapp".equals(parsed.optString("type"))) return;
        if (alreadySent(c, clientId)) { note(c, "אין"); return; }
        try {
            JSONObject o = new JSONObject();
            o.put("id", clientId);
            o.put("route", CalQueueLogic.ROUTE_DEVICE.equals(route) ? CalQueueLogic.ROUTE_DEVICE : CalQueueLogic.ROUTE_GOOGLE);
            o.put("title", parsed.optString("title", ""));
            o.put("startStr", parsed.getString("startStr"));
            o.put("durationMin", parsed.optInt("durationMin", 60));
            o.put("allDay", parsed.optBoolean("allDay", false));
            o.put("location", parsed.optString("location", ""));
            o.put("spoken", spoken == null ? "" : spoken);
            JSONArray ea = new JSONArray();
            if (emails != null) for (String e : emails) if (e != null && !e.isEmpty()) ea.put(e);
            o.put("emails", ea);
            o.put("attendeesReady", attendeesReady);
            o.put("status", "pending");
            synchronized (CalQueue.class) {
                JSONArray q = queue(c);
                for (int i = 0; i < q.length(); i++) {
                    if (clientId.equals(q.optJSONObject(i).optString("id"))) return; // already queued
                }
                q.put(o);
                saveQueue(c, q);
            }
            kickOne(c, clientId, CalQueueLogic.ROUTE_DEVICE.equals(route));
            note(c, "אין");
        } catch (Exception e) {
            note(c, "שמירה לתור נכשלה: " + e.getClass().getSimpleName());
        }
    }

    /** Picker finished: attach invitees and schedule a patch (Google only). */
    public static void setAttendees(Context c, String clientId, List<String> emails) {
        if (clientId == null) return;
        synchronized (CalQueue.class) {
            JSONArray q = queue(c);
            for (int i = 0; i < q.length(); i++) {
                JSONObject o = q.optJSONObject(i);
                if (o == null || !clientId.equals(o.optString("id"))) continue;
                try {
                    JSONArray ea = new JSONArray();
                    if (emails != null) for (String e : emails) if (e != null && !e.isEmpty()) ea.put(e);
                    o.put("emails", ea);
                    o.put("attendeesReady", true);
                    if ("done".equals(o.optString("status")) || o.optBoolean("inserted")) {
                        o.put("status", "pending_patch");
                    }
                    saveQueue(c, q);
                    kickOne(c, clientId, CalQueueLogic.ROUTE_DEVICE.equals(o.optString("route")));
                } catch (Exception ignored) {}
                return;
            }
        }
    }

    public static int pending(Context c) {
        JSONArray q = queue(c); int n = 0;
        for (int i = 0; i < q.length(); i++) {
            String s = q.optJSONObject(i).optString("status", "pending");
            if ("pending".equals(s) || "pending_patch".equals(s) || "needs_auth".equals(s)) n++;
        }
        return n;
    }
    public static int failed(Context c) {
        JSONArray q = queue(c); int n = 0;
        for (int i = 0; i < q.length(); i++) if ("failed".equals(q.optJSONObject(i).optString("status"))) n++;
        return n;
    }
    public static int needsAuth(Context c) {
        JSONArray q = queue(c); int n = 0;
        for (int i = 0; i < q.length(); i++) if ("needs_auth".equals(q.optJSONObject(i).optString("status"))) n++;
        return n;
    }
    public static boolean showDiagnostics(Context c) {
        return CalQueueLogic.showDiagnostics(pending(c), failed(c), needsAuth(c), lastError(c));
    }
    public static String statusTitle(Context c) {
        return CalQueueLogic.statusTitle(pending(c), failed(c), needsAuth(c));
    }
    public static String lastError(Context c) { return Core.prefs(c).getString(LAST_ERR, null); }

    /** After Google sign-in: retry items waiting for consent. */
    public static void onGoogleAuthorized(Context c) {
        synchronized (CalQueue.class) {
            JSONArray q = queue(c);
            boolean any = false;
            for (int i = 0; i < q.length(); i++) {
                JSONObject o = q.optJSONObject(i);
                if (o != null && "needs_auth".equals(o.optString("status"))) {
                    try { o.put("status", "pending"); any = true; } catch (Exception ignored) {}
                }
            }
            if (any) saveQueue(c, q);
        }
        kickAll(c);
    }

    public static void wipeAll(Context c) {
        try {
            WorkManager wm = WorkManager.getInstance(c);
            JSONArray q = queue(c);
            for (int i = 0; i < q.length(); i++) {
                String id = q.optJSONObject(i).optString("id");
                if (!id.isEmpty()) wm.cancelUniqueWork(workName(id));
            }
        } catch (Exception ignored) {}
        synchronized (CalQueue.class) {
            Core.prefs(c).edit().remove(QUEUE).remove(SENT).remove(LAST_ERR).commit();
        }
    }

    static synchronized JSONArray queue(Context c) {
        try { return new JSONArray(Core.prefs(c).getString(QUEUE, "[]")); } catch (Exception e) { return new JSONArray(); }
    }
    static synchronized void saveQueue(Context c, JSONArray a) {
        Core.prefs(c).edit().putString(QUEUE, a.toString()).commit();
    }

    static boolean alreadySent(Context c, String id) {
        return sentSet(c).contains(id);
    }
    static synchronized void markSent(Context c, String id) {
        Set<String> s = sentSet(c);
        s.add(id);
        // cap growth
        if (s.size() > 200) {
            List<String> list = new ArrayList<>(s);
            s = new HashSet<>(list.subList(list.size() - 150, list.size()));
        }
        Core.prefs(c).edit().putStringSet(SENT, s).apply();
    }
    private static Set<String> sentSet(Context c) {
        Set<String> s = Core.prefs(c).getStringSet(SENT, null);
        return s == null ? new HashSet<>() : new HashSet<>(s);
    }

    static void note(Context c, String msg) {
        Core.prefs(c).edit().putString(LAST_ERR, msg).apply();
    }

    private static String workName(String id) { return "cal-" + id; }

    private static void kickOne(Context c, String id, boolean device) {
        Constraints.Builder cb = new Constraints.Builder();
        if (!device) cb.setRequiredNetworkType(NetworkType.CONNECTED);
        OneTimeWorkRequest w = new OneTimeWorkRequest.Builder(CalWorker.class)
            .setInputData(new Data.Builder().putString("id", id).build())
            .setConstraints(cb.build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build();
        WorkManager.getInstance(c).enqueueUniqueWork(workName(id), ExistingWorkPolicy.KEEP, w);
    }

    /** Re-kick every non-failed item (e.g. after sign-in). */
    public static void kickAll(Context c) {
        JSONArray q = queue(c);
        for (int i = 0; i < q.length(); i++) {
            JSONObject o = q.optJSONObject(i);
            if (o == null) continue;
            String s = o.optString("status");
            if ("failed".equals(s) || "done".equals(s)) continue;
            // REPLACE so a needs_auth → pending transition actually runs
            Constraints.Builder cb = new Constraints.Builder();
            if (!CalQueueLogic.ROUTE_DEVICE.equals(o.optString("route"))) cb.setRequiredNetworkType(NetworkType.CONNECTED);
            OneTimeWorkRequest w = new OneTimeWorkRequest.Builder(CalWorker.class)
                .setInputData(new Data.Builder().putString("id", o.optString("id")).build())
                .setConstraints(cb.build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build();
            WorkManager.getInstance(c).enqueueUniqueWork(workName(o.optString("id")), ExistingWorkPolicy.REPLACE, w);
        }
    }

    private static JSONObject find(Context c, String id) {
        JSONArray q = queue(c);
        for (int i = 0; i < q.length(); i++) {
            JSONObject o = q.optJSONObject(i);
            if (o != null && id.equals(o.optString("id"))) return o;
        }
        return null;
    }

    private static void update(Context c, String id, Mutator m) {
        synchronized (CalQueue.class) {
            JSONArray q = queue(c);
            for (int i = 0; i < q.length(); i++) {
                JSONObject o = q.optJSONObject(i);
                if (o == null || !id.equals(o.optString("id"))) continue;
                try { m.apply(o); saveQueue(c, q); } catch (Exception ignored) {}
                return;
            }
        }
    }
    private interface Mutator { void apply(JSONObject o) throws Exception; }

    private static void remove(Context c, String id) {
        synchronized (CalQueue.class) {
            JSONArray q = queue(c), b = new JSONArray();
            for (int i = 0; i < q.length(); i++) {
                JSONObject o = q.optJSONObject(i);
                if (o != null && !id.equals(o.optString("id"))) b.put(o);
            }
            saveQueue(c, b);
        }
    }

    /** Serialized Google token refresh — at most one clearToken at a time. */
    static String refreshToken(Context app, String old) throws Exception {
        synchronized (TOKEN_LOCK) {
            if (old != null) {
                try { GoogleAuthUtil.clearToken(app, old); } catch (Exception ignored) {}
            }
            AuthorizationResult r = Tasks.await(
                Identity.getAuthorizationClient(app).authorize(Core.authRequest()), 20, TimeUnit.SECONDS);
            if (r.hasResolution()) return null; // needs UI
            return r.getAccessToken();
        }
    }

    static String authorizeToken(Context app) throws Exception {
        synchronized (TOKEN_LOCK) {
            AuthorizationResult r = Tasks.await(
                Identity.getAuthorizationClient(app).authorize(Core.authRequest()), 20, TimeUnit.SECONDS);
            if (r.hasResolution()) return null;
            return r.getAccessToken();
        }
    }

    static void notifyFail(Context c, String body) {
        ensureChannel(c);
        Intent i = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 0, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        android.app.Notification.Builder b = Build.VERSION.SDK_INT >= 26
            ? new android.app.Notification.Builder(c, CalQueueLogic.NOTIF_CHANNEL)
            : new android.app.Notification.Builder(c);
        b.setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(CalQueueLogic.NOTIF_TITLE_FAIL)
            .setContentText(body)
            .setStyle(new android.app.Notification.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pi);
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm != null) {
            try { nm.notify(NOTIF_FAIL, b.build()); } catch (SecurityException ignored) {}
        }
    }

    static void notifyAuth(Context c) {
        ensureChannel(c);
        Intent i = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 1, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        android.app.Notification.Builder b = Build.VERSION.SDK_INT >= 26
            ? new android.app.Notification.Builder(c, CalQueueLogic.NOTIF_CHANNEL)
            : new android.app.Notification.Builder(c);
        b.setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(CalQueueLogic.NOTIF_TITLE_AUTH)
            .setContentText(CalQueueLogic.NOTIF_BODY_AUTH)
            .setStyle(new android.app.Notification.BigTextStyle().bigText(CalQueueLogic.NOTIF_BODY_AUTH))
            .setAutoCancel(true)
            .setContentIntent(pi);
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm != null) {
            try { nm.notify(NOTIF_AUTH, b.build()); } catch (SecurityException ignored) {}
        }
    }

    private static void ensureChannel(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationChannel ch = new NotificationChannel(CalQueueLogic.NOTIF_CHANNEL, "יומן BabiSnap", NotificationManager.IMPORTANCE_DEFAULT);
        ch.setDescription("התראות כששמירת פגישה ליומן נכשלת או דורשת אישור");
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm != null) nm.createNotificationChannel(ch);
    }

    public static class CalWorker extends Worker {
        public CalWorker(Context c, WorkerParameters p) { super(c, p); }

        @Override public Result doWork() {
            Context c = getApplicationContext();
            String id = getInputData().getString("id");
            if (id == null || id.isEmpty()) return Result.success();
            if (alreadySent(c, id)) { remove(c, id); return Result.success(); }

            JSONObject item = find(c, id);
            if (item == null) return Result.success();
            String status = item.optString("status", "pending");
            if ("failed".equals(status) || "done".equals(status)) return Result.success();
            if ("needs_auth".equals(status)) { notifyAuth(c); return Result.success(); } // wait for sign-in

            if (SnapClient.suspended(c)) {
                fail(c, id, c.getString(R.string.snap_blocked_calendar));
                return Result.success();
            }

            boolean device = CalQueueLogic.ROUTE_DEVICE.equals(item.optString("route"));
            if (device) return doDevice(c, id, item);
            return doGoogle(c, id, item);
        }

        private Result doDevice(Context c, String id, JSONObject item) {
            try {
                JSONObject p = toParsed(item);
                String err = DeviceCalendar.insert(c, p, item.optString("spoken"), id);
                if (err == null) {
                    markSent(c, id);
                    remove(c, id);
                    note(c, "אין");
                    return Result.success();
                }
                fail(c, id, err);
                return Result.success();
            } catch (Exception e) {
                note(c, "יומן מכשיר: " + e.getClass().getSimpleName());
                return Result.retry();
            }
        }

        private Result doGoogle(Context c, String id, JSONObject item) {
            try {
                boolean inserted = item.optBoolean("inserted");
                String eventId = item.optString("eventId", null);
                if (eventId != null && eventId.isEmpty()) eventId = null;

                if (!inserted) {
                    String tok = authorizeToken(c);
                    if (tok == null) {
                        needsAuth(c, id);
                        return Result.success();
                    }
                    JSONObject p = toParsed(item);
                    Core.Resp r = Core.insert(tok, p, item.optString("spoken"), id);
                    CalQueueLogic.Outcome out = CalQueueLogic.googleInsertOutcome(r.code);
                    if (out == CalQueueLogic.Outcome.NEEDS_AUTH) {
                        // one silent refresh
                        String fresh = refreshToken(c, tok);
                        if (fresh == null) { needsAuth(c, id); return Result.success(); }
                        r = Core.insert(fresh, p, item.optString("spoken"), id);
                        out = CalQueueLogic.googleInsertOutcome(r.code);
                        tok = fresh;
                    }
                    if (out == CalQueueLogic.Outcome.RETRY) {
                        note(c, "יומן גוגל לא זמין (" + r.code + "), ננסה שוב");
                        return Result.retry();
                    }
                    if (out == CalQueueLogic.Outcome.FAILED) {
                        fail(c, id, CardFlow.resultMessage(r.code) != null ? CardFlow.resultMessage(r.code) : "שגיאה מיומן גוגל");
                        return Result.success();
                    }
                    if (out == CalQueueLogic.Outcome.NEEDS_AUTH) { needsAuth(c, id); return Result.success(); }
                    // DONE
                    final String eid = r.code == 409 ? CalQueueLogic.googleEventId(id) : Core.eventId(r);
                    update(c, id, o -> {
                        o.put("inserted", true);
                        if (eid != null) o.put("eventId", eid);
                        o.put("tokenHint", "ok"); // never store the token
                    });
                    inserted = true;
                    eventId = eid;
                    // fall through to attendees if ready
                    item = find(c, id);
                    if (item == null) { markSent(c, id); return Result.success(); }
                }

                // Re-read after possible insert update
                item = find(c, id);
                if (item == null) { markSent(c, id); return Result.success(); }
                inserted = item.optBoolean("inserted");
                eventId = item.optString("eventId", null);
                if (eventId != null && eventId.isEmpty()) eventId = null;
                if (!inserted) return Result.retry();

                JSONArray emails = item.optJSONArray("emails");
                boolean hasEmails = emails != null && emails.length() > 0;
                // attendeesReady defaults false only when explicitly set; normal undo sets it true at enqueue
                boolean attendeesReady = item.optBoolean("attendeesReady", true);

                if (hasEmails && !attendeesReady) {
                    // Insert done; wait for contact picker — keep queue row, do not markSent yet
                    update(c, id, o -> o.put("status", "pending_patch").put("inserted", true));
                    note(c, "אין");
                    return Result.success();
                }

                if (hasEmails && attendeesReady && eventId != null) {
                    String tok = authorizeToken(c);
                    if (tok == null) { needsAuth(c, id); return Result.success(); }
                    List<String> list = new ArrayList<>();
                    for (int i = 0; i < emails.length(); i++) list.add(emails.optString(i));
                    int code = Core.addAttendees(tok, eventId, list);
                    CalQueueLogic.Outcome po = CalQueueLogic.googlePatchOutcome(code);
                    if (po == CalQueueLogic.Outcome.NEEDS_AUTH) {
                        String fresh = refreshToken(c, tok);
                        if (fresh == null) { needsAuth(c, id); return Result.success(); }
                        code = Core.addAttendees(fresh, eventId, list);
                        po = CalQueueLogic.googlePatchOutcome(code);
                    }
                    if (po == CalQueueLogic.Outcome.RETRY) return Result.retry();
                    if (po == CalQueueLogic.Outcome.NEEDS_AUTH) { needsAuth(c, id); return Result.success(); }
                    if (po == CalQueueLogic.Outcome.FAILED) {
                        markSent(c, id);
                        remove(c, id);
                        String msg = InviteFlow.patchFailed(code);
                        note(c, msg);
                        notifyFail(c, msg);
                        return Result.success();
                    }
                }

                markSent(c, id);
                remove(c, id);
                note(c, "אין");
                return Result.success();
            } catch (Exception e) {
                note(c, "יומן גוגל: " + e.getClass().getSimpleName());
                return Result.retry();
            }
        }

        private static JSONObject toParsed(JSONObject item) throws Exception {
            JSONObject p = new JSONObject();
            p.put("title", item.optString("title", ""));
            p.put("startStr", item.getString("startStr"));
            p.put("durationMin", item.optInt("durationMin", 60));
            p.put("allDay", item.optBoolean("allDay", false));
            p.put("location", item.optString("location", ""));
            return p;
        }

        private void needsAuth(Context c, String id) {
            update(c, id, o -> o.put("status", "needs_auth"));
            note(c, CalQueueLogic.NOTIF_BODY_AUTH);
            notifyAuth(c);
        }

        private void fail(Context c, String id, String msg) {
            update(c, id, o -> { o.put("status", "failed"); o.put("error", msg); });
            note(c, msg);
            notifyFail(c, msg);
        }
    }

    private CalQueue() {}
}
