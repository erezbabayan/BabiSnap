package com.babisnap.app;

import static org.junit.Assert.*;
import org.junit.Test;

/** Pre-send validation / truncation to the BabiTk server limits. */
public class BabiTkJsonTest {
    static String rep(String s, int n) { StringBuilder b = new StringBuilder(); for (int i = 0; i < n; i++) b.append(s); return b.toString(); }
    static String field(String json, String k) {
        int i = json.indexOf("\"" + k + "\":\""); if (i < 0) return null;
        StringBuilder b = new StringBuilder();
        for (int j = i + k.length() + 4; j < json.length(); j++) {
            char ch = json.charAt(j);
            if (ch == '\\') { b.append(json.charAt(++j)); continue; }
            if (ch == '"') return b.toString();
            b.append(ch);
        }
        return null;
    }

    @Test public void truncatesToServerLimits() {
        String j = BabiTkJson.build(rep("א", 250), null, null, rep("n", 5000), rep("L", 400), "task", "bsnap-1");
        assertEquals(200, field(j, "title").length());
        assertEquals(4000, field(j, "notes").length());
        assertEquals(300, field(j, "location").length());
    }
    @Test public void clipNeverSplitsEmojiAndTrims() {
        String s = rep("a", 199) + "😀";             // emoji straddles the 200 limit
        assertEquals(199, BabiTkJson.clip(s, 200).length());
        assertEquals("abc", BabiTkJson.clip("  abc  ", 200));
        assertEquals("", BabiTkJson.clip(null, 10));
        assertEquals("ab", BabiTkJson.clip("ab   cd", 4));
    }
    @Test public void emptyTitleGetsFallback() {
        assertEquals(BabiTkJson.FALLBACK_TITLE, field(BabiTkJson.build("   ", null, null, null, null, "note", "x"), "title"));
    }
    @Test public void dateAndTimeValidation() {
        assertTrue(BabiTkJson.validDate("2026-02-28")); assertTrue(BabiTkJson.validDate("2028-02-29"));
        for (String d : new String[]{"2026-02-30", "2026-13-01", "2026-1-05", "26-01-05", "2026/01/05", "", null}) assertFalse(d, BabiTkJson.validDate(d));
        assertTrue(BabiTkJson.validTime("00:00")); assertTrue(BabiTkJson.validTime("23:59"));
        for (String t : new String[]{"24:00", "12:60", "9:05", "09:5", "0905", "", null}) assertFalse(t, BabiTkJson.validTime(t));
        String j = BabiTkJson.build("t", "2026-02-30", "25:00", null, null, "task", "x");
        assertFalse(j.contains("due_date")); assertFalse(j.contains("due_time"));
        j = BabiTkJson.build("t", "2026-10-23", "14:30", null, null, "task", "x");
        assertEquals("2026-10-23", field(j, "due_date")); assertEquals("14:30", field(j, "due_time"));
    }
    @Test public void clientIdMatchesServerPatternAndIsUnique() {
        String a = BabiTkJson.newClientId(), b = BabiTkJson.newClientId();
        assertNotEquals(a, b);
        assertTrue(BabiTkJson.validClientId(a));
        assertTrue(a.matches("^[A-Za-z0-9._:-]{1,128}$"));
        assertFalse(BabiTkJson.validClientId("has space")); assertFalse(BabiTkJson.validClientId(rep("a", 129)));
        assertFalse(BabiTkJson.validClientId("")); assertFalse(BabiTkJson.validClientId(null)); assertFalse(BabiTkJson.validClientId("עברית"));
        String id = field(BabiTkJson.build("t", null, null, null, null, "task", "bad id!"), "client_id"); // invalid -> regenerated
        assertTrue(BabiTkJson.validClientId(id));
        assertEquals("keep-me:1.2_3", field(BabiTkJson.build("t", null, null, null, null, "task", "keep-me:1.2_3"), "client_id"));
    }
    @Test public void fixedFieldsAndType() {
        String j = BabiTkJson.build("t", null, null, null, null, "weird", "x");
        assertEquals("task", field(j, "type")); assertEquals("voice", field(j, "source")); assertEquals("notebook", field(j, "board"));
        assertEquals("note", field(BabiTkJson.build("t", null, null, null, null, "note", "x"), "type"));
        assertFalse(j.contains("notes")); assertFalse(j.contains("location"));
    }
    @Test public void outcomes() {
        assertEquals(BabiTkJson.Outcome.DONE, BabiTkJson.outcome(201));
        assertEquals(BabiTkJson.Outcome.DONE, BabiTkJson.outcome(200));
        assertEquals(BabiTkJson.Outcome.UNAUTHORIZED, BabiTkJson.outcome(401));
        assertEquals(BabiTkJson.Outcome.RATE_LIMITED, BabiTkJson.outcome(429));
        assertEquals(BabiTkJson.Outcome.FAILED, BabiTkJson.outcome(400));
        assertEquals(BabiTkJson.Outcome.FAILED, BabiTkJson.outcome(413));
        assertEquals(BabiTkJson.Outcome.RETRY, BabiTkJson.outcome(500));
        assertEquals(BabiTkJson.Outcome.RETRY, BabiTkJson.outcome(-1));
    }
    @Test public void errorTextUsesServerMessage() {
        assertEquals("HTTP 400 – title is too long", BabiTkJson.errorText(400, "{\"ok\":false,\"error\":{\"code\":\"validation\",\"message\":\"title is too long\"}}"));
        assertEquals("HTTP 400", BabiTkJson.errorText(400, null));
    }
}
