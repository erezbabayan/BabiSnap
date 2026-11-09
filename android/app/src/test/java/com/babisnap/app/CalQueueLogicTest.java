package com.babisnap.app;

import static org.junit.Assert.*;
import org.junit.Test;

public class CalQueueLogicTest {
    @Test public void googleEventIdStripsHyphensAndKeepsAv09() {
        String id = CalQueueLogic.googleEventId("bsnap-01234567-89ab-cdef-0123-456789abcdef");
        assertNotNull(id);
        assertFalse(id.contains("-"));
        assertTrue(id.startsWith("bsnap"));
        assertTrue(id.length() >= 5);
        for (char c : id.toCharArray()) {
            assertTrue((c >= 'a' && c <= 'v') || (c >= '0' && c <= '9'));
        }
    }

    @Test public void googleEventIdNullSafe() {
        assertNull(CalQueueLogic.googleEventId(null));
        assertNull(CalQueueLogic.googleEventId("----"));
    }

    @Test public void insertOutcomes() {
        assertEquals(CalQueueLogic.Outcome.DONE, CalQueueLogic.googleInsertOutcome(200));
        assertEquals(CalQueueLogic.Outcome.DONE, CalQueueLogic.googleInsertOutcome(201));
        assertEquals(CalQueueLogic.Outcome.DONE, CalQueueLogic.googleInsertOutcome(409));
        assertEquals(CalQueueLogic.Outcome.NEEDS_AUTH, CalQueueLogic.googleInsertOutcome(401));
        assertEquals(CalQueueLogic.Outcome.NEEDS_AUTH, CalQueueLogic.googleInsertOutcome(403));
        assertEquals(CalQueueLogic.Outcome.FAILED, CalQueueLogic.googleInsertOutcome(400));
        assertEquals(CalQueueLogic.Outcome.RETRY, CalQueueLogic.googleInsertOutcome(500));
        assertEquals(CalQueueLogic.Outcome.RETRY, CalQueueLogic.googleInsertOutcome(429));
    }

    @Test public void deviceMarker() {
        assertEquals("babisnapId:bsnap-1", CalQueueLogic.deviceMarker("bsnap-1"));
        assertEquals("נוצר\nbabisnapId:x", CalQueueLogic.withDeviceMarker("נוצר", "x"));
        assertEquals("babisnapId:x", CalQueueLogic.withDeviceMarker("", "x"));
        assertTrue(CalQueueLogic.withDeviceMarker("babisnapId:x", "x").equals("babisnapId:x"));
    }

    @Test public void diagnosticsAndStatus() {
        assertFalse(CalQueueLogic.showDiagnostics(0, 0, 0, null));
        assertFalse(CalQueueLogic.showDiagnostics(0, 0, 0, "אין"));
        assertTrue(CalQueueLogic.showDiagnostics(1, 0, 0, null));
        assertTrue(CalQueueLogic.showDiagnostics(0, 1, 0, null));
        assertTrue(CalQueueLogic.showDiagnostics(0, 0, 1, null));
        assertTrue(CalQueueLogic.showDiagnostics(0, 0, 0, "שגיאה"));
        assertTrue(CalQueueLogic.statusTitle(2, 0, 0).contains("ממתינים"));
        assertTrue(CalQueueLogic.statusTitle(0, 0, 1).contains("אישור"));
        assertTrue(CalQueueLogic.statusTitle(0, 3, 0).contains("נכשלו"));
        assertTrue(CalQueueLogic.cardHeadline(false).contains("גוגל"));
        assertTrue(CalQueueLogic.cardHeadline(true).contains("המכשיר"));
    }
}
