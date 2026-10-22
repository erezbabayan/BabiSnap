package com.babisnap.app;

/** Builds the BabiTk voice-ingest body. Pure Java, unit-testable. */
public final class BabiTkJson {
    public static final String URL = "https://ghibfuinantybqidwadj.supabase.co/functions/v1/voice-ingest";

    /** dueDate "YYYY-MM-DD" or null, dueTime "HH:MM" or null; empty optional fields are omitted. */
    public static String build(String title, String dueDate, String dueTime, String notes, String location, String type, String clientId) {
        StringBuilder sb = new StringBuilder("{\"title\":").append(EventJson.q(title));
        if (dueDate != null && !dueDate.isEmpty()) sb.append(",\"due_date\":").append(EventJson.q(dueDate));
        if (dueTime != null && !dueTime.isEmpty()) sb.append(",\"due_time\":").append(EventJson.q(dueTime));
        if (notes != null && !notes.isEmpty()) sb.append(",\"notes\":").append(EventJson.q(notes));
        if (location != null && !location.isEmpty()) sb.append(",\"location\":").append(EventJson.q(location));
        sb.append(",\"source\":\"voice\",\"client_id\":").append(EventJson.q(clientId));
        sb.append(",\"type\":").append(EventJson.q(type)).append(",\"board\":\"notebook\"}");
        return sb.toString();
    }

    /** What to do with a queued item after a response. */
    public enum Outcome { DONE, DROP, WAIT_TOKEN, RETRY }
    public static Outcome outcome(int code) {
        if (code == 200 || code == 201) return Outcome.DONE;
        if (code == 401 || code == 403) return Outcome.WAIT_TOKEN;
        if (code == 429 || code >= 500 || code <= 0) return Outcome.RETRY;
        return Outcome.DROP; // 400 and other 4xx: payload rejected, retrying will not help
    }
    private BabiTkJson() {}
}
