package com.babisnap.app;

import static org.junit.Assert.*;

import org.json.JSONObject;
import org.junit.Test;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Iterator;

public class SnapRegistryTest {
    private static final String ID = "11111111-2222-4333-8444-555555555555";

    @Test public void liveUrlMatchesBuildConfig() {
        assertEquals("https://ghibfuinantybqidwadj.supabase.co/functions/v1/snap-users", SnapRegistry.DEFAULT_URL);
        assertEquals(SnapRegistry.DEFAULT_URL, BuildConfig.SNAP_REGISTRY_URL);
        assertTrue(BuildConfig.SNAP_REGISTRY_URL.startsWith("https://"));
    }

    @Test public void postBodyIsMinimumFields() throws Exception {
        String json = SnapRegistry.postBody("User@Gmail.com", ID, "signin",
            SnapRegistry.sources(true, true), "2.19", "Google Pixel");
        JSONObject o = new JSONObject(json);
        assertEquals("user@gmail.com", o.getString("email"));
        assertEquals(ID, o.getString("install_id"));
        assertEquals("signin", o.getString("event"));
        assertEquals("google_calendar", o.getJSONArray("sources").getString(0));
        assertEquals("babitk", o.getJSONArray("sources").getString(1));
        assertEquals("2.19", o.getString("app_version"));
        assertEquals("Google Pixel", o.getString("device_label"));
        assertEquals(6, o.length());
        for (Iterator<String> it = o.keys(); it.hasNext(); ) {
            String k = it.next();
            assertTrue(k.equals("email") || k.equals("install_id") || k.equals("event") || k.equals("sources") || k.equals("app_version") || k.equals("device_label"));
        }
        String low = json.toLowerCase();
        assertFalse(low.contains("token"));
        assertFalse(low.contains("transcript"));
        assertFalse(low.contains("recording"));
        assertFalse(low.contains("bearer"));
        assertFalse(low.contains("calendar_event"));
    }

    @Test public void optionalFieldsOmittedAndSourcesFiltered() throws Exception {
        String json = SnapRegistry.postBody("a@b.co", ID, "disconnect", java.util.List.of("google_calendar", "notes", "imei"), "  ", null);
        JSONObject o = new JSONObject(json);
        assertFalse(o.has("app_version"));
        assertFalse(o.has("device_label"));
        assertEquals(1, o.getJSONArray("sources").length());
        assertEquals("google_calendar", o.getJSONArray("sources").getString(0));
        JSONObject bare = new JSONObject(SnapRegistry.postBody("a@b.co", ID, "disconnect", java.util.List.of(), null, null));
        assertFalse(bare.has("sources"));
        assertFalse(bare.has("app_version"));
        assertEquals("disconnect", bare.getString("event"));
    }

    @Test public void statusParsing() {
        SnapRegistry.Status blocked = SnapRegistry.parseStatus("{\"status\":\"blocked\",\"known\":true}");
        assertTrue(blocked.blocked());
        assertTrue(blocked.known);
        assertEquals("blocked", blocked.status);
        SnapRegistry.Status unknown = SnapRegistry.parseStatus("{\"status\":\"disconnected\",\"known\":false}");
        assertFalse(unknown.blocked());
        assertFalse(unknown.known);
        assertFalse(SnapRegistry.parseStatus("{\"status\":\"active\",\"known\":true}").blocked());
        assertEquals("disconnected", SnapRegistry.parseStatus("").status);
        assertFalse(SnapRegistry.parseStatus("not-json").known);
        assertFalse(SnapRegistry.parseStatus("not-json").blocked());
        SnapRegistry.PostResult ok = SnapRegistry.parsePost("{\"ok\":true,\"status\":\"active\"}");
        assertTrue(ok.ok);
        assertFalse(ok.blocked());
        assertTrue(SnapRegistry.parsePost("{\"ok\":true,\"status\":\"blocked\"}").blocked());
        assertFalse(SnapRegistry.parsePost("{").ok);
    }

    @Test public void heartbeatOncePerDayOrOnVersionChange() {
        ZoneId z = ZoneId.of("Asia/Jerusalem");
        long morning = ZonedDateTime.of(2026, 11, 3, 8, 0, 0, 0, z).toInstant().toEpochMilli();
        long evening = ZonedDateTime.of(2026, 11, 3, 23, 30, 0, 0, z).toInstant().toEpochMilli();
        long next = ZonedDateTime.of(2026, 11, 4, 0, 5, 0, 0, z).toInstant().toEpochMilli();
        assertTrue(SnapRegistry.shouldHeartbeat(0, "", "2.19", morning));
        assertFalse(SnapRegistry.shouldHeartbeat(morning, "2.19", "2.19", evening));
        assertTrue(SnapRegistry.shouldHeartbeat(evening, "2.19", "2.19", next));
        assertTrue(SnapRegistry.shouldHeartbeat(evening, "2.19", "2.20", evening));
        assertTrue(SnapRegistry.shouldHeartbeat(evening, null, "2.19", evening));
    }

    @Test public void statusUrlEncodesEmailAndInstall() throws Exception {
        String url = SnapRegistry.statusUrl(SnapRegistry.DEFAULT_URL + "/", "A+B@C.com", ID);
        assertTrue(url.startsWith(SnapRegistry.DEFAULT_URL + "?email="));
        assertTrue(url.contains("install_id=" + ID));
        assertFalse(url.contains(" "));
        String q = url.substring(url.indexOf('?') + 1);
        String email = null;
        for (String part : q.split("&")) {
            if (part.startsWith("email=")) email = URLDecoder.decode(part.substring(6), StandardCharsets.UTF_8);
        }
        assertEquals("a+b@c.com", email);
    }

    @Test public void emailAndInstallValidation() {
        assertTrue(SnapRegistry.validEmail("dkehat55@gmail.com"));
        assertFalse(SnapRegistry.validEmail("(חשבון Google)"));
        assertFalse(SnapRegistry.validEmail("not-an-email"));
        assertFalse(SnapRegistry.validEmail("a@b"));
        assertFalse(SnapRegistry.validEmail(""));
        assertTrue(SnapRegistry.validInstallId(SnapRegistry.newInstallId()));
        assertFalse(SnapRegistry.validInstallId("imei-123"));
        assertFalse(SnapRegistry.validInstallId(null));
        assertEquals(java.util.List.of("google_calendar"), SnapRegistry.sources(true, false));
        assertTrue(SnapRegistry.sources(false, false).isEmpty());
    }
}
