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
            WhatsAppRecipients.Contact ca = src.get(a);
            WhatsAppRecipients.Contact cb = src.get(b);
            int cmp = Integer.compare(band(spoken, ca), band(spoken, cb));
            if (cmp != 0) return cmp;
            int times = Integer.compare(cb.timesContacted, ca.timesContacted);
            if (times != 0) return times;
            int recent = Long.compare(cb.lastTimeContacted, ca.lastTimeContacted);
            if (recent != 0) return recent;
            return Integer.compare(a, b);
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

    /** 0 exact, 1 near (prefix, diminutive, or one edit), 2 the rest. */
    private static int band(String spoken, WhatsAppRecipients.Contact c) {
        return WhatsAppRecipients.closeness(spoken, c);
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
