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
}
