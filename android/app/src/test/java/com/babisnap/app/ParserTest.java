package com.babisnap.app;

import static org.junit.Assert.*;
import org.junit.Test;
import java.nio.file.*;

/** Runs the exact same cases as the Node test (test-cases.json) through Rhino, i.e. the on-device engine. */
public class ParserTest {
    static Path root() { return Paths.get(System.getProperty("user.dir")).resolve("../..").normalize(); }
    static String r(String f) throws Exception { return new String(Files.readAllBytes(root().resolve(f)), "UTF-8"); }

    @Test public void sharedCasesPassInRhino() throws Exception {
        HebrewParser p = new HebrewParser(r("Code.gs"), r("run-cases.js"));
        String out = p.call("runCases", r("test-cases.json"));
        System.out.println(out);
        assertTrue(out, out.contains("\"pass\":") && out.matches("(?s).*\"pass\":(\\d+),\"total\":\\1,.*"));
    }

    @Test public void defaultDurationFromSettings() throws Exception {
        HebrewParser p = new HebrewParser(r("Code.gs"));
        String j = p.parseJson("פגישה עם דני מחר ב-10", 2026, 10, 22, 4, 30);
        assertTrue(j, j.contains("\"durationMin\":30") && j.contains("2026-10-23 10:00"));
        j = p.parseJson("פגישה עם דני מחר ב-10 לשעתיים", 2026, 10, 22, 4, 30);
        assertTrue(j, j.contains("\"durationMin\":120"));
    }

    @Test public void eventJsonCrossesMidnightAndEscapes() {
        String j = EventJson.build("פגישה \"חשובה\"", "2026-10-22 23:30", 90, "תל אביב", null);
        assertTrue(j, j.contains("\"start\":{\"dateTime\":\"2026-10-22T23:30:00\",\"timeZone\":\"Asia/Jerusalem\"}"));
        assertTrue(j, j.contains("\"end\":{\"dateTime\":\"2026-10-23T01:00:00\""));
        assertTrue(j, j.contains("\\\"חשובה\\\"") && j.contains("\"location\":\"תל אביב\""));
        assertFalse(j.contains("description"));
    }

    @Test public void eveningTimeFeedsClassifier() throws Exception {
        HebrewParser p = new HebrewParser(r("Code.gs"));
        String j = p.parseJson("לשחק עם אלון בסוני פלייסטיישן היום ב-7:00 בערב", 2026, 10, 22, 4, 60);
        assertTrue(j, j.contains("2026-10-22 19:00") && j.contains("\"timeDefaulted\":false") && j.contains("\"hour\":19"));
        // the classifier gets explicitTime=true from the same parse: an action phrase stays a task, with due_time 19:00
        assertEquals(Classifier.Kind.TASK, Classifier.classify("לשחק עם אלון בסוני פלייסטיישן היום ב-7:00 בערב", true).kind);
    }

    @Test public void roniMeetingTodayAt21() throws Exception {
        String txt = "תוסיף לי פגישה היום בערב עם רוני בשעה 9:00 בערב משחק של מכבי";
        String j = new HebrewParser(r("Code.gs")).parseJson(txt, 2026, 10, 22, 4, 60);
        assertTrue(j, j.contains("\"startStr\":\"2026-10-22 21:00\"") && j.contains("\"title\":\"פגישה עם רוני – משחק של מכבי\""));
        assertEquals(Classifier.Kind.MEETING, Classifier.classify(txt, true).kind);
    }

    @Test public void allDayPhrasesBecomeAllDayEvents() throws Exception {
        HebrewParser p = new HebrewParser(r("Code.gs"));
        String[][] cases = {
            {"פגישה עם דני מחר יום שלם", "פגישה עם דני", "2026-10-23"},
            {"כנס ביום ראשון כל היום", "כנס", "2026-10-25"},
            {"חופש ב-5/11 לכל היום", "חופש", "2026-11-05"},
            {"יום מלא סדנה מחרתיים", "סדנה", "2026-10-24"},
            {"מילואים היום כל היום", "מילואים", "2026-10-22"},
            {"אירוע של יום שלם ב-3 בנובמבר", "אירוע", "2026-11-03"},
        };
        for (String[] c : cases) {
            String j = p.parseJson(c[0], 2026, 10, 22, 4, 60);
            assertTrue(c[0] + " -> " + j, j.contains("\"allDay\":true") && j.contains("\"timeDefaulted\":false")
                && j.contains("\"title\":\"" + c[1] + "\"") && j.contains(c[2]) && !j.contains("09:00"));
            String body = EventJson.buildAllDay(c[1], new org.json.JSONObject(j).getString("startStr"), "", null);
            assertTrue(body, body.contains("\"start\":{\"date\":\"" + c[2] + "\"}") && !body.contains("dateTime"));
        }
        // explicit time wins; no time and no all-day phrase keeps the 09:00 default (flagged timeDefaulted)
        String j = p.parseJson("פגישה עם דני מחר כל היום ב-10", 2026, 10, 22, 4, 60);
        assertTrue(j, j.contains("\"allDay\":false") && j.contains("2026-10-23 10:00"));
        j = p.parseJson("פגישה עם דני מחר", 2026, 10, 22, 4, 60);
        assertTrue(j, j.contains("\"allDay\":false") && j.contains("2026-10-23 09:00") && j.contains("\"timeDefaulted\":true"));
    }

    @Test public void allDayEventJsonEndsNextDay() {
        String j = EventJson.buildAllDay("חופש", "2026-12-31 00:00", "אילת", "x");
        assertTrue(j, j.contains("\"start\":{\"date\":\"2026-12-31\"}") && j.contains("\"end\":{\"date\":\"2027-01-01\"}"));
        assertFalse(j.contains("dateTime") || j.contains("timeZone"));
    }

    @Test public void allDayCardText() {
        assertTrue(CardText.meeting("כנס", 25, 10, 2026, 0, 0, false, true, "").contains("כל היום"));
        assertFalse(CardText.meeting("כנס", 25, 10, 2026, 0, 0, false, true, "").contains("00:00"));
    }
}
