package com.babisnap.app;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Fetches the user's own BabiTk tag list (GET voice-ingest, same device token) into Tags.saveCached.
 * Runs on a background thread, at most once per 12 h; never blocks recording. On 401/429/network error the
 * existing cache and the embedded fallback list stay as they are. The token is never logged or shown.
 */
public final class TagSync {
    static final long INTERVAL_MS = 12L * 60 * 60 * 1000;   // success / 401 / 429 / bad body
    static final long NET_RETRY_MS = 60L * 60 * 1000;       // network error: try again after 1 h
    static final String AT = "babitk_tags_at", NEXT = "babitk_tags_next_at";
    private static volatile boolean running;

    /** Pure: tag names from {"tags":[{"name":..,"color":..}]} in display order; null if the body is not that shape. */
    static List<String> parse(String body) {
        try {
            JSONArray a = new JSONObject(body).optJSONArray("tags");
            if (a == null) return null;
            List<String> out = new ArrayList<>();
            for (int i = 0; i < a.length(); i++) {
                Object o = a.opt(i); String n = null;
                if (o instanceof JSONObject) n = ((JSONObject) o).optString("name", null);
                else if (o instanceof String) n = (String) o;
                n = Tags.clean(n);
                if (!n.isEmpty() && n.length() <= Tags.TAG_MAX && !out.contains(n)) out.add(n);
            }
            return out;
        } catch (Exception e) { return null; }
    }

    /** Pure throttle: fetch when forced (fresh pairing) or when the next allowed time has passed (clock moved back = due). */
    static boolean due(long nextAt, long lastAt, long now, boolean force) {
        if (force) return true;
        if (lastAt > now) return true;
        return now >= nextAt;
    }
    /** Pure: next allowed fetch time for an HTTP code (-1 = network error). */
    static long nextAfter(int code, long now) { return now + (code < 0 ? NET_RETRY_MS : INTERVAL_MS); }

    /** Call from app open / settings screen (force=false) and after pairing (force=true). Returns immediately. */
    public static void maybeFetch(Context ctx, boolean force) {
        final Context c = ctx.getApplicationContext();
        long now = System.currentTimeMillis();
        android.content.SharedPreferences p = Core.prefs(c);
        if (running || !due(p.getLong(NEXT, 0), p.getLong(AT, 0), now, force)) return;
        if (!BabiTk.hasToken(c)) return;
        running = true;
        Thread t = new Thread(() -> {
            int code = -1;
            try {
                String tok = BabiTk.token(c);
                if (tok == null) return;
                Core.Resp r = Core.http("GET", BabiTkJson.URL, tok, null);
                code = r.code;
                if (r.code == 200) {
                    List<String> names = parse(r.body);
                    if (names != null) { Tags.saveCached(c, names); BabiTk.note(c, "babitk_tags_status", "תגיות עודכנו מ-BabiTk: " + names.size()); }
                    else BabiTk.note(c, "babitk_tags_status", "תשובת תגיות לא תקינה – נשארה הרשימה הקודמת");
                } else BabiTk.note(c, "babitk_tags_status", "HTTP " + r.code + " – נשארה רשימת התגיות הקודמת");
            } catch (Exception e) {
                BabiTk.note(c, "babitk_tags_status", "אין חיבור – נשארה רשימת התגיות הקודמת");
            } finally {
                long n = System.currentTimeMillis();
                Core.prefs(c).edit().putLong(AT, n).putLong(NEXT, nextAfter(code, n)).apply();
                running = false;
            }
        }, "babitk-tags");
        t.setDaemon(true); t.setPriority(Thread.MIN_PRIORITY); t.start();
    }
    private TagSync() {}
}
