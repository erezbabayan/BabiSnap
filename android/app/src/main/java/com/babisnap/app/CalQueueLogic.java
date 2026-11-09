package com.babisnap.app;


/**
 * Pure calendar-queue decisions (no Android types) — unit-tested.
 * Google Calendar custom event ids may only use [a-v0-9] (length 5–1024).
 */
public final class CalQueueLogic {
    public static final String ROUTE_GOOGLE = "google";
    public static final String ROUTE_DEVICE = "device";

    public enum Outcome { DONE, RETRY, NEEDS_AUTH, FAILED }

    /** Stable Google Calendar event id derived from babisnap client id (retries → same id → no duplicates). */
    public static String googleEventId(String clientId) {
        if (clientId == null) return null;
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < clientId.length(); i++) {
            char c = Character.toLowerCase(clientId.charAt(i));
            if ((c >= 'a' && c <= 'v') || (c >= '0' && c <= '9')) b.append(c);
        }
        if (b.length() < 5) return null;
        if (b.length() > 1024) b.setLength(1024);
        return b.toString();
    }

    /** Marker embedded in device-calendar description for retry dedup. */
    public static String deviceMarker(String clientId) {
        return clientId == null || clientId.isEmpty() ? "" : "babisnapId:" + clientId;
    }

    public static String withDeviceMarker(String description, String clientId) {
        String mark = deviceMarker(clientId);
        if (mark.isEmpty()) return description == null ? "" : description;
        String d = description == null ? "" : description;
        if (d.contains(mark)) return d;
        return d.isEmpty() ? mark : d + "\n" + mark;
    }

    /** HTTP outcome for a Google Calendar insert (409 = our id already exists → success). */
    public static Outcome googleInsertOutcome(int code) {
        if (code / 100 == 2 || code == 409) return Outcome.DONE;
        if (code == 401 || code == 403) return Outcome.NEEDS_AUTH;
        if (code == 429) return Outcome.RETRY;
        if (code >= 400 && code < 500) return Outcome.FAILED;
        return Outcome.RETRY;
    }

    public static Outcome googlePatchOutcome(int code) {
        if (code / 100 == 2) return Outcome.DONE;
        if (code == 401 || code == 403) return Outcome.NEEDS_AUTH;
        if (code == 429) return Outcome.RETRY;
        if (code >= 400 && code < 500) return Outcome.FAILED;
        return Outcome.RETRY;
    }

    public static boolean showDiagnostics(int pending, int failed, int needsAuth, String lastError) {
        if (pending > 0 || failed > 0 || needsAuth > 0) return true;
        return lastError != null && !lastError.isEmpty() && !"אין".equals(lastError);
    }

    /** One short Hebrew status line for Settings. */
    public static String statusTitle(int pending, int failed, int needsAuth) {
        if (needsAuth > 0) return needsAuth + " ממתינים לאישור Google";
        if (pending > 0) return pending + " ממתינים ליומן";
        if (failed > 0) return failed + " נכשלו ביומן";
        return "בעיה בשמירה ליומן";
    }

    public static String cardHeadline(boolean device) {
        return device ? "פגישה · יירשם ביומן המכשיר" : "פגישה · יירשם ביומן גוגל";
    }

    public static final String ENQUEUED_TOAST = "נשמר · נשלח ליומן";
    public static final String NOTIF_CHANNEL = "babisnap_calendar";
    public static final String NOTIF_TITLE_FAIL = "פגישה לא נשמרה ביומן";
    public static final String NOTIF_TITLE_AUTH = "צריך לאשר גישה ליומן";
    public static final String NOTIF_BODY_AUTH = "פתחו את BabiSnap ← התחברו ל-Google יומן. הפגישה ממתינה במכשיר.";

    private CalQueueLogic() {}
}
