package com.babisnap.app;

import static org.junit.Assert.*;
import static com.babisnap.app.RePair.Decision.*;
import org.junit.Test;

/** 2.3: confirmation before replacing a working BabiTk connection. Tokens are generated fakes. */
public class RePairTest {
    static final String OLD = PairLinkTest.fakeToken(1), NEW = PairLinkTest.fakeToken(2);

    @Test public void connectedAndDifferentTokenAsksFirst() {
        assertEquals(CONFIRM, RePair.decide(OLD, false, NEW));
    }
    @Test public void firstPairingIsSilent() {
        assertEquals(SAVE, RePair.decide(null, false, NEW));
        assertEquals(SAVE, RePair.decide("", false, NEW));
        assertEquals(SAVE, RePair.decide("  ", false, NEW));
    }
    @Test public void needsReconnectIsSilent() {
        assertEquals(SAVE, RePair.decide(null, true, NEW)); // after 401 the token is wiped
        assertEquals(SAVE, RePair.decide(OLD, true, NEW));  // even if a stale value were still around
    }
    @Test public void sameTokenIsSilentSuccess() {
        assertEquals(SAME, RePair.decide(OLD, false, OLD));
        assertEquals(SAME, RePair.decide(OLD, false, " " + OLD + " "));
    }
    @Test public void tokensDifferingInOneCharStillAsk() {
        String almost = OLD.substring(0, OLD.length() - 1) + (OLD.endsWith("A") ? "B" : "A");
        assertEquals(CONFIRM, RePair.decide(OLD, false, almost));
    }
}
