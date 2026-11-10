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

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Widget/launcher target: speech (he-IL) -> parse on device -> (optional confirm) -> durable calendar / BabiTk queue. */
public class VoiceActivity extends Activity {
    static final int REQ_SPEECH = 1, REQ_AUTH = 2, REQ_MIC = 3, REQ_CONTACTS = 4;
    private JSONObject parsed; private String spoken; private java.util.List<String> spokenTags = new java.util.ArrayList<>();

    private SpeechRecognizer rec; private View logo, halo; private TextView status, partial;
    private int listenRetries;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        SnapHeartbeat.onAppOpen(this);
        // Recreate after process death: never leave a blank dialog — start a fresh listen session.
        if (b != null) {
            beginListenUi();
            return;
        }
        beginListenUi();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        // Back-to-back from MainActivity/widget while Undo (or listen) is open: commit then re-listen.
        if ((pendingBody != null || pendingMeeting) && !committed) commitPending();
        else if (pickerMode && !cardClosed && !awaitingConsent) finishInvites();
        restartListening();
    }

    private void beginListenUi() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { systemDialog(); return; }
        try { requestWindowFeature(Window.FEATURE_NO_TITLE); } catch (Exception ignored) {}
        setContentView(R.layout.activity_listen);
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        logo = findViewById(R.id.logo); halo = findViewById(R.id.halo);
        status = findViewById(R.id.status); partial = findViewById(R.id.partial);
        findViewById(R.id.cancel).setOnClickListener(v -> { ended = true; tick.removeCallbacks(ticker); stopRec(); dropDraft(); done(null); });
        logo.setOnClickListener(v -> tapToFinish()); halo.setOnClickListener(v -> tapToFinish());
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) listen(false);
        else requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
    }

    /** Reset session state and open the mic again (after onNewIntent commit). */
    private void restartListening() {
        ui.removeCallbacks(commit); ui.removeCallbacks(closeCard); ui.removeCallbacks(pickerTimeout);
        ended = true; tick.removeCallbacks(ticker); stopRec();
        pendingBody = null; pendingId = null; pendingMeeting = false; committed = false;
        cardClosed = false; awaitingConsent = false; pickerMode = false;
        invitees.clear(); inviteQueries = new ArrayList<>();
        draftId = null; parsed = null; spoken = null; spokenTags = new ArrayList<>();
        jobClientId = null;
        listenRetries = 0;
        beginListenUi();
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
        if (g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED) listen(false); else systemDialog();
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

    private void systemDialog() {
        try { startActivityForResult(speechIntent(), REQ_SPEECH); }
        catch (ActivityNotFoundException e) { done("אין שירות זיהוי דיבור בטלפון (התקינו את אפליקציית Google)"); }
    }

    private void pulse(float level) {
        float s = 1f + Math.max(0f, Math.min(1f, (level + 2f) / 12f)) * 0.35f;
        halo.animate().scaleX(s).scaleY(s).setDuration(90).start();
        logo.animate().scaleX(1f + (s - 1f) * 0.25f).scaleY(1f + (s - 1f) * 0.25f).setDuration(90).start();
    }

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
        else if (rec != null) { rec.stopListening(); status.setText("מעבד…"); }
    }

    private void endRecording(String text) {
        if (ended) return;
        ended = true; tick.removeCallbacks(ticker);
        if (rec != null) { try { rec.stopListening(); } catch (Exception ignored) {} }
        stopRec();
        if (text != null && !text.isEmpty()) { partial.setText(text); handleSpoken(text); }
    }

    private void listen(boolean isRetry) {
        if (!isRetry) listenRetries = 0;
        silence = new SilenceDetector(android.os.SystemClock.elapsedRealtime()); ended = false;
        tick.removeCallbacks(ticker); tick.postDelayed(ticker, 250);
        stopRec();
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
                if (!t.isEmpty()) { endRecording(t); return; }
                if ((err == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || err == SpeechRecognizer.ERROR_CLIENT)
                        && listenRetries < 2) {
                    listenRetries++;
                    ended = true; tick.removeCallbacks(ticker); stopRec();
                    final int attempt = listenRetries;
                    tick.postDelayed(() -> {
                        if (isFinishing() || cardClosed) return;
                        status.setText("מנסה שוב…");
                        listen(true);
                    }, 350L * attempt);
                    return;
                }
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

    private void stopRec() { if (rec != null) { try { rec.destroy(); } catch (Exception ignored) {} rec = null; } }
    @Override protected void onDestroy() { ended = true; tick.removeCallbacks(ticker); stopRec(); super.onDestroy(); }

    private void handleSpoken(String text) {
        Tags.Result tr = Tags.extract(text, Tags.names(this));
        spoken = tr.text; spokenTags = tr.tags;
        try { parsed = Core.parse(this, spoken); } catch (Exception e) { done("שגיאת פענוח: " + e.getMessage()); return; }
        Classifier.Result c = Classifier.classify(spoken, !parsed.optBoolean("timeDefaulted", true) || parsed.optBoolean("allDay", false));
        if (c.kind != Classifier.Kind.MEETING) { showSaved(c); return; }
        // Re-parse the text after destination/command words were stripped, so "יומן" / "לקלנדר" never stay in the title.
        try {
            if (c.stripped.isEmpty()) {
                parsed.put("title", "אירוע");
                parsed.put("titleFull", "אירוע");
            } else if (!c.stripped.equals(Classifier.normalize(spoken))) {
                parsed = Core.parse(this, c.stripped);
            }
        } catch (Exception ignored) {}
        armMeetingDraft();
        if (meetingRoute == DeviceCalendarLogic.Route.DEVICE_UNAVAILABLE) {
            dropDraft(); done(DeviceCalendar.ERR_UNAVAILABLE); return;
        }
        showMeeting();
    }

    /** Capture a durable draft as soon as we know it is a meeting — survives leave-before-card. */
    private String draftId;
    private void armMeetingDraft() {
        meetingRoute = DeviceCalendar.route(this);
        draftId = BabiTkJson.newClientId();
        pendingMeeting = true;
        committed = false;
    }
    private void dropDraft() {
        pendingMeeting = false; draftId = null; jobClientId = null;
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        if (req == REQ_SPEECH) {
            ArrayList<String> r = data == null ? null : data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (res != RESULT_OK || r == null || r.isEmpty()) { done(null); return; }
            handleSpoken(r.get(0));
        } else if (req == REQ_AUTH) {
            // Legacy consent path no longer inserts directly — CalQueue waits for Settings sign-in.
            awaitingConsent = false;
            if (res == RESULT_OK) {
                CalQueue.onGoogleAuthorized(getApplicationContext());
                if (!cardClosed) done(CalQueueLogic.ENQUEUED_TOAST);
            } else if (!cardClosed) done("לא אושרה גישה ליומן – הפגישה ממתינה במכשיר");
        }
    }

    private boolean cardClosed, awaitingConsent;
    private final Runnable closeCard = () -> { if (!cardClosed) done(null); };
    private String jobClientId;
    private DeviceCalendarLogic.Route meetingRoute = DeviceCalendarLogic.Route.GOOGLE;

    private void enqueueMeeting(boolean attendeesReady) {
        if (draftId == null || parsed == null) return;
        String route = meetingRoute == DeviceCalendarLogic.Route.DEVICE
            ? CalQueueLogic.ROUTE_DEVICE : CalQueueLogic.ROUTE_GOOGLE;
        List<String> emails = attendeesReady ? InviteFlow.attendees(invitees) : new ArrayList<>();
        jobClientId = draftId;
        CalQueue.enqueue(getApplicationContext(), draftId, route, parsed, spoken, emails, attendeesReady);
    }

    private final android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());
    private static final long UNDO_MS = CardFlow.UNDO_MS;
    private String pendingId, pendingBody; private boolean committed, pendingMeeting;

    private final List<InviteFlow.Invitee> invitees = new ArrayList<>();
    private List<String> inviteQueries = new ArrayList<>();
    private boolean pickerMode;

    private void showMeeting() {
        inviteQueries = new ArrayList<>();
        JSONArray a = parsed.optJSONArray("invite");
        for (int i = 0; a != null && i < a.length(); i++) inviteQueries.add(a.optString(i));
        if (meetingRoute == DeviceCalendarLogic.Route.DEVICE || !InviteFlow.wanted(inviteQueries, Core.sendInvites(this), true)) {
            showMeetingCard(); return;
        }
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) { resolveInvites(); return; }
        if (!Core.prefs(this).getBoolean("contacts_jit_shown", false)) { showContactsSheet(); return; }
        noPermission();
    }

    private void noPermission() { for (String q : inviteQueries) invitees.add(InviteFlow.Invitee.noPermission(q)); showMeetingCard(); }

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

    private void showContactsSheet() {
        Core.prefs(this).edit().putBoolean("contacts_jit_shown", true).apply();
        setContentView(R.layout.card_contacts);
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        ((TextView) findViewById(R.id.body)).setText(InviteFlow.jitBody(inviteQueries));
        findViewById(R.id.later).setOnClickListener(v -> noPermission());
        findViewById(R.id.allow).setOnClickListener(v -> {
            if (MainActivity.isBlocked(this, Manifest.permission.READ_CONTACTS)) {
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
        ((TextView) findViewById(R.id.headline)).setText(
            CalQueueLogic.cardHeadline(meetingRoute == DeviceCalendarLogic.Route.DEVICE));
        TextView chip = findViewById(R.id.chip); chip.setText("פגישה");
        chip.setBackgroundResource(R.drawable.chip_meeting); chip.setTextColor(0xFFFFFFFF);
        ((TextView) findViewById(R.id.summary)).setText(CardText.meeting(p.optString("title"), p.optInt("d"), p.optInt("m"), p.optInt("y"),
            p.optInt("hour"), p.optInt("minute"), p.optBoolean("timeDefaulted"), p.optBoolean("allDay"), p.optString("location", "")));
        if (InviteFlow.needsPicker(invitees)) { startPickers(); return; }
        renderInvites();
        android.widget.ProgressBar bar = findViewById(R.id.countdown);
        android.animation.ObjectAnimator.ofInt(bar, "progress", (int) UNDO_MS, 0).setDuration(UNDO_MS).start();
        pendingMeeting = true;
        findViewById(R.id.undo).setOnClickListener(v -> {
            ui.removeCallbacks(commit); dropDraft(); done("בוטל – לא נרשם ביומן");
        });
        ui.postDelayed(commit, UNDO_MS);
    }

    private void startPickers() {
        pickerMode = true; committed = true;
        findViewById(R.id.countdown).setVisibility(View.GONE);
        findViewById(R.id.undo).setVisibility(View.GONE);
        renderInvites();
        // Persist immediately (attendees filled in when picker ends).
        enqueueMeeting(false);
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
        ui.postDelayed(pickerTimeout, 1200);
    }

    private void finishInvites() {
        if (jobClientId != null) CalQueue.setAttendees(getApplicationContext(), jobClientId, InviteFlow.attendees(invitees));
        if (!awaitingConsent) done(CalQueueLogic.ENQUEUED_TOAST);
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
        String deviceNote = InviteFlow.deviceInviteNote(meetingRoute == DeviceCalendarLogic.Route.DEVICE, inviteQueries);
        if (deviceNote != null) box.addView(InviteViews.strip(this, false, deviceNote, null));
        else if (InviteFlow.settingOffHint(inviteQueries, Core.sendInvites(this)))
            box.addView(InviteViews.strip(this, false, InviteFlow.SETTING_OFF_STRIP, null));
        box.setVisibility(box.getChildCount() > 0 ? View.VISIBLE : View.GONE);
    }

    private final Runnable commit = this::commitPending;

    private void showSaved(Classifier.Result c) {
        JSONObject p = parsed;
        try { if (!c.stripped.isEmpty() && !c.stripped.equals(Classifier.normalize(spoken))) p = Core.parse(this, c.stripped, true); }
        catch (Exception ignored) {}
        boolean noDate = p.optBoolean("dateDefaulted", true) && p.optBoolean("timeDefaulted", true);
        String date = noDate ? null : String.format(java.util.Locale.US, "%04d-%02d-%02d", p.optInt("y"), p.optInt("m"), p.optInt("d"));
        String time = p.optBoolean("timeDefaulted", true) || p.optBoolean("allDay", false) ? null : String.format(java.util.Locale.US, "%02d:%02d", p.optInt("hour"), p.optInt("minute"));
        String title = p.optString("titleFull", p.optString("title", "")).trim(); if (title.isEmpty()) title = spoken;
        pendingId = BabiTkJson.newClientId();
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

    private void commitPending() {
        if (committed) return;
        if (pendingMeeting) {
            committed = true; ui.removeCallbacks(commit);
            try { findViewById(R.id.undo).setVisibility(View.INVISIBLE); } catch (Exception ignored) {}
            enqueueMeeting(true);
            pendingMeeting = false;
            done(CalQueueLogic.ENQUEUED_TOAST);
            return;
        }
        if (pendingBody == null) return;
        committed = true; ui.removeCallbacks(commit);
        BabiTk.enqueue(getApplicationContext(), pendingId, pendingBody);
        done(BabiTk.hasToken(this) ? null : (BabiTk.needsRepair(this) ? "BabiTk " + BabiTk.NOT_CONNECTED : "BabiTk לא מחובר") + ": הפריט נשמר במכשיר ויישלח אחרי חיבור (BabiSnap ← הגדרות ← BabiTk)");
    }

    @Override protected void onStop() {
        if ((pendingBody != null || pendingMeeting) && !committed) commitPending();
        else if (pickerMode && !cardClosed && !awaitingConsent) finishInvites();
        super.onStop();
    }

    private void done(String msg) {
        cardClosed = true; ui.removeCallbacks(closeCard); ui.removeCallbacks(commit); ui.removeCallbacks(pickerTimeout);
        if (msg != null) Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
        if (!isFinishing()) finish();
    }
}
