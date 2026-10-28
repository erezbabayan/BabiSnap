package com.babisnap.app;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import com.google.android.gms.auth.api.identity.AuthorizationRequest;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.common.api.Scope;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;

/** Shared app logic: prefs, parser, auth request, Calendar REST calls. */
public final class Core {
    public static final String SCOPE = "https://www.googleapis.com/auth/calendar.events";
    private static HebrewParser parser;

    public static SharedPreferences prefs(Context c) {
        SharedPreferences p = c.getSharedPreferences("babisnap", Context.MODE_PRIVATE);
        if (p.contains("tok")) p.edit().remove("tok").apply(); // 2.1 kept a Google access token here; 2.2 never stores Google tokens
        if (p.contains("preview")) p.edit().remove("preview").apply(); // 2.4: unused 'preview' setting removed
        return p;
    }
    public static int defaultDuration(Context c) { return prefs(c).getInt("dur", 60); }
    /** Stage-2 invites ('שליחת הזמנות'), default OFF. */
    public static boolean sendInvites(Context c) { return prefs(c).getBoolean("send_invites", false); }

    public static AuthorizationRequest authRequest() {
        return AuthorizationRequest.builder().setRequestedScopes(Collections.singletonList(new Scope(SCOPE))).build();
    }

    public static synchronized HebrewParser parser(Context c) throws Exception {
        if (parser == null) {
            try (InputStream in = c.getAssets().open("parser.js")) { parser = new HebrewParser(read(in)); }
        }
        return parser;
    }

    public static JSONObject parse(Context c, String text) throws Exception {
        java.time.LocalDateTime t = java.time.LocalDateTime.now(ZoneId.of(EventJson.TZ));
        int dow = t.getDayOfWeek().getValue() % 7; // Sunday=0
        return new JSONObject(parser(c).parseJson(text, t.getYear(), t.getMonthValue(), t.getDayOfMonth(), dow, defaultDuration(c),
                t.getHour(), t.getMinute()));
    }

    static final String EVENTS = "https://www.googleapis.com/calendar/v3/calendars/primary/events";

    /** Inserts into primary calendar. Returns HTTP code; throws on network error. */
    public static int insertEvent(String token, JSONObject p, String spoken) throws Exception { return insert(token, p, spoken).code; }

    static Resp insert(String token, JSONObject p, String spoken) throws Exception {
        String desc = "נוצר בהכתבה: " + spoken;
        String body = p.optBoolean("allDay", false)
                ? EventJson.buildAllDay(p.getString("title"), p.getString("startStr"), p.optString("location", ""), desc)
                : EventJson.build(p.getString("title"), p.getString("startStr"), p.getInt("durationMin"), p.optString("location", ""), desc);
        return http("POST", EVENTS, token, body);
    }

    /** id of the event just created (from the insert response), or null. */
    static String eventId(Resp r) {
        try { String id = new JSONObject(r.body).optString("id", ""); return id.isEmpty() ? null : id; } catch (Exception e) { return null; }
    }

    /** Stage-2 invites: adds the attendees and lets Google email them (sendUpdates=all). Same calendar.events scope. */
    static int addAttendees(String token, String eventId, java.util.List<String> emails) throws Exception {
        String url = EVENTS + "/" + java.net.URLEncoder.encode(eventId, "UTF-8") + "?sendUpdates=all";
        return http("PATCH", url, token, EventJson.attendees(emails)).code;
    }

    static final class Resp {
        final int code; final String body, retryAfter;
        Resp(int c, String b) { this(c, b, null); }
        Resp(int c, String b, String ra) { code = c; body = b; retryAfter = ra; }
    }

    /** Bearer token is only ever sent over https, and redirects are never followed (a redirect cannot carry the token elsewhere). */
    static Resp http(String method, String url, String token, String body) throws Exception {
        if (url == null || !url.startsWith("https://")) throw new SecurityException("https only");
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
        h.setInstanceFollowRedirects(false);
        h.setUseCaches(false);
        // HttpURLConnection has no PATCH: Google APIs accept POST + X-HTTP-Method-Override
        if ("PATCH".equals(method)) { h.setRequestMethod("POST"); h.setRequestProperty("X-HTTP-Method-Override", "PATCH"); }
        else h.setRequestMethod(method);
        h.setConnectTimeout(15000); h.setReadTimeout(20000);
        h.setRequestProperty("Authorization", "Bearer " + token);
        if (body != null) {
            h.setDoOutput(true);
            h.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            try (OutputStream o = h.getOutputStream()) { o.write(body.getBytes(StandardCharsets.UTF_8)); }
        }
        int code = h.getResponseCode();
        InputStream in = code < 400 ? h.getInputStream() : h.getErrorStream();
        String resp = in == null ? "" : read(in);
        String ra = h.getHeaderField("Retry-After");
        h.disconnect();
        return new Resp(code, resp, ra);
    }

    /** Account email = summary of the primary calendar (allowed by calendar.events scope). */
    public static String fetchEmail(String token) throws Exception {
        Resp r = http("GET", "https://www.googleapis.com/calendar/v3/calendars/primary/events?maxResults=1&fields=summary", token, null);
        if (r.code != 200) return null;
        return new JSONObject(r.body).optString("summary", null);
    }

    /** Revokes the Google grant behind an access token (token goes in the POST body, never in a URL). */
    public static void revoke(String token) {
        try {
            HttpURLConnection h = (HttpURLConnection) new URL("https://oauth2.googleapis.com/revoke").openConnection();
            h.setInstanceFollowRedirects(false);
            h.setRequestMethod("POST"); h.setDoOutput(true);
            h.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            try (OutputStream o = h.getOutputStream()) {
                o.write(("token=" + java.net.URLEncoder.encode(token, "UTF-8")).getBytes(StandardCharsets.UTF_8));
            }
            h.getResponseCode(); h.disconnect();
        } catch (Exception ignored) {}
    }

    static String read(InputStream in) throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream(); byte[] buf = new byte[8192]; int n;
        while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
        return b.toString("UTF-8");
    }

    static void requestAuth(Activity a, int reqCode, java.util.function.Consumer<String> onToken, java.util.function.Consumer<String> onError) {
        Identity.getAuthorizationClient(a).authorize(authRequest())
            .addOnSuccessListener(res -> {
                if (res.hasResolution()) {
                    try { a.startIntentSenderForResult(res.getPendingIntent().getIntentSender(), reqCode, null, 0, 0, 0, null); }
                    catch (Exception e) { onError.accept(e.toString()); }
                } else onToken.accept(res.getAccessToken());
            })
            .addOnFailureListener(e -> onError.accept(e.toString()));
    }

    static String tokenFromResult(Activity a, android.content.Intent data) throws Exception {
        return Identity.getAuthorizationClient(a).getAuthorizationResultFromIntent(data).getAccessToken();
    }
    private Core() {}
}
