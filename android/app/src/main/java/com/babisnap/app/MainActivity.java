package com.babisnap.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import com.google.android.gms.auth.GoogleAuthUtil;

import org.json.JSONObject;

/** 2.4 Settings (הגדרות), Hebrew RTL: connections, permissions, preferences, privacy, delete-all.
 *  A green ✓ is shown only when something is really connected / granted; otherwise one action button. */
public class MainActivity extends Activity {
    static final int REQ_AUTH = 10, REQ_MIC = 11, REQ_CONTACTS = 12, REQ_CALENDAR = 13;
    private static final int PURPLE = 0xFF2A1B7A, PURPLE_DARK = 0xFF1E1458, BG = 0xFFF6F5FA, GREEN = 0xFF1A8A4A,
        MUTED = 0xFF7A7888, SECTION = 0xFF6A5FA8, RED = 0xFFB3261E, DIVIDER = 0xFFEEECF5, ICON_BG = 0xFFEFEDF8, ICON_BG_Y = 0xFFFFF1C4;
    private LinearLayout content;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        SnapHeartbeat.onAppOpen(this); // registry: at most once a day, or when the version changed
        TagSync.maybeFetch(this, false); // background, throttled to 12 h
        UpdateCheck.maybeCheck(this); // GitHub latest release, throttled to 6 h; dialog only if newer
        setTitle("הגדרות");
        getWindow().setStatusBarColor(PURPLE_DARK);
        if (getActionBar() != null) {
            getActionBar().setBackgroundDrawable(new ColorDrawable(PURPLE));
            getActionBar().setDisplayShowHomeEnabled(true);
            Drawable logo = getDrawable(R.drawable.widget_logo);
            int in = dp(4);
            getActionBar().setIcon(new android.graphics.drawable.InsetDrawable(logo, in, in, dp(10), in));
        }
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL); content.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        content.setPadding(dp(14), dp(12), dp(14), dp(24));
        ScrollView sv = new ScrollView(this); sv.setBackgroundColor(BG); sv.addView(content); setContentView(sv);
    }

    @Override protected void onResume() { super.onResume(); render(); } // permissions may have changed in App info

    private void render() {
        content.removeAllViews();
        content.addView(recordButton());

        // ---- חיבורים ----
        section("חיבורים");
        LinearLayout conn = card();
        String email = Core.prefs(this).getString("email", null);
        if (email != null) row(conn, "📅", false, "Google יומן", ok("מחובר ✓", " · \u2066" + email + "\u2069"), link("התנתק", false, v -> signOut()));
        else row(conn, "📅", false, "Google יומן", plain("לשמירת פגישות"), pill("התחבר", v -> signIn()));
        renderDeviceCalendar(conn);
        boolean btk = BabiTk.hasToken(this);
        if (btk) row(conn, "📓", true, "BabiTk · מחברת", ok("מחובר ✓", " · משימות והערות"), link("נתק", false, v -> { BabiTk.disconnect(this);
            Toast.makeText(this, "נותק – הטוקן נמחק מהמכשיר", Toast.LENGTH_SHORT).show(); render(); }));
        else row(conn, "📓", true, "BabiTk · מחברת", plain(BabiTk.needsRepair(this) ? BabiTk.NOT_CONNECTED : "משימות והערות"), pill("חבר", v -> openBabiTk()));
        if (BabiTk.showDiagnostics(this)) {
            int pending = BabiTk.pending(this), failed = BabiTk.failed(this);
            String t = pending > 0 ? pending + " ממתינים לשליחה" : failed > 0 ? failed + " פריטים נדחו" : "בעיה בשליחה";
            String err = Core.prefs(this).getString("babitk_last_error", null);
            row(conn, "⚠️", true, t, plain(err == null || "אין".equals(err) ? "בדיקה ושליחה חוזרת" : err), link("פרטים", false, v -> openBabiTk()));
        }
        if (SnapClient.configured() && SnapRegistry.validEmail(email)) {
            TextView notice = new TextView(this);
            notice.setText(R.string.snap_registry_notice);
            notice.setTextSize(12.5f); notice.setTextColor(MUTED);
            notice.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            notice.setPadding(dp(14), dp(4), dp(14), dp(12));
            conn.addView(notice);
        }

        // ---- הרשאות ----
        section("הרשאות");
        LinearLayout perms = card();
        permRow(perms, "🎙", "מיקרופון", null, Manifest.permission.RECORD_AUDIO, REQ_MIC);
        permRow(perms, "👥", "אנשי קשר", "להזמנת משתתפים", Manifest.permission.READ_CONTACTS, REQ_CONTACTS);

        // ---- העדפות ----
        section("העדפות");
        LinearLayout prefs = card();
        TextView dur = link(SettingsLogic.durationLabel(Core.defaultDuration(this)) + " ▾", false, v -> pickDuration());
        row(prefs, "⏱", false, "משך פגישה", null, dur);
        Switch inv = new Switch(this);
        inv.setChecked(Core.sendInvites(this));
        inv.setContentDescription("שליחת הזמנות");
        inv.setOnCheckedChangeListener((s, on) -> Core.prefs(this).edit().putBoolean("send_invites", on).apply());
        row(prefs, "✉️", false, "שליחת הזמנות", plain("כשמזכירים איש קשר בפגישה"), inv);
        row(prefs, "➕", true, "כפתור במסך הבית", null, link("הוסף", false, v -> addWidget()));

        footer();
        if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) debugParserTest(); // debug builds only
    }

    // ================= actions =================

    private void openBabiTk() { startActivity(new Intent(this, BabiTkSettingsActivity.class)); }

    /** Same connections card as Google: connect, pick a writable phone calendar, then optionally make it the destination. */
    private void renderDeviceCalendar(LinearLayout conn) {
        boolean perm = DeviceCalendar.hasPermission(this);
        if (perm) Core.prefs(this).edit()
            .remove(blockedKey(Manifest.permission.READ_CALENDAR))
            .remove(blockedKey(Manifest.permission.WRITE_CALENDAR)).apply();
        boolean blocked = !perm && (isBlocked(this, Manifest.permission.READ_CALENDAR) || isBlocked(this, Manifest.permission.WRITE_CALENDAR));
        long savedId = Core.prefs(this).getLong(DeviceCalendar.PREF_ID, -1L);
        boolean saved = perm && Core.prefs(this).getBoolean(DeviceCalendar.PREF_ON, false) && savedId > 0;
        boolean alive = true;
        if (saved) {
            try { alive = DeviceCalendar.stillWritable(this, savedId); }
            catch (SecurityException e) { alive = false; }
            catch (Exception ignored) { alive = true; }
        }
        boolean on = saved && alive;
        boolean destDevice = DeviceCalendarLogic.DEVICE.equals(Core.prefs(this).getString(DeviceCalendar.PREF_DEST, DeviceCalendarLogic.GOOGLE));
        if (on) {
            String name = DeviceCalendar.savedName(this);
            row(conn, "📱", false, "יומן המכשיר", ok("מחובר ✓", " · " + name), link("נתק", false, v -> disconnectDeviceCalendar()));
            row(conn, "🗓️", false, "יומן", null, link(name + " ▾", false, v -> pickDeviceCalendar()));
        } else if (saved) {
            row(conn, "📱", false, "יומן המכשיר", plain("היומן שנבחר לא זמין"), link("נתק", false, v -> disconnectDeviceCalendar()));
            row(conn, "🗓️", false, "יומן", null, link("בחרו ▾", false, v -> pickDeviceCalendar()));
        } else if (blocked) {
            row(conn, "📱", false, "יומן המכשיר", plain("נחסם – אפשר לאשר בהגדרות המערכת"), pill("אשר", v -> {
                Toast.makeText(this, "הרשאות ← יומן ← אפשר", Toast.LENGTH_LONG).show(); openAppInfo();
            }));
        } else {
            row(conn, "📱", false, "יומן המכשיר", plain("לשמירה ביומן שבטלפון"), pill("חבר", v -> connectDeviceCalendar()));
        }
        if (on || destDevice) {
            String cur = destDevice ? "יומן המכשיר" : "Google יומן";
            String sub = destDevice
                ? (on ? "פגישות נשמרות ביומן המכשיר" : "אין יומן זמין – בחרו יומן או חזרו ל-Google")
                : "פגישות נשמרות ב-Google";
            row(conn, "📌", false, "יעד לפגישות", plain(sub), link(cur + " ▾", false, v -> pickDestination(on)));
        }
    }

    private void connectDeviceCalendar() {
        if (DeviceCalendar.hasPermission(this)) { pickDeviceCalendar(); return; }
        if (isBlocked(this, Manifest.permission.READ_CALENDAR) || isBlocked(this, Manifest.permission.WRITE_CALENDAR)) {
            Toast.makeText(this, "הרשאות ← יומן ← אפשר", Toast.LENGTH_LONG).show();
            openAppInfo();
            return;
        }
        rtl(new AlertDialog.Builder(this).setTitle("יומן המכשיר")
            .setMessage("BabiSnap יבקש גישה ליומנים בטלפון כדי לשמור פגישות ביומן שתבחרו, כולל יומן שמסונכרן לטלפון. הגישה מקומית: לא נשלחות הקלטות, תמלילים או תוכן יומן לשרת.")
            .setPositiveButton("המשך", (x, w) -> requestPermissions(new String[]{
                Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR}, REQ_CALENDAR))
            .setNegativeButton("ביטול", null).show());
    }

    private void pickDeviceCalendar() {
        java.util.List<DeviceCalendar.Cal> cals;
        try { cals = DeviceCalendar.listWritable(this); }
        catch (SecurityException e) {
            Toast.makeText(this, DeviceCalendar.ERR_PERMISSION, Toast.LENGTH_LONG).show(); return;
        } catch (Exception e) {
            Toast.makeText(this, "לא ניתן לקרוא את יומני הטלפון", Toast.LENGTH_LONG).show(); return;
        }
        if (cals.isEmpty()) {
            Toast.makeText(this, "לא נמצא יומן שאפשר לכתוב אליו בטלפון.", Toast.LENGTH_LONG).show();
            render(); return;
        }
        String[] labels = new String[cals.size()];
        long current = Core.prefs(this).getLong(DeviceCalendar.PREF_ID, -1L);
        int sel = -1;
        for (int i = 0; i < cals.size(); i++) {
            labels[i] = cals.get(i).label;
            if (cals.get(i).id == current) sel = i;
        }
        final java.util.List<DeviceCalendar.Cal> choices = cals;
        boolean first = !Core.prefs(this).getBoolean(DeviceCalendar.PREF_ON, false);
        rtl(new AlertDialog.Builder(this).setTitle("בחרו יומן")
            .setSingleChoiceItems(labels, sel, (d, i) -> {
                DeviceCalendar.Cal cal = choices.get(i);
                Core.prefs(this).edit()
                    .putLong(DeviceCalendar.PREF_ID, cal.id)
                    .putString(DeviceCalendar.PREF_NAME, cal.label)
                    .putBoolean(DeviceCalendar.PREF_ON, true).apply();
                Toast.makeText(this, first
                    ? "חוברו ליומן המכשיר. פגישות יישמרו ב-Google עד שתשנו את יעד הפגישות."
                    : "היומן עודכן", Toast.LENGTH_LONG).show();
                d.dismiss(); render();
            }).setNegativeButton("ביטול", null).show());
    }

    private void pickDestination(boolean deviceReady) {
        String[] items = deviceReady ? new String[]{"Google יומן", "יומן המכשיר"} : new String[]{"Google יומן"};
        String dest = Core.prefs(this).getString(DeviceCalendar.PREF_DEST, DeviceCalendarLogic.GOOGLE);
        int cur = deviceReady && DeviceCalendarLogic.DEVICE.equals(dest) ? 1 : 0;
        rtl(new AlertDialog.Builder(this).setTitle("יעד לפגישות")
            .setSingleChoiceItems(items, cur, (d, i) -> {
                Core.prefs(this).edit().putString(DeviceCalendar.PREF_DEST,
                    i == 1 ? DeviceCalendarLogic.DEVICE : DeviceCalendarLogic.GOOGLE).apply();
                d.dismiss(); render();
            }).setNegativeButton("ביטול", null).show());
    }

    private void disconnectDeviceCalendar() {
        Core.prefs(this).edit()
            .remove(DeviceCalendar.PREF_ID).remove(DeviceCalendar.PREF_NAME)
            .putBoolean(DeviceCalendar.PREF_ON, false)
            .putString(DeviceCalendar.PREF_DEST, DeviceCalendarLogic.destinationAfterDisconnect()).apply();
        Toast.makeText(this, "נותקתם מיומן המכשיר. פגישות יישמרו ב-Google יומן.", Toast.LENGTH_LONG).show();
        render();
    }

    private void signIn() {
        Core.requestAuth(this, REQ_AUTH, this::gotToken, e -> Toast.makeText(this, "שגיאת התחברות: " + e, Toast.LENGTH_LONG).show());
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        if (req != REQ_AUTH) return;
        if (res != RESULT_OK) { Toast.makeText(this, "ההתחברות בוטלה", Toast.LENGTH_SHORT).show(); return; }
        try { gotToken(Core.tokenFromResult(this, data)); } catch (Exception e) { Toast.makeText(this, "שגיאה: " + e, Toast.LENGTH_LONG).show(); }
    }

    private void gotToken(String token) {
        new Thread(() -> {
            String email; try { email = Core.fetchEmail(token); } catch (Exception e) { email = null; }
            String shown = email == null ? "(חשבון Google)" : email;
            Core.prefs(this).edit().putString("email", shown).apply(); // the Google access token itself is never stored by us
            boolean tell = SnapRegistry.validEmail(email) && SnapClient.configured();
            runOnUiThread(() -> {
                if (isFinishing()) return;
                render();
                if (tell) Toast.makeText(this, getString(R.string.snap_registry_notice), Toast.LENGTH_LONG).show();
            });
            if (!tell) return;
            SnapRegistry.PostResult r = SnapClient.post(SnapRegistry.normalizeEmail(email), SnapInstall.id(this),
                SnapRegistry.EVENT_SIGNIN, SnapRegistry.sources(true, BabiTk.hasToken(this)),
                SnapInstall.appVersion(this), SnapInstall.deviceLabel());
            if (r != null && r.blocked()) runOnUiThread(() -> {
                if (!isFinishing()) Toast.makeText(this, getString(R.string.snap_blocked), Toast.LENGTH_LONG).show();
            });
        }, "snap-signin").start();
    }

    /** Sign out = forget the account locally, tell the registry, AND revoke BabiSnap's Calendar grant at Google. */
    private void signOut() {
        reportGoogleDisconnect(BabiTk.hasToken(this));
        Core.prefs(this).edit().remove("email").remove("tok").apply();
        revokeGoogle();
        render();
        Toast.makeText(this, "התנתקת מ-Google – הגישה ליומן מבוטלת", Toast.LENGTH_SHORT).show();
    }

    /** event=disconnect for the Google account. Captures ids before prefs are cleared. */
    private void reportGoogleDisconnect(boolean babiTkStillConnected) {
        String email = SnapRegistry.normalizeEmail(Core.prefs(this).getString("email", null));
        if (!SnapRegistry.validEmail(email)) return;
        Context app = getApplicationContext();
        SnapClient.reportAsync(app, email, SnapInstall.id(app), SnapRegistry.EVENT_DISCONNECT,
            SnapRegistry.sources(false, babiTkStillConnected), SnapInstall.appVersion(app), SnapInstall.deviceLabel());
    }

    /** We never keep Google tokens, so a fresh one is fetched silently from Play services just to revoke it. */
    private void revokeGoogle() {
        Context app = getApplicationContext();
        com.google.android.gms.auth.api.identity.Identity.getAuthorizationClient(this).authorize(Core.authRequest())
            .addOnSuccessListener(res -> {
                String tok = res.hasResolution() ? null : res.getAccessToken(); // needs UI = no grant left to revoke
                if (tok != null) new Thread(() -> {
                    Core.revoke(tok);
                    try { GoogleAuthUtil.clearToken(app, tok); } catch (Exception ignored) {}
                }).start();
            });
    }

    // ---- runtime permissions ----
    private static String blockedKey(String perm) { return "perm_blocked_" + perm; }

    /** Records the answer to a permission dialog (also used by VoiceActivity for the microphone). */
    static void noteResult(Activity a, String perm, boolean granted) {
        boolean blocked = SettingsLogic.blockedAfterDenial(granted, a.shouldShowRequestPermissionRationale(perm));
        Core.prefs(a).edit().putBoolean(blockedKey(perm), blocked).apply();
    }

    private void permRow(LinearLayout card, String icon, String title, String hint, String perm, int req) {
        boolean granted = checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED;
        if (granted || shouldShowRequestPermissionRationale(perm)) Core.prefs(this).edit().remove(blockedKey(perm)).apply();
        boolean blocked = Core.prefs(this).getBoolean(blockedKey(perm), false);
        switch (SettingsLogic.permAction(granted, blocked)) {
            case GRANTED:
                row(card, icon, false, title, ok("מאושר ✓", hint == null ? "" : " · " + hint), link("שנה", false, v -> openAppInfo())); break;
            case APP_INFO:
                row(card, icon, false, title, plain("נחסם – אפשר לאשר בהגדרות המערכת"), pill("אשר", v -> {
                    Toast.makeText(this, "הרשאות ← " + title + " ← אפשר", Toast.LENGTH_LONG).show(); openAppInfo(); })); break;
            default:
                row(card, icon, false, title, hint == null ? null : plain(hint), pill("אשר", v -> requestPermissions(new String[]{perm}, req)));
        }
    }

    @Override public void onRequestPermissionsResult(int req, String[] p, int[] g) {
        if (req == REQ_CALENDAR) {
            for (int i = 0; i < p.length; i++)
                noteResult(this, p[i], i < g.length && g[i] == PackageManager.PERMISSION_GRANTED);
            if (DeviceCalendar.hasPermission(this)) pickDeviceCalendar();
            else render();
            return;
        }
        if ((req == REQ_MIC || req == REQ_CONTACTS) && p.length > 0) {
            noteResult(this, p[0], g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED);
            render();
        }
    }

    private void openAppInfo() { openAppInfo(this); }

    static void openAppInfo(Activity a) {
        try { a.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", a.getPackageName(), null))); }
        catch (Exception e) { Toast.makeText(a, "פתחו: הגדרות ← אפליקציות ← BabiSnap", Toast.LENGTH_LONG).show(); }
    }

    /** Denied twice: Android no longer shows its dialog, only App info can grant it. */
    static boolean isBlocked(Activity a, String perm) {
        return Core.prefs(a).getBoolean(blockedKey(perm), false) && !a.shouldShowRequestPermissionRationale(perm);
    }

    // ---- preferences ----
    private void pickDuration() {
        rtl(new AlertDialog.Builder(this).setTitle("משך פגישה")
            .setSingleChoiceItems(SettingsLogic.DURATION_LABELS, SettingsLogic.durationIndex(Core.defaultDuration(this)), (d, i) -> {
                Core.prefs(this).edit().putInt("dur", SettingsLogic.DURATIONS[i]).apply(); d.dismiss(); render();
            }).setNegativeButton("ביטול", null).show());
    }

    private void addWidget() {
        AppWidgetManager m = getSystemService(AppWidgetManager.class);
        if (m == null || !m.isRequestPinAppWidgetSupported()) {
            Toast.makeText(this, "לחיצה ארוכה על מסך הבית ← ווידג'טים ← BabiSnap", Toast.LENGTH_LONG).show(); return;
        }
        showWidgetPicker(m);
    }

    // ---- footer: version · free · privacy, delete all ----
    private void footer() {
        String ver = "";
        try { ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception ignored) {}
        TextView f = new TextView(this);
        f.setText(SettingsLogic.footer(ver));
        f.setTextSize(12.5f); f.setTextColor(0xFF8A879A); f.setGravity(Gravity.CENTER); f.setPadding(0, dp(16), 0, 0);
        content.addView(f, new LinearLayout.LayoutParams(-1, -2));
        // Privacy / Terms as two separate text buttons, each >= 48dp tall (were ~15dp inline spans).
        LinearLayout links = new LinearLayout(this); links.setOrientation(LinearLayout.HORIZONTAL); links.setGravity(Gravity.CENTER);
        links.addView(footerLink("פרטיות", v -> showPrivacy()));
        TextView dot = new TextView(this); dot.setText(" · "); dot.setTextSize(12.5f); dot.setTextColor(0xFF8A879A); links.addView(dot);
        links.addView(footerLink("תנאי שימוש", v -> openUrl(TERMS_URL)));
        content.addView(links, new LinearLayout.LayoutParams(-1, -2));
        TextView del = new TextView(this);
        del.setText("מחק את כל הנתונים מהמכשיר"); del.setTextColor(RED); del.setTextSize(13.5f); del.setGravity(Gravity.CENTER);
        del.setPadding(dp(8), dp(8), dp(8), dp(8)); del.setMinHeight(dp(48)); del.setMinimumHeight(dp(48));
        del.setOnClickListener(v -> confirmDeleteAll());
        content.addView(del, new LinearLayout.LayoutParams(-1, -2));
    }

    static final String PRIVACY_URL =
        "https://github.com/erezbabayan/BabiSnap/blob/main/docs/PRIVACY_HE.md";
    static final String TERMS_URL =
        "https://github.com/erezbabayan/BabiSnap/blob/main/docs/TERMS_HE.md";

    static final String PRIVACY_HE =
        "• הדיבור מתומלל ע\"י שירות זיהוי הדיבור של הטלפון, והפענוח (פגישה/משימה/הערה, תאריך, שעה, מקום) נעשה בטלפון עצמו.\n\n" +
        "• פגישות נשמרות ישירות ביומן Google שלך, דרך החשבון שחיברת. BabiSnap לא שומר סיסמה או טוקן של Google.\n\n" +
        "• אם בחרתם יעד \"יומן המכשיר\", הפגישה נכתבת ליומן שבטלפון בלבד. BabiSnap לא שולח את האירוע לשרת. אם היומן מסונכרן באפליקציה אחרת, הסנכרון הוא שלה. זימון במייל נשלח רק מיומן Google.\n\n" +
        "• משימות והערות נשלחות רק ללוח \"מחברת\" ב-BabiTk שלך. מפתח החיבור נשמר מוצפן בטלפון.\n\n" +
        "• אנשי קשר נקראים בטלפון בלבד ולא נשלחים לשום מקום (אימייל מוזמן עובר ל-Google רק אם ביקשת לשלוח הזמנה).\n\n" +
        "• האימייל של חשבון Google שחיברתם, יחד עם מזהה התקנה אקראי (לא IMEI), גרסה ושם דגם, נשמרים לניהול הגישה בלבד. לא נשלחים הקלטות, תמלילים, תוכן יומן או טוקן Google.\n\n" +
        "• אין פרסומות ואין מעקב תוכן. \"מחק את כל הנתונים מהמכשיר\" מוחק את נתוני האפליקציה מהטלפון ומסמן את ההתקנה כמנותקת.\n\n" +
        "• מדיניות הפרטיות המלאה זמינה בקישור \"מדיניות מלאה\".";

    private void openUrl(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (Exception e) { Toast.makeText(this, "לא ניתן לפתוח את הקישור", Toast.LENGTH_SHORT).show(); }
    }

    private void showPrivacy() {
        AlertDialog d = new AlertDialog.Builder(this).setTitle("פרטיות").setMessage(PRIVACY_HE)
            .setPositiveButton("הבנתי", null)
            .setNeutralButton("מדיניות מלאה", (x, w) -> openUrl(PRIVACY_URL)).show();
        rtl(d);
    }

    private void confirmDeleteAll() {
        AlertDialog d = new AlertDialog.Builder(this).setTitle("למחוק את כל הנתונים מהמכשיר?")
            .setMessage("ינותקו Google יומן (כולל ביטול הגישה) ו-BabiTk, יישכח יומן המכשיר שנבחר, יימחקו פריטים שעוד לא נשלחו וכל ההעדפות, והאימייל ברישום הגישה יסומן כמנותק.\nהפגישות ביומן (Google או יומן המכשיר) וההערות ב-BabiTk לא נמחקות.")
            .setPositiveButton("מחק", (x, w) -> deleteAll()).setNegativeButton("ביטול", null).show();
        rtl(d);
        d.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(RED);
    }

    private void deleteAll() {
        String email = Core.prefs(this).getString("email", null);
        String installId = SnapInstall.id(this);
        if (email != null) revokeGoogle();
        reportGoogleDisconnect(false);
        BabiTk.wipeAll(getApplicationContext());
        Core.prefs(this).edit().clear().commit();
        Core.prefs(this).edit().putString(SnapInstall.PREF, installId).commit(); // same install, data gone
        render();
        Toast.makeText(this, "כל הנתונים נמחקו מהמכשיר", Toast.LENGTH_LONG).show();
    }

    // ---- debug builds only: parser test (moved out of the user-facing settings) ----
    private void debugParserTest() {
        section("בדיקת פענוח (גרסת פיתוח, לא שומר)");
        LinearLayout c = card(); c.setPadding(dp(14), dp(8), dp(14), dp(12));
        EditText in = new EditText(this); in.setHint("פגישה עם דני מחר ב-14:00"); in.setInputType(InputType.TYPE_CLASS_TEXT); c.addView(in);
        TextView out = new TextView(this); out.setTextSize(14);
        Button test = new Button(this); test.setText("בדוק"); c.addView(test); c.addView(out);
        test.setOnClickListener(v -> {
            try { JSONObject r = Core.parse(this, in.getText().toString());
                out.setText(r.getString("confirm") + (r.optBoolean("allDay") ? "\nאירוע של יום שלם" : "\nמשך: " + r.getInt("durationMin") + " דקות") + (r.optBoolean("timeDefaulted") ? "\n(לא נאמרה שעה, נקבע 09:00)" : ""));
            } catch (Exception e) { out.setText("שגיאה: " + e); }
        });
    }

    // ================= view helpers =================

    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private static GradientDrawable round(int color, float radiusPx) {
        GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(radiusPx); return g;
    }

    private View recordButton() {
        LinearLayout r = new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL); r.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        r.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TR_BL, new int[]{0xFFFFD43B, 0xFFFFA41B});
        g.setCornerRadius(dp(26)); r.setBackground(g); r.setElevation(dp(2));
        ImageView logo = new ImageView(this); logo.setImageResource(R.drawable.widget_logo);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(dp(30), dp(30)); llp.setMarginEnd(dp(10));
        r.addView(logo, llp);
        TextView t = new TextView(this);
        t.setText("הכתבה עכשיו"); t.setTextSize(18); t.setTextColor(PURPLE); t.setTypeface(Typeface.DEFAULT_BOLD);
        r.addView(t);
        r.setContentDescription("הכתבה עכשיו"); r.setClickable(true);
        r.setOnClickListener(v -> startActivity(new Intent(this, VoiceActivity.class)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(52)); lp.bottomMargin = dp(4);
        r.setLayoutParams(lp);
        return r;
    }

    private void section(String s) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(12.5f); t.setTextColor(SECTION); t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(dp(6), dp(14), dp(6), dp(6)); t.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        content.addView(t, new LinearLayout.LayoutParams(-1, -2));
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL); c.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        c.setBackground(round(0xFFFFFFFF, dp(18))); c.setElevation(dp(1)); c.setClipToOutline(true);
        content.addView(c, new LinearLayout.LayoutParams(-1, -2));
        return c;
    }

    private void row(LinearLayout card, String icon, boolean yellow, String title, CharSequence sub, View action) {
        if (card.getChildCount() > 0) {
            View div = new View(this); div.setBackgroundColor(DIVIDER); card.addView(div, new LinearLayout.LayoutParams(-1, 1));
        }
        LinearLayout r = new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL); r.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        r.setGravity(Gravity.CENTER_VERTICAL); r.setMinimumHeight(dp(56)); r.setPadding(dp(14), dp(10), dp(14), dp(10));
        TextView ic = new TextView(this); ic.setText(icon); ic.setTextSize(16); ic.setGravity(Gravity.CENTER);
        ic.setBackground(round(yellow ? ICON_BG_Y : ICON_BG, dp(10)));
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(34), dp(34)); ilp.setMarginEnd(dp(12));
        r.addView(ic, ilp);
        LinearLayout tx = new LinearLayout(this); tx.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(this); t.setText(title); t.setTextSize(15.5f); t.setTextColor(0xFF1D1B26);
        t.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START); tx.addView(t);
        if (sub != null) {
            TextView s = new TextView(this); s.setText(sub); s.setTextSize(12.5f); s.setTextColor(MUTED); s.setSingleLine(true);
            s.setEllipsize(android.text.TextUtils.TruncateAt.END); s.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START); tx.addView(s);
        }
        r.addView(tx, new LinearLayout.LayoutParams(0, -2, 1f));
        if (action != null) {
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-2, -2); alp.setMarginStart(dp(8));
            r.addView(action, alp);
        }
        card.addView(r, new LinearLayout.LayoutParams(-1, -2));
    }

    private TextView footerLink(String text, View.OnClickListener l) {
        TextView b = new TextView(this); b.setText(text); b.setTextSize(12.5f); b.setTextColor(MUTED);
        b.setPaintFlags(b.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        b.setGravity(Gravity.CENTER); b.setPadding(dp(12), 0, dp(12), 0);
        b.setMinHeight(dp(48)); b.setMinimumHeight(dp(48)); b.setMinWidth(dp(48)); b.setMinimumWidth(dp(48));
        b.setOnClickListener(l);
        return b;
    }

    private CharSequence ok(String check, String rest) {
        SpannableString s = new SpannableString(check + rest);
        s.setSpan(new ForegroundColorSpan(GREEN), 0, check.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return s;
    }
    private CharSequence plain(String s) { return s; }

    private TextView pill(String text, View.OnClickListener l) {
        TextView b = new TextView(this); b.setText(text); b.setTextColor(0xFFFFFFFF); b.setTextSize(14); b.setTypeface(Typeface.DEFAULT_BOLD);
        // 48dp tap target; visual pill stays ~36dp via a transparent 6dp vertical inset.
        // Background must be set BEFORE padding: setBackground() replaces padding with the
        // InsetDrawable's (0,6,0,6), which removed all horizontal padding and clipped Hebrew text.
        b.setBackground(new android.graphics.drawable.InsetDrawable(round(PURPLE, dp(18)), 0, dp(6), 0, dp(6)));
        b.setGravity(Gravity.CENTER); b.setSingleLine(true); b.setIncludeFontPadding(false);
        b.setMinHeight(dp(48)); b.setMinimumHeight(dp(48)); b.setMinWidth(dp(72)); b.setMinimumWidth(dp(72));
        b.setPadding(dp(18), dp(6), dp(18), dp(6)); b.setOnClickListener(l);
        return b;
    }
    private TextView link(String text, boolean red, View.OnClickListener l) {
        TextView b = new TextView(this); b.setText(text); b.setTextColor(red ? RED : PURPLE); b.setTextSize(13.5f);
        b.setPadding(dp(8), dp(8), dp(8), dp(8)); b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(48)); b.setMinimumHeight(dp(48)); b.setMinWidth(dp(48)); b.setMinimumWidth(dp(48)); // 48dp tap target
        b.setOnClickListener(l);
        return b;
    }
    private static void rtl(AlertDialog d) {
        if (d.getWindow() != null) d.getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    }

    private void showWidgetPicker(AppWidgetManager m) {
        float dp = getResources().getDisplayMetrics().density;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL); row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        row.setGravity(Gravity.CENTER); int pad = (int) (16 * dp); row.setPadding(pad, pad, pad, (int) (4 * dp));
        final AlertDialog[] dlg = new AlertDialog[1];
        int[] imgs = {R.drawable.record_tile, R.drawable.widget_preview};
        String[] desc = {"כפתור הקלטה (ריבוע)", "יישומון רחב עם שם"};
        Class<?>[] providers = {ButtonWidget.class, MicWidget.class};
        int[] w = {88, 176};
        for (int i = 0; i < 2; i++) {
            ImageView iv = new ImageView(this);
            iv.setImageResource(imgs[i]); iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setContentDescription(desc[i]); iv.setAdjustViewBounds(true);
            int ip = (int) (10 * dp); iv.setPadding(ip, ip, ip, ip);
            GradientDrawable card = new GradientDrawable();
            card.setCornerRadius(20 * dp); card.setColor(0x1A808080); card.setStroke((int) dp, 0x40808080);
            android.util.TypedValue tv = new android.util.TypedValue();
            getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
            iv.setBackground(new android.graphics.drawable.LayerDrawable(new Drawable[]{card, getDrawable(tv.resourceId)}));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int) (w[i] * dp), (int) (88 * dp));
            lp.setMargins((int) (6 * dp), 0, (int) (6 * dp), 0);
            final Class<?> prov = providers[i];
            iv.setOnClickListener(v -> {
                if (dlg[0] != null) dlg[0].dismiss();
                boolean ok = m.requestPinAppWidget(new ComponentName(this, prov), null, null);
                if (!ok) Toast.makeText(this, "המסך הראשי לא אישר הוספה – הוסיפו ידנית מתפריט הווידג'טים", Toast.LENGTH_LONG).show();
            });
            row.addView(iv, lp);
        }
        dlg[0] = new AlertDialog.Builder(this).setTitle("איזה יישומון להוסיף?")
            .setView(row).setNegativeButton("ביטול", null).create();
        dlg[0].show();
    }
}
