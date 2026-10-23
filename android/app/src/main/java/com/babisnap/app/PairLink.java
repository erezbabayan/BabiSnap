package com.babisnap.app;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Parses babisnap://pair?token=..&url=.. (also what Chrome delivers for intent://pair?..#Intent;scheme=babisnap;..;end).
 * 2.2: strict. The url param is required and must equal the hard-coded BabiTk endpoint exactly (after %-decoding);
 * the token must look like a BabiTk device token (babi_ + base64url of 32 bytes). Unknown params are ignored.
 * Pure Java; never logs or returns the token inside an error message.
 */
public final class PairLink {
    public static final String HOST = "ghibfuinantybqidwadj.supabase.co", PATH = "/functions/v1/voice-ingest";
    /** The only endpoint BabiSnap ever sends the device token to. */
    public static final String ENDPOINT = "https://" + HOST + PATH;
    /** @deprecated kept for source compatibility; same as {@link #ENDPOINT}. */
    @Deprecated public static final String DEFAULT_URL = ENDPOINT;
    /** BabiTk generateDeviceToken(): "babi_" + base64url(32 random bytes) without padding = 43 chars. */
    static final java.util.regex.Pattern TOKEN_RE = java.util.regex.Pattern.compile("^babi_[A-Za-z0-9_-]{43}$");

    public static final class Result {
        public final String token, error; // exactly one is non-null; error is a Hebrew reason (never contains the token)
        Result(String t, String e) { token = t; error = e; }
    }

    public static boolean validToken(String t) { return t != null && TOKEN_RE.matcher(t).matches(); }

    public static Result parse(String link) {
        if (link == null || link.trim().isEmpty()) return new Result(null, "הקישור ריק");
        String s = link.trim();
        if (s.length() > 2048) return new Result(null, "הקישור ארוך מדי");
        int h = s.indexOf('#'); if (h >= 0) s = s.substring(0, h);
        String lower = s.toLowerCase(java.util.Locale.ROOT);
        if (!lower.startsWith("babisnap://pair") && !lower.startsWith("babisnap:pair") && !lower.startsWith("intent://pair"))
            return new Result(null, "זה לא קישור חיבור של BabiSnap");
        int q = s.indexOf('?');
        if (q < 0) return new Result(null, "בקישור חסר טוקן");
        String tok = null, url = null; int tokCount = 0, urlCount = 0;
        for (String kv : s.substring(q + 1).split("&")) {
            int i = kv.indexOf('='); if (i <= 0) continue;
            String k = kv.substring(0, i).trim();
            if (k.equals("token")) { tokCount++; tok = decode(kv.substring(i + 1)); }
            else if (k.equals("url")) { urlCount++; url = decode(kv.substring(i + 1)); }
            // any other parameter is ignored
        }
        if (tokCount > 1 || urlCount > 1) return new Result(null, "בקישור יש פרמטר כפול");
        if (tok == null || tok.trim().isEmpty()) return new Result(null, "בקישור חסר טוקן");
        if (url == null || url.isEmpty()) return new Result(null, "בקישור חסרה כתובת השרת");
        if (!validUrl(url)) return new Result(null, "כתובת השרת בקישור לא מוכרת");
        tok = tok.trim();
        if (!validToken(tok)) return new Result(null, "הטוקן בקישור לא בפורמט של BabiTk");
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

    /** The (decoded) url must be exactly the hard-coded endpoint: no trailing slash, case change, port, query or extra path. */
    public static boolean validUrl(String url) { return url != null && ENDPOINT.equals(decode(url)); }
    private PairLink() {}
}
