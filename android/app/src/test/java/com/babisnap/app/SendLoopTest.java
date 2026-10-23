package com.babisnap.app;

import static org.junit.Assert.*;
import org.junit.Test;
import java.util.*;

/** 401 / 429 / 400 handling of one queue pass, with a fake transport. */
public class SendLoopTest {
    static List<SendLoop.Item> items(int n) { List<SendLoop.Item> l = new ArrayList<>(); for (int i = 1; i <= n; i++) l.add(new SendLoop.Item("id" + i, "{" + i + "}")); return l; }
    static SendLoop.Transport codes(int[] calls, Core.Resp... rs) { return b -> rs[calls[0]++]; }
    static Core.Resp r(int c) { return new Core.Resp(c, ""); }

    @Test public void allSent() {
        int[] n = {0}; SendLoop.Report rep = SendLoop.run(items(2), codes(n, r(201), r(201)));
        assertEquals(Arrays.asList("id1", "id2"), rep.sent); assertFalse(rep.unauthorized || rep.rateLimited || rep.retry);
    }
    @Test public void unauthorizedStopsAndKeepsRemainingItems() {
        int[] n = {0}; SendLoop.Report rep = SendLoop.run(items(3), codes(n, r(201), r(401), r(201)));
        assertTrue(rep.unauthorized); assertEquals(Collections.singletonList("id1"), rep.sent);
        assertTrue(rep.failed.isEmpty()); // id2, id3 are NOT dropped or failed: they stay queued for after re-pair
        assertEquals(2, n[0]);           // nothing more is sent with a rejected token
    }
    @Test public void wipeOnlyTheRejectedToken() {
        assertTrue(SendLoop.shouldWipe("babi_old", "babi_old"));
        assertFalse(SendLoop.shouldWipe("babi_new", "babi_old")); // re-paired while the request was in flight
        assertFalse(SendLoop.shouldWipe(null, "babi_old"));
    }
    @Test public void rateLimitedStopsWithRetryAfter() {
        int[] n = {0}; SendLoop.Report rep = SendLoop.run(items(3), codes(n, r(201), new Core.Resp(429, "{}", "120"), r(201)));
        assertTrue(rep.rateLimited); assertEquals("120", rep.retryAfter); assertEquals(2, n[0]);
        assertEquals(Collections.singletonList("id1"), rep.sent); assertTrue(rep.failed.isEmpty());
    }
    @Test public void badRequestMarksFailedAndContinues() {
        int[] n = {0}; SendLoop.Report rep = SendLoop.run(items(2), codes(n,
            new Core.Resp(400, "{\"ok\":false,\"error\":{\"code\":\"validation\",\"message\":\"due_date must be YYYY-MM-DD\"}}"), r(201)));
        assertEquals("HTTP 400 – due_date must be YYYY-MM-DD", rep.failed.get("id1"));
        assertEquals(Collections.singletonList("id2"), rep.sent); assertFalse(rep.retry);
    }
    @Test public void networkErrorAsksForRetry() {
        SendLoop.Report rep = SendLoop.run(items(2), b -> { throw new java.io.IOException("offline"); });
        assertTrue(rep.retry); assertTrue(rep.sent.isEmpty()); assertTrue(rep.failed.isEmpty());
    }
    @Test public void exponentialBackoff() {
        long now = 1_000_000L;
        assertEquals(60, BabiTkJson.backoffSeconds(1, null, now));
        assertEquals(120, BabiTkJson.backoffSeconds(2, null, now));
        assertEquals(240, BabiTkJson.backoffSeconds(3, "", now));
        assertEquals(3600, BabiTkJson.backoffSeconds(10, null, now));   // capped at the 1h server window
        assertEquals(3600, BabiTkJson.backoffSeconds(99, null, now));
    }
    @Test public void retryAfterIsRespected() {
        long now = java.time.ZonedDateTime.parse("Fri, 23 Oct 2026 08:00:00 GMT", java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli();
        assertEquals(900, BabiTkJson.backoffSeconds(1, "900", now));
        assertEquals(30, BabiTkJson.backoffSeconds(5, "1", now));       // clamped to >= 30s
        assertEquals(3600, BabiTkJson.backoffSeconds(1, "99999", now)); // clamped to <= 1h
        assertEquals(600, BabiTkJson.backoffSeconds(1, "Fri, 23 Oct 2026 08:10:00 GMT", now)); // HTTP-date form
        assertEquals(60, BabiTkJson.backoffSeconds(1, "soon", now));   // garbage -> exponential
    }
}
