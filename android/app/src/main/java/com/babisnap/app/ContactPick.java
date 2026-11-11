package com.babisnap.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Order and filter for the WhatsApp and call contact pickers.
 * Pure Java. Nothing here is stored or logged.
 * A row tap is the only way out with a contact. The picker never counts down and never acts alone.
 */
final class ContactPick {
    /** The list itself never places a call and never opens WhatsApp. */
    static boolean autoActs() { return false; }

    /** Contact for a row tap. Cancel, a timeout, and a bad index select nobody. */
    static WhatsAppRecipients.Contact picked(List<WhatsAppRecipients.Contact> shown, int index) {
        if (shown == null || index < 0 || index >= shown.size()) return null;
        return shown.get(index);
    }

    /**
     * Exact first name or full name, then names that start with the spoken name, then the rest.
     * An empty spoken name keeps the original order. Niqqud and spaces are ignored.
     */
    static List<WhatsAppRecipients.Contact> rank(String spoken, List<WhatsAppRecipients.Contact> contacts) {
        List<WhatsAppRecipients.Contact> src = contacts == null ? Collections.emptyList() : contacts;
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < src.size(); i++) if (src.get(i) != null) order.add(i);
        order.sort((a, b) -> {
            int cmp = Integer.compare(band(spoken, src.get(a)), band(spoken, src.get(b)));
            return cmp != 0 ? cmp : Integer.compare(a, b);
        });
        List<WhatsAppRecipients.Contact> out = new ArrayList<>();
        for (int i : order) out.add(src.get(i));
        return out;
    }

    /** Name or number. Niqqud and spaces are ignored. An empty query keeps every contact. */
    static List<WhatsAppRecipients.Contact> filter(List<WhatsAppRecipients.Contact> contacts, String query) {
        List<WhatsAppRecipients.Contact> src = contacts == null ? Collections.emptyList() : contacts;
        if (query == null || query.trim().isEmpty()) {
            List<WhatsAppRecipients.Contact> all = new ArrayList<>();
            for (WhatsAppRecipients.Contact c : src) if (c != null) all.add(c);
            return all;
        }
        String q = squash(query);
        String digits = digits(query);
        List<WhatsAppRecipients.Contact> out = new ArrayList<>();
        for (WhatsAppRecipients.Contact c : src) if (c != null && matches(c, q, digits)) out.add(c);
        return out;
    }

    /**
     * Rows the picker draws. A typed name re-ranks by that text.
     * A typed number keeps the spoken-name order and only hides non-matching numbers.
     */
    static List<WhatsAppRecipients.Contact> present(String spoken, List<WhatsAppRecipients.Contact> people, String query) {
        String q = squash(query);
        String rankBy = !q.isEmpty() && !digitsOnly(q) ? query : spoken;
        return rank(rankBy, filter(people, query));
    }

    private static boolean matches(WhatsAppRecipients.Contact c, String q, String digits) {
        if (q.isEmpty()) return true;
        if (blob(c).contains(q)) return true;
        return digits.length() >= 2 && phoneHit(c, digits);
    }

    /** 0 exact first or full name, 1 starts with the spoken name, 2 the rest. */
    private static int band(String spoken, WhatsAppRecipients.Contact c) {
        String key = squash(spoken);
        if (key.isEmpty() || c == null) return 2;
        String first = squash(firstName(c));
        String full = squash(c.displayName);
        if ((!first.isEmpty() && first.equals(key)) || (!full.isEmpty() && full.equals(key))) return 0;
        if (starts(first, key) || starts(full, key)) return 1;
        return 2;
    }

    private static boolean starts(String name, String key) {
        return !name.isEmpty() && name.startsWith(key) && !name.equals(key);
    }

    private static String firstName(WhatsAppRecipients.Contact c) {
        String given = c.given == null ? "" : c.given.trim();
        if (!given.isEmpty()) return firstWord(given);
        return firstWord(c.displayName);
    }

    private static String firstWord(String raw) {
        if (raw == null) return "";
        String t = raw.trim();
        if (t.isEmpty()) return "";
        int sp = t.indexOf(' ');
        return sp < 0 ? t : t.substring(0, sp);
    }

    static String squash(String s) { return ContactMatcher.norm(s).replace(" ", ""); }

    private static String blob(WhatsAppRecipients.Contact c) {
        return squash((c.displayName == null ? "" : c.displayName) + " "
            + (c.given == null ? "" : c.given) + " "
            + (c.family == null ? "" : c.family) + " "
            + (c.nickname == null ? "" : c.nickname));
    }

    private static boolean digitsOnly(String q) {
        if (q == null || q.isEmpty()) return false;
        for (int i = 0; i < q.length(); i++) {
            char ch = q.charAt(i);
            if (ch < '0' || ch > '9') return false;
        }
        return true;
    }

    private static String digits(String raw) {
        if (raw == null) return "";
        StringBuilder b = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (ch >= '0' && ch <= '9') b.append(ch);
        }
        return b.toString();
    }

    private static boolean phoneHit(WhatsAppRecipients.Contact c, String needle) {
        if (c.phones == null) return false;
        for (WhatsAppRecipients.Phone p : c.phones) {
            if (p == null) continue;
            for (String form : phoneForms(p.raw)) if (form.contains(needle)) return true;
        }
        return false;
    }

    private static List<String> phoneForms(String raw) {
        String d = digits(raw);
        List<String> out = new ArrayList<>();
        if (d.isEmpty()) return out;
        out.add(d);
        if (d.startsWith("972") && d.length() > 3) out.add("0" + d.substring(3));
        else if (d.startsWith("0") && d.length() > 1) out.add("972" + d.substring(1));
        return out;
    }

    private ContactPick() {}
}
