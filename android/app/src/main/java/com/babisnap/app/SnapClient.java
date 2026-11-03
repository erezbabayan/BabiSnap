package com.babisnap.app;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * snap_users HTTP for this install only.
 * Authorization is BuildConfig.SNAP_REGISTRY_CLIENT_KEY — never a Google token and never the admin key.
 * Calls: POST signin / heartbeat / disconnect, and GET this email+install_id status.
 * No list, block, or other admin calls. Missing key or any network/HTTP failure returns null
 * so calendar and pairing keep working.
 */
final class SnapClient {
    private SnapClient() {}

    static boolean configured() {
        String key = BuildConfig.SNAP_REGISTRY_CLIENT_KEY;
        return key != null && !key.trim().isEmpty() && allowed(BuildConfig.SNAP_REGISTRY_URL);
    }

    /** GET ?email=&amp;install_id= → status. null = skip the block (offline / not configured). */
    static SnapRegistry.Status fetchStatus(String email, String installId) {
        if (!configured() || !SnapRegistry.validEmail(email) || !SnapRegistry.validInstallId(installId)) return null;
        try {
            // Short timeout: this runs before a calendar write / new pairing and must not stall them.
            String body = exchange("GET", SnapRegistry.statusUrl(BuildConfig.SNAP_REGISTRY_URL, email, installId), null, 3000, 3000);
            return body == null ? null : SnapRegistry.parseStatus(body);
        } catch (Exception e) { return null; }
    }

    static SnapRegistry.PostResult post(String email, String installId, String event, List<String> sources, String appVersion, String deviceLabel) {
        if (!configured() || !SnapRegistry.validEmail(email) || !SnapRegistry.validInstallId(installId) || !SnapRegistry.knownEvent(event)) return null;
        try {
            String json = SnapRegistry.postBody(email, installId, event, sources, appVersion, deviceLabel);
            String body = exchange("POST", BuildConfig.SNAP_REGISTRY_URL, json, 8000, 8000);
            return body == null ? null : SnapRegistry.parsePost(body);
        } catch (Exception e) { return null; }
    }

    /** Background POST. Heartbeat success is recorded only after {ok:true}. */
    static void reportAsync(android.content.Context app, String email, String installId, String event, List<String> sources, String appVersion, String deviceLabel) {
        android.content.Context c = app.getApplicationContext();
        new Thread(() -> {
            SnapRegistry.PostResult r = post(email, installId, event, sources, appVersion, deviceLabel);
            if (r != null && r.ok && SnapRegistry.EVENT_HEARTBEAT.equals(event)) {
                Core.prefs(c).edit().putLong(SnapInstall.HB_AT, System.currentTimeMillis()).putString(SnapInstall.HB_VER, appVersion == null ? "" : appVersion).apply();
            }
        }, "snap-registry").start();
    }

    /** True only when the registry says this email+install is blocked. */
    static boolean suspended(android.content.Context c) {
        String email = SnapRegistry.normalizeEmail(Core.prefs(c).getString("email", null));
        SnapRegistry.Status st = fetchStatus(email, SnapInstall.id(c));
        return st != null && st.blocked();
    }

    private static boolean allowed(String url) {
        return url != null && url.startsWith("https://ghibfuinantybqidwadj.supabase.co/functions/v1/snap-users");
    }

    /** HTTPS only, no redirects (a redirect must not carry the client key). */
    private static String exchange(String method, String url, String jsonBody, int connectMs, int readMs) throws Exception {
        if (!allowed(url)) throw new SecurityException("registry host only");
        if (!"GET".equals(method) && !"POST".equals(method)) throw new SecurityException("client methods only");
        String key = BuildConfig.SNAP_REGISTRY_CLIENT_KEY;
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
        h.setInstanceFollowRedirects(false);
        h.setUseCaches(false);
        h.setRequestMethod(method);
        h.setConnectTimeout(connectMs);
        h.setReadTimeout(readMs);
        h.setRequestProperty("Authorization", "Bearer " + key);
        h.setRequestProperty("Accept", "application/json");
        try {
            if (jsonBody != null) {
                h.setDoOutput(true);
                h.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                try (OutputStream o = h.getOutputStream()) { o.write(jsonBody.getBytes(StandardCharsets.UTF_8)); }
            }
            int code = h.getResponseCode();
            if (code < 200 || code >= 300) return null; // includes redirects — do not follow
            InputStream in = h.getInputStream();
            return in == null ? "" : Core.read(in);
        } finally { h.disconnect(); }
    }
}
