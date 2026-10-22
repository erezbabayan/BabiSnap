package com.babisnap.app;

import static org.junit.Assert.*;
import org.junit.Test;

public class PairLinkTest {
    static final String OK_URL = "https%3A%2F%2Fghibfuinantybqidwadj.supabase.co%2Ffunctions%2Fv1%2Fvoice-ingest";

    /** Exactly what BabiTk's babisnapPairLink() builds: encodeURIComponent on both values. */
    @Test public void babitkExactFormat() {
        assertEquals("ab+c/d=", PairLink.token("babisnap://pair?token=ab%2Bc%2Fd%3D&url=" + OK_URL));
        assertEquals("bsn_Z9-x_y", PairLink.token("babisnap://pair?token=bsn_Z9-x_y&url=" + OK_URL));
    }
    @Test public void lenientForms() {
        assertEquals("t1", PairLink.token("babisnap://pair?url=" + OK_URL + "&token=t1"));                 // order
        assertEquals("t1", PairLink.token("babisnap://pair?token=t1"));                                       // missing url -> default
        assertEquals("t1", PairLink.token("babisnap://pair/?token=t1&url=" + OK_URL + "%2F"));              // trailing slashes
        assertEquals("t1", PairLink.token("babisnap://pair?token=t1&url=https://ghibfuinantybqidwadj.supabase.co/functions/v1/voice-ingest/")); // raw url
        assertEquals("t1", PairLink.token("babisnap://pair?token=t1&url=https%253A%252F%252Fghibfuinantybqidwadj.supabase.co%252Ffunctions%252Fv1%252Fvoice-ingest")); // double-encoded
        assertEquals("t1", PairLink.token("babisnap://pair?token=t1&url=" + OK_URL + "&utm_source=x&v=2")); // extra params
        assertEquals("t1", PairLink.token("BabiSnap://pair?token=%20t1%20&url=" + OK_URL));                 // case + spaces
        assertEquals("a+b", PairLink.token("babisnap://pair?token=a+b"));                                     // literal '+'
        assertEquals("t1", PairLink.token("intent://pair?token=t1&url=" + OK_URL + "#Intent;scheme=babisnap;package=com.babisnap.app;end"));
    }
    @Test public void rejectsWithReason() {
        assertNotNull(PairLink.parse("babisnap://pair?url=" + OK_URL).error);
        assertNotNull(PairLink.parse("babisnap://pair?token=&url=" + OK_URL).error);
        assertNotNull(PairLink.parse("babisnap://other?token=t1").error);
        assertNotNull(PairLink.parse("https://pair?token=t1").error);
        assertTrue(PairLink.parse("babisnap://pair?token=t1&url=https%3A%2F%2Fevil.com%2Ffunctions%2Fv1%2Fvoice-ingest").error.contains("כתובת"));
        String[] bad = {"http://ghibfuinantybqidwadj.supabase.co/functions/v1/voice-ingest",
            "https://evil.com/functions/v1/voice-ingest", "https://ghibfuinantybqidwadj.supabase.co.evil.com/functions/v1/voice-ingest",
            "https://x@ghibfuinantybqidwadj.supabase.co/functions/v1/voice-ingest", "https://ghibfuinantybqidwadj.supabase.co:8443/functions/v1/voice-ingest",
            "https://ghibfuinantybqidwadj.supabase.co/functions/v1/other", "https://ghibfuinantybqidwadj.supabase.co/functions/v1/voice-ingest?x=1"};
        for (String b : bad) assertFalse(b, PairLink.validUrl(b));
        assertTrue(PairLink.validUrl(" https://ghibfuinantybqidwadj.supabase.co/functions/v1/voice-ingest// "));
    }
}
