package com.babisnap.app;

import java.util.Locale;

/** Summary text shown in the 3-second undo card. Pure Java so it is unit-testable. */
public final class CardText {
    /** The card is 300dp wide; longer places are clipped here only (the full text still goes to the calendar / BabiTk). */
    public static final int LOCATION_CARD_MAX = 80;

    public static String meeting(String title, int d, int m, int y, int hour, int minute, boolean timeDefaulted, String location) {
        return meeting(title, d, m, y, hour, minute, timeDefaulted, false, location);
    }

    public static String meeting(String title, int d, int m, int y, int hour, int minute, boolean timeDefaulted, boolean allDay, String location) {
        StringBuilder sum = new StringBuilder(title == null ? "" : title);
        sum.append("\n📅 ").append(d).append("/").append(m).append("/").append(y)
            .append(allDay ? " · כל היום" : String.format(Locale.US, " · %02d:%02d", hour, minute));
        if (timeDefaulted) sum.append(" (לא נאמרה שעה)");
        return sum.append(locationLine(location)).toString();
    }

    /** Task / note card. {@code hasDate=false} shows "ללא תאריך"; {@code time} may be null. */
    public static String saved(String title, boolean hasDate, int d, int m, int y, String time, String location) {
        StringBuilder sum = new StringBuilder(title == null ? "" : title);
        if (hasDate) sum.append("\n📅 ").append(d).append("/").append(m).append("/").append(y).append(time != null ? " · " + time : "");
        else sum.append("\nללא תאריך");
        return sum.append(locationLine(location)).toString();
    }

    /** "\n📍 place" or "" when there is no place. */
    public static String locationLine(String location) {
        if (location == null) return "";
        String l = location.trim().replaceAll("\\s+", " ");
        if (l.isEmpty()) return "";
        if (l.codePointCount(0, l.length()) > LOCATION_CARD_MAX)
            l = l.substring(0, l.offsetByCodePoints(0, LOCATION_CARD_MAX - 1)).trim() + "…";
        return "\n📍 " + l;
    }

    private CardText() {}
}
