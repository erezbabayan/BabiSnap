package com.babisnap.app;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Parses babisnap://pair?token=..&url=.. (also what Chrome delivers for intent://pair?..#Intent;scheme=babisnap;..;end).
 * Lenient on form (encoding, trailing slash, extra params, missing url), strict on target. Pure Java; never logs the token.
 */
public final class PairLink {
    public static final String HOST = "ghibfuinantybqidwadj.supabase.co", PATH = "/functions/v1/voice-ingest";
    public static final String DEFAULT_URL = "https://" + HOST + PATH;

    public static final class Result {
        public final String token, error; // exactly one is non-null; error is a Hebrew reason
        Result(String t, String e) { token = t; error = e; }
    }

    public static Result parse(String link) {
        if (link == null || link.trim().isEmpty()) return new Result(null, "הקישור ריק");
        String s = link.trim();
        int h = s.indexOf('#'); if (h >= 0) s = s.substring(0, h);
        String lower = s.toLowerCase(java.util.Locale.ROOT);
        if (!lower.startsWith("babisnap://pair") && !lower.startsWith("babisnap:pair") && !lower.startsWith("intent://pair"))
            return new Result(null, "זה לא קישור חיבור של BabiSnap");
        int q = s.indexOf('?');
        if (q < 0) return new Result(null, "בקישור חסר טוקן");
        String tok = null, url = null;
        for (String kv : s.substring(q + 1).split("&")) {
            int i = kv.indexOf('='); if (i <= 0) continue;
            String k = kv.substring(0, i).trim(), v = decode(kv.substring(i + 1));
            if (k.equals("token") && tok == null) tok = v; else if (k.equals("url") && url == null) url = v;
        }
        if (tok == null || tok.trim().isEmpty()) return new Result(null, "בקישור חסר טוקן");
        tok = tok.trim();
        if (tok.length() > 4096 || tok.matches(".*\\s.*")) return new Result(null, "הטוקן בקישור לא תקין");
        if (url != null && !url.trim().isEmpty() && !validUrl(url)) return new Result(null, "כתובת השרת בקישור לא מוכרת (רק " + HOST + ")");
        return new Result(tok, null);
    }

    /** Back-compat helper: token or null. */
    public static String token(String link) { return parse(link).token; }

    /** Decodes %-escapes (repeatedly, for double-encoded values) without turning a literal '+' into a space. */
    static String decode(String v) {
        String cur = v;
        for (int n = 0; n < 3 && cur.contains("%"); n++) {
            try {
                String next = URLDecoder.decode(cur.replace("+", "%2B"), StandardCharsets.UTF_8.name());
                if (next.equals(cur)) break; cur = next;
            } catch (Exception e) { break; }
        }
        return cur;
    }

    /** Normalises (decode, trim, drop trailing slash) then requires https + exact host + exact path. */
    public static boolean validUrl(String url) {
        if (url == null) return false;
        String u0 = decode(url).trim();
        while (u0.endsWith("/")) u0 = u0.substring(0, u0.length() - 1);
        try {
            URI u = new URI(u0);
            return "https".equalsIgnoreCase(u.getScheme()) && HOST.equalsIgnoreCase(u.getHost()) && u.getPort() == -1
                && u.getUserInfo() == null && PATH.equals(u.getPath()) && u.getQuery() == null && u.getFragment() == null;
        } catch (Exception e) { return false; }
    }
    private PairLink() {}
}
