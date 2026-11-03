package com.babisnap.app;

import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * On-device calendars via Calendar Provider (READ_CALENDAR / WRITE_CALENDAR).
 * No Microsoft Graph, MSAL, or network. A calendar synced by another app (Outlook included)
 * shows up here when that app has synced it to the phone. Email invites are not sent on this path.
 */
final class DeviceCalendar {
    static final String PREF_DEST = "cal_dest";
    static final String PREF_ID = "device_cal_id";
    static final String PREF_NAME = "device_cal_name";
    static final String PREF_ON = "device_cal_on";

    static final String ERR_PERMISSION = "אין גישה ליומן המכשיר. אשרו קריאה וכתיבה בהגדרות.";
    static final String ERR_UNAVAILABLE = "היעד הוא יומן המכשיר, אבל אין יומן זמין. בחרו יומן בהגדרות, או החזירו את היעד ל-Google.";
    static final String ERR_GONE = "היומן שנבחר לא זמין. בחרו יומן אחר בהגדרות.";
    static final String ERR_REJECTED = "היומן לא קיבל את האירוע.";
    static final String ERR_FAILED = "לא נרשם ביומן המכשיר.";

    static final class Cal {
        final long id;
        final String label;
        final boolean primary;

        Cal(long id, String label, boolean primary) {
            this.id = id;
            this.label = label;
            this.primary = primary;
        }
    }

    static boolean hasPermission(Context c) {
        return c.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
            && c.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED;
    }

    /** User finished connect (permission + a chosen calendar id). */
    static boolean isReady(Context c) {
        return hasPermission(c)
            && Core.prefs(c).getBoolean(PREF_ON, false)
            && Core.prefs(c).getLong(PREF_ID, -1L) > 0;
    }

    static DeviceCalendarLogic.Route route(Context c) {
        String pref = Core.prefs(c).getString(PREF_DEST, DeviceCalendarLogic.GOOGLE);
        boolean ready = false;
        // Google (the default) must not touch Calendar Provider.
        if (DeviceCalendarLogic.DEVICE.equals(pref)) {
            ready = isReady(c);
            if (ready) {
                try {
                    if (!stillWritable(c, Core.prefs(c).getLong(PREF_ID, -1L))) ready = false;
                } catch (SecurityException e) {
                    ready = false;
                } catch (Exception ignored) { /* insert reports a provider failure */ }
            }
        }
        return DeviceCalendarLogic.route(pref, ready);
    }

    static String savedName(Context c) {
        String n = Core.prefs(c).getString(PREF_NAME, "");
        return n == null || n.isEmpty() ? "יומן" : n;
    }

    /** Visible calendars the user can insert into. Primary first, then by label. */
    static List<Cal> listWritable(Context c) {
        if (!hasPermission(c)) throw new SecurityException("calendar");
        String[] proj = {
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Calendars.IS_PRIMARY
        };
        String sel = CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL + ">=? AND ("
            + CalendarContract.Calendars.VISIBLE + "=1 OR " + CalendarContract.Calendars.VISIBLE + " IS NULL)";
        String[] args = {Integer.toString(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR)};
        List<Cal> out = new ArrayList<>();
        try (Cursor cur = c.getContentResolver().query(CalendarContract.Calendars.CONTENT_URI, proj, sel, args, null)) {
            if (cur == null) return out;
            int iId = cur.getColumnIndexOrThrow(CalendarContract.Calendars._ID);
            int iName = cur.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME);
            int iAcct = cur.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_NAME);
            int iAccess = cur.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL);
            int iPrimary = cur.getColumnIndex(CalendarContract.Calendars.IS_PRIMARY);
            while (cur.moveToNext()) {
                int access = cur.isNull(iAccess) ? 0 : cur.getInt(iAccess);
                if (!DeviceCalendarLogic.writable(access)) continue;
                boolean primary = iPrimary >= 0 && !cur.isNull(iPrimary) && cur.getInt(iPrimary) == 1;
                out.add(new Cal(cur.getLong(iId),
                    DeviceCalendarLogic.calendarLabel(cur.getString(iName), cur.getString(iAcct)), primary));
            }
        }
        out.sort((a, b) -> {
            if (a.primary != b.primary) return a.primary ? -1 : 1;
            return a.label.compareToIgnoreCase(b.label);
        });
        return out;
    }

    static boolean stillWritable(Context c, long id) {
        for (Cal cal : listWritable(c)) if (cal.id == id) return true;
        return false;
    }

    /**
     * Inserts one event into the selected calendar. Null = saved. Hebrew title, location and description
     * are passed through as Java strings; Calendar Provider stores them as UTF-8. No attendee rows and
     * no mail: a synced calendar may upload the event, but BabiSnap does not email guests from here.
     */
    static String insert(Context c, JSONObject p, String spoken) {
        if (!hasPermission(c)) return ERR_PERMISSION;
        long id = Core.prefs(c).getLong(PREF_ID, -1L);
        if (!Core.prefs(c).getBoolean(PREF_ON, false) || id <= 0) return ERR_UNAVAILABLE;
        try {
            if (!stillWritable(c, id)) return ERR_GONE;
            DeviceCalendarLogic.Event ev = DeviceCalendarLogic.event(
                p.optString("title", ""), p.getString("startStr"), p.optInt("durationMin", 60),
                p.optBoolean("allDay", false), p.optString("location", ""), spoken);
            ContentValues v = new ContentValues();
            v.put(CalendarContract.Events.CALENDAR_ID, id);
            v.put(CalendarContract.Events.TITLE, ev.title);
            v.put(CalendarContract.Events.DESCRIPTION, ev.description);
            if (!ev.location.isEmpty()) v.put(CalendarContract.Events.EVENT_LOCATION, ev.location);
            v.put(CalendarContract.Events.DTSTART, ev.startMillis);
            v.put(CalendarContract.Events.DTEND, ev.endMillis);
            v.put(CalendarContract.Events.EVENT_TIMEZONE, ev.timeZone);
            if (!ev.allDay) v.put(CalendarContract.Events.EVENT_END_TIMEZONE, ev.timeZone);
            v.put(CalendarContract.Events.ALL_DAY, ev.allDay ? 1 : 0);
            v.put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CONFIRMED);
            v.put(CalendarContract.Events.HAS_ATTENDEE_DATA, 0);
            v.put(CalendarContract.Events.HAS_ALARM, 0);
            v.put(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY);
            Uri uri = c.getContentResolver().insert(CalendarContract.Events.CONTENT_URI, v);
            return uri == null ? ERR_REJECTED : null;
        } catch (SecurityException e) {
            return ERR_PERMISSION;
        } catch (Exception e) {
            return ERR_FAILED;
        }
    }

    private DeviceCalendar() {}
}
