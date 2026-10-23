package com.babisnap.app;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One pass over the local queue. Pure Java (no Android), so the 401/429/400 handling is unit-tested. */
public final class SendLoop {
    public interface Transport { Core.Resp send(String body) throws Exception; }
    public static final class Item {
        public final String id, body;
        public Item(String id, String body) { this.id = id; this.body = body; }
    }
    public static final class Report {
        public final List<String> sent = new ArrayList<>();
        public final Map<String, String> failed = new LinkedHashMap<>(); // id -> readable server reason (400 etc.)
        public boolean unauthorized, rateLimited, retry;
        public String retryAfter; public int lastCode;
    }

    /**
     * Sends items in order. 2xx -> sent; 400/other 4xx -> failed (kept, visible, not retried);
     * 401 -> stop (token is bad; nothing else is attempted, nothing is dropped); 429 -> stop and back off;
     * 5xx/network -> stop and let WorkManager retry.
     */
    public static Report run(List<Item> items, Transport t) {
        Report r = new Report();
        for (Item it : items) {
            Core.Resp resp;
            try { resp = t.send(it.body); } catch (Exception e) { resp = new Core.Resp(-1, ""); }
            r.lastCode = resp.code;
            switch (BabiTkJson.outcome(resp.code)) {
                case DONE: r.sent.add(it.id); break;
                case FAILED: r.failed.put(it.id, BabiTkJson.errorText(resp.code, resp.body)); break;
                case UNAUTHORIZED: r.unauthorized = true; return r;
                case RATE_LIMITED: r.rateLimited = true; r.retryAfter = resp.retryAfter; return r;
                case RETRY: r.retry = true; return r;
            }
        }
        return r;
    }

    /** On 401, wipe the stored token only if it is still the one that was rejected (a fresh pairing made meanwhile survives). */
    public static boolean shouldWipe(String storedNow, String rejected) { return storedNow != null && storedNow.equals(rejected); }
    private SendLoop() {}
}
