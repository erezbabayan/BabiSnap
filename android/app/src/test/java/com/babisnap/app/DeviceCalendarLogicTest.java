package com.babisnap.app;

import static org.junit.Assert.*;

import org.junit.Test;

import java.time.Instant;
import java.util.Locale;

public class DeviceCalendarLogicTest {
    @Test public void googleStaysDefaultUntilDeviceIsChosen() {
        assertEquals(DeviceCalendarLogic.Route.GOOGLE, DeviceCalendarLogic.route(null, false));
        assertEquals(DeviceCalendarLogic.Route.GOOGLE, DeviceCalendarLogic.route(null, true));
        assertEquals(DeviceCalendarLogic.Route.GOOGLE, DeviceCalendarLogic.route("", true));
        assertEquals(DeviceCalendarLogic.Route.GOOGLE, DeviceCalendarLogic.route(DeviceCalendarLogic.GOOGLE, true));
        assertEquals(DeviceCalendarLogic.Route.GOOGLE, DeviceCalendarLogic.route("graph", true));
        assertEquals(DeviceCalendarLogic.Route.GOOGLE, DeviceCalendarLogic.route("microsoft", false));
    }

    @Test public void deviceChoiceDoesNotSilentlyFallBack() {
        assertEquals(DeviceCalendarLogic.Route.DEVICE, DeviceCalendarLogic.route(DeviceCalendarLogic.DEVICE, true));
        assertEquals(DeviceCalendarLogic.Route.DEVICE_UNAVAILABLE, DeviceCalendarLogic.route(DeviceCalendarLogic.DEVICE, false));
    }

    @Test public void disconnectReturnsToGoogle() {
        assertEquals(DeviceCalendarLogic.GOOGLE, DeviceCalendarLogic.destinationAfterDisconnect());
    }

    @Test public void onlyContributorAndAboveAreWritable() {
        assertFalse(DeviceCalendarLogic.writable(0));
        assertFalse(DeviceCalendarLogic.writable(100));
        assertFalse(DeviceCalendarLogic.writable(200));
        assertFalse(DeviceCalendarLogic.writable(300));
        assertFalse(DeviceCalendarLogic.writable(400));
        assertTrue(DeviceCalendarLogic.writable(500));
        assertTrue(DeviceCalendarLogic.writable(700));
        assertTrue(DeviceCalendarLogic.writable(800));
    }

    @Test public void labelUsesTheCalendarNameAndDoesNotInventPersonalOrWork() {
        String labeled = DeviceCalendarLogic.calendarLabel("Calendar", "erez@contoso.com");
        assertEquals("Calendar · erez@contoso.com", labeled);
        String low = labeled.toLowerCase(Locale.ROOT);
        assertFalse(labeled.contains("אישי"));
        assertFalse(labeled.contains("עבודה"));
        assertFalse(low.contains("personal"));
        assertFalse(low.contains("work"));
        assertEquals("היומן שלי", DeviceCalendarLogic.calendarLabel("היומן שלי", "היומן שלי"));
        assertEquals("Erez (erez@contoso.com)", DeviceCalendarLogic.calendarLabel("Erez (erez@contoso.com)", "erez@contoso.com"));
        assertEquals("יומן", DeviceCalendarLogic.calendarLabel("  ", null));
        assertEquals("erez@contoso.com", DeviceCalendarLogic.calendarLabel(null, "erez@contoso.com"));
    }

    @Test public void timedEventUsesJerusalemAndKeepsHebrew() {
        DeviceCalendarLogic.Event e = DeviceCalendarLogic.event(
            "פגישה עם דני", "2026-11-03 14:00", 60, false, "קפה לנדוור", "פגישה עם דני מחר ב-14:00");
        assertEquals("פגישה עם דני", e.title);
        assertEquals("קפה לנדוור", e.location);
        assertEquals("נוצר בהכתבה: פגישה עם דני מחר ב-14:00", e.description);
        assertEquals("Asia/Jerusalem", e.timeZone);
        assertFalse(e.allDay);
        assertEquals(Instant.parse("2026-11-03T12:00:00Z").toEpochMilli(), e.startMillis);
        assertEquals(Instant.parse("2026-11-03T13:00:00Z").toEpochMilli(), e.endMillis);
        assertTrue(e.title.indexOf('פ') >= 0);
        assertFalse(e.title.contains("\\u"));
    }

    @Test public void summerTimeIsUtcPlusThree() {
        DeviceCalendarLogic.Event e = DeviceCalendarLogic.event("קיץ", "2026-07-03 14:00", 30, false, null, "x");
        assertEquals("", e.location);
        assertEquals(Instant.parse("2026-07-03T11:00:00Z").toEpochMilli(), e.startMillis);
        assertEquals(Instant.parse("2026-07-03T11:30:00Z").toEpochMilli(), e.endMillis);
    }

    @Test public void allDayIsUtcMidnightToNextMidnight() {
        DeviceCalendarLogic.Event e = DeviceCalendarLogic.event("יום גיבוש", "2026-11-03 09:00", 60, true, "", "יום שלם");
        assertTrue(e.allDay);
        assertEquals("UTC", e.timeZone);
        assertEquals("יום גיבוש", e.title);
        assertEquals(Instant.parse("2026-11-03T00:00:00Z").toEpochMilli(), e.startMillis);
        assertEquals(Instant.parse("2026-11-04T00:00:00Z").toEpochMilli(), e.endMillis);
    }

    @Test public void nonPositiveDurationBecomesOneHour() {
        DeviceCalendarLogic.Event e = DeviceCalendarLogic.event("קצר", "2026-11-03 14:00", 0, false, "", "x");
        assertEquals(Instant.parse("2026-11-03T13:00:00Z").toEpochMilli(), e.endMillis);
    }
}
