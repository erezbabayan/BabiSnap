package com.babisnap.grouptest;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Private WhatsApp send: Hebrew command, 8 second card, then one guarded Send press. */
public class AutoActivity extends Activity {
    private static final int REQ_AUDIO = 21;
    private static final int REQ_SPEECH = 22;
    private static final String MANUAL = "לא נשלח, לחץ שלח ידנית";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Store store;
    private EditText transcript;
    private View card;
    private Button record;
    private SpeechRecognizer recognizer;
    private boolean listening;
    private boolean counting;
    private int secondsLeft;
    private Store.ContactSel pending;
    private String pendingBody = "";

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!counting || pending == null) return;
            bindCard(secondsLeft);
            if (secondsLeft <= 0) {
                counting = false;
                card.setVisibility(View.GONE);
                launch(pending, pendingBody);
                return;
            }
            secondsLeft--;
            handler.postDelayed(this, 1000);
        }
    };

    private final Runnable giveUp = () -> {
        Arm.Payload payload = Arm.get();
        if (payload == null || payload.consumed) return;
        payload.consumed = true;
        if ("NONE".equals(store.code("autoResult", "NONE"))) store.putCode("autoResult", "TIMEOUT");
        Toast.makeText(this, MANUAL, Toast.LENGTH_LONG).show();
        refresh();
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auto);
        store = Store.get(this);
        transcript = findViewById(R.id.transcript);
        card = findViewById(R.id.countdown_card);
        record = findViewById(R.id.record);
        View scroll = card.findViewById(R.id.card_scroll);
        scroll.setOnTouchListener((v, event) -> {
            v.getParent().requestDisallowInterceptTouchEvent(true);
            return false;
        });
        card.findViewById(R.id.card_cancel).setOnClickListener(v -> cancelCard());
        record.setOnClickListener(v -> toggleListen());
        findViewById(R.id.send_typed).setOnClickListener(v -> begin(transcript.getText().toString()));
        findViewById(R.id.open_recipients).setOnClickListener(v ->
                startActivity(new Intent(this, RecipientsActivity.class)));
        findViewById(R.id.open_settings).setOnClickListener(v ->
                startActivity(new Intent(this, AutoSettingsActivity.class)));
        findViewById(R.id.copy_report).setOnClickListener(v -> copyReport());
    }

    @Override
    protected void onResume() {
        super.onResume();
        store = Store.get(this);
        UiHook.set(() -> runOnUiThread(this::refresh));
        refresh();
    }

    @Override
    protected void onPause() {
        stopListen();
        UiHook.set(null);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (recognizer != null) {
            recognizer.destroy();
            recognizer = null;
        }
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == REQ_AUDIO && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            toggleListen();
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != REQ_SPEECH) return;
        record.setText("הקלט");
        if (result != RESULT_OK || data == null) {
            ProbeLog.code("SPEECH_CANCELLED");
            return;
        }
        ArrayList<String> lines = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (lines == null || lines.isEmpty()) {
            ProbeLog.code("SPEECH_EMPTY");
            return;
        }
        onHeard(lines.get(0));
    }

    private void toggleListen() {
        if (listening) {
            stopListen();
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
            return;
        }
        if (SpeechRecognizer.isRecognitionAvailable(this)) startRecognizer();
        else startSpeechIntent();
    }

    private void startRecognizer() {
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(new Listener());
        }
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "he-IL");
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        listening = true;
        record.setText("מקשיב…");
        try {
            recognizer.startListening(intent);
            ProbeLog.code("SPEECH_START");
        } catch (RuntimeException e) {
            listening = false;
            record.setText("הקלט");
            ProbeLog.code("SPEECH_FAIL");
            startSpeechIntent();
        }
    }

    @SuppressWarnings("deprecation")
    private void startSpeechIntent() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "he-IL");
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "הודעה לוואטסאפ");
        try {
            startActivityForResult(intent, REQ_SPEECH);
            record.setText("מקשיב…");
            ProbeLog.code("SPEECH_INTENT");
        } catch (ActivityNotFoundException e) {
            ProbeLog.code("SPEECH_MISSING");
            Toast.makeText(this, "זיהוי דיבור לא זמין. הקלידו את הפקודה.", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopListen() {
        listening = false;
        if (record != null) record.setText("הקלט");
        if (recognizer != null) {
            try {
                recognizer.stopListening();
            } catch (RuntimeException e) {
                ProbeLog.code("SPEECH_STOP");
            }
        }
    }

    private void onHeard(String text) {
        if (text == null) return;
        transcript.setText(text);
        if (!counting) begin(text);
    }

    private void begin(String text) {
        if (counting) return;
        List<String> labels = new ArrayList<>();
        for (Store.ContactSel contact : store.contacts()) {
            if (!contact.name.isEmpty()) labels.add(contact.name);
            if (!contact.alias.isEmpty()) labels.add(contact.alias);
        }
        CommandParse.Result parsed = CommandParse.parse(text, labels);
        store.putCode("command", parsed.code);
        if (!"OK".equals(parsed.code)) {
            toast(commandMessage(parsed.code));
            ProbeLog.code("COMMAND_" + parsed.code);
            refresh();
            return;
        }
        List<Store.ContactSel> hits = new ArrayList<>();
        String key = CommandParse.normalize(parsed.label);
        for (Store.ContactSel contact : store.contacts()) {
            if (key.equals(CommandParse.normalize(contact.name)) || key.equals(CommandParse.normalize(contact.alias))) {
                hits.add(contact);
            }
        }
        if (hits.size() != 1) {
            store.putCode("command", "AMBIGUOUS");
            toast("כמה נמענים תואמים. לא נשלח.");
            ProbeLog.code("COMMAND_AMBIGUOUS");
            refresh();
            return;
        }
        pending = hits.get(0);
        pendingBody = parsed.body;
        store.putCode("sendKind", "CONTACT");
        store.putCode("sendPath", "NONE");
        store.putCode("flow", "NONE");
        store.putCode("autoResult", "NONE");
        store.setObservation("UNKNOWN", "UNKNOWN", "NONE", null);
        counting = true;
        secondsLeft = 8;
        card.setVisibility(View.VISIBLE);
        ProgressBar bar = card.findViewById(R.id.card_progress);
        bar.setVisibility(View.VISIBLE);
        bar.setMax(8000);
        ObjectAnimator.ofInt(bar, "progress", 8000, 0).setDuration(8000).start();
        ((ScrollView) findViewById(R.id.auto_scroll)).smoothScrollTo(0, 0);
        handler.removeCallbacks(tick);
        handler.post(tick);
        ProbeLog.code("COUNTDOWN_START");
    }

    private void cancelCard() {
        if (!counting) {
            card.setVisibility(View.GONE);
            return;
        }
        counting = false;
        handler.removeCallbacks(tick);
        card.setVisibility(View.GONE);
        store.putCode("flow", "CANCELLED");
        ProbeLog.code("COUNTDOWN_CANCEL");
        refresh();
    }

    private void bindCard(int seconds) {
        if (pending == null) return;
        SendCard.show(card,
                ProbeLogic.cardHeadline("contact", pending.name, pending.alias, seconds),
                seconds <= 0 ? "" : Integer.toString(seconds),
                ProbeLogic.checkLine(pending.name.isEmpty() ? pending.alias : pending.name),
                pendingBody,
                getString(R.string.cancel));
    }

    private void launch(Store.ContactSel contact, String body) {
        String pkg = Access.preferredPkg(this);
        if (pkg == null) {
            store.putCode("autoResult", "NO_ACTIVITY");
            toast("WhatsApp לא מותקן");
            ProbeLog.code("SEND_NO_PKG");
            refresh();
            return;
        }
        String jid = ProbeLogic.contactJid(contact.e164);
        boolean opened = false;
        if (jid != null) {
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.setPackage(pkg);
            send.putExtra(Intent.EXTRA_TEXT, body);
            send.putExtra("jid", jid);
            send.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            opened = startSend(send, "ACTION_JID");
        }
        if (!opened && contact.e164 != null && !contact.e164.isEmpty()) {
            Uri uri = Uri.parse("https://wa.me/" + contact.e164 + "?text=" + Uri.encode(body));
            Intent view = new Intent(Intent.ACTION_VIEW, uri).setPackage(pkg);
            view.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            opened = startSend(view, "WAME");
            if (!opened) {
                view.setPackage(null);
                opened = startSend(view, "WAME");
            }
        }
        if (!opened) {
            store.putCode("flow", "NONE");
            store.putCode("autoResult", "NO_ACTIVITY");
            toast("WhatsApp לא נפתח");
            refresh();
            return;
        }
        store.putCode("flow", "OPENED");
        boolean armed = contact.checked && SpecialAccess.accessibility(this);
        if (armed) {
            String title = contact.name.isEmpty() ? contact.alias : contact.name;
            Arm.Payload payload = new Arm.Payload(title, "contact", contact.alias, contact.name, body, true);
            payload.deadlineMs = SystemClock.elapsedRealtime() + 10000L;
            Arm.arm(payload);
            handler.removeCallbacks(giveUp);
            handler.postDelayed(giveUp, 10000L);
            ProbeLog.code("SEND_ARMED");
        } else {
            store.putCode("autoResult", contact.checked ? "A11Y_OFF" : "NOT_CHECKED");
            toast(MANUAL);
            ProbeLog.code(contact.checked ? "A11Y_OFF" : "NOT_CHECKED");
        }
        refresh();
    }

    private boolean startSend(Intent intent, String path) {
        try {
            startActivity(intent);
            store.putCode("sendPath", path);
            ProbeLog.code("SEND_" + path);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            ProbeLog.code("SEND_FAIL");
            return false;
        }
    }

    private void copyReport() {
        ProbeLogic.Report report = store.report(
                Build.VERSION.SDK_INT,
                Access.waVersion(this),
                Access.installed(this, Access.WA),
                Access.installed(this, Access.W4B),
                false,
                false,
                SpecialAccess.accessibility(this));
        report.variant = "AUTO";
        report.command = store.code("command", "NONE");
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("k1", report.format()));
        ProbeLog.code("REPORT_COPIED");
        toast("הדוח הועתק");
    }

    private void refresh() {
        boolean a11y = SpecialAccess.accessibility(this);
        String result = store.code("autoResult", "NONE");
        String line = (a11y ? "✅" : "❌") + " נגישות " + (a11y ? "פעילה" : "כבויה");
        line += "\nפקודה: " + store.code("command", "NONE");
        line += "\n" + ("OPENED".equals(store.code("flow", "NONE")) ? "✅ הצ׳אט נפתח" : "❌ הצ׳אט עוד לא נפתח");
        line += "\nלחיצה: " + result;
        if (!"NONE".equals(result) && !"PRESSED".equals(result)) {
            line += "\n" + MANUAL;
            line += "\n" + ProbeLogic.autoReason(result, "contact");
        } else if ("PRESSED".equals(result)) {
            line += "\nנלחץ שלח";
        }
        ((android.widget.TextView) findViewById(R.id.auto_status)).setText(line);
        findViewById(R.id.send_typed).setEnabled(!counting);
    }

    private static String commandMessage(String code) {
        if ("EMPTY_BODY".equals(code)) return "נשמע נמען, אבל אין טקסט לשליחה.";
        if ("AMBIGUOUS".equals(code)) return "כמה נמענים תואמים. לא נשלח.";
        return "לא זוהתה פקודה. נסו «הודעה לתמרי בוואטסאפ» ואז את הטקסט.";
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private final class Listener implements RecognitionListener {
        @Override public void onReadyForSpeech(Bundle params) {}
        @Override public void onBeginningOfSpeech() {}
        @Override public void onRmsChanged(float rmsdB) {}
        @Override public void onBufferReceived(byte[] buffer) {}
        @Override public void onEndOfSpeech() {
            listening = false;
            record.setText("הקלט");
        }
        @Override public void onError(int error) {
            listening = false;
            record.setText("הקלט");
            ProbeLog.code("SPEECH_FAIL");
            if (!counting) toast("לא שמעתי. נסו שוב או הקלידו.");
        }
        @Override public void onResults(Bundle results) {
            listening = false;
            record.setText("הקלט");
            ArrayList<String> lines = results == null ? null
                    : results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (lines == null || lines.isEmpty()) {
                ProbeLog.code("SPEECH_EMPTY");
                return;
            }
            onHeard(lines.get(0));
        }
        @Override public void onPartialResults(Bundle partial) {
            if (counting || partial == null) return;
            ArrayList<String> lines = partial.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (lines != null && !lines.isEmpty()) transcript.setText(lines.get(0));
        }
        @Override public void onEvent(int eventType, Bundle params) {}
    }
}
