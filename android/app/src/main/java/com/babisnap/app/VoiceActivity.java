package com.babisnap.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
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
import android.widget.TextView;

import com.google.android.gms.auth.GoogleAuthUtil;

import org.json.JSONObject;

import java.util.ArrayList;

/** Widget/launcher target: speech (he-IL) -> parse on device -> (optional confirm) -> Google Calendar. */
public class VoiceActivity extends Activity {
    static final int REQ_SPEECH = 1, REQ_AUTH = 2;
    private JSONObject parsed; private String spoken; private boolean retried;

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
        if (req != REQ_MIC) return;
        if (g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED) listen(); else systemDialog();
    }

    private Intent speechIntent() {
        return new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "he-IL")
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "he-IL")
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
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
        spoken = text;
        try { parsed = Core.parse(this, spoken); } catch (Exception e) { done("שגיאת פענוח: " + e.getMessage()); return; }
        Classifier.Result c = Classifier.classify(spoken, !parsed.optBoolean("timeDefaulted", true));
        if (c.kind != Classifier.Kind.MEETING) { showSaved(c); return; }
        showMeeting(); // Google Calendar only, written after the 3s undo window
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        if (req == REQ_SPEECH) {
            ArrayList<String> r = data == null ? null : data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (res != RESULT_OK || r == null || r.isEmpty()) { done(null); return; }
            handleSpoken(r.get(0));
        } else if (req == REQ_AUTH) {
            if (res != RESULT_OK) { done("לא אושרה גישה ליומן"); return; }
            try { save(Core.tokenFromResult(this, data)); } catch (Exception e) { done("שגיאת התחברות: " + e.getMessage()); }
        }
    }

    private void authorize() { Core.requestAuth(this, REQ_AUTH, this::save, err -> done("שגיאת התחברות: " + err)); }

    private void save(String token) {
        new Thread(() -> {
            String m;
            try {
                int code = Core.insertEvent(token, parsed, spoken);
                if (code == 401 && !retried) {
                    retried = true; GoogleAuthUtil.clearToken(this, token);
                    runOnUiThread(this::authorize); return;
                }
                m = code / 100 == 2 ? null : "שגיאה מיומן גוגל (" + code + ")"; // success card already shown
            } catch (Exception e) { m = "אין חיבור לאינטרנט? " + e.getMessage(); }
            final String fm = m; runOnUiThread(() -> done(fm));
        }).start();
    }

    // ---- Tasks & notes -> BabiTk "notebook" board, sent after a 3s undo window ----
    private static final long UNDO_MS = 3000;
    private final android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());
    private String pendingId, pendingBody; private boolean committed, pendingMeeting;

    private void showMeeting() {
        JSONObject p = parsed;
        setContentView(R.layout.card_saved);
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        ((TextView) findViewById(R.id.check)).setText("📅");
        ((TextView) findViewById(R.id.headline)).setText("נרשם ביומן גוגל · פגישה");
        TextView chip = findViewById(R.id.chip); chip.setText("פגישה");
        chip.setBackgroundResource(R.drawable.chip_meeting); chip.setTextColor(0xFFFFFFFF);
        StringBuilder sum = new StringBuilder(p.optString("title"));
        sum.append("\n📅 ").append(p.optInt("d")).append("/").append(p.optInt("m")).append("/").append(p.optInt("y"))
            .append(String.format(java.util.Locale.US, " · %02d:%02d", p.optInt("hour"), p.optInt("minute")));
        if (p.optBoolean("timeDefaulted")) sum.append(" (לא נאמרה שעה)");
        String loc = p.optString("location", ""); if (!loc.isEmpty()) sum.append("\n📍 ").append(loc);
        ((TextView) findViewById(R.id.summary)).setText(sum);
        android.widget.ProgressBar bar = findViewById(R.id.countdown);
        android.animation.ObjectAnimator.ofInt(bar, "progress", (int) UNDO_MS, 0).setDuration(UNDO_MS).start();
        pendingMeeting = true;
        findViewById(R.id.undo).setOnClickListener(v -> { ui.removeCallbacks(commit); pendingMeeting = false; done("בוטל – לא נרשם ביומן"); });
        ui.postDelayed(commit, UNDO_MS);
    }
    private final Runnable commit = this::commitPending;

    private void showSaved(Classifier.Result c) {
        JSONObject p = parsed;
        try { if (!c.stripped.isEmpty() && !c.stripped.equals(Classifier.normalize(spoken))) p = Core.parse(this, c.stripped); }
        catch (Exception ignored) {}
        boolean noDate = p.optBoolean("dateDefaulted", true) && p.optBoolean("timeDefaulted", true);
        String date = noDate ? null : String.format(java.util.Locale.US, "%04d-%02d-%02d", p.optInt("y"), p.optInt("m"), p.optInt("d"));
        String time = p.optBoolean("timeDefaulted", true) ? null : String.format(java.util.Locale.US, "%02d:%02d", p.optInt("hour"), p.optInt("minute"));
        String title = p.optString("title", "").trim(); if (title.isEmpty()) title = spoken;
        pendingId = java.util.UUID.randomUUID().toString();
        pendingBody = BabiTkJson.build(title, date, time, "נוצר בהכתבה: " + spoken, p.optString("location", ""), c.kind.api(), pendingId);

        setContentView(R.layout.card_saved);
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        boolean hasTok = BabiTk.hasToken(this);
        ((TextView) findViewById(R.id.headline)).setText(hasTok ? "נכנס למחברת ב-BabiTk · " + c.kind.he()
            : "נשמר במכשיר · " + c.kind.he() + "\nBabiTk לא מחובר – פתחו את BabiSnap ← חיבור BabiTk");
        TextView chip = findViewById(R.id.chip); chip.setText(c.kind.he());
        if (c.kind == Classifier.Kind.NOTE) { chip.setBackgroundResource(R.drawable.chip_note); chip.setTextColor(0xFF2A1B7A); }
        StringBuilder sum = new StringBuilder(title);
        if (date != null) sum.append("\n📅 ").append(p.optInt("d")).append("/").append(p.optInt("m")).append("/").append(p.optInt("y"))
            .append(time != null ? " · " + time : "");
        else sum.append("\nללא תאריך");
        String loc = p.optString("location", ""); if (!loc.isEmpty()) sum.append("\n📍 ").append(loc);
        ((TextView) findViewById(R.id.summary)).setText(sum);
        android.widget.ProgressBar bar = findViewById(R.id.countdown);
        android.animation.ObjectAnimator.ofInt(bar, "progress", (int) UNDO_MS, 0).setDuration(UNDO_MS).start();
        findViewById(R.id.undo).setOnClickListener(v -> { ui.removeCallbacks(commit); pendingBody = null; done("בוטל – לא נשלח ל-BabiTk"); });
        ui.postDelayed(commit, UNDO_MS);
    }

    /** Undo window closed (or the card was left): queue locally and let WorkManager send with the same client_id. */
    private void commitPending() {
        if (committed) return;
        if (pendingMeeting) { committed = true; ui.removeCallbacks(commit); findViewById(R.id.undo).setVisibility(View.INVISIBLE); authorize(); return; }
        if (pendingBody == null) return;
        committed = true; ui.removeCallbacks(commit);
        BabiTk.enqueue(getApplicationContext(), pendingId, pendingBody);
        done(BabiTk.hasToken(this) ? null : "BabiTk לא מחובר: הפריט נשמר במכשיר ויישלח אחרי חיבור (הגדרות ← חיבור BabiTk)");
    }

    @Override protected void onStop() { if ((pendingBody != null || pendingMeeting) && !committed) commitPending(); super.onStop(); }

    private void done(String msg) {
        if (msg != null) Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
        finish();
    }
}
