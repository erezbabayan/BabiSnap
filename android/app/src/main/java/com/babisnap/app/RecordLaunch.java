package com.babisnap.app;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

/**
 * Starts the same recording the home button uses. Without the microphone permission,
 * opens Settings so the existing permission row can ask. No extra audio is stored here.
 */
public final class RecordLaunch {
    private RecordLaunch() {}

    public static final String EXTRA_ASK_MIC = "ask_mic";
    public static final int FLAGS = Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK;
    /** Shown under the home-button row. Samsung side key, then the system assistant. */
    public static final String HINT =
        "כפתור צד, לחיצה כפולה: הגדרות ← תכונות מתקדמות ← כפתור צד ← לחיצה כפולה ← פתיחת אפליקציה ← «BabiSnap הקלטה». "
        + "לחיצה ארוכה: הגדרות ← אפליקציות ← אפליקציות ברירת מחדל ← אפליקציית עוזר דיגיטלי ← BabiSnap. "
        + "בלי הרשאת מיקרופון נפתח מסך ההגדרות לאישור. אין הקלטה נוספת מעבר להקלטה הרגילה.";

    /** VoiceActivity when the mic is already granted, otherwise the settings screen. */
    public static String target(boolean micGranted) {
        return (micGranted ? VoiceActivity.class : MainActivity.class).getName();
    }

    public static Intent intent(Context c) {
        boolean mic = c.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        Intent i = new Intent();
        i.setClassName(c, target(mic));
        i.addFlags(FLAGS);
        if (!mic) i.putExtra(EXTRA_ASK_MIC, true);
        return i;
    }
}
