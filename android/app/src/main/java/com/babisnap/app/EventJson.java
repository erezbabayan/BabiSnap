package com.babisnap.app;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Builds the Google Calendar API event body. Pure Java (no org.json) so it is unit-testable. */
public final class EventJson {
    public static final String TZ = "Asia/Jerusalem";
    private static final DateTimeFormatter IN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter OUT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    public static String build(String title, String startStr, int durationMin, String location, String description) {
        LocalDateTime start = LocalDateTime.parse(startStr, IN);
        LocalDateTime end = start.plusMinutes(durationMin);
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"summary\":").append(q(title));
        if (location != null && !location.isEmpty()) sb.append(",\"location\":").append(q(location));
        if (description != null) sb.append(",\"description\":").append(q(description));
        sb.append(",\"start\":{\"dateTime\":").append(q(start.format(OUT))).append(",\"timeZone\":").append(q(TZ)).append("}");
        sb.append(",\"end\":{\"dateTime\":").append(q(end.format(OUT))).append(",\"timeZone\":").append(q(TZ)).append("}");
        return sb.append("}").toString();
    }

    /** All-day event (v2.14): start.date = day, end.date = next day (exclusive), no dateTime. startStr may carry a time; it is ignored. */
    public static String buildAllDay(String title, String startStr, String location, String description) {
        java.time.LocalDate day = java.time.LocalDate.parse(startStr.substring(0, 10));
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"summary\":").append(q(title));
        if (location != null && !location.isEmpty()) sb.append(",\"location\":").append(q(location));
        if (description != null) sb.append(",\"description\":").append(q(description));
        sb.append(",\"start\":{\"date\":").append(q(day.toString())).append("}");
        sb.append(",\"end\":{\"date\":").append(q(day.plusDays(1).toString())).append("}");
        return sb.append("}").toString();
    }

    /** Body of the attendee PATCH: {"attendees":[{"email":"a@b"}, ...]} (stage-2 invites). */
    public static String attendees(java.util.List<String> emails) {
        StringBuilder sb = new StringBuilder("{\"attendees\":[");
        for (int i = 0; i < emails.size(); i++) sb.append(i == 0 ? "" : ",").append("{\"email\":").append(q(emails.get(i))).append("}");
        return sb.append("]}").toString();
    }

    static String q(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default: if (c < 0x20) b.append(String.format("\\u%04x", (int) c)); else b.append(c);
            }
        }
        return b.append('"').toString();
    }
    private EventJson() {}
}
