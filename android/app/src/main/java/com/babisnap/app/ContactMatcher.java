package com.babisnap.app;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Stage-2 invites: finds the contacts meant by a spoken query ("תמר", "תמר עבודה", "דני מהגן").
 * Pure Java (unit-tested); contacts are read on the device and never leave it. Nothing here logs anything.
 * A contact matches only when EVERY word of the query is found in one of its fields
 * (given/family/display name, nickname, company, job title, groups, email, notes).
 */
final class ContactMatcher {
    /** One phone contact, already read from ContactsContract. */
    static final class Contact {
        final String displayName;
        final List<String> fields = new ArrayList<>();   // every searchable text
        final List<String> emails = new ArrayList<>();   // primary first
        final List<String> groups = new ArrayList<>();
        String company, jobTitle;
        Contact(String displayName) { this.displayName = displayName == null ? "" : displayName; if (displayName != null) fields.add(displayName); }
        Contact field(String f) { if (f != null && !f.trim().isEmpty()) fields.add(f); return this; }
        Contact email(String e) { if (e != null && e.contains("@") && !emails.contains(e.trim())) { emails.add(e.trim()); fields.add(e.trim()); } return this; }
        Contact group(String g) { if (g != null && !g.trim().isEmpty() && !groups.contains(g)) { groups.add(g); fields.add(g); } return this; }
        Contact org(String company, String title) { this.company = company; this.jobTitle = title; return field(company).field(title); }
    }

    /** A contact that can be invited (has an email). */
    static final class Match {
        final String name, email, tag;
        Match(String name, String email, String tag) { this.name = name; this.email = email; this.tag = tag; }
        @Override public String toString() { return name + " <" + email + ">"; }
    }

    static final class Result {
        final List<Match> matches = new ArrayList<>();
        /** someone matched the words but has no email ("תמר נמצאה אבל בלי מייל") */
        boolean matchedWithoutEmail;
    }

    /** Hebrew-aware normal form: no niqqud/cantillation, final letters -> regular, no geresh/quotes, lower case. */
    static String norm(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            if (c >= '\u0591' && c <= '\u05C7' && c != '\u05BE') continue;         // niqqud & te'amim (keep maqaf -> space below)
            switch (c) {
                case 'ך': c = 'כ'; break; case 'ם': c = 'מ'; break; case 'ן': c = 'נ'; break;
                case 'ף': c = 'פ'; break; case 'ץ': c = 'צ'; break;
                case '"': case '\'': case '״': case '׳': case '`': case '’': case '‘': case '“': case '”': continue;
                case '\u05BE': case '-': case '_': case '.': case ',': case '@': case '/': case '(': case ')': case '·': case '•': c = ' '; break;
                default:
            }
            b.append(c);
        }
        return b.toString().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    static List<String> words(String query) {
        List<String> w = new ArrayList<>();
        for (String t : norm(query).split(" ")) if (!t.isEmpty()) w.add(t);
        return w;
    }

    // Hebrew prefixes a marker word may carry when spoken: "מהעבודה", "מהגן", "שבעבודה", "מהחוג"
    private static final String[] PREFIXES = {"שמה", "שב", "מה", "מ", "ה", "ב", "ש", "ו", "ל"};

    /** One spoken word against one normalized field: a whole-word hit (or a word that starts with it, for 4+ letters). */
    static boolean wordIn(String w, String field) {
        if (hit(w, field)) return true;
        for (String p : PREFIXES)
            if (w.startsWith(p) && w.length() - p.length() >= 2 && hit(w.substring(p.length()), field)) return true;
        return false;
    }
    private static boolean hit(String w, String field) {
        for (String t : field.split(" ")) {
            if (t.equals(w)) return true;
            if (w.length() >= 4 && t.startsWith(w)) return true;
        }
        return false;
    }

    static boolean matches(Contact c, List<String> qw) {
        if (qw.isEmpty()) return false;
        List<String> nf = new ArrayList<>();
        for (String f : c.fields) nf.add(norm(f));
        for (String w : qw) {
            boolean found = false;
            for (String f : nf) if (wordIn(w, f)) { found = true; break; }
            if (!found) return false;
        }
        return true;
    }

    /** Small grey tag shown in the picker: "עבודה · דוגמה בע״מ" (group, then company / job title). */
    static String tag(Contact c) {
        Set<String> parts = new LinkedHashSet<>();
        for (String g : c.groups) if (parts.size() < 1) parts.add(g.trim());
        if (c.company != null && !c.company.trim().isEmpty()) parts.add(c.company.trim());
        else if (c.jobTitle != null && !c.jobTitle.trim().isEmpty()) parts.add(c.jobTitle.trim());
        return String.join(" · ", parts);
    }

    static Result find(String query, List<Contact> contacts) {
        Result r = new Result();
        List<String> qw = words(query);
        Set<String> seen = new LinkedHashSet<>();
        for (Contact c : contacts) {
            if (!matches(c, qw)) continue;
            if (c.emails.isEmpty()) { r.matchedWithoutEmail = true; continue; }
            String e = c.emails.get(0);
            if (!seen.add(e.toLowerCase(Locale.ROOT))) continue;     // the same person saved twice
            r.matches.add(new Match(c.displayName.isEmpty() ? e : c.displayName, e, tag(c)));
        }
        return r;
    }

    private ContactMatcher() {}
}
