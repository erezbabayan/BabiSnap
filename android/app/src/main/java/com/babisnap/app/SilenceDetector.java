package com.babisnap.app;

/** Decides when to end a recording ourselves (some Samsung/Google recognizers never send onResults). Pure Java, testable. */
public final class SilenceDetector {
    public static final long SILENCE_MS = 5000, CALL_SILENCE_MS = 1500, HARD_CAP_MS = 60000;
    public enum Action { WAIT, FINISH_SILENCE, FINISH_CAP }
    private final long start; private String last = ""; private long lastChange;

    public SilenceDetector(long nowMs) { start = nowMs; lastChange = nowMs; }

    /** Feed each partial result; only a changed non-empty text resets the silence clock. */
    public void partial(String text, long nowMs) {
        String t = text == null ? "" : text.trim();
        if (!t.isEmpty() && !t.equals(last)) { last = t; lastChange = nowMs; }
    }
    public String lastText() { return last; }

    /**
     * A call command at the start of the partial uses {@link #CALL_SILENCE_MS}. Everything else stays
     * at {@link #SILENCE_MS}. The 60s cap does not change. The recognizer's own silence extras are fixed
     * for the session, so this timer is what actually stops a call recording early.
     */
    public Action check(long nowMs) {
        if (nowMs - start >= HARD_CAP_MS) return Action.FINISH_CAP;
        long wait = CallDial.shortListen(last) ? CALL_SILENCE_MS : SILENCE_MS;
        if (!last.isEmpty() && nowMs - lastChange >= wait) return Action.FINISH_SILENCE;
        return Action.WAIT;
    }

    /** Final results win when non-empty; otherwise the latest partial. */
    public static String pick(java.util.List<String> finals, String lastPartial) {
        java.util.List<String> all = hypotheses(finals, lastPartial);
        return all.isEmpty() ? "" : all.get(0);
    }

    /** Every non-empty hypothesis, primary first, at most five. Duplicates are dropped. */
    public static java.util.List<String> hypotheses(java.util.List<String> finals, String lastPartial) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (finals != null) for (String s : finals) addHeard(out, s);
        addHeard(out, lastPartial);
        if (out.size() > 5) return new java.util.ArrayList<>(out.subList(0, 5));
        return out;
    }

    private static void addHeard(java.util.List<String> out, String raw) {
        if (out.size() >= 5 || raw == null) return;
        String t = raw.trim();
        if (t.isEmpty() || out.contains(t)) return;
        out.add(t);
    }
}
