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

    public static String modeHint(Mode mode) {
        if (mode == Mode.VOICE) return "הודעת קול בלבד";
        if (mode == Mode.TRANSCRIPT) return "הטקסט בלבד";
        return "שני כפתורים — נשלח רק מה שבוחרים";
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
