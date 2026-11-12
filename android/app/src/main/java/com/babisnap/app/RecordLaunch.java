package com.babisnap.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/**
 * Starts the same recording the home button uses. A side-key double press delivers
 * MAIN/LAUNCHER, including while this activity is already in the background (onNewIntent).
 * The recording screen itself asks for the microphone. No extra audio is stored here.
 */
public final class RecordLaunch {
    private RecordLaunch() {}

    public static final String EXTRA_ASK_MIC = "ask_mic";
    /** Settings gear and any in-app open. Must not start another recording. */
    public static final String EXTRA_NO_AUTO_RECORD = "no_auto_record";
    public static final String PREF_AUTO = "launch_records";
    /** Side-button double press opens the main icon, so recording on launch starts on. */
    public static final boolean DEFAULT_AUTO = true;
    public static final int FLAGS = Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK;
    public static final String HINT =
        "כפתור צד, לחיצה כפולה: הגדרות ← תכונות מתקדמות ← כפתור צד ← לחיצה כפולה ← פתיחת אפליקציה ← BabiSnap "
        + "(המתג «פתיחת BabiSnap מתחילה הקלטה מיד» דלוק), או «BabiSnap הקלטה» אם הוא מופיע ברשימה. "
        + "לחיצה ארוכה: הגדרות ← אפליקציות ← אפליקציות ברירת מחדל ← אפליקציית עוזר דיגיטלי ← BabiSnap. "
        + "בלי הרשאת מיקרופון מסך ההקלטה מבקש אישור. אין הקלטה נוספת מעבר להקלטה הרגילה.";

    public static boolean enabled(SharedPreferences prefs) {
        return prefs == null || prefs.getBoolean(PREF_AUTO, DEFAULT_AUTO);
    }

    public static boolean fromHistory(Intent in) {
        return in != null && (in.getFlags() & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0;
    }

    public static boolean skipAuto(Intent in) {
        if (in == null) return false;
        return in.getBooleanExtra(EXTRA_NO_AUTO_RECORD, false) || in.getBooleanExtra(EXTRA_ASK_MIC, false);
    }

    /**
     * A fresh MAIN/LAUNCHER open records when the toggle is on.
     * Recents, a recreated activity, notifications, and the settings gear do not.
     */
    public static boolean shouldRecordOnLaunch(boolean toggleOn, String action, boolean launcher,
            boolean fromHistory, boolean openedFromInside, boolean recreated) {
        if (recreated) return false;
        return shouldRecordOnNewIntent(toggleOn, action, launcher, fromHistory, openedFromInside);
    }

    /**
     * The activity is already alive (singleTask). A new side-key or launcher intent still records.
     * FLAG_ACTIVITY_NEW_TASK from SystemUI does not change this; recents and the gear do.
     */
    public static boolean shouldRecordOnNewIntent(boolean toggleOn, String action, boolean launcher,
            boolean fromHistory, boolean openedFromInside) {
        if (!toggleOn || openedFromInside || fromHistory) return false;
        return Intent.ACTION_MAIN.equals(action) && launcher;
    }

    /** Always the recording screen. The assistant and a double press must not land on Settings. */
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
