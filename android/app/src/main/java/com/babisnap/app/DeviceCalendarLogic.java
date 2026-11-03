package com.babisnap.app;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Pure decisions for writing a meeting into an Android calendar (Calendar Provider).
 * No Android types. Google stays the destination until the user picks the device calendar.
 * Labels are the calendar's own name; this class never adds "personal" / "work" wording.
 */
public final class DeviceCalendarLogic {
    public static final String GOOGLE = "google";
    public static final String DEVICE = "device";
    /** {@code CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR}: can insert events. */
    public static final int CONTRIBUTOR = 500;

    public enum Route { GOOGLE, DEVICE, DEVICE_UNAVAILABLE }

    /**
     * Anything other than an explicit device choice stays on Google, including when the device
     * calendar is already connected. A device choice that is not usable does not fall back silently.
     */
    public static Route route(String pref, boolean deviceReady) {
        if (!DEVICE.equals(pref)) return Route.GOOGLE;
        return deviceReady ? Route.DEVICE : Route.DEVICE_UNAVAILABLE;
    }

    /** Disconnecting the device calendar returns new meetings to Google. */
    public static String destinationAfterDisconnect() { return GOOGLE; }

    public static boolean writable(int accessLevel) { return accessLevel >= CONTRIBUTOR; }

    /** Display name, with the account appended only so two calendars can be told apart. */
    public static String calendarLabel(String displayName, String accountName) {
        String name = displayName == null ? "" : displayName.trim();
        String acct = accountName == null ? "" : accountName.trim();
        if (name.isEmpty()) return acct.isEmpty() ? "יומן" : acct;
        if (acct.isEmpty() || name.equals(acct) || name.contains(acct)) return name;
        return name + " · " + acct;
    }

    public static final class Event {
        public final String title, location, description, timeZone;
        public final boolean allDay;
        public final long startMillis, endMillis;

        Event(String title, String location, String description, String timeZone, boolean allDay, long startMillis, long endMillis) {
            this.title = title;
            this.location = location;
            this.description = description;
            this.timeZone = timeZone;
            this.allDay = allDay;
            this.startMillis = startMillis;
            this.endMillis = endMillis;
        }
    }

    private static final DateTimeFormatter IN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final ZoneId JERUSALEM = ZoneId.of("Asia/Jerusalem");

    /**
     * Timed events use Asia/Jerusalem. All-day events use UTC midnight, which is what Calendar Provider
     * requires ({@code ALL_DAY=1} and {@code EVENT_TIMEZONE=UTC}). {@code startStr} for an all-day event
     * may still carry a time; only the date is used. A non-positive duration becomes 60 minutes so the
     * provider accepts the row. Title and description are kept as Java strings (Hebrew included).
     */
    public static Event event(String title, String startStr, int durationMin, boolean allDay, String location, String spoken) {
        String t = title == null ? "" : title;
        String loc = location == null ? "" : location.trim();
        String desc = "נוצר בהכתבה: " + (spoken == null ? "" : spoken);
        if (allDay) {
            LocalDate day = LocalDate.parse(startStr.substring(0, 10));
            long s = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
            long e = day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
            return new Event(t, loc, desc, "UTC", true, s, e);
        }
        int mins = durationMin < 1 ? 60 : durationMin;
        LocalDateTime start = LocalDateTime.parse(startStr, IN);
        long s = start.atZone(JERUSALEM).toInstant().toEpochMilli();
        long e = start.plusMinutes(mins).atZone(JERUSALEM).toInstant().toEpochMilli();
        return new Event(t, loc, desc, "Asia/Jerusalem", false, s, e);
    }

    private DeviceCalendarLogic() {}
}
