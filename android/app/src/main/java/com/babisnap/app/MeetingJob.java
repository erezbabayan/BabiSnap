package com.babisnap.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.tasks.Tasks;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * One meeting on its way to Google Calendar, in the background (the card never waits for the network).
 * Insert first; once the attendee list is final (after the undo window, or after the picker), PATCH the attendees
 * with sendUpdates=all so Google emails the invite. Uses only the application context. Emails are never logged.
 */
final class MeetingJob {
    private final Context app; private final JSONObject p; private final String spoken;
    private String token, eventId;
    private boolean inserted, attendeesFinal, patchStarted;
    private final List<String> emails = new ArrayList<>();

    MeetingJob(Context app, JSONObject p, String spoken) { this.app = app; this.p = p; this.spoken = spoken; }

    /** Final list of invitees (may be empty). Call once. */
    synchronized void setAttendees(List<String> e) {
        if (attendeesFinal) return;
        attendeesFinal = true; emails.addAll(e);
        maybePatch();
    }

    void start(String firstToken) {
        new Thread(() -> {
            String m;
            try {
                String tok = firstToken;
                Core.Resp r = Core.insert(tok, p, spoken);
                if (CardFlow.retryWithFreshToken(r.code, false)) {
                    tok = freshToken(tok);
                    r = tok == null ? new Core.Resp(401, "") : Core.insert(tok, p, spoken);
                }
                m = CardFlow.resultMessage(r.code);
                if (m == null) synchronized (this) { token = tok; eventId = Core.eventId(r); inserted = true; maybePatch(); }
            } catch (Exception e) { m = CardFlow.networkMessage(e.getMessage()); }
            if (m != null) toast(m);
        }, "babisnap-calendar-insert").start();
    }

    private void maybePatch() {   // holds the lock
        if (!inserted || !attendeesFinal || patchStarted || emails.isEmpty()) return;
        patchStarted = true;
        if (eventId == null) { toast(InviteFlow.patchFailed(0)); return; }
        final String id = eventId, tok0 = token; final List<String> to = new ArrayList<>(emails);
        new Thread(() -> {
            String m;
            try {
                int code = Core.addAttendees(tok0, id, to);
                if (CardFlow.retryWithFreshToken(code, false)) {
                    String tok = freshToken(tok0);
                    code = tok == null ? 401 : Core.addAttendees(tok, id, to);
                }
                m = code / 100 == 2 ? null : InviteFlow.patchFailed(code);
            } catch (Exception e) { m = "הפגישה נרשמה, אבל הזימון לא נשלח – אין חיבור לאינטרנט?"; }
            if (m != null) toast(m);
        }, "babisnap-calendar-invite").start();
    }

    /** 401: drop the cached token and ask Play services for a new one (no UI). null = consent needed. */
    private String freshToken(String old) throws Exception {
        GoogleAuthUtil.clearToken(app, old);
        AuthorizationResult r = Tasks.await(Identity.getAuthorizationClient(app).authorize(Core.authRequest()), 20, TimeUnit.SECONDS);
        return r.hasResolution() ? null : r.getAccessToken();
    }

    private void toast(String m) { new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(app, m, Toast.LENGTH_LONG).show()); }
}
