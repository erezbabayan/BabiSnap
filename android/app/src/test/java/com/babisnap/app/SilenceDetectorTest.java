package com.babisnap.app;

import static org.junit.Assert.*;
import static com.babisnap.app.SilenceDetector.Action.*;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class SilenceDetectorTest {
    static final String S = "לשחק עם אלון בסוני פלייסטיישן היום ב-7:00 בערב";

    @Test public void waitsBeforeAnySpeech() {
        SilenceDetector d = new SilenceDetector(0);
        assertEquals(WAIT, d.check(5000)); // nothing heard: no silence-finish, only the cap
    }
    @Test public void finishesAfter2sOfUnchangedPartial() {
        SilenceDetector d = new SilenceDetector(0);
        d.partial("לשחק עם אלון", 1000);
        d.partial(S, 2500);
        assertEquals(WAIT, d.check(4000));
        d.partial(S, 4200); // same text repeated by the recognizer does not reset the clock
        assertEquals(FINISH_SILENCE, d.check(4500));
        assertEquals(S, d.lastText());
    }
    @Test public void changingTextResetsClock() {
        SilenceDetector d = new SilenceDetector(0);
        d.partial("א", 0); d.partial("א ב", 1900); assertEquals(WAIT, d.check(3000));
        assertEquals(FINISH_SILENCE, d.check(3900));
    }
    @Test public void hardCapAt30s() {
        SilenceDetector d = new SilenceDetector(0);
        for (int t = 0; t < 30000; t += 1000) d.partial("מילה " + t, t);
        assertEquals(FINISH_CAP, d.check(30000));
    }
    @Test public void finalsWinElsePartial() {
        assertEquals("סופי", SilenceDetector.pick(Arrays.asList("סופי"), "חלקי"));
        assertEquals("חלקי", SilenceDetector.pick(Collections.emptyList(), "חלקי"));
        assertEquals("חלקי", SilenceDetector.pick(null, "חלקי"));
        assertEquals("חלקי", SilenceDetector.pick(Arrays.asList(" "), "חלקי"));
    }
}
