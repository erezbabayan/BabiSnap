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
