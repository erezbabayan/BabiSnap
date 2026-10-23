package com.babisnap.app;

import static org.junit.Assert.*;
import org.junit.Test;

/** Bug 2 (2.4): the meeting card stayed on screen with a drained bar until Google answered. */
public class CardFlowTest {
    @Test public void tokenInHandClosesCardAndSendsInBackground() {
        assertEquals(CardFlow.AfterAuth.SEND_IN_BACKGROUND_AND_CLOSE, CardFlow.afterAuth(false, true));
        assertEquals(CardFlow.AfterAuth.SEND_IN_BACKGROUND_AND_CLOSE, CardFlow.afterAuth(false, false));
    }

    @Test public void consentOnlyWhileCardIsStillOpen() {
        assertEquals(CardFlow.AfterAuth.ASK_CONSENT, CardFlow.afterAuth(true, true));
        assertEquals(CardFlow.AfterAuth.TELL_CONSENT_NEEDED, CardFlow.afterAuth(true, false));
    }

    @Test public void cardNeverWaitsForTheNetwork() {
        assertEquals(3000, CardFlow.UNDO_MS);
        assertEquals(3000, CardFlow.latestAutoCloseMs(false));          // task/note: closes right at the end of the window
        assertTrue(CardFlow.latestAutoCloseMs(true) <= 5000);           // meeting: at most 2s more while auth resolves
        // the old path could hold the card for a 15s connect + 20s read timeout, twice on a 401
        assertTrue(CardFlow.latestAutoCloseMs(true) < 15000 + 20000);
    }

    @Test public void retryOnceOn401() {
        assertTrue(CardFlow.retryWithFreshToken(401, false));
        assertFalse(CardFlow.retryWithFreshToken(401, true));
        assertFalse(CardFlow.retryWithFreshToken(500, false));
        assertFalse(CardFlow.retryWithFreshToken(200, false));
    }

    @Test public void resultMessages() {
        assertNull(CardFlow.resultMessage(200));
        assertNull(CardFlow.resultMessage(201));
        assertTrue(CardFlow.resultMessage(401).contains("לאשר"));
        assertTrue(CardFlow.resultMessage(500).contains("500"));
        assertTrue(CardFlow.networkMessage("timeout").startsWith("לא נרשם ביומן"));
    }
}
