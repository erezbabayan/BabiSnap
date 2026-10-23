package com.babisnap.app;

/**
 * Pure decisions for the undo card (no Android types, unit-tested).
 *
 * 2.4 bug: after the 3s undo window the meeting card waited for Google authorization AND the Calendar insert
 * (up to ~35s per attempt, longer on the 401 re-auth path, forever if the auth task never returned) with the
 * bar drained and no ביטול. Now the card never waits for the network: it closes right after the window
 * (at most CLOSE_GRACE_MS later while authorization resolves) and the insert finishes in the background.
 */
final class CardFlow {
    static final long UNDO_MS = 3000;
    /** After the undo window the card stays at most this long (only while Google authorization is still resolving). */
    static final long CLOSE_GRACE_MS = 2000;

    enum AfterAuth {
        /** token in hand: insert in the background, close the card now */
        SEND_IN_BACKGROUND_AND_CLOSE,
        /** Google needs the user's consent and the card is still on screen: show Google's consent screen */
        ASK_CONSENT,
        /** consent needed but the card already closed: just tell the user (never pop UI over another app) */
        TELL_CONSENT_NEEDED
    }

    static AfterAuth afterAuth(boolean needsConsent, boolean cardOpen) {
        if (!needsConsent) return AfterAuth.SEND_IN_BACKGROUND_AND_CLOSE;
        return cardOpen ? AfterAuth.ASK_CONSENT : AfterAuth.TELL_CONSENT_NEEDED;
    }

    /** Latest moment (ms after the card appeared) the card can still be on screen without the user touching it. */
    static long latestAutoCloseMs(boolean meeting) { return UNDO_MS + (meeting ? CLOSE_GRACE_MS : 0); }

    /** A 401 gets one silent retry with a fresh token. */
    static boolean retryWithFreshToken(int code, boolean alreadyRetried) { return code == 401 && !alreadyRetried; }

    /** Toast after the background insert (null = success, the card already said "נרשם"). */
    static String resultMessage(int code) {
        if (code / 100 == 2) return null;
        if (code == 401 || code == 403) return "לא נרשם ביומן: צריך לאשר שוב גישה ליומן (BabiSnap ← הגדרות)";
        return "שגיאה מיומן גוגל (" + code + ") – לא נרשם";
    }

    static final String CONSENT_NEEDED = "לא נרשם ביומן: צריך לאשר גישה ליומן – פתחו את BabiSnap והקליטו שוב";
    static String networkMessage(String err) { return "לא נרשם ביומן – אין חיבור לאינטרנט? " + (err == null ? "" : err); }

    private CardFlow() {}
}
