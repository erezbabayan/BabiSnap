package com.babisnap.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.widget.Toast;
import android.Manifest;
import android.content.pm.PackageManager;
import android.speech.RecognitionListener;
import android.speech.SpeechRecognizer;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.gms.auth.api.identity.Identity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Widget/launcher target: speech (he-IL) -> parse on device -> (optional confirm) -> Google Calendar. */
public class VoiceActivity extends Activity {
    static final int REQ_SPEECH = 1, REQ_AUTH = 2;
    private JSONObject parsed; private String spoken; private java.util.List<String> spokenTags = new java.util.ArrayList<>();

    static final int REQ_MIC = 3;
    private SpeechRecognizer rec; private View logo, halo; private TextView status, partial;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (b != null) return;
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { systemDialog(); return; }
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_listen);
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        logo = findViewById(R.id.logo); halo = findViewById(R.id.halo);
        status = findViewById(R.id.status); partial = findViewById(R.id.partial);
        findViewById(R.id.cancel).setOnClickListener(v -> { ended = true; tick.removeCallbacks(ticker); stopRec(); done(null); });
        logo.setOnClickListener(v -> tapToFinish()); halo.setOnClickListener(v -> tapToFinish());
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) listen();
        else requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
    }

    @Override public void onRequestPermissionsResult(int req, String[] p, int[] g) {
        if (req == REQ_CONTACTS) {
            boolean ok = g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED;
            MainActivity.noteResult(this, Manifest.permission.READ_CONTACTS, ok);
            if (ok) resolveInvites(); else noPermission();
            return;
        }
        if (req != REQ_MIC) return;
        MainActivity.noteResult(this, Manifest.permission.RECORD_AUDIO, g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED);
        if (g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED) listen(); else systemDialog();
    }

    private Intent speechIntent() {
        return new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "he-IL")
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "he-IL")
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "פגישה, משימה או הערה?");
    }

    /** Fallback: Google's own speech dialog (no permission needed). */
    private void systemDialog() {
        try { startActivityForResult(speechIntent(), REQ_SPEECH); }
        catch (ActivityNotFoundException e) { done("אין שירות זיהוי דיבור בטלפון (התקינו את אפליקציית Google)"); }
    }

    private void pulse(float level) {
        float s = 1f + Math.max(0f, Math.min(1f, (level + 2f) / 12f)) * 0.35f;
        halo.animate().scaleX(s).scaleY(s).setDuration(90).start();
        logo.animate().scaleX(1f + (s - 1f) * 0.25f).scaleY(1f + (s - 1f) * 0.25f).setDuration(90).start();
    }

    // ---- our own end-of-speech: silence timer, hard cap, tap-to-finish ----
    private SilenceDetector silence; private boolean ended;
    private final android.os.Handler tick = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable ticker = new Runnable() { public void run() {
        if (ended || silence == null) return;
        SilenceDetector.Action a = silence.check(android.os.SystemClock.elapsedRealtime());
        if (a == SilenceDetector.Action.WAIT) { tick.postDelayed(this, 250); return; }
        String t = silence.lastText();
        if (t.isEmpty()) { endRecording(null); done("לא נשמע דיבור"); } else endRecording(t);
    } };

    private void tapToFinish() {
        if (ended) return;
        String t = silence == null ? "" : silence.lastText();
        if (!t.isEmpty()) endRecording(t);
        else if (rec != null) { rec.stopListening(); status.setText("מעבד…"); } // nothing heard yet: let the recognizer finish (cap still applies)
    }

    /** Ends the session exactly once; text != null continues the normal flow (classify, then save). */
    private void endRecording(String text) {
        if (ended) return;
        ended = true; tick.removeCallbacks(ticker);
        if (rec != null) { try { rec.stopListening(); } catch (Exception ignored) {} }
        stopRec();
        if (text != null && !text.isEmpty()) { partial.setText(text); handleSpoken(text); }
    }

    private void listen() {
        silence = new SilenceDetector(android.os.SystemClock.elapsedRealtime()); ended = false;
        tick.postDelayed(ticker, 250);
        rec = SpeechRecognizer.createSpeechRecognizer(this);
        rec.setRecognitionListener(new RecognitionListener() {
            public void onReadyForSpeech(Bundle p) { status.setText("מקשיב… (הקש לסיום)"); }
            public void onBeginningOfSpeech() {}
            public void onRmsChanged(float db) { pulse(db); }
            public void onBufferReceived(byte[] buf) {}
            public void onEndOfSpeech() { status.setText("מעבד…"); pulse(-2f); }
            public void onError(int err) {
                if (ended) return;
                String t = silence.lastText();
                if (!t.isEmpty()) { endRecording(t); return; } // keep what was heard
                ended = true; tick.removeCallbacks(ticker);
                stopRec();
                if (err == SpeechRecognizer.ERROR_NO_MATCH || err == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) done("לא נשמע דיבור");
                else systemDialog();
            }
            public void onPartialResults(Bundle r) {
                ArrayList<String> a = r.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (ended || a == null || a.isEmpty()) return;
                partial.setText(a.get(0)); silence.partial(a.get(0), android.os.SystemClock.elapsedRealtime());
            }
            public void onResults(Bundle r) {
                ArrayList<String> a = r.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (ended) return;
                String t = SilenceDetector.pick(a, silence.lastText());
                if (t.isEmpty()) { endRecording(null); done(null); } else endRecording(t);
            }
            public void onEvent(int t, Bundle p) {}
        });
        rec.startListening(speechIntent());
    }

    private void stopRec() { if (rec != null) { rec.destroy(); rec = null; } }
    @Override protected void onDestroy() { ended = true; tick.removeCallbacks(ticker); stopRec(); super.onDestroy(); }

    private void handleSpoken(String text) {
        Tags.Result tr = Tags.extract(text, Tags.names(this)); // "תתייג מילואים" is removed, topic words stay
        spoken = tr.text; spokenTags = tr.tags;
        try { parsed = Core.parse(this, spoken); } catch (Exception e) { done("שגיאת פענוח: " + e.getMessage()); return; }
        Classifier.Result c = Classifier.classify(spoken, !parsed.optBoolean("timeDefaulted", true) || parsed.optBoolean("allDay", false));
        if (c.kind != Classifier.Kind.MEETING) { showSaved(c); return; }
        showMeeting(); // Google Calendar only, written after the 5s undo window
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        if (req == REQ_SPEECH) {
            ArrayList<String> r = data == null ? null : data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (res != RESULT_OK || r == null || r.isEmpty()) { done(null); return; }
            handleSpoken(r.get(0));
        } else if (req == REQ_AUTH) {
            awaitingConsent = false;
            if (res != RESULT_OK) { done("לא אושרה גישה ליומן – לא נרשם"); return; }
            try { job.start(Core.tokenFromResult(this, data)); if (!pickerMode) done(null); else armPickerTimeout(); }
            catch (Exception e) { done("שגיאת התחברות: " + e.getMessage()); }
        }
    }

    // ---- meeting commit: authorize, then insert in the BACKGROUND; the card never waits for the network ----
    private boolean cardClosed, awaitingConsent;
    private final Runnable closeCard = () -> { if (!cardClosed) done(null); }; // auth still resolving: close anyway
    private MeetingJob job;

    /** pickerMode: the card stays open for the contact choice; otherwise it closes within CLOSE_GRACE_MS. */
    private void commitMeeting() {
        if (!pickerMode) ui.postDelayed(closeCard, CardFlow.CLOSE_GRACE_MS);
        final Context app = getApplicationContext();
        final MeetingJob j = job = new MeetingJob(app, parsed, spoken);
        if (!pickerMode) j.setAttendees(InviteFlow.attendees(invitees)); // undo window over: single matches are final
        Identity.getAuthorizationClient(app).authorize(Core.authRequest())
            .addOnSuccessListener(res -> {
                switch (CardFlow.afterAuth(res.hasResolution(), !cardClosed)) {
                    case SEND_IN_BACKGROUND_AND_CLOSE:
                        j.start(res.getAccessToken());
                        if (!cardClosed && !pickerMode) done(null);
                        break;
                    case ASK_CONSENT: // Google's consent screen; the card waits for the user, not for the network
                        ui.removeCallbacks(closeCard); ui.removeCallbacks(pickerTimeout); awaitingConsent = true;
                        try { startIntentSenderForResult(res.getPendingIntent().getIntentSender(), REQ_AUTH, null, 0, 0, 0, null); }
                        catch (Exception e) { done("שגיאת התחברות: " + e.getMessage()); }
                        break;
                    default: toast(app, CardFlow.CONSENT_NEEDED);
                }
            })
            .addOnFailureListener(e -> { if (!cardClosed) done("שגיאת התחברות: " + e); else toast(app, "לא נרשם ביומן – שגיאת התחברות: " + e); });
    }

    private static void toast(Context app, String msg) { Toast.makeText(app, msg, Toast.LENGTH_LONG).show(); }

    // ---- Tasks & notes -> BabiTk "notebook" board, sent after a 5s undo window ----
    private static final long UNDO_MS = CardFlow.UNDO_MS;
    private final android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());
    private String pendingId, pendingBody; private boolean committed, pendingMeeting;

    // ---- Stage 2: meeting invites ("ותזמין את תמר"), only with the 'שליחת הזמנות' setting on ----
    static final int REQ_CONTACTS = 4;
    private final List<InviteFlow.Invitee> invitees = new ArrayList<>();
    private List<String> inviteQueries = new ArrayList<>();
    private boolean pickerMode;

    private void showMeeting() {
        inviteQueries = new ArrayList<>();
        JSONArray a = parsed.optJSONArray("invite");
        for (int i = 0; a != null && i < a.length(); i++) inviteQueries.add(a.optString(i));
        if (!InviteFlow.wanted(inviteQueries, Core.sendInvites(this), true)) { showMeetingCard(); return; }
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) { resolveInvites(); return; }
        if (!Core.prefs(this).getBoolean("contacts_jit_shown", false)) { showContactsSheet(); return; }
        noPermission();
    }

    private void noPermission() { for (String q : inviteQueries) invitees.add(InviteFlow.Invitee.noPermission(q)); showMeetingCard(); }

    /** Contacts are read on a background thread, matched on the device, and dropped with the card. */
    private void resolveInvites() {
        final Context app = getApplicationContext();
        new Thread(() -> {
            List<ContactMatcher.Contact> all;
            try { all = ContactsSource.load(app); } catch (Exception e) { all = new ArrayList<>(); }
            final List<InviteFlow.Invitee> r = new ArrayList<>();
            for (String q : inviteQueries) r.add(InviteFlow.Invitee.resolve(q, all));
            runOnUiThread(() -> { if (isFinishing() || cardClosed) return; invitees.addAll(r); showMeetingCard(); });
        }, "babisnap-contacts").start();
    }

    /** Just-in-time permission card, shown once (settings design, screen 2). */
    private void showContactsSheet() {
        Core.prefs(this).edit().putBoolean("contacts_jit_shown", true).apply();
        setContentView(R.layout.card_contacts);
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        ((TextView) findViewById(R.id.body)).setText(InviteFlow.jitBody(inviteQueries));
        findViewById(R.id.later).setOnClickListener(v -> noPermission());
        findViewById(R.id.allow).setOnClickListener(v -> {
            if (MainActivity.isBlocked(this, Manifest.permission.READ_CONTACTS)) { // denied twice: Android won't ask again
                MainActivity.openAppInfo(this); noPermission(); return;
            }
            requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, REQ_CONTACTS);
        });
    }

    private void showMeetingCard() {
        JSONObject p = parsed;
        setContentView(R.layout.card_saved);
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        ((TextView) findViewById(R.id.check)).setText("📅");
        ((TextView) findViewById(R.id.headline)).setText("נרשם ביומן גוגל · פגישה");
        TextView chip = findViewById(R.id.chip); chip.setText("פגישה");
        chip.setBackgroundResource(R.drawable.chip_meeting); chip.setTextColor(0xFFFFFFFF);
        ((TextView) findViewById(R.id.summary)).setText(CardText.meeting(p.optString("title"), p.optInt("d"), p.optInt("m"), p.optInt("y"),
            p.optInt("hour"), p.optInt("minute"), p.optBoolean("timeDefaulted"), p.optBoolean("allDay"), p.optString("location", "")));
        if (InviteFlow.needsPicker(invitees)) { startPickers(); return; }
        renderInvites();
        android.widget.ProgressBar bar = findViewById(R.id.countdown);
        android.animation.ObjectAnimator.ofInt(bar, "progress", (int) UNDO_MS, 0).setDuration(UNDO_MS).start();
        pendingMeeting = true;
        // Undo cancels the meeting AND its invites: nothing reaches Google before the window closes.
        findViewById(R.id.undo).setOnClickListener(v -> { ui.removeCallbacks(commit); pendingMeeting = false; done("בוטל – לא נרשם ביומן"); });
        ui.postDelayed(commit, UNDO_MS);
    }

    /** Several contacts match: countdown stops, the meeting is saved now, a tap adds the attendee. */
    private void startPickers() {
        pickerMode = true; committed = true;
        findViewById(R.id.countdown).setVisibility(View.GONE);
        findViewById(R.id.undo).setVisibility(View.GONE);
        renderInvites();
        commitMeeting();
        armPickerTimeout();
    }

    private final Runnable pickerTimeout = this::finishInvites;
    private void armPickerTimeout() { ui.removeCallbacks(pickerTimeout); if (!awaitingConsent) ui.postDelayed(pickerTimeout, InviteFlow.PICKER_TIMEOUT_MS); }

    private void pick(InviteFlow.Invitee i, ContactMatcher.Match m) {
        if (cardClosed) return;
        i.chosen = m; i.decided = true;
        renderInvites();
        if (InviteFlow.nextPicker(invitees) != null) { armPickerTimeout(); return; }
        ui.removeCallbacks(pickerTimeout);
        ui.postDelayed(pickerTimeout, 1200); // let the blue strip be seen
    }

    /** Picker done, timed out or left: whatever was chosen gets the invite; an untouched picker gets none. */
    private void finishInvites() {
        if (job != null) job.setAttendees(InviteFlow.attendees(invitees));
        if (!awaitingConsent) done(null);
    }

    private void renderInvites() {
        LinearLayout box = findViewById(R.id.invites);
        box.removeAllViews();
        InviteFlow.Invitee cur = pickerMode ? InviteFlow.nextPicker(invitees) : null;
        int people = invitees.size();
        for (InviteFlow.Invitee i : invitees) {
            switch (i.kind) {
                case ONE: box.addView(InviteViews.strip(this, true, InviteFlow.okStrip(i.chosen), i.chosen.name)); break;
                case NONE: box.addView(InviteViews.strip(this, false, InviteFlow.noEmailStrip(i.query), null)); break;
                case NO_PERMISSION: box.addView(InviteViews.strip(this, false, InviteFlow.noPermissionStrip(i.query), null)); break;
                case MANY:
                    if (i == cur) box.addView(InviteViews.picker(this, i, people, m -> pick(i, m)));
                    else if (i.decided) box.addView(i.chosen != null ? InviteViews.strip(this, true, InviteFlow.sentStrip(i.chosen), i.chosen.name)
                        : InviteViews.strip(this, false, InviteFlow.skippedStrip(i.query), null));
                    break;
            }
        }
        if (InviteFlow.settingOffHint(inviteQueries, Core.sendInvites(this)))
            box.addView(InviteViews.strip(this, false, InviteFlow.SETTING_OFF_STRIP, null));
        box.setVisibility(box.getChildCount() > 0 ? View.VISIBLE : View.GONE);
    }

    private final Runnable commit = this::commitPending;

    private void showSaved(Classifier.Result c) {
        JSONObject p = parsed;
        try { if (!c.stripped.isEmpty() && !c.stripped.equals(Classifier.normalize(spoken))) p = Core.parse(this, c.stripped); }
        catch (Exception ignored) {}
        boolean noDate = p.optBoolean("dateDefaulted", true) && p.optBoolean("timeDefaulted", true);
        String date = noDate ? null : String.format(java.util.Locale.US, "%04d-%02d-%02d", p.optInt("y"), p.optInt("m"), p.optInt("d"));
        String time = p.optBoolean("timeDefaulted", true) || p.optBoolean("allDay", false) ? null : String.format(java.util.Locale.US, "%02d:%02d", p.optInt("hour"), p.optInt("minute"));
        // Tasks/notes keep the place inside the title ("לקנות חלב בסופר") and also get it in the location field.
        String title = p.optString("titleFull", p.optString("title", "")).trim(); if (title.isEmpty()) title = spoken;
        pendingId = BabiTkJson.newClientId(); // unique per recording, matches the server client_id pattern
        // No "נוצר בהכתבה: …" notes: BabiTk rebuilds task titles from the longest text it has (title vs content), so the
        // provenance line leaked into card titles ("קוד לשער נוצר בהכתבה: קוד"). Provenance is already in metadata.source=voice.
        pendingBody = BabiTkJson.build(title, date, time, "", p.optString("location", ""), c.kind.api(), pendingId, spokenTags);

        setContentView(R.layout.card_saved);
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        boolean hasTok = BabiTk.hasToken(this);
        ((TextView) findViewById(R.id.headline)).setText(hasTok ? "נכנס למחברת ב-BabiTk · " + c.kind.he()
            : "נשמר במכשיר · " + c.kind.he() + "\n" + (BabiTk.needsRepair(this) ? "BabiTk " + BabiTk.NOT_CONNECTED : "BabiTk לא מחובר") + " – פתחו את BabiSnap ← הגדרות ← BabiTk");
        TextView chip = findViewById(R.id.chip); chip.setText(c.kind.he() + (spokenTags.isEmpty() ? "" : " · #" + String.join(" #", spokenTags)));
        if (c.kind == Classifier.Kind.NOTE) { chip.setBackgroundResource(R.drawable.chip_note); chip.setTextColor(0xFF2A1B7A); }
        ((TextView) findViewById(R.id.summary)).setText(CardText.saved(title, date != null, p.optInt("d"), p.optInt("m"), p.optInt("y"), time, p.optString("location", "")));
        android.widget.ProgressBar bar = findViewById(R.id.countdown);
        android.animation.ObjectAnimator.ofInt(bar, "progress", (int) UNDO_MS, 0).setDuration(UNDO_MS).start();
        findViewById(R.id.undo).setOnClickListener(v -> { ui.removeCallbacks(commit); pendingBody = null; done("בוטל – לא נשלח ל-BabiTk"); });
        ui.postDelayed(commit, UNDO_MS);
    }

    /** Undo window closed (or the card was left): queue locally and let WorkManager send with the same client_id. */
    private void commitPending() {
        if (committed) return;
        if (pendingMeeting) { committed = true; ui.removeCallbacks(commit); findViewById(R.id.undo).setVisibility(View.INVISIBLE); commitMeeting(); return; }
        if (pendingBody == null) return;
        committed = true; ui.removeCallbacks(commit);
        BabiTk.enqueue(getApplicationContext(), pendingId, pendingBody);
        done(BabiTk.hasToken(this) ? null : (BabiTk.needsRepair(this) ? "BabiTk " + BabiTk.NOT_CONNECTED : "BabiTk לא מחובר") + ": הפריט נשמר במכשיר ויישלח אחרי חיבור (BabiSnap ← הגדרות ← BabiTk)");
    }

    @Override protected void onStop() {
        if ((pendingBody != null || pendingMeeting) && !committed) commitPending();
        else if (pickerMode && !cardClosed && !awaitingConsent) finishInvites(); // card left mid-picker
        super.onStop();
    }

    private void done(String msg) {
        cardClosed = true; ui.removeCallbacks(closeCard); ui.removeCallbacks(commit); ui.removeCallbacks(pickerTimeout);
        if (msg != null) toast(getApplicationContext(), msg);
        if (!isFinishing()) finish();
    }
}
