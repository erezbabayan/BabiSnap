package com.babisnap.app;

import java.util.regex.Pattern;

/** Builds the BabiTk voice-ingest body (validated + truncated to the server limits) and decides what to do with responses. Pure Java, unit-testable. */
public final class BabiTkJson {
    public static final String URL = PairLink.ENDPOINT;

    // Server limits (babitk supabase/functions/_shared/device-voice-ingest.ts). Lengths are UTF-16 units, like JS .length.
    public static final int TITLE_MAX = 200, NOTES_MAX = 4000, LOCATION_MAX = 300, CLIENT_ID_MAX = 128;
    static final Pattern DATE_RE = Pattern.compile("^(\\d{4})-(\\d{2})-(\\d{2})$");
    static final Pattern TIME_RE = Pattern.compile("^(\\d{2}):(\\d{2})$");
    static final Pattern CLIENT_ID_RE = Pattern.compile("^[A-Za-z0-9._:-]+$");
    static final String FALLBACK_TITLE = "פריט קולי";

    /** dueDate "YYYY-MM-DD" or null, dueTime "HH:MM" or null; invalid date/time are dropped, long text is cut, empty optional fields are omitted. */
    public static String build(String title, String dueDate, String dueTime, String notes, String location, String type, String clientId) {
        String t = clip(title, TITLE_MAX); if (t.isEmpty()) t = FALLBACK_TITLE;
        String d = validDate(dueDate) ? dueDate : null;
        String tm = validTime(dueTime) ? dueTime : null; // time-only = today on the server
        String n = clip(notes, NOTES_MAX), loc = clip(location, LOCATION_MAX);
        String ty = "note".equals(type) ? "note" : "task";
        String id = validClientId(clientId) ? clientId : newClientId();
        StringBuilder sb = new StringBuilder("{\"title\":").append(EventJson.q(t));
        if (d != null) sb.append(",\"due_date\":").append(EventJson.q(d));
        if (tm != null) sb.append(",\"due_time\":").append(EventJson.q(tm));
        if (!n.isEmpty()) sb.append(",\"notes\":").append(EventJson.q(n));
        if (!loc.isEmpty()) sb.append(",\"location\":").append(EventJson.q(loc));
        sb.append(",\"source\":\"voice\",\"client_id\":").append(EventJson.q(id));
        sb.append(",\"type\":").append(EventJson.q(ty)).append(",\"board\":\"notebook\"}");
        return sb.toString();
    }

    /** Trims and cuts to max UTF-16 units without splitting a surrogate pair (emoji); result also fits after the server's own trim. */
    public static String clip(String s, int max) {
        if (s == null) return "";
        String t = s.trim();
        if (t.length() <= max) return t;
        int end = max;
        if (Character.isHighSurrogate(t.charAt(end - 1))) end--;
        return t.substring(0, end).trim();
    }

    public static boolean validDate(String s) {
        if (s == null) return false;
        java.util.regex.Matcher m = DATE_RE.matcher(s);
        if (!m.matches()) return false;
        int y = Integer.parseInt(m.group(1)), mo = Integer.parseInt(m.group(2)), d = Integer.parseInt(m.group(3));
        try { java.time.LocalDate.of(y, mo, d); return true; } catch (Exception e) { return false; }
    }
    public static boolean validTime(String s) {
        if (s == null) return false;
        java.util.regex.Matcher m = TIME_RE.matcher(s);
        return m.matches() && Integer.parseInt(m.group(1)) <= 23 && Integer.parseInt(m.group(2)) <= 59;
    }
    public static boolean validClientId(String s) { return s != null && s.length() <= CLIENT_ID_MAX && CLIENT_ID_RE.matcher(s).matches(); }
    /** One per recording; server de-duplicates on it, so retries never create duplicates. */
    public static String newClientId() { return "bsnap-" + java.util.UUID.randomUUID(); }

    /** What to do with a queued item after a response. */
    public enum Outcome {
        DONE,          // 2xx: remove from queue
        UNAUTHORIZED,  // 401: token missing/invalid/revoked -> wipe token, keep queue, wait for re-pair
        RATE_LIMITED,  // 429: stop this run, retry later with exponential backoff (Retry-After if given)
        FAILED,        // 400 / other 4xx: payload rejected -> mark failed (visible), never retried automatically
        RETRY          // 5xx / network: WorkManager exponential retry
    }
    public static Outcome outcome(int code) {
        if (code / 100 == 2) return Outcome.DONE;
        if (code == 401) return Outcome.UNAUTHORIZED;
        if (code == 429) return Outcome.RATE_LIMITED;
        if (code >= 500 || code <= 0 || code == 408 || code == 403) return Outcome.RETRY; // 403/408: gateway/transient, not our payload
        return Outcome.FAILED;
    }

    static final long BACKOFF_MIN_S = 60, BACKOFF_MAX_S = 3600; // server window is 1 hour
    /**
     * Delay before the next attempt after the n-th consecutive 429 (n starts at 1).
     * Retry-After (seconds or HTTP-date) wins when present, clamped to [30s, 1h]; otherwise 60s * 2^(n-1), capped at 1h.
     */
    public static long backoffSeconds(int n, String retryAfter, long nowMs) {
        Long ra = parseRetryAfter(retryAfter, nowMs);
        if (ra != null) return Math.max(30, Math.min(BACKOFF_MAX_S, ra));
        int k = Math.max(0, Math.min(n - 1, 10));
        return Math.min(BACKOFF_MAX_S, BACKOFF_MIN_S << k);
    }
    static Long parseRetryAfter(String v, long nowMs) {
        if (v == null || v.trim().isEmpty()) return null;
        String s = v.trim();
        if (s.matches("\\d{1,7}")) return Long.parseLong(s);
        try {
            long at = java.time.ZonedDateTime.parse(s, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli();
            return Math.max(0, (at - nowMs + 999) / 1000);
        } catch (Exception e) { return null; }
    }

    /** Server error body {"ok":false,"error":{"code":..,"message":..}} -> short readable text (no secrets are ever in it). */
    public static String errorText(int code, String body) {
        String msg = body == null ? "" : body;
        java.util.regex.Matcher m = Pattern.compile("\"message\"\\s*:\\s*\"([^\"]{0,200})\"").matcher(msg);
        String s = m.find() ? m.group(1) : msg.replaceAll("\\s+", " ").trim();
        if (s.length() > 160) s = s.substring(0, 160);
        return "HTTP " + code + (s.isEmpty() ? "" : " – " + s);
    }
    private BabiTkJson() {}
}
