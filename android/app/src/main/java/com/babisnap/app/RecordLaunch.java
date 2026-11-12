package com.babisnap.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/**
 * The record shortcut and the assistant open the recording screen.
 * The main launcher icon does not: it is the settings activity.
 */
public final class RecordLaunch {
    private RecordLaunch() {}

    /** Left behind by the removed «פתיחת BabiSnap מתחילה הקלטה מיד» switch. Dropped, never read. */
    public static final String OLD_PREF = "launch_records";
    public static final String EXTRA_ASK_MIC = "ask_mic";
    public static final String EXTRA_NO_AUTO_RECORD = "no_auto_record";
    public static final int FLAGS = Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK;
    public static final String HINT =
        "כפתור צד, לחיצה כפולה: הגדרות ← תכונות מתקדמות ← כפתור צד ← לחיצה כפולה ← פתיחת אפליקציה ← «BabiSnap הקלטה» (האייקון עם המיקרופון). "
        + "האייקון «BabiSnap» פותח את ההגדרות ולא מתחיל הקלטה. "
        + "לחיצה ארוכה: הגדרות ← אפליקציות ← אפליקציות ברירת מחדל ← אפליקציית עוזר דיגיטלי ← BabiSnap. "
        + "בלי הרשאת מיקרופון מסך ההקלטה מבקש אישור. אין הקלטה נוספת מעבר להקלטה הרגילה.";

    public static void forgetOldPref(SharedPreferences prefs) {
        if (prefs != null && prefs.contains(OLD_PREF)) prefs.edit().remove(OLD_PREF).apply();
    }

    /** Always the recording screen. The main icon never uses this. */
    public static String recordingClass() { return VoiceActivity.class.getName(); }

    public static Intent recording(Context c) {
        Intent i = new Intent();
        i.setClassName(c, recordingClass());
        i.addFlags(FLAGS);
        return i;
    }

    public static Intent settings(Context c) {
        Intent i = new Intent(c, MainActivity.class);
        i.putExtra(EXTRA_NO_AUTO_RECORD, true);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return i;
    }
}
