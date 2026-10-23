package com.babisnap.app;

import static org.junit.Assert.*;
import org.junit.Test;

/** 2.2 strict pairing: exact endpoint, BabiTk token format, unknown params ignored. Tokens are generated fakes. */
public class PairLinkTest {
    static final String OK_URL = "https%3A%2F%2Fghibfuinantybqidwadj.supabase.co%2Ffunctions%2Fv1%2Fvoice-ingest";
    /** Same shape as BabiTk generateDeviceToken(): babi_ + base64url(32 bytes), no padding. */
    static String fakeToken(int seed) {
        byte[] b = new byte[32]; for (int i = 0; i < 32; i++) b[i] = (byte) (seed * 31 + i * 7);
        return "babi_" + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
    static final String T1 = fakeToken(1), T2 = fakeToken(2);

    /** Exactly what BabiTk's babisnapPairLink() / babisnapAndroidIntentLink() build (encodeURIComponent on both values). */
    @Test public void babitkExactFormat() {
        assertEquals(48, T1.length());
        assertEquals(T1, PairLink.token("babisnap://pair?token=" + T1 + "&url=" + OK_URL));
        assertEquals(T2, PairLink.token("intent://pair?token=" + T2 + "&url=" + OK_URL + "#Intent;scheme=babisnap;package=com.babisnap.app;S.browser_fallback_url=https%3A%2F%2Fx;end"));
        assertEquals(T1, PairLink.token("babisnap://pair?url=" + OK_URL + "&token=" + T1));                          // order
        assertEquals(T1, PairLink.token("babisnap://pair?token=" + T1 + "&url=" + OK_URL + "&utm_source=x&v=2"));     // unknown params ignored
        assertEquals(T1, PairLink.token("babisnap://pair?token=" + T1 + "&url=https://ghibfuinantybqidwadj.supabase.co/functions/v1/voice-ingest")); // raw url
    }
    @Test public void lenientForms() { // form leniency that does not weaken the target check
        assertEquals(T1, PairLink.token("BabiSnap://pair?token=%20" + T1 + "%20&url=" + OK_URL));
        assertEquals(T1, PairLink.token("  babisnap://pair?token=" + T1 + "&url=" + OK_URL + "  "));
    }
    @Test public void rejectsWithReason() {
        assertNotNull(PairLink.parse("babisnap://pair?url=" + OK_URL).error);
        assertNotNull(PairLink.parse("babisnap://pair?token=&url=" + OK_URL).error);
        assertNotNull(PairLink.parse("babisnap://other?token=" + T1 + "&url=" + OK_URL).error);
        assertNotNull(PairLink.parse("https://pair?token=" + T1 + "&url=" + OK_URL).error);
        assertTrue(PairLink.parse("babisnap://pair?token=" + T1 + "&url=https%3A%2F%2Fevil.com%2Ffunctions%2Fv1%2Fvoice-ingest").error.contains("כתובת"));
    }
    @Test public void urlMustBeExactEndpoint() {
        assertNotNull("missing url", PairLink.parse("babisnap://pair?token=" + T1).error);
        String[] bad = {"http://ghibfuinantybqidwadj.supabase.co/functions/v1/voice-ingest",
            "https://evil.com/functions/v1/voice-ingest", "https://ghibfuinantybqidwadj.supabase.co.evil.com/functions/v1/voice-ingest",
            "https://x@ghibfuinantybqidwadj.supabase.co/functions/v1/voice-ingest", "https://ghibfuinantybqidwadj.supabase.co:8443/functions/v1/voice-ingest",
            "https://ghibfuinantybqidwadj.supabase.co/functions/v1/other", "https://ghibfuinantybqidwadj.supabase.co/functions/v1/voice-ingest?x=1",
            "https://ghibfuinantybqidwadj.supabase.co/functions/v1/voice-ingest/", "https://GHIBFUINANTYBQIDWADJ.supabase.co/functions/v1/voice-ingest",
            " https://ghibfuinantybqidwadj.supabase.co/functions/v1/voice-ingest", "", null};
        for (String b : bad) assertFalse(String.valueOf(b), PairLink.validUrl(b));
        assertTrue(PairLink.validUrl(PairLink.ENDPOINT));
        assertTrue(PairLink.validUrl(OK_URL));
    }
    @Test public void tokenFormat() {
        assertTrue(PairLink.validToken(T1));
        String[] bad = {null, "", "t1", "bsn_Z9-x_y", T1.substring(0, 47), T1 + "A", T1.replace("babi_", "BABI_"),
            "babi_" + T1.substring(5, 47) + "=", "babi_" + T1.substring(5, 47) + "+", "babi_" + T1.substring(5, 47) + "/", " " + T1};
        for (String b : bad) assertFalse(String.valueOf(b), PairLink.validToken(b));
        assertTrue(PairLink.parse("babisnap://pair?token=abc&url=" + OK_URL).error.contains("פורמט"));
        assertNotNull("duplicate token param", PairLink.parse("babisnap://pair?token=" + T1 + "&token=" + T2 + "&url=" + OK_URL).error);
    }
    @Test public void errorsNeverContainTheToken() {
        String[] links = {"babisnap://pair?token=" + T1, "babisnap://pair?token=" + T1 + "&url=https%3A%2F%2Fevil.com",
            "babisnap://pair?token=" + T1 + "x&url=" + OK_URL, "intent://pair?token=" + T1 + "&token=" + T1 + "&url=" + OK_URL};
        for (String l : links) { PairLink.Result r = PairLink.parse(l); assertNull(r.token); assertFalse(r.error.contains(T1.substring(5))); }
    }
}
