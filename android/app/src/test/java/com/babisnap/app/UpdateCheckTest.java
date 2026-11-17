package com.babisnap.app;

import static org.junit.Assert.*;
import org.junit.Test;

public class UpdateCheckTest {
    @Test public void compares() {
        assertTrue(UpdateCheck.isNewer("v2.16", "2.15"));
        assertTrue(UpdateCheck.isNewer("v3.0", "2.15"));
        assertTrue(UpdateCheck.isNewer("2.15.1", "2.15"));
        assertFalse(UpdateCheck.isNewer("v2.15", "2.15"));
        assertFalse(UpdateCheck.isNewer("v2.9", "2.15"));
        assertFalse(UpdateCheck.isNewer("", "2.15"));
        assertFalse(UpdateCheck.isNewer("nightly", "2.15"));
        assertEquals("2.16", UpdateCheck.clean("v2.16"));
    }

    @Test public void twoDigitMinorIsNumericNotLexical() {
        assertTrue(UpdateCheck.isNewer("v2.40", "2.39"));
        assertTrue(UpdateCheck.isNewer("v2.100", "2.99"));
        assertFalse(UpdateCheck.isNewer("v2.4", "2.39"));
        assertTrue(UpdateCheck.isNewer("V3.00", "2.99"));
    }

    @Test public void suffixesAndJunk() {
        assertFalse(UpdateCheck.isNewer("v2.39", "2.39-debug"));
        assertTrue(UpdateCheck.isNewer("v2.40", "2.39-debug"));
        assertFalse(UpdateCheck.isNewer(null, "2.39"));
        assertFalse(UpdateCheck.isNewer("v2.40", null));
        assertFalse(UpdateCheck.isNewer("v2..40", "2.39"));
        assertTrue(UpdateCheck.isNewer(" v2.40 ", "2.39"));
    }

    @Test public void oncePerDay() {
        long day = UpdateCheck.DAY_MS, t = 1_800_000_000_000L;
        assertTrue(UpdateCheck.due(0, t));
        assertFalse(UpdateCheck.due(t, t + day - 1));
        assertTrue(UpdateCheck.due(t, t + day));
        assertTrue(UpdateCheck.due(t, t - 1000)); // clock moved back
    }

    @Test public void readsTagFromReleaseJson() {
        assertEquals("v2.40", UpdateCheck.tagFrom("{\"tag_name\":\"v2.40\",\"name\":\"BabiSnap 2.40\",\"assets\":[]}"));
        assertEquals("", UpdateCheck.tagFrom("{\"message\":\"API rate limit exceeded\"}"));
        assertEquals("", UpdateCheck.tagFrom("<html>offline</html>"));
        assertEquals("", UpdateCheck.tagFrom(null));
    }

    @Test public void hebrewTexts() {
        assertEquals("יש גרסה חדשה 2.40", UpdateCheck.title("v2.40"));
        assertEquals("יש לך את הגרסה האחרונה (2.39)", UpdateCheck.upToDate("2.39"));
        assertEquals("https://github.com/erezbabayan/BabiSnap/releases/latest/download/BabiSnap.apk", UpdateCheck.APK_URL);
    }
}
