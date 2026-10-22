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

    public static SharedPreferences prefs(Context c) { return c.getSharedPreferences("babisnap", Context.MODE_PRIVATE); }
    public static int defaultDuration(Context c) { return prefs(c).getInt("dur", 60); }
    public static boolean preview(Context c) { return prefs(c).getBoolean("preview", true); }

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
        LocalDate t = LocalDate.now(ZoneId.of(EventJson.TZ));
        int dow = t.getDayOfWeek().getValue() % 7; // Sunday=0
        return new JSONObject(parser(c).parseJson(text, t.getYear(), t.getMonthValue(), t.getDayOfMonth(), dow, defaultDuration(c)));
    }

    /** Inserts into primary calendar. Returns HTTP code; throws on network error. */
    public static int insertEvent(String token, JSONObject p, String spoken) throws Exception {
        String body = EventJson.build(p.getString("title"), p.getString("startStr"), p.getInt("durationMin"),
                p.optString("location", ""), "נוצר בהכתבה: " + spoken);
        return http("POST", "https://www.googleapis.com/calendar/v3/calendars/primary/events", token, body).code;
    }

    static final class Resp { final int code; final String body; Resp(int c, String b) { code = c; body = b; } }

    static Resp http(String method, String url, String token, String body) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
        h.setRequestMethod(method);
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
        return new Resp(code, resp);
    }

    /** Account email = summary of the primary calendar (allowed by calendar.events scope). */
    public static String fetchEmail(String token) throws Exception {
        Resp r = http("GET", "https://www.googleapis.com/calendar/v3/calendars/primary/events?maxResults=1&fields=summary", token, null);
        if (r.code != 200) return null;
        return new JSONObject(r.body).optString("summary", null);
    }

    public static void revoke(String token) {
        try {
            HttpURLConnection h = (HttpURLConnection) new URL("https://oauth2.googleapis.com/revoke?token=" + token).openConnection();
            h.setRequestMethod("POST"); h.setDoOutput(true); h.getOutputStream().close(); h.getResponseCode();
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
