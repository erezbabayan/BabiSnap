package com.babisnap.app;

import static org.junit.Assert.*;
import org.junit.Test;

public class SettingsLogicTest {
    @Test public void grantedShowsCheck() {
        assertEquals(SettingsLogic.PermAction.GRANTED, SettingsLogic.permAction(true, false));
        assertEquals(SettingsLogic.PermAction.GRANTED, SettingsLogic.permAction(true, true)); // granted later in App info
    }
    @Test public void notGrantedAsksInApp() {
        assertEquals(SettingsLogic.PermAction.REQUEST, SettingsLogic.permAction(false, false));
    }
    @Test public void permanentlyDeniedOpensAppInfo() {
        assertEquals(SettingsLogic.PermAction.APP_INFO, SettingsLogic.permAction(false, true));
    }
    @Test public void blockedOnlyWhenNoRationale() {
        assertTrue(SettingsLogic.blockedAfterDenial(false, false));
        assertFalse(SettingsLogic.blockedAfterDenial(false, true));
        assertFalse(SettingsLogic.blockedAfterDenial(true, false));
    }
    @Test public void diagnosticsHiddenWhenAllGood() {
        assertFalse(SettingsLogic.showBabiTkDiagnostics(0, 0, true, null, null));
        assertFalse(SettingsLogic.showBabiTkDiagnostics(0, 0, true, "אין", null));
        assertFalse(SettingsLogic.showBabiTkDiagnostics(0, 0, false, "לא מחובר – אין טוקן", null)); // row already says "חבר"
    }
    @Test public void diagnosticsShownWhenPendingOrError() {
        assertTrue(SettingsLogic.showBabiTkDiagnostics(2, 0, true, "אין", null));
        assertTrue(SettingsLogic.showBabiTkDiagnostics(0, 1, true, null, null));
        assertTrue(SettingsLogic.showBabiTkDiagnostics(0, 0, true, "HTTP 500", null));
        assertTrue(SettingsLogic.showBabiTkDiagnostics(0, 0, false, null, "שמירה נכשלה"));
        assertTrue(SettingsLogic.showBabiTkDiagnostics(1, 0, false, null, null)); // queued while disconnected
    }
    @Test public void durations() {
        assertEquals("שעה", SettingsLogic.durationLabel(60));
        assertEquals("רבע שעה", SettingsLogic.durationLabel(15));
        assertEquals("50 דקות", SettingsLogic.durationLabel(50));
        assertEquals(3, SettingsLogic.durationIndex(60));
        assertEquals(3, SettingsLogic.durationIndex(77));
        assertEquals(5, SettingsLogic.durationIndex(120));
    }
    @Test public void footerText() { assertEquals("BabiSnap 2.4 · חינם · ", SettingsLogic.footer("2.4")); }
}
