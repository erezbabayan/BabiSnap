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
}
