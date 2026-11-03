package com.babisnap.app;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Pure snap_users protocol: JSON bodies, status parsing, heartbeat throttle.
 * Sends nothing but email, install_id, event, sources, app_version, device_label.
 */
public final class SnapRegistry {
    public static final String DEFAULT_URL = "https://ghibfuinantybqidwadj.supabase.co/functions/v1/snap-users";
    public static final String SOURCE_GOOGLE = "google_calendar";
    public static final String SOURCE_BABITK = "babitk";
    public static final String EVENT_SIGNIN = "signin";
    public static final String EVENT_HEARTBEAT = "heartbeat";
    public static final String EVENT_DISCONNECT = "disconnect";
    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_BLOCKED = "blocked";
    public static final String STATUS_DISCONNECTED = "disconnected";
    static final ZoneId DAY = ZoneId.of("Asia/Jerusalem");

    public static final class Status {
        public final String status;
        public final boolean known;
        Status(String status, boolean known) { this.status = status; this.known = known; }
        public boolean blocked() { return isBlocked(status); }
    }

    public static final class PostResult {
        public final boolean ok;
        public final String status;
        PostResult(boolean ok, String status) { this.ok = ok; this.status = status == null ? "" : status; }
        public boolean blocked() { return isBlocked(status); }
    }

    public static boolean isBlocked(String status) { return STATUS_BLOCKED.equals(status); }

    public static boolean knownEvent(String event) {
        return EVENT_SIGNIN.equals(event) || EVENT_HEARTBEAT.equals(event) || EVENT_DISCONNECT.equals(event);
    }

    /** Trim + lowercase. Display strings stay as Google returned them; the wire value is normalized. */
    public static String normalizeEmail(String email) {
        if (email == null) return "";
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /** Real mailbox only. The "(חשבון Google)" placeholder is not an address and is never sent. */
    public static boolean validEmail(String email) {
        String e = normalizeEmail(email);
        if (e.isEmpty() || e.startsWith("(") || e.length() > 254) return false;
        int at = e.indexOf('@');
        if (at <= 0 || at != e.lastIndexOf('@')) return false;
        if (e.indexOf(' ') >= 0 || e.indexOf('/') >= 0 || e.indexOf('\\') >= 0) return false;
        String domain = e.substring(at + 1);
        return domain.contains(".") && !domain.startsWith(".") && !domain.endsWith(".") && !domain.contains("..");
    }

    public static boolean validInstallId(String id) {
        if (id == null) return false;
        return id.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    }

    public static String newInstallId() { return UUID.randomUUID().toString(); }

    public static List<String> sources(boolean googleCalendar, boolean babiTk) {
        List<String> s = new ArrayList<>();
        if (googleCalendar) s.add(SOURCE_GOOGLE);
        if (babiTk) s.add(SOURCE_BABITK);
        return s;
    }

    /**
     * Heartbeat when this install has not reported today (Asia/Jerusalem), or the installed version changed.
     * lastMs &lt;= 0 means never reported.
     */
    public static boolean shouldHeartbeat(long lastMs, String lastVersion, String currentVersion, long nowMs) {
        String cur = currentVersion == null ? "" : currentVersion;
        String prev = lastVersion == null ? "" : lastVersion;
        if (!cur.equals(prev)) return true;
        if (lastMs <= 0) return true;
        LocalDate last = Instant.ofEpochMilli(lastMs).atZone(DAY).toLocalDate();
        LocalDate today = Instant.ofEpochMilli(nowMs).atZone(DAY).toLocalDate();
        return today.isAfter(last);
    }

    /** Unknown / garbage → disconnected + known false (same shape the server uses for an unknown user). */
    public static Status parseStatus(String json) {
        if (json == null || json.trim().isEmpty()) return new Status(STATUS_DISCONNECTED, false);
        try {
            JSONObject o = new JSONObject(json);
            String status = o.optString("status", STATUS_DISCONNECTED);
            if (status.isEmpty()) status = STATUS_DISCONNECTED;
            return new Status(status, o.optBoolean("known", false));
        } catch (JSONException e) {
            return new Status(STATUS_DISCONNECTED, false);
        }
    }

    public static PostResult parsePost(String json) {
        if (json == null || json.trim().isEmpty()) return new PostResult(false, "");
        try {
            JSONObject o = new JSONObject(json);
            return new PostResult(o.optBoolean("ok", false), o.optString("status", ""));
        } catch (JSONException e) {
            return new PostResult(false, "");
        }
    }

    public static String statusUrl(String base, String email, String installId) {
        String b = base == null ? "" : base;
        while (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        return b + "?email=" + enc(normalizeEmail(email)) + "&install_id=" + enc(installId == null ? "" : installId);
    }

    public static String postBody(String email, String installId, String event, List<String> sources, String appVersion, String deviceLabel) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("email", normalizeEmail(email));
        o.put("install_id", installId == null ? "" : installId);
        o.put("event", event == null ? "" : event);
        if (sources != null) {
            JSONArray a = new JSONArray();
            for (String s : sources) if (SOURCE_GOOGLE.equals(s) || SOURCE_BABITK.equals(s)) a.put(s);
            if (a.length() > 0) o.put("sources", a);
        }
        if (appVersion != null && !appVersion.trim().isEmpty()) o.put("app_version", clip(appVersion.trim(), 32));
        if (deviceLabel != null && !deviceLabel.trim().isEmpty()) o.put("device_label", clip(deviceLabel.trim(), 80));
        return o.toString();
    }

    private static String clip(String s, int max) { return s.length() <= max ? s : s.substring(0, max); }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private SnapRegistry() {}
}
