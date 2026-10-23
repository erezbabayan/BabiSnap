package com.babisnap.app;

import java.util.ArrayList;
import java.util.List;

/**
 * Stage-2 invite decisions and texts for the meeting card. Pure Java, unit-tested.
 * No memory: every recording looks every name up again.
 */
final class InviteFlow {
    /** With several matches the meeting is saved at once and the card waits this long for a tap, then closes with no invite. */
    static final long PICKER_TIMEOUT_MS = 10000;

    enum Kind { ONE, MANY, NONE, NO_PERMISSION }

    /** One spoken invitee ("תמר עבודה") and what was found for it. */
    static final class Invitee {
        final String query;
        final Kind kind;
        final List<ContactMatcher.Match> matches;
        ContactMatcher.Match chosen;      // ONE: the match; MANY: what the user tapped (null = no invite)
        boolean decided;                  // MANY: tapped a name or "בלי זימון"

        Invitee(String query, Kind kind, List<ContactMatcher.Match> matches) {
            this.query = query; this.kind = kind; this.matches = matches == null ? new ArrayList<>() : matches;
            if (kind == Kind.ONE) { chosen = this.matches.get(0); decided = true; }
            if (kind != Kind.MANY) decided = true;
        }

        static Invitee resolve(String query, List<ContactMatcher.Contact> contacts) {
            ContactMatcher.Result r = ContactMatcher.find(query, contacts);
            int n = r.matches.size();
            return new Invitee(query, n == 0 ? Kind.NONE : n == 1 ? Kind.ONE : Kind.MANY, r.matches);
        }
        static Invitee noPermission(String query) { return new Invitee(query, Kind.NO_PERMISSION, null); }
    }

    /** Invites happen only when the user asked for one AND the 'שליחת הזמנות' setting is on (default off). */
    static boolean wanted(List<String> invite, boolean settingOn, boolean meeting) {
        return settingOn && meeting && invite != null && !invite.isEmpty();
    }

    /** Several matches for any name -> countdown stops, meeting is saved now, pickers are shown one after another. */
    static boolean needsPicker(List<Invitee> all) {
        for (Invitee i : all) if (i.kind == Kind.MANY) return true;
        return false;
    }

    /** Next picker to show (in spoken order), or null when every name is settled. */
    static Invitee nextPicker(List<Invitee> all) {
        for (Invitee i : all) if (i.kind == Kind.MANY && !i.decided) return i;
        return null;
    }

    /** Emails that get the invite. A picker left untouched (timeout / card left) means no invite for that name. */
    static List<String> attendees(List<Invitee> all) {
        List<String> e = new ArrayList<>();
        for (Invitee i : all) if (i.chosen != null && !e.contains(i.chosen.email)) e.add(i.chosen.email);
        return e;
    }

    static String okStrip(ContactMatcher.Match m) { return "זימון יישלח ל: " + m.name + " · \u2066" + m.email + "\u2069"; }
    static String sentStrip(ContactMatcher.Match m) { return "זימון נשלח ל: " + m.name + " · \u2066" + m.email + "\u2069"; }
    static String noEmailStrip(String query) { return "לא נשלח זימון – לא נמצא מייל " + to(query); }
    static String noPermissionStrip(String query) { return "לא נשלח זימון " + to(query) + " – אין גישה לאנשי קשר"; }
    static String skippedStrip(String query) { return "בלי זימון " + to(query); }
    static String pickerQuestion(Invitee i, int people) { return people > 1 ? "למי לשלוח זימון – " + i.query + "?" : "למי לשלוח זימון?"; }
    static String pickerCount(int n) { return n + " אנשי קשר"; }
    static String jitBody(List<String> invite) {
        String who = invite.size() == 1 ? invite.get(0) : String.join(", ", invite);
        return "כדי להזמין את " + who + " לפגישה, BabiSnap צריך למצוא את המייל באנשי הקשר.\n"
            + "הקריאה נעשית בטלפון בלבד – אנשי הקשר לא נשלחים לשום מקום.";
    }

    /** "לתמר" for a Hebrew name, "ל-David" otherwise. */
    static String to(String name) {
        if (name == null || name.isEmpty()) return "";
        char c = name.charAt(0);
        return (c >= 'א' && c <= 'ת') ? "ל" + name : "ל-" + name;
    }

    /** Toast when the attendee update failed (the meeting itself is already in the calendar). */
    static String patchFailed(int code) {
        if (code == 401 || code == 403) return "הפגישה נרשמה, אבל הזימון לא נשלח: צריך לאשר שוב גישה ליומן";
        return "הפגישה נרשמה, אבל הזימון לא נשלח (" + code + ")";
    }

    private InviteFlow() {}
}
