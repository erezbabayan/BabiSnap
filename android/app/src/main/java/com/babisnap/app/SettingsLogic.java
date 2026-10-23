package com.babisnap.app;

/** Pure decisions behind the Settings screen (unit-tested; no Android types). */
public final class SettingsLogic {
    /** What the single action on a permission row does. */
    public enum PermAction { GRANTED, REQUEST, APP_INFO }

    /** Granted -> show ✓ ("שנה" opens App info). Not granted: ask in-app, unless Android stopped showing the dialog
     *  (denied with "don't ask again" / twice) -> App info is the only way. */
    public static PermAction permAction(boolean granted, boolean blocked) {
        if (granted) return PermAction.GRANTED;
        return blocked ? PermAction.APP_INFO : PermAction.REQUEST;
    }

    /** After a denied request: if Android will no longer show a rationale, the dialog is blocked for good. */
    public static boolean blockedAfterDenial(boolean granted, boolean shouldShowRationale) {
        return !granted && !shouldShowRationale;
    }

    /** BabiTk test/retry/diagnostics appear only when something is waiting or wrong. */
    public static boolean showBabiTkDiagnostics(int pending, int failed, boolean connected, String lastError, String storeError) {
        if (pending > 0 || failed > 0) return true;
        if (storeError != null && !storeError.isEmpty()) return true;
        return connected && lastError != null && !lastError.isEmpty() && !"אין".equals(lastError);
    }

    public static final int[] DURATIONS = {15, 30, 45, 60, 90, 120};
    public static final String[] DURATION_LABELS = {"רבע שעה", "חצי שעה", "45 דקות", "שעה", "שעה וחצי", "שעתיים"};

    public static String durationLabel(int minutes) {
        for (int i = 0; i < DURATIONS.length; i++) if (DURATIONS[i] == minutes) return DURATION_LABELS[i];
        return minutes + " דקות";
    }
    public static int durationIndex(int minutes) {
        for (int i = 0; i < DURATIONS.length; i++) if (DURATIONS[i] == minutes) return i;
        return 3; // שעה
    }

    /** Footer line under the settings. */
    public static String footer(String version) { return "BabiSnap " + version + " · חינם · "; }

    private SettingsLogic() {}
}
