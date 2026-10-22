package com.babisnap.app;

import static org.junit.Assert.*;
import org.junit.Test;

public class PairLinkTest {
    static final String OK_URL = "https%3A%2F%2Fghibfuinantybqidwadj.supabase.co%2Ffunctions%2Fv1%2Fvoice-ingest";
    @Test public void acceptsValid() {
        assertEquals("ab+c/d=", PairLink.token("babisnap://pair?token=ab%2Bc%2Fd%3D&url=" + OK_URL));
        assertEquals("t1", PairLink.token("babisnap://pair?url=" + OK_URL + "&token=t1"));
    }
    @Test public void rejectsBad() {
        assertNull(PairLink.token("babisnap://pair?token=t1"));
        assertNull(PairLink.token("babisnap://pair?token=&url=" + OK_URL));
        assertNull(PairLink.token("babisnap://other?token=t1&url=" + OK_URL));
        assertNull(PairLink.token("https://pair?token=t1&url=" + OK_URL));
        String[] bad = {"http://ghibfuinantybqidwadj.supabase.co/functions/v1/voice-ingest",
            "https://evil.com/functions/v1/voice-ingest", "https://ghibfuinantybqidwadj.supabase.co.evil.com/functions/v1/voice-ingest",
            "https://x@ghibfuinantybqidwadj.supabase.co/functions/v1/voice-ingest", "https://ghibfuinantybqidwadj.supabase.co:8443/functions/v1/voice-ingest",
            "https://ghibfuinantybqidwadj.supabase.co/functions/v1/other", "https://ghibfuinantybqidwadj.supabase.co/functions/v1/voice-ingest?x=1"};
        for (String b : bad) assertFalse(b, PairLink.validUrl(b));
    }
}
