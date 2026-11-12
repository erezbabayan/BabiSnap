package com.babisnap.app;

/**
 * Stage-1 WhatsApp share. Pure decisions, no Android types.
 * Off by default. One destination: a channel word never also writes the calendar or BabiTk.
 * "both" shows two buttons; the user sends one of them, never both.
 * No Green-API and no WhatsApp Business API — the device app is opened with a share Intent.
 */
public final class WhatsAppLogic {
    public static final boolean DEFAULT_ENABLED = false;
    public static final String PREF_ENABLED = "wa_enabled";
    public static final String PREF_MODE = "wa_mode";

    public static final String MODE_VOICE = "voice";
    public static final String MODE_TRANSCRIPT = "transcript";
    public static final String MODE_BOTH = "both";
    public static final String DEFAULT_MODE = MODE_BOTH;

    /** Consumer app, then the installed WhatsApp Business app. Not the Cloud API. */
    public static final String PKG = "com.whatsapp";
    public static final String PKG_BUSINESS = "com.whatsapp.w4b";

    public static final String[] MODE_VALUES = {MODE_BOTH, MODE_VOICE, MODE_TRANSCRIPT};
    public static final String[] MODE_LABELS = {"קול או תמלול", "קול", "תמלול"};

    public enum Mode { VOICE, TRANSCRIPT, BOTH }

    /** Which send buttons to show. A click sends exactly one payload. */
    public static final class Buttons {
        public final boolean voice;
        public final boolean transcript;
        public Buttons(boolean voice, boolean transcript) { this.voice = voice; this.transcript = transcript; }
    }

    public static Mode mode(String stored) {
        if (MODE_VOICE.equals(stored)) return Mode.VOICE;
        if (MODE_TRANSCRIPT.equals(stored)) return Mode.TRANSCRIPT;
        return Mode.BOTH;
    }

    public static String modeValue(Mode mode) {
        if (mode == Mode.VOICE) return MODE_VOICE;
        if (mode == Mode.TRANSCRIPT) return MODE_TRANSCRIPT;
        return MODE_BOTH;
    }

    public static int modeIndex(Mode mode) {
        String v = modeValue(mode);
        for (int i = 0; i < MODE_VALUES.length; i++) if (MODE_VALUES[i].equals(v)) return i;
        return 0;
    }

    public static String modeLabel(Mode mode) { return MODE_LABELS[modeIndex(mode)]; }

    /** What happens after the private recipient is known. Groups stay on the text picker. */
    public enum AfterMatch { TEXT_CARD, RECORD, CHOICE_CARD }

    /** Voice-only records first. Both shows the choice card. Transcript goes straight to the text card. */
    public static AfterMatch afterMatch(Mode mode) {
        if (mode == Mode.VOICE) return AfterMatch.RECORD;
        if (mode == Mode.BOTH) return AfterMatch.CHOICE_CARD;
        return AfterMatch.TEXT_CARD;
    }

    /** The 5s countdown sends the transcript, except a finished voice take which sends the file. */
    public static boolean countdownSendsTranscript(Mode mode, boolean voiceFileReady) {
        if (mode == Mode.VOICE && voiceFileReady) return false;
        return true;
    }

    /** A missing or empty recording is not a dead end: the transcript is what gets shared. */
    public static boolean fallbackToTranscript(boolean voiceFileReady) { return !voiceFileReady; }

    public static final String BTN_SEND_TEXT = "שלח תמלול";
    public static final String BTN_RECORD = "הקלט קול";
    public static final String BTN_STOP = "עצור";
    public static final String RECORD_FAILED = "ההקלטה לא נשמרה. נפתח את התמלול במקום";

    /** Quiet stretch that ends a voice take, and the hard cap. Not the speech-recognizer silence. */
    public static final long VOICE_SILENCE_MS = 2000L;
    public static final long VOICE_CAP_MS = 60000L;
    /** MediaRecorder max amplitude at or above this counts as speech. Below it, silence accumulates. */
    public static final int VOICE_QUIET = 500;

    public static boolean voiceShouldStop(long elapsedMs, long quietMs, boolean tappedStop) {
        if (tappedStop) return true;
        if (elapsedMs >= VOICE_CAP_MS) return true;
        return quietMs >= VOICE_SILENCE_MS;
    }

    /** Reset the quiet clock when the mic hears something. Otherwise add the poll interval. */
    public static long quietAfter(long quietMs, int amplitude, long deltaMs) {
        if (amplitude >= VOICE_QUIET) return 0L;
        long next = quietMs + Math.max(0L, deltaMs);
        return next < 0 ? Long.MAX_VALUE : next;
    }

    public static String modeHint(Mode mode) {
        if (mode == Mode.VOICE)
            return "אחרי בחירת הנמען מוקלט קול. הקובץ נפתח כקובץ שמע, לא כהודעה קולית. השליחה רק בלחיצה על שלח בוואטסאפ";
        if (mode == Mode.TRANSCRIPT)
            return "הטקסט נפתח מוכן בצ'אט של הנמען. השליחה רק בלחיצה על שלח בוואטסאפ";
        return "שני כפתורים: «שלח תמלול» ו«הקלט קול». בלי בחירה נשלח התמלול. קול מגיע כקובץ שמע, לא כהודעה קולית. השליחה רק בלחיצה על שלח בוואטסאפ";
    }

    public static boolean wantsVoice(Mode mode) { return mode == Mode.VOICE || mode == Mode.BOTH; }
    public static boolean wantsTranscript(Mode mode) { return mode == Mode.TRANSCRIPT || mode == Mode.BOTH; }

    /**
     * Voice-only with no file still offers the transcript, so a failed capture is not a dead end.
     * Transcript-only never offers voice. Both offers only the payloads that exist.
     */
    public static Buttons buttons(Mode mode, boolean hasVoice, boolean hasText) {
        boolean voice = wantsVoice(mode) && hasVoice;
        boolean text = wantsTranscript(mode) && hasText;
        if (mode == Mode.VOICE && !hasVoice && hasText) text = true;
        if (mode == Mode.TRANSCRIPT) voice = false;
        return new Buttons(voice, text);
    }

    /** Review window from the transcript the user sees. Short / medium / long ≈ 8 / 15 / 25 seconds. */
    public static int confirmSeconds(String transcript) {
        int n = transcript == null ? 0 : transcript.trim().length();
        if (n <= 24) return 8;
        if (n <= 80) return 15;
        return 25;
    }

    public static long confirmMs(String transcript) { return confirmSeconds(transcript) * 1000L; }

    /** Text on the card. Channel word is already gone. Empty speech still has a voice message. */
    public static String preview(String stripped) {
        if (stripped == null || stripped.trim().isEmpty()) return "הודעת קול";
        return stripped.trim();
    }

    /** Installed package to target. Consumer app wins. Null when neither app is installed. */
    public static String packageName(boolean consumerInstalled, boolean businessAppInstalled) {
        if (consumerInstalled) return PKG;
        if (businessAppInstalled) return PKG_BUSINESS;
        return null;
    }

    /** Opus in ogg from API 29. Older devices fall back to m4a. */
    public static boolean useOpus(int sdkInt) { return sdkInt >= 29; }
    public static String audioExt(int sdkInt) { return useOpus(sdkInt) ? "ogg" : "m4a"; }
    public static String audioMime(int sdkInt) { return useOpus(sdkInt) ? "audio/ogg" : "audio/mp4"; }

    private WhatsAppLogic() {}
}
