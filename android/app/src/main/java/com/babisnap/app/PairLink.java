package com.babisnap.app;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/** Parses babisnap://pair?token=..&url=.. . Pure Java, unit-testable. Never logs the token. */
public final class PairLink {
    public static final String HOST = "ghibfuinantybqidwadj.supabase.co", PATH = "/functions/v1/voice-ingest";

    /** @return the token if the link is valid and the url is the exact BabiTk endpoint; otherwise null. */
    public static String token(String link) {
        try {
            URI u = new URI(link);
            if (!"babisnap".equalsIgnoreCase(u.getScheme()) || !"pair".equalsIgnoreCase(u.getHost())) return null;
            String q = u.getRawQuery(); if (q == null) return null;
            String tok = null, url = null;
            for (String kv : q.split("&")) {
                int i = kv.indexOf('='); if (i <= 0) continue;
                String k = kv.substring(0, i), v = URLDecoder.decode(kv.substring(i + 1), StandardCharsets.UTF_8.name());
                if (k.equals("token")) tok = v; else if (k.equals("url")) url = v;
            }
            if (tok == null || tok.trim().isEmpty() || tok.length() > 4096 || !validUrl(url)) return null;
            return tok.trim();
        } catch (Exception e) { return null; }
    }

    public static boolean validUrl(String url) {
        if (url == null) return false;
        try {
            URI u = new URI(url);
            return "https".equals(u.getScheme()) && HOST.equalsIgnoreCase(u.getHost()) && u.getPort() == -1
                && u.getUserInfo() == null && PATH.equals(u.getPath()) && u.getQuery() == null && u.getFragment() == null;
        } catch (Exception e) { return false; }
    }
    private PairLink() {}
}
