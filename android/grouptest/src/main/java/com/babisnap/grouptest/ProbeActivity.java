package com.babisnap.grouptest;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Hebrew RTL probe. One screen for the six checks, plus the recipients screen. */
public class ProbeActivity extends Activity {
    private static final int REQ_SHORTCUT = 41;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Store store;
    private EditText transcript;
    private View card;
    private Switch autoSwitch;
    private boolean manualRunning;
    private int secondsLeft;
    private Store.Target pending;
    private String pendingText = "";

    private final Runnable manualTick = new Runnable() {
        @Override
        public void run() {
            if (!manualRunning || pending == null) return;
            bindManual(secondsLeft);
            if (secondsLeft <= 0) {
                manualRunning = false;
                card.setVisibility(View.GONE);
                boolean opened = launch(pending, pendingText);
                store.putCode("flow", opened ? "OPENED" : "NONE");
                if (!opened) toast("WhatsApp לא נפתח");
                refresh();
                return;
            }
            secondsLeft--;
            handler.postDelayed(this, 1000);
        }
    };

    private final Runnable giveUp = () -> {
        Arm.Payload payload = Arm.get();
        if (payload == null || payload.counting || payload.consumed || payload.cancelled) return;
        payload.consumed = true;
        store.putCode("autoResult", "NOT_FOREGROUND");
        ProbeLog.code("AUTO_NOT_FOREGROUND");
        refresh();
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_probe);
        store = Store.get(this);
        transcript = findViewById(R.id.transcript);
        card = findViewById(R.id.countdown_card);
        autoSwitch = findViewById(R.id.auto_switch);
        String draft = store.draft();
        if (draft != null && !draft.isEmpty()) transcript.setText(draft);
        View scroll = card.findViewById(R.id.card_scroll);
        if (scroll != null) {
            scroll.setOnTouchListener((v, event) -> {
                v.getParent().requestDisallowInterceptTouchEvent(true);
                return false;
            });
        }
        card.findViewById(R.id.card_cancel).setOnClickListener(v -> cancelManual());
        findViewById(R.id.add_group).setOnClickListener(v -> addGroup());
        findViewById(R.id.notif_settings).setOnClickListener(v -> open(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        findViewById(R.id.open_recipients).setOnClickListener(v -> startActivity(new Intent(this, RecipientsActivity.class)));
        findViewById(R.id.send_test).setOnClickListener(v -> onSend());
        findViewById(R.id.mark_ok).setOnClickListener(v -> mark("OK"));
        findViewById(R.id.mark_bad).setOnClickListener(v -> mark("FAIL"));
        findViewById(R.id.a11y_settings).setOnClickListener(v -> open(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        findViewById(R.id.copy_report).setOnClickListener(v -> copyReport());
        autoSwitch.setOnCheckedChangeListener((button, on) -> {
            store.putBool("autoPress", on);
            ProbeLog.code(on ? "AUTO_ON" : "AUTO_OFF");
        });
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
        store.setDraft(transcript.getText().toString());
        UiHook.set(null);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != REQ_SHORTCUT) return;
        if (result != RESULT_OK || data == null) {
            store.putCode("shortcut", "CANCELLED");
            ProbeLog.code("SHORTCUT_CANCELLED");
            refresh();
            return;
        }
        try {
            String name = IntentDump.label(data);
            ProbeLogic.Found found = ProbeLogic.findJid(IntentDump.strings(data));
            if (found != null && found.group) {
                store.upsertGroup(found.jid, name == null ? "" : name, "shortcut");
                store.putCode("shortcut", "JID_OK");
                ProbeLog.code("SHORTCUT_JID_OK");
            } else if (found != null) {
                store.putCode("shortcut", "NOT_GROUP");
                ProbeLog.code("SHORTCUT_NOT_GROUP");
            } else {
                store.putCode("shortcut", "NO_JID");
                ProbeLog.code("SHORTCUT_NO_JID");
            }
        } catch (RuntimeException e) {
            store.putCode("shortcut", "ERROR");
            ProbeLog.code("SHORTCUT_ERROR");
        }
        refresh();
    }

    private void onSend() {
        if (manualRunning) return;
        String text = transcript.getText().toString();
        if (text.trim().isEmpty()) {
            toast("אין טקסט");
            ProbeLog.code("SEND_EMPTY");
            return;
        }
        Store.Target target = store.target();
        if (target == null) {
            toast("בחרו יעד במסך הנמענים");
            ProbeLog.code("SEND_NO_TARGET");
            return;
        }
        store.setDraft(text);
        store.putCode("sendKind", "group".equals(target.kind) ? "GROUP" : "CONTACT");
        store.putCode("sendMark", "NONE");
        store.putCode("sendPath", "NONE");
        store.putCode("flow", "NONE");
        store.setObservation("UNKNOWN", "UNKNOWN", "NONE", "");
        boolean auto = store.bool("autoPress");
        boolean a11y = Access.accessibility(this);
        if (auto && target.checked && a11y) {
            Arm.arm(new Arm.Payload(target.name, target.kind, target.alias, target.name, text, true));
            store.putCode("autoResult", "NONE");
            boolean opened = launch(target, text);
            if (!opened) {
                Arm.Payload payload = Arm.get();
                if (payload != null) payload.consumed = true;
                store.putCode("autoResult", "NO_ACTIVITY");
                toast("WhatsApp לא נפתח");
                refresh();
                return;
            }
            store.putCode("flow", "OPENED");
            handler.removeCallbacks(giveUp);
            handler.postDelayed(giveUp, 25000);
            toast("הכרטיס יופיע מעל WhatsApp");
            refresh();
            return;
        }
        if (auto && !a11y) store.putCode("autoResult", "A11Y_OFF");
        else if (auto && !target.checked) store.putCode("autoResult", "NOT_CHECKED");
        else store.putCode("autoResult", "NONE");
        Arm.Payload idle = new Arm.Payload(target.name, target.kind, target.alias, target.name, text, false);
        idle.consumed = true;
        Arm.arm(idle);
        pending = target;
        pendingText = text;
        manualRunning = true;
        secondsLeft = 8;
        card.setVisibility(View.VISIBLE);
        ScrollView scroller = findViewById(R.id.probe_scroll);
        scroller.smoothScrollTo(0, 0);
        handler.removeCallbacks(manualTick);
        handler.post(manualTick);
        refresh();
    }

    private void cancelManual() {
        if (!manualRunning) {
            card.setVisibility(View.GONE);
            return;
        }
        manualRunning = false;
        handler.removeCallbacks(manualTick);
        card.setVisibility(View.GONE);
        store.putCode("flow", "CANCELLED");
        ProbeLog.code("COUNTDOWN_CANCEL");
        refresh();
    }

    private void bindManual(int seconds) {
        if (pending == null) return;
        SendCard.show(card,
                ProbeLogic.cardHeadline(pending.kind, pending.name, pending.alias, seconds),
                seconds <= 0 ? "" : Integer.toString(seconds),
                ProbeLogic.checkLine(pending.name),
                pendingText,
                getString(R.string.cancel));
    }

    private boolean launch(Store.Target target, String text) {
        String pkg = Access.preferredPkg(this);
        if (pkg == null) {
            ProbeLog.code("SEND_NO_PKG");
            return false;
        }
        if ("group".equals(target.kind)) {
            if (!ProbeLogic.isGroupJid(target.jid)) {
                ProbeLog.code("SEND_BAD_JID");
                toast("אין מזהה קבוצה");
                return false;
            }
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.setPackage(pkg);
            send.putExtra(Intent.EXTRA_TEXT, text);
            send.putExtra("jid", target.jid);
            send.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return startSend(send, "ACTION_JID");
        }
        String jid = target.jid == null || target.jid.isEmpty() ? ProbeLogic.contactJid(target.e164) : target.jid;
        if (jid != null) {
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.setPackage(pkg);
            send.putExtra(Intent.EXTRA_TEXT, text);
            send.putExtra("jid", jid);
            send.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (startSend(send, "ACTION_JID")) return true;
        }
        if (target.e164 == null || target.e164.isEmpty()) return false;
        Uri uri = Uri.parse("https://wa.me/" + target.e164 + "?text=" + Uri.encode(text));
        Intent view = new Intent(Intent.ACTION_VIEW, uri).setPackage(pkg);
        view.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (startSend(view, "WAME")) return true;
        view.setPackage(null);
        return startSend(view, "WAME");
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

    private void addGroup() {
        String pkg = Access.preferredPkg(this);
        if (pkg == null) {
            store.putCode("shortcut", "NO_ACTIVITY");
            store.putCode("shortcutRoute", "NONE");
            ProbeLog.code("SHORTCUT_NO_PKG");
            refresh();
            toast("WhatsApp לא מותקן");
            return;
        }
        PackageManager pm = getPackageManager();
        Intent base = new Intent(Intent.ACTION_CREATE_SHORTCUT).setPackage(pkg);
        List<ResolveInfo> found = query(pm, base);
        if (!found.isEmpty()) {
            ResolveInfo best = pick(found);
            Intent launch = new Intent(base);
            launch.setComponent(new ComponentName(best.activityInfo.packageName, best.activityInfo.name));
            if (launchForResult(launch, "RESOLVED", best.activityInfo.name)) return;
        }
        if (launchForResult(base, "IMPLICIT", pkg)) return;
        for (String className : ProbeLogic.SHORTCUT_ACTIVITIES) {
            Intent explicit = new Intent(Intent.ACTION_CREATE_SHORTCUT);
            explicit.setComponent(new ComponentName(pkg, className));
            if (launchForResult(explicit, "EXPLICIT", className)) return;
        }
        store.putCode("shortcut", "NO_ACTIVITY");
        store.putCode("shortcutRoute", "NONE");
        ProbeLog.code("SHORTCUT_NO_ACTIVITY");
        refresh();
        toast("WhatsApp לא פתח מסך קיצור");
    }

    private List<ResolveInfo> query(PackageManager pm, Intent base) {
        List<ResolveInfo> list = pm.queryIntentActivities(base, PackageManager.MATCH_DEFAULT_ONLY);
        if (list == null || list.isEmpty()) list = pm.queryIntentActivities(base, PackageManager.MATCH_ALL);
        return list == null ? new ArrayList<>() : list;
    }

    private static ResolveInfo pick(List<ResolveInfo> list) {
        for (ResolveInfo info : list) {
            if (info.activityInfo == null || info.activityInfo.name == null) continue;
            String name = info.activityInfo.name.toLowerCase(java.util.Locale.US);
            if (name.contains("shortcut") || name.contains("picker") || name.contains("contact")) return info;
        }
        return list.get(0);
    }

    @SuppressWarnings("deprecation")
    private boolean launchForResult(Intent intent, String route, String activity) {
        try {
            startActivityForResult(intent, REQ_SHORTCUT);
            store.putCode("shortcutRoute", route);
            store.setShortcutActivity(activity == null ? "" : activity);
            ProbeLog.code("SHORTCUT_LAUNCHED");
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            ProbeLog.code("SHORTCUT_LAUNCH_FAIL");
            return false;
        }
    }

    private void mark(String code) {
        store.putCode("sendMark", code);
        ProbeLog.code("OK".equals(code) ? "SEND_MARK_OK" : "SEND_MARK_FAIL");
        refresh();
    }

    private void copyReport() {
        ProbeLogic.Report report = store.report(
                Build.VERSION.SDK_INT,
                Access.waVersion(this),
                Access.installed(this, Access.WA),
                Access.installed(this, Access.W4B),
                Access.notifications(this),
                checkSelfPermission(android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED,
                Access.accessibility(this));
        String text = report.format();
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("k0", text));
        ProbeLog.code("REPORT_COPIED");
        toast("הדוח הועתק");
    }

    private void open(String action) {
        try {
            startActivity(new Intent(action));
        } catch (ActivityNotFoundException e) {
            ProbeLog.code("SETTINGS_MISSING");
            toast("מסך ההגדרות לא נמצא");
        }
    }

    private void refresh() {
        autoSwitch.setOnCheckedChangeListener(null);
        autoSwitch.setChecked(store.bool("autoPress"));
        autoSwitch.setOnCheckedChangeListener((button, on) -> {
            store.putBool("autoPress", on);
            ProbeLog.code(on ? "AUTO_ON" : "AUTO_OFF");
        });
        Arm.Payload payload = Arm.get();
        boolean busy = manualRunning || (payload != null && payload.counting && !payload.consumed);
        ((Button) findViewById(R.id.send_test)).setEnabled(!busy);
        ((TextView) findViewById(R.id.header_status)).setText(header());
        ((TextView) findViewById(R.id.step1_status)).setText(step1());
        ((TextView) findViewById(R.id.step2_status)).setText(step2());
        ((TextView) findViewById(R.id.step3_status)).setText(step3());
        ((TextView) findViewById(R.id.step4_status)).setText(step4());
        ((TextView) findViewById(R.id.step5_status)).setText(step5());
    }

    private String header() {
        String version = ProbeLogic.versionCode(Access.waVersion(this));
        return (Access.installed(this, Access.WA) ? "✅" : "❌") + " WhatsApp"
                + "\n" + (Access.installed(this, Access.W4B) ? "✅" : "❌") + " WhatsApp Business"
                + "\nגרסה: " + version;
    }

    private String step1() {
        String code = store.code("shortcut", "NOT_TRIED");
        String line;
        if ("JID_OK".equals(code)) line = "✅ התקבל מזהה קבוצה (@g.us) והקבוצה נשמרה";
        else if ("NO_JID".equals(code)) line = "❌ הבורר חזר בלי מזהה קבוצה";
        else if ("NOT_GROUP".equals(code)) line = "❌ התקבל מזהה שאינו קבוצה";
        else if ("CANCELLED".equals(code)) line = "❌ הבחירה בוטלה";
        else if ("NO_ACTIVITY".equals(code)) line = "❌ WhatsApp לא פתח מסך קיצור";
        else if ("ERROR".equals(code)) line = "❌ הפענוח נכשל";
        else line = "❌ עדיין לא נוסה";
        String route = store.code("shortcutRoute", "NONE");
        if (!"NONE".equals(route)) line += "\nנתיב: " + route;
        String activity = ProbeLogic.activityCode(store.shortcutActivity());
        if (!"NONE".equals(activity)) line += "\nפעילות: " + activity;
        if ("JID_OK".equals(code)) {
            String added = store.lastAddedName();
            if (!added.isEmpty()) line += "\nנוספה לרשימה: " + added;
        }
        return line;
    }

    private String step2() {
        boolean on = Access.notifications(this);
        String line = (on ? "✅" : "❌") + " גישה להתראות " + (on ? "פעילה" : "כבויה");
        line += "\n" + (store.bool("directReply") ? "✅" : "❌") + " מענה ישיר (RemoteInput) "
                + (store.bool("directReply") ? "נצפה" : "עדיין לא נצפה");
        line += "\nקבוצות שנשמרו: " + store.groupCount();
        if (store.bool("sawGroupNoJid")) line += "\n❌ נראתה התראת קבוצה בלי מזהה";
        if (store.bool("sawContactJid")) line += "\n✅ נראה מזהה של איש קשר בהתראה";
        return line;
    }

    private String step3() {
        boolean perm = checkSelfPermission(android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
        int selected = store.selectedCount();
        return (perm ? "✅" : "❌") + " הרשאת אנשי קשר"
                + "\n" + (selected > 0 ? "✅" : "❌") + " נבחרו " + selected;
    }

    private String step4() {
        Store.Target target = store.target();
        String line = target == null
                ? "❌ אין יעד"
                : "✅ יעד (" + ("group".equals(target.kind) ? "קבוצה" : "איש קשר") + "): " + display(target);
        String flow = store.code("flow", "NONE");
        if ("OPENED".equals(flow)) line += "\n✅ נפתח WhatsApp";
        else if ("CANCELLED".equals(flow)) line += "\n❌ בוטל לפני השליחה";
        else line += "\n❌ עדיין לא נשלחה בדיקה";
        line += "\nנתיב: " + store.code("sendPath", "NONE");
        String mark = store.code("sendMark", "NONE");
        if ("OK".equals(mark)) line += "\n✅ סימנתם: הצ׳אט נפתח עם הטקסט";
        else if ("FAIL".equals(mark)) line += "\n❌ סימנתם: לא נפתח ישר";
        else line += "\n❌ עדיין לא סימנתם אם הצ׳אט נפתח";
        return line;
    }

    private String step5() {
        boolean on = Access.accessibility(this);
        String line = (on ? "✅" : "❌") + " שירות נגישות " + (on ? "פעיל" : "כבוי");
        line += "\n" + mark(store.code("titleMatch", "UNKNOWN"), "כותרת תואמת בדיוק");
        line += "\n" + mark(store.code("sendButton", "UNKNOWN"), "כפתור שלח");
        line += "\nכיתוב כפתור: " + store.code("sendLabel", "NONE");
        String result = store.code("autoResult", "NONE");
        line += "\nתוצאת לחיצה: " + result;
        if (!"NONE".equals(result)) {
            Store.Target target = store.target();
            String kind = target == null ? "" : target.kind;
            line += "\n" + ProbeLogic.autoReason(result, kind);
        }
        Store.Target target = store.target();
        if (target != null && !target.name.isEmpty()) line += "\nשם שמור (לא בדוח): " + target.name;
        String observed = store.observedTitle();
        if (!observed.isEmpty()) line += "\nכותרת על המסך (לא בדוח): " + observed;
        return line;
    }

    private static String display(Store.Target target) {
        String alias = ProbeLogic.normalizeTitle(target.alias);
        if (!alias.isEmpty()) return alias;
        String name = ProbeLogic.normalizeTitle(target.name);
        return name.isEmpty() ? ("group".equals(target.kind) ? "קבוצה" : "איש קשר") : name;
    }

    private static String mark(String code, String label) {
        if ("YES".equals(code)) return "✅ " + label;
        if ("NO".equals(code)) return "❌ " + label;
        return "❌ " + label + " — לא נבדק";
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
