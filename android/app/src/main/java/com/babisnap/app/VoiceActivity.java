package com.babisnap.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.view.Gravity;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.widget.Toast;
import android.Manifest;
import android.content.pm.PackageManager;
import android.speech.RecognitionListener;
import android.speech.SpeechRecognizer;
import android.view.View;
import android.view.Window;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Widget/launcher target: speech (he-IL) -> parse on device -> (optional confirm) -> durable calendar / BabiTk queue.
 *  WhatsApp is a separate opt-in destination (setting off by default) and never writes those queues.
 *  An immediate call ("תתקשר לתמרי", no future date or time) opens the system dialer and never writes those queues. */
public class VoiceActivity extends Activity {
    static final int REQ_SPEECH = 1, REQ_AUTH = 2, REQ_MIC = 3, REQ_CONTACTS = 4, REQ_WA = WhatsAppShare.REQ;
    private JSONObject parsed; private String spoken; private java.util.List<String> spokenTags = new java.util.ArrayList<>();

    private SpeechRecognizer rec; private View logo, halo; private TextView status, partial;
    private int listenRetries;
    private WhatsAppCapture waAudio;
    private boolean waPending, waHandedOff, waLeft, waCaptureGaveUp, waConfirming, waGroup, waAskingContacts;
    private Runnable waCommit;
    private String waText = "";
    private String waBody = "";
    private String waGroupName = "";
    private WhatsAppRecipients.Contact waPerson;
    private WhatsAppRecipients.Command waCommand;
    private final List<WhatsAppRecipients.Contact> waContacts = new ArrayList<>();
    private Runnable waAfterContacts;
    private Uri waUri;
    private android.animation.ObjectAnimator waAnim;
    private boolean callPending, callConfirming, callAskingContacts, callDialed;
    private String callName = "";
    private WhatsAppRecipients.Contact callPerson;
    private final List<WhatsAppRecipients.Contact> callContacts = new ArrayList<>();
    private Runnable callAfterContacts;
    private android.animation.ObjectAnimator callAnim;

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
        // A WhatsApp card and a call card are not queue items — leaving them cancels, it does not save.
        if (callPending && !callDialed) cancelCall(null);
        else if (waPending && !waHandedOff) cancelWhatsApp(null);
        else if ((pendingBody != null || pendingMeeting) && !committed) commitPending();
        else if (pickerMode && !cardClosed && !awaitingConsent) finishInvites();
        restartListening();
    }

    private void beginListenUi() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { systemDialog(); return; }
        try { requestWindowFeature(Window.FEATURE_NO_TITLE); } catch (Exception ignored) {}
        WhatsAppCapture.sweep(getCacheDir());
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
        ui.removeCallbacks(commit); ui.removeCallbacks(closeCard); ui.removeCallbacks(pickerTimeout); ui.removeCallbacks(waExpire); ui.removeCallbacks(callExpire);
        ended = true; tick.removeCallbacks(ticker); stopRec();
        if (waHandedOff) { waAudio = null; waUri = null; } else discardWa();
        waPending = false; waHandedOff = false; waLeft = false; waConfirming = false; waGroup = false; waAskingContacts = false;
        waCommit = null;
        waText = ""; waBody = ""; waGroupName = ""; waPerson = null; waCommand = null; waAfterContacts = null; waContacts.clear();
        callPending = false; callConfirming = false; callAskingContacts = false; callDialed = false;
        callName = ""; callPerson = null; callAfterContacts = null; callContacts.clear();
        if (callAnim != null) callAnim.cancel();
        pendingBody = null; pendingId = null; pendingMeeting = false; committed = false;
        cardClosed = false; awaitingConsent = false; pickerMode = false;
        invitees.clear(); inviteQueries = new ArrayList<>();
        draftId = null; parsed = null; spoken = null; spokenTags = new ArrayList<>();
        jobClientId = null;
        listenRetries = 0; waCaptureGaveUp = false;
        beginListenUi();
    }

    @Override public void onRequestPermissionsResult(int req, String[] p, int[] g) {
        if (req == REQ_CONTACTS) {
            boolean ok = g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED;
            MainActivity.noteResult(this, Manifest.permission.READ_CONTACTS, ok);
            if (callPending) {
                callAskingContacts = false;
                if (ok) loadCallContacts();
                else { callContacts.clear(); if (callAfterContacts != null) callAfterContacts.run(); }
                return;
            }
            if (waPending) {
                waAskingContacts = false;
                if (ok) loadWaContacts();
                else { waContacts.clear(); if (waAfterContacts != null) waAfterContacts.run(); }
                return;
            }
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
            .putExtra(RecognizerIntent.EXTRA_PROMPT, Core.whatsAppEnabled(this)
                ? "פגישה, משימה, שיחה, הערה או וואטסאפ?" : "פגישה, משימה, שיחה או הערה?");
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
        stopWaCapture();
        if (text != null && !text.isEmpty()) { partial.setText(text); handleSpoken(text); }
    }

    private void listen(boolean isRetry) {
        if (!isRetry) { listenRetries = 0; waCaptureGaveUp = false; }
        silence = new SilenceDetector(android.os.SystemClock.elapsedRealtime()); ended = false;
        tick.removeCallbacks(ticker); tick.postDelayed(ticker, 250);
        stopRec();
        rec = SpeechRecognizer.createSpeechRecognizer(this);
        rec.setRecognitionListener(new RecognitionListener() {
            public void onReadyForSpeech(Bundle p) { status.setText("מקשיב… (הקש לסיום)"); startWaCaptureIfWanted(); }
            public void onBeginningOfSpeech() {}
            public void onRmsChanged(float db) { pulse(db); }
            public void onBufferReceived(byte[] buf) {}
            public void onEndOfSpeech() { status.setText("מעבד…"); pulse(-2f); }
            public void onError(int err) {
                if (ended) return;
                String t = silence.lastText();
                if (!t.isEmpty()) { endRecording(t); return; }
                // A second recorder can take the mic from the recognizer. Drop it and listen once more.
                if (waAudio != null && waAudio.isRecording() && !waCaptureGaveUp) {
                    waCaptureGaveUp = true;
                    discardWa();
                    ended = true; tick.removeCallbacks(ticker); stopRec();
                    tick.postDelayed(() -> {
                        if (isFinishing() || cardClosed) return;
                        status.setText("מנסה שוב…");
                        listen(true);
                    }, 350L);
                    return;
                }
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
    @Override protected void onDestroy() {
        ended = true; tick.removeCallbacks(ticker); ui.removeCallbacks(waExpire); ui.removeCallbacks(callExpire); stopRec();
        if (!waHandedOff) discardWa();
        super.onDestroy();
    }

    private void handleSpoken(String text) {
        Tags.Result tr = Tags.extract(text, Tags.names(this));
        spoken = tr.text; spokenTags = tr.tags;
        try { parsed = Core.parse(this, spoken); } catch (Exception e) { done("שגיאת פענוח: " + e.getMessage()); return; }
        boolean explicitTime = !parsed.optBoolean("timeDefaulted", true) || parsed.optBoolean("allDay", false);
        Classifier.Result c = Classifier.classify(spoken, explicitTime, Core.whatsAppEnabled(this));
        RouteGate.Dest dest = RouteGate.dest(c.kind);
        if (dest == RouteGate.Dest.WHATSAPP
                && WhatsAppGroupShare.entersWhatsApp(WhatsAppRecipients.parse(spoken == null ? "" : spoken).target)) {
            showWhatsApp(c); return;
        }
        if (dest == RouteGate.Dest.WHATSAPP) {
            c = Classifier.classify(spoken, explicitTime, false);
            dest = RouteGate.dest(c.kind);
        }
        if (dest == RouteGate.Dest.CALL) { showCall(c); return; }
        discardWa();
        if (dest != RouteGate.Dest.CALENDAR) { showSaved(c); return; }
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
        if (req == REQ_WA) {
            if (waLeft) finishWaShare();
            return;
        }
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
        RouteMark.apply(chip, RouteMark.Route.CALENDAR, 0xFFFFFFFF);
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
        if (!RouteGate.toBabiTk(c.kind)) return;
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
        RouteMark.apply(chip, RouteMark.Route.BABITK, chip.getCurrentTextColor());
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
        if (BabiTkJson.isWhatsAppPayload(pendingBody)) { pendingBody = null; done("בוטל – לא נשלח לוואטסאפ"); return; }
        if (BabiTkJson.isCallPayload(pendingBody)) { pendingBody = null; done(CallDial.CANCELLED); return; }
        committed = true; ui.removeCallbacks(commit);
        BabiTk.enqueue(getApplicationContext(), pendingId, pendingBody);
        done(BabiTk.hasToken(this) ? null : (BabiTk.needsRepair(this) ? "BabiTk " + BabiTk.NOT_CONNECTED : "BabiTk לא מחובר") + ": הפריט נשמר במכשיר ויישלח אחרי חיבור (BabiSnap ← הגדרות ← BabiTk)");
    }

    @Override protected void onStop() {
        if (callAskingContacts || waAskingContacts) { super.onStop(); return; }
        if ((callConfirming || callPending) && !callDialed) cancelCall(CallDial.CANCELLED);
        if (waHandedOff) waLeft = true;
        else if (waConfirming) runWaCommit();
        else if (waPending) cancelWhatsApp("בוטל – לא נשלח לוואטסאפ");
        if ((pendingBody != null || pendingMeeting) && !committed) commitPending();
        else if (pickerMode && !cardClosed && !awaitingConsent) finishInvites();
        super.onStop();
    }

    @Override protected void onResume() {
        super.onResume();
        if (waHandedOff && waLeft) finishWaShare();
    }

    private void done(String msg) {
        cardClosed = true; ui.removeCallbacks(closeCard); ui.removeCallbacks(commit); ui.removeCallbacks(pickerTimeout); ui.removeCallbacks(waExpire); ui.removeCallbacks(callExpire);
        if (waAnim != null) waAnim.cancel();
        if (callAnim != null) callAnim.cancel();
        if (!waHandedOff) discardWa();
        if (msg != null) Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
        if (!isFinishing()) finish();
    }

    /** The chat gets the transcript. A second recorder is not started beside the recognizer. */
    private void startWaCaptureIfWanted() {}

    private void stopWaCapture() { if (waAudio != null) waAudio.stop(); }

    /** Release the temp file. Does not log the path or the transcript. */
    private void discardWa() {
        if (waUri != null) {
            try { revokeUriPermission(waUri, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) {}
            waUri = null;
        }
        if (waAudio != null) { waAudio.delete(); waAudio = null; }
    }

    private final Runnable waExpire = this::runWaCommit;

    private void runWaCommit() {
        if (!waConfirming || waCommit == null) return;
        Runnable go = waCommit;
        waConfirming = false;
        waCommit = null;
        ui.removeCallbacks(waExpire);
        if (waAnim != null) waAnim.cancel();
        go.run();
    }

    private void cancelWhatsApp(String toast) {
        if (waHandedOff) return;
        waPending = false;
        ui.removeCallbacks(waExpire);
        if (waAnim != null) waAnim.cancel();
        discardWa();
        if (toast != null) done(toast);
    }

    private void showWhatsApp(Classifier.Result c) {
        stopWaCapture();
        waText = c.stripped == null ? "" : c.stripped.trim();
        waPending = true; waHandedOff = false; waLeft = false; waConfirming = false; waGroup = false; waPerson = null; waCommit = null;
        pendingBody = null; pendingMeeting = false; committed = false;
        waCommand = WhatsAppRecipients.parse(spoken == null ? "" : spoken);
        waBody = WhatsAppRecipients.shownText(waCommand);
        if (waCommand.target == WhatsAppRecipients.Target.GROUP) {
            showWaGroup(waCommand.name, waCommand.body);
            return;
        }
        waAfterContacts = () -> {
            if (isFinishing() || cardClosed) return;
            if (waCommand.target != WhatsAppRecipients.Target.PERSON || waCommand.name.isEmpty()) {
                showWaChooser(WhatsAppRecipients.search(waContacts, ""), true);
                return;
            }
            WhatsAppRecipients.Matches hits = WhatsAppRecipients.match(waCommand.name, waContacts);
            if (hits.one()) showWaDirect(hits.hits.get(0), waBody);
            else if (hits.code == WhatsAppRecipients.MatchCode.MANY) showWaChooser(hits.hits, false);
            else showWaChooser(WhatsAppRecipients.search(waContacts, ""), true);
        };
        ensureWaContacts();
    }

    /** READ_CONTACTS is already in the manifest for invites. Ask only when it is still missing. */
    private void ensureWaContacts() {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            loadWaContacts();
            return;
        }
        if (MainActivity.isBlocked(this, Manifest.permission.READ_CONTACTS)) {
            MainActivity.openAppInfo(this);
            waContacts.clear();
            if (waAfterContacts != null) waAfterContacts.run();
            return;
        }
        showWaContactsSheet();
    }

    /** Same permission card the meeting invite uses. */
    private void showWaContactsSheet() {
        setContentView(R.layout.card_contacts);
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        ((TextView) findViewById(R.id.body)).setText("כדי למצוא את הנמען בוואטסאפ צריך גישה לאנשי הקשר בטלפון. המספר לא נשמר ולא נשלח.");
        findViewById(R.id.later).setOnClickListener(v -> {
            waContacts.clear();
            if (waAfterContacts != null) waAfterContacts.run();
        });
        findViewById(R.id.allow).setOnClickListener(v -> {
            if (MainActivity.isBlocked(this, Manifest.permission.READ_CONTACTS)) {
                MainActivity.openAppInfo(this);
                waContacts.clear();
                if (waAfterContacts != null) waAfterContacts.run();
                return;
            }
            waAskingContacts = true;
            requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, REQ_CONTACTS);
        });
    }

    private void loadWaContacts() {
        final Context app = getApplicationContext();
        new Thread(() -> {
            List<WhatsAppRecipients.Contact> list;
            try { list = WhatsAppContacts.load(app); }
            catch (Exception e) { list = new ArrayList<>(); }
            final List<WhatsAppRecipients.Contact> ready = list;
            runOnUiThread(() -> {
                if (isFinishing() || cardClosed) return;
                waContacts.clear();
                waContacts.addAll(ready);
                if (waAfterContacts != null) waAfterContacts.run();
            });
        }, "babisnap-wa-contacts").start();
    }

    private void showWaChooser(List<WhatsAppRecipients.Contact> people, boolean search) {
        waConfirming = false;
        waGroup = false;
        waPerson = null;
        waCommit = null;
        paintWaCard(search ? "וואטסאפ · בחירת נמען" : "וואטסאפ · כמה אנשי קשר", "וואטסאפ",
            waBody.isEmpty() ? "אין טקסט" : waBody, false);
        LinearLayout box = findViewById(R.id.invites);
        box.removeAllViews();
        if (search) {
            EditText field = new EditText(this);
            field.setHint("חיפוש איש קשר");
            field.setSingleLine(true);
            field.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
            field.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    bindWaChoices(WhatsAppRecipients.search(waContacts, s == null ? "" : s.toString()), true);
                }
            });
            box.addView(field);
        }
        bindWaChoices(people, true);
        box.setVisibility(View.VISIBLE);
    }

    private void bindWaChoices(List<WhatsAppRecipients.Contact> people, boolean keepSearch) {
        LinearLayout box = findViewById(R.id.invites);
        if (box == null) return;
        View search = keepSearch && box.getChildCount() > 0 && box.getChildAt(0) instanceof EditText ? box.getChildAt(0) : null;
        box.removeAllViews();
        if (search != null) box.addView(search);
        if (search != null && checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED)
            box.addView(InviteViews.strip(this, false, "בלי הרשאת אנשי קשר אי אפשר למצוא נמען", null));
        List<String> names = new ArrayList<>();
        List<String> details = new ArrayList<>();
        for (WhatsAppRecipients.Contact p : people) {
            names.add(p.label());
            details.add(p.pretty());
        }
        box.addView(InviteViews.choiceList(this, "למי לפתוח וואטסאפ?", names, details, "פתח וואטסאפ בלי נמען", index -> {
            if (index < 0) showWaPlain();
            else if (index < people.size()) showWaDirect(people.get(index), waBody);
        }));
    }

    /** One contact, on the meeting card. The countdown is CardFlow.UNDO_MS, then the chat opens. */
    private void showWaDirect(WhatsAppRecipients.Contact person, String body) {
        waGroup = false;
        waPerson = person;
        waGroupName = "";
        waBody = body == null ? "" : body;
        waText = waBody;
        paintWaCard(WhatsAppRecipients.cardHeadline(person), person.pretty().isEmpty() ? "וואטסאפ" : person.pretty(),
            waBody.isEmpty() ? "אין טקסט" : waBody, true);
        waCommit = this::openDirectChat;
    }

    /** Group name on the meeting card. WhatsApp's own forward picker is where the group is searched. */
    private void showWaGroup(String name, String body) {
        waGroup = true;
        waPerson = null;
        waGroupName = name == null ? "" : name.trim();
        waBody = body == null ? "" : body;
        waText = waBody;
        paintWaCard(WhatsAppRecipients.groupCardHeadline(waGroupName), waGroupName.isEmpty() ? "קבוצה" : waGroupName,
            waBody.isEmpty() ? "אין טקסט" : waBody, true);
        if (!waGroupName.isEmpty()) {
            copyGroupName(waGroupName);
            LinearLayout box = findViewById(R.id.invites);
            box.removeAllViews();
            box.addView(InviteViews.strip(this, true, "«" + waGroupName + "» הועתק. הדביקו בחיפוש של וואטסאפ", waGroupName));
            box.setVisibility(View.VISIBLE);
        }
        waCommit = this::openGroupShare;
    }

    private void showWaPlain() {
        waGroup = true;
        waPerson = null;
        waGroupName = "";
        paintWaCard(WhatsAppRecipients.plainCardHeadline(), "וואטסאפ", waBody.isEmpty() ? "אין טקסט" : waBody, true);
        waCommit = this::openGroupShare;
    }

    private void copyGroupName(String name) {
        if (name == null || name.trim().isEmpty()) return;
        try {
            android.content.ClipboardManager clips = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clips != null) clips.setPrimaryClip(android.content.ClipData.newPlainText("קבוצה", name.trim()));
        } catch (RuntimeException ignored) {}
    }

    /** The saved-card used for a meeting and for a BabiTk item. Undo text stays the layout's «ביטול (Undo)». */
    private void paintWaCard(String headline, String chipText, String summary, boolean counting) {
        setContentView(R.layout.card_saved);
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        ((TextView) findViewById(R.id.check)).setText("💬");
        ((TextView) findViewById(R.id.headline)).setText(headline);
        TextView chip = findViewById(R.id.chip);
        chip.setText(chipText);
        chip.setBackgroundResource(R.drawable.chip_whatsapp);
        chip.setTextColor(0xFFFFFFFF);
        RouteMark.apply(chip, RouteMark.Route.WHATSAPP, 0xFFFFFFFF);
        ((TextView) findViewById(R.id.summary)).setText(summary);
        findViewById(R.id.undo).setOnClickListener(v -> cancelWhatsApp("בוטל – לא נשלח לוואטסאפ"));
        if (!counting) {
            waConfirming = false;
            findViewById(R.id.countdown).setVisibility(View.GONE);
            return;
        }
        waConfirming = true;
        android.widget.ProgressBar bar = findViewById(R.id.countdown);
        bar.setMax((int) UNDO_MS);
        bar.setProgress((int) UNDO_MS);
        if (waAnim != null) waAnim.cancel();
        waAnim = android.animation.ObjectAnimator.ofInt(bar, "progress", (int) UNDO_MS, 0).setDuration(UNDO_MS);
        waAnim.start();
        ui.removeCallbacks(waExpire);
        ui.postDelayed(waExpire, UNDO_MS);
    }

    /** WhatsApp's own forward picker. The group name is already on the clipboard for its search. */
    private void openGroupShare() {
        if (!waPending || waHandedOff) return;
        ui.removeCallbacks(waExpire);
        if (waAnim != null) waAnim.cancel();
        copyGroupName(waGroupName);
        String text = waBody == null ? "" : waBody;
        Intent intent = WhatsAppShare.groupPicker(this, text);
        if (intent != null && startWa(intent)) { discardWa(); return; }
        Toast.makeText(this, "וואטסאפ לא מותקן במכשיר", Toast.LENGTH_LONG).show();
    }

    /** Open the private chat with the text filled in. WhatsApp sends only after the user taps Send. */
    private void openDirectChat() {
        if (!waPending || waHandedOff || waPerson == null) return;
        ui.removeCallbacks(waExpire);
        if (waAnim != null) waAnim.cancel();
        String pkg = WhatsAppShare.resolvePackage(this);
        String digits = waPerson.digits();
        String jid = WhatsAppRecipients.contactJid(digits);
        String text = waBody == null ? "" : waBody;
        if (pkg != null && jid != null && startWa(WhatsAppShare.textToJid(pkg, jid, text))) {
            discardWa();
            return;
        }
        if (digits != null && !digits.isEmpty()
                && (startWa(WhatsAppShare.waMe(pkg, digits, text)) || startWa(WhatsAppShare.waMe(null, digits, text)))) {
            discardWa();
            return;
        }
        Toast.makeText(this, pkg == null ? "וואטסאפ לא מותקן במכשיר" : "וואטסאפ לא נפתח", Toast.LENGTH_LONG).show();
    }

    private boolean startWa(Intent intent) {
        waHandedOff = true;
        waPending = false;
        try {
            startActivityForResult(intent, REQ_WA);
            Toast.makeText(getApplicationContext(), "נפתח בוואטסאפ", Toast.LENGTH_LONG).show();
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            waHandedOff = false;
            waPending = true;
            return false;
        }
    }

    private void finishWaShare() {
        waHandedOff = false;
        waPending = false;
        waLeft = false;
        discardWa();
        done(null);
    }

    private final Runnable callExpire = this::runCallCommit;

    private void runCallCommit() {
        if (!callConfirming) return;
        placeCall(CallDial.Moment.TIMEOUT);
    }

    /** Drop the call card. A null toast does not finish — onNewIntent is about to listen again. */
    private void cancelCall(String toast) {
        if (callDialed) return;
        callPending = false;
        callConfirming = false;
        ui.removeCallbacks(callExpire);
        if (callAnim != null) callAnim.cancel();
        callPerson = null;
        if (toast != null) done(toast);
    }

    /** Immediate dial. Never enqueues a meeting or a BabiTk item. */
    private void showCall(Classifier.Result c) {
        discardWa();
        pendingBody = null;
        pendingMeeting = false;
        committed = false;
        callName = c.stripped == null ? "" : c.stripped.trim();
        callPerson = null;
        callDialed = false;
        callConfirming = false;
        if (callName.isEmpty()) { done(CallDial.NO_NAME); return; }
        callPending = true;
        callAfterContacts = () -> {
            if (isFinishing() || cardClosed) return;
            if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                done(CallDial.NO_PERMISSION);
                return;
            }
            WhatsAppRecipients.Matches hits = CallDial.match(callName, callContacts);
            if (hits.one()) showCallConfirm(hits.hits.get(0));
            else if (hits.code == WhatsAppRecipients.MatchCode.MANY) showCallPicker(hits.hits);
            else done(CallDial.notFound(callName));
        };
        ensureCallContacts();
    }

    private void ensureCallContacts() {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            loadCallContacts();
            return;
        }
        if (MainActivity.isBlocked(this, Manifest.permission.READ_CONTACTS)) {
            done(CallDial.NO_PERMISSION);
            return;
        }
        showCallContactsSheet();
    }

    private void showCallContactsSheet() {
        setContentView(R.layout.card_contacts);
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        ((TextView) findViewById(R.id.body)).setText("כדי לחייג לפי השם צריך גישה לאנשי הקשר בטלפון. המספר לא נשמר ולא נשלח.");
        findViewById(R.id.later).setOnClickListener(v -> {
            callContacts.clear();
            if (callAfterContacts != null) callAfterContacts.run();
        });
        findViewById(R.id.allow).setOnClickListener(v -> {
            if (MainActivity.isBlocked(this, Manifest.permission.READ_CONTACTS)) {
                done(CallDial.NO_PERMISSION);
                return;
            }
            callAskingContacts = true;
            requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, REQ_CONTACTS);
        });
    }

    private void loadCallContacts() {
        final Context app = getApplicationContext();
        new Thread(() -> {
            List<WhatsAppRecipients.Contact> list;
            try { list = WhatsAppContacts.load(app, false); }
            catch (Exception e) { list = new ArrayList<>(); }
            final List<WhatsAppRecipients.Contact> ready = list;
            runOnUiThread(() -> {
                if (isFinishing() || cardClosed) return;
                callContacts.clear();
                callContacts.addAll(ready);
                if (callAfterContacts != null) callAfterContacts.run();
            });
        }, "babisnap-call-contacts").start();
    }

    private void showCallPicker(List<WhatsAppRecipients.Contact> people) {
        callConfirming = false;
        callPerson = null;
        paintCallCard("שיחה · כמה אנשי קשר", "שמעתי «" + callName + "»", false);
        LinearLayout box = findViewById(R.id.invites);
        box.removeAllViews();
        List<String> names = new ArrayList<>();
        List<String> details = new ArrayList<>();
        for (WhatsAppRecipients.Contact p : people) {
            names.add(p.label());
            details.add("\u2066" + CallDial.mask(CallDial.chooseRaw(p)) + "\u2069");
        }
        box.addView(InviteViews.choiceList(this, CallDial.PICK, names, details, null, index -> {
            if (index >= 0 && index < people.size()) showCallConfirm(people.get(index));
        }));
        box.setVisibility(View.VISIBLE);
    }

    private void showCallConfirm(WhatsAppRecipients.Contact person) {
        callPerson = person;
        boolean direct = CallDial.effective(directPref(), callPhoneGranted());
        paintCallCard(CallDial.headline(person, direct), CallDial.summary(person, direct), true);
        ui.removeCallbacks(callExpire);
        ui.postDelayed(callExpire, CallDial.CONFIRM_MS);
    }

    private boolean directPref() { return Core.prefs(this).getBoolean(CallDial.PREF_DIRECT, false); }

    private boolean callPhoneGranted() {
        return checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * The same saved card as a meeting. Actions come from {@link CallDial#actions()}
     * so a later "וואטסאפ" voice button is another entry, not a new layout.
     */
    private void paintCallCard(String headline, String summary, boolean counting) {
        setContentView(R.layout.card_saved);
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        ((TextView) findViewById(R.id.check)).setText("📞");
        ((TextView) findViewById(R.id.headline)).setText(headline);
        TextView chip = findViewById(R.id.chip);
        chip.setText("שיחה");
        chip.setBackgroundResource(R.drawable.chip_call);
        chip.setTextColor(0xFFFFFFFF);
        RouteMark.apply(chip, RouteMark.Route.CALL, 0xFFFFFFFF);
        ((TextView) findViewById(R.id.summary)).setText(summary);
        TextView undo = findViewById(R.id.undo);
        undo.setText("ביטול");
        undo.setOnClickListener(v -> cancelCall(CallDial.CANCELLED));
        LinearLayout actions = findViewById(R.id.call_actions);
        actions.removeAllViews();
        if (!counting) {
            callConfirming = false;
            actions.setVisibility(View.GONE);
            findViewById(R.id.countdown).setVisibility(View.GONE);
            return;
        }
        callConfirming = true;
        for (CallDial.Action action : CallDial.actions()) {
            TextView button = new TextView(this);
            button.setText(action.label);
            button.setTag(action.name());
            button.setGravity(Gravity.CENTER);
            button.setTextColor(0xFFFFFFFF);
            button.setTextSize(15);
            button.setTypeface(Typeface.DEFAULT_BOLD);
            button.setMinHeight(InviteViews.dp(this, 48));
            button.setMinimumHeight(InviteViews.dp(this, 48));
            button.setBackgroundResource(R.drawable.pill_purple);
            button.setOnClickListener(v -> onCallAction(action));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = InviteViews.dp(this, 10);
            actions.addView(button, lp);
        }
        actions.setVisibility(View.VISIBLE);
        android.widget.ProgressBar bar = findViewById(R.id.countdown);
        bar.setVisibility(View.VISIBLE);
        bar.setMax((int) CallDial.CONFIRM_MS);
        bar.setProgress((int) CallDial.CONFIRM_MS);
        if (callAnim != null) callAnim.cancel();
        callAnim = android.animation.ObjectAnimator.ofInt(bar, "progress", (int) CallDial.CONFIRM_MS, 0);
        callAnim.setDuration(CallDial.CONFIRM_MS);
        callAnim.start();
    }

    /** «חייג» uses the same decision as the countdown. A later WhatsApp voice action gets its own branch. */
    private void onCallAction(CallDial.Action action) {
        if (action == CallDial.Action.DIAL) placeCall(CallDial.Moment.TAP);
    }

    /**
     * Timeout or «חייג». ACTION_CALL only when the settings switch is on and CALL_PHONE is granted.
     * Otherwise the dialer. The permission is never requested from this card.
     */
    private void placeCall(CallDial.Moment moment) {
        if (callDialed || callPerson == null) return;
        String tel = CallDial.tel(callPerson);
        CallDial.Handset how = CallDial.place(moment, directPref(), callPhoneGranted(), tel != null);
        callConfirming = false;
        callPending = false;
        ui.removeCallbacks(callExpire);
        if (callAnim != null) callAnim.cancel();
        if (how == CallDial.Handset.NONE || tel == null) {
            done(tel == null ? CallDial.NO_NUMBER : CallDial.CANCELLED);
            return;
        }
        callDialed = true;
        String action = how == CallDial.Handset.CALL ? Intent.ACTION_CALL : Intent.ACTION_DIAL;
        try {
            startActivity(new Intent(action, Uri.parse(tel)));
            done(null);
        } catch (SecurityException e) {
            if (how == CallDial.Handset.CALL && openDialer(tel)) return;
            callDialed = false;
            done(CallDial.NO_DIALER);
        } catch (ActivityNotFoundException e) {
            if (how == CallDial.Handset.CALL && openDialer(tel)) return;
            callDialed = false;
            done(CallDial.NO_DIALER);
        }
    }

    private boolean openDialer(String tel) {
        try {
            startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse(tel)));
            done(null);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }
}
