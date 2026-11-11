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
    @Test public void finishesAfter5sOfUnchangedPartial() {
        SilenceDetector d = new SilenceDetector(0);
        d.partial("לשחק עם אלון", 1000);
        d.partial(S, 2500);
        assertEquals(WAIT, d.check(7000));
        d.partial(S, 6000); // same text repeated by the recognizer does not reset the clock
        assertEquals(FINISH_SILENCE, d.check(7600));
        assertEquals(S, d.lastText());
    }
    @Test public void changingTextResetsClock() {
        SilenceDetector d = new SilenceDetector(0);
        d.partial("א", 0); d.partial("א ב", 1900); assertEquals(WAIT, d.check(6000));
        assertEquals(FINISH_SILENCE, d.check(6900));
    }
    @Test public void callCommandEndsAfterOneAndAHalfSeconds() {
        SilenceDetector d = new SilenceDetector(0);
        d.partial("תתקשר לתמרי", 1000);
        assertEquals(WAIT, d.check(2499));
        assertEquals(FINISH_SILENCE, d.check(2500));
        for (String start : new String[]{"תתקשרי לדני", "תתקשרו לאמא", "להתקשר לאמא עכשיו", "תחייג לאמא", "התקשר לדני", "ותתקשר לתמרי"}) {
            assertTrue(start, CallDial.shortListen(start));
        }
        assertFalse(CallDial.shortListen("מחר תתקשר לדני"));
        assertFalse(CallDial.shortListen("לקנות חלב"));
        assertFalse(CallDial.shortListen("וואטסאפ לתמרי"));
        assertFalse(CallDial.shortListen(""));
    }

    @Test public void otherSpeechKeepsFiveSecondsAndTheCap() {
        SilenceDetector d = new SilenceDetector(0);
        d.partial("לקנות חלב", 1000);
        assertEquals(WAIT, d.check(5999));
        assertEquals(FINISH_SILENCE, d.check(6000));
        SilenceDetector cap = new SilenceDetector(0);
        cap.partial("תתקשר לדני", 1000);
        assertEquals(FINISH_CAP, cap.check(60000));
        assertEquals(60000L, SilenceDetector.HARD_CAP_MS);
        assertEquals(5000L, SilenceDetector.SILENCE_MS);
        assertEquals(1500L, SilenceDetector.CALL_SILENCE_MS);
    }

    @Test public void hardCapAt60s() {
        SilenceDetector d = new SilenceDetector(0);
        for (int t = 0; t < 60000; t += 1000) d.partial("מילה " + t, t);
        assertEquals(FINISH_CAP, d.check(60000));
    }
    @Test public void finalsWinElsePartial() {
        assertEquals("סופי", SilenceDetector.pick(Arrays.asList("סופי"), "חלקי"));
        assertEquals("חלקי", SilenceDetector.pick(Collections.emptyList(), "חלקי"));
        assertEquals("חלקי", SilenceDetector.pick(null, "חלקי"));
        assertEquals("חלקי", SilenceDetector.pick(Arrays.asList(" "), "חלקי"));
    }
}
