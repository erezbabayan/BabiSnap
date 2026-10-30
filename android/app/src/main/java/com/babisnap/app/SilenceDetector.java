package com.babisnap.app;

/** Decides when to end a recording ourselves (some Samsung/Google recognizers never send onResults). Pure Java, testable. */
public final class SilenceDetector {
    public static final long SILENCE_MS = 5000, HARD_CAP_MS = 60000;
    public enum Action { WAIT, FINISH_SILENCE, FINISH_CAP }
    private final long start; private String last = ""; private long lastChange;

    public SilenceDetector(long nowMs) { start = nowMs; lastChange = nowMs; }

    /** Feed each partial result; only a changed non-empty text resets the silence clock. */
    public void partial(String text, long nowMs) {
        String t = text == null ? "" : text.trim();
        if (!t.isEmpty() && !t.equals(last)) { last = t; lastChange = nowMs; }
    }
    public String lastText() { return last; }

    public Action check(long nowMs) {
        if (nowMs - start >= HARD_CAP_MS) return Action.FINISH_CAP;
        if (!last.isEmpty() && nowMs - lastChange >= SILENCE_MS) return Action.FINISH_SILENCE;
        return Action.WAIT;
    }

    /** Final results win when non-empty; otherwise the latest partial. */
    public static String pick(java.util.List<String> finals, String lastPartial) {
        if (finals != null && !finals.isEmpty() && finals.get(0) != null && !finals.get(0).trim().isEmpty()) return finals.get(0).trim();
        return lastPartial == null ? "" : lastPartial.trim();
    }
}
