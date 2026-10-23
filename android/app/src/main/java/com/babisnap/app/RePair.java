package com.babisnap.app;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 2.3: what to do when a valid BabiTk token arrives (pairing link or manual paste).
 * Pure Java (no Android) so it is unit-tested. Never logs or returns the token.
 */
public final class RePair {
    public enum Decision {
        /** No working connection (first pairing, disconnected, or 'needs reconnect' after 401): save silently. */
        SAVE,
        /** Incoming token equals the stored one: nothing to replace, report success, no dialog. */
        SAME,
        /** A working connection exists and the token differs: ask before replacing it. */
        CONFIRM
    }

    /**
     * @param stored      the currently stored token (null/empty = none)
     * @param needsRepair true when the server rejected the stored token (401) and the app shows 'reconnect'
     * @param incoming    the new, already validated token
     */
    public static Decision decide(String stored, boolean needsRepair, String incoming) {
        String s = stored == null ? "" : stored.trim();
        if (s.isEmpty() || needsRepair) return Decision.SAVE;
        if (incoming != null && sameToken(s, incoming.trim())) return Decision.SAME;
        return Decision.CONFIRM;
    }

    /** Constant-time comparison of two tokens. */
    static boolean sameToken(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private RePair() {}
}
