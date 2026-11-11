package com.babisnap.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;

/**
 * Private WhatsApp recipients and the Hebrew "message to &lt;alias&gt;" split.
 * Pure Java. Numbers stay Israeli E.164. Groups are not stored.
 * Nothing here is logged.
 */
public final class WhatsAppRecipients {
    public static final String PREF = "wa_recipients";
    /** Review window before the chat opens. The user can cancel; nothing is sent. */
    public static final int OPEN_SECONDS = 8;
    public static final long OPEN_MS = OPEN_SECONDS * 1000L;

    private static final String[] LEADING = {
        "הודעה", "תשלח", "שלח", "תשלחי", "תכתוב", "כתוב", "תכתבי", "תגיד", "תגידי"
    };

    public static final class Person {
        public final String id;
        public final String name;
        public final String alias;
        /** Digits only, no plus. Israeli, 972… */
        public final String e164;

        public Person(String id, String name, String alias, String e164) {
            this.id = id == null ? "" : id;
            this.name = name == null ? "" : name;
            this.alias = alias == null ? "" : alias;
            this.e164 = e164 == null ? "" : e164;
        }

        public Person withAlias(String next) { return new Person(id, name, next == null ? "" : next, e164); }
        public Person withName(String next) { return new Person(id, next == null ? "" : next, alias, e164); }

        /** Who the card names. The spoken alias wins over the address-book name. */
        public String spokenName() {
            String a = alias.trim();
            if (!a.isEmpty()) return a;
            String n = name.trim();
            return n.isEmpty() ? prettyPhone() : n;
        }

        public String prettyPhone() { return e164.isEmpty() ? "" : "+" + e164; }

        /** Name and number under the alias, so the chat is not opened for the wrong person. */
        public String detail() {
            String phone = prettyPhone();
            String n = name.trim();
            String a = alias.trim();
            if (!n.isEmpty() && !n.equals(a)) return phone.isEmpty() ? n : n + " · " + phone;
            return phone;
        }
    }

    public enum Code { UNIQUE, CHOOSE }

    public static final class Resolved {
        public final Code code;
        public final Person person;
        public final String body;

        private Resolved(Code code, Person person, String body) {
            this.code = code;
            this.person = person;
            this.body = body == null ? "" : body;
        }

        public boolean unique() { return code == Code.UNIQUE && person != null; }
    }

    public static String newId() { return "wa-" + UUID.randomUUID(); }

    /** First word of the address-book name, as a starting alias the user can edit. */
    public static String suggestAlias(String displayName) {
        String n = Classifier.normalize(displayName);
        if (n.isEmpty()) return "";
        int space = n.indexOf(' ');
        return space < 0 ? n : n.substring(0, space);
    }

    public static String toIlDigits(String raw) {
        if (raw == null) return null;
        StringBuilder d = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c >= '0' && c <= '9') d.append(c);
            else if (c == '+' && d.length() == 0) d.append('+');
        }
        String s = d.toString();
        if (s.startsWith("+")) s = s.substring(1);
        if (s.startsWith("00")) s = s.substring(2);
        if (s.startsWith("0") && s.length() >= 9 && s.length() <= 10) s = "972" + s.substring(1);
        else if (s.startsWith("5") && s.length() == 9) s = "972" + s;
        if (!s.startsWith("972") || s.length() < 11 || s.length() > 13) return null;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') return null;
        }
        return s;
    }

    /** Local 05… becomes +9725…. Null when the number is not an Israeli one we can open. */
    public static String toIlE164(String raw) {
        String digits = toIlDigits(raw);
        return digits == null ? null : "+" + digits;
    }

    /** Private chat id. Accepts 05…, +972…, or digits already in 972…. Never a group id. */
    public static String contactJid(String raw) {
        String digits = toIlDigits(raw);
        if (digits == null) return null;
        return digits + "@s.whatsapp.net";
    }

    public static boolean isPrivateJid(String jid) {
        return jid != null && jid.endsWith("@s.whatsapp.net") && contactJid(jid.substring(0, jid.indexOf('@'))) != null
            && !jid.toLowerCase(Locale.US).contains("g.us");
    }

    /** https://wa.me/&lt;digits&gt;?text=… — the user still taps Send in WhatsApp. */
    public static String waMeUrl(String e164Digits, String text) {
        String digits = e164Digits == null ? "" : (e164Digits.startsWith("+") ? e164Digits.substring(1) : e164Digits);
        String body = text == null ? "" : text;
        return "https://wa.me/" + digits + "?text=" + urlEncode(body);
    }

    private static String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    public static String headline(Person person) {
        String who = person == null ? "נמען" : person.spokenName();
        return "לפתוח שיחה עם " + who + "?";
    }

    public static List<Person> parse(String json) {
        List<Person> out = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) return out;
        try {
            JSONArray a = new JSONArray(json);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                if (o.has("jid") && String.valueOf(o.optString("jid")).contains("g.us")) continue;
                String digits = toIlDigits(o.optString("e164", ""));
                if (digits == null) digits = toIlDigits(o.optString("phone", ""));
                if (digits == null) continue;
                String id = o.optString("id", "").trim();
                if (id.isEmpty()) id = newId();
                String name = o.optString("name", "").trim();
                String alias = o.optString("alias", "").trim();
                out.add(new Person(id, name.isEmpty() ? "+" + digits : name, alias, digits));
            }
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
        return out;
    }

    public static String toJson(List<Person> people) {
        JSONArray a = new JSONArray();
        if (people != null) {
            for (Person p : people) {
                if (p == null || toIlDigits(p.e164) == null) continue;
                JSONObject o = new JSONObject();
                try {
                    o.put("id", p.id);
                    o.put("name", p.name);
                    o.put("alias", p.alias);
                    o.put("e164", p.e164);
                    a.put(o);
                } catch (Exception ignored) { /* one bad row is skipped */ }
            }
        }
        return a.toString();
    }

    /** Insert or refresh by phone. Keeps the saved alias when the new one is blank. */
    public static List<Person> upsert(List<Person> people, String name, String alias, String rawPhone) {
        String digits = toIlDigits(rawPhone);
        if (digits == null) return null;
        List<Person> out = new ArrayList<>();
        boolean found = false;
        String keptAlias = alias == null ? "" : alias.trim();
        String keptName = name == null || name.trim().isEmpty() ? "+" + digits : name.trim();
        if (people != null) {
            for (Person p : people) {
                if (!found && digits.equals(p.e164)) {
                    String nextAlias = keptAlias.isEmpty() ? p.alias : keptAlias;
                    out.add(new Person(p.id, keptName, nextAlias, digits));
                    found = true;
                } else out.add(p);
            }
        }
        if (!found) out.add(new Person(newId(), keptName, keptAlias, digits));
        return out;
    }

    public static List<Person> withAlias(List<Person> people, String id, String alias) {
        List<Person> out = new ArrayList<>();
        String next = alias == null ? "" : alias.trim();
        if (people != null) for (Person p : people) out.add(p.id.equals(id) ? p.withAlias(next) : p);
        return out;
    }

    public static List<Person> without(List<Person> people, String id) {
        List<Person> out = new ArrayList<>();
        if (people != null) for (Person p : people) if (!p.id.equals(id)) out.add(p);
        return out;
    }

    /** "050" matches a stored 97250… number, and the full digits match too. */
    private static boolean phoneMatches(String e164, String digits) {
        if (digits == null || digits.isEmpty() || e164 == null) return false;
        if (e164.contains(digits)) return true;
        String national = digits.startsWith("0") ? digits.substring(1) : digits;
        return !national.isEmpty() && e164.startsWith("972") && e164.substring(3).contains(national);
    }

    public static List<Person> search(List<Person> people, String query) {
        if (people == null) return new ArrayList<>();
        String q = Classifier.normalize(query).toLowerCase(Locale.ROOT);
        String digits = query == null ? "" : query.replaceAll("\\D", "");
        if (q.isEmpty() && digits.isEmpty()) return new ArrayList<>(people);
        List<Person> out = new ArrayList<>();
        for (Person p : people) {
            String blob = (p.alias + " " + p.name + " " + p.prettyPhone()).toLowerCase(Locale.ROOT);
            if (!q.isEmpty() && Classifier.normalize(blob).toLowerCase(Locale.ROOT).contains(q)) out.add(p);
            else if (phoneMatches(p.e164, digits)) out.add(p);
        }
        return out;
    }

    /** Channel words and leading "הודעה / שלח" removed. Alias stays, so a guess is never deleted. */
    public static String plainBody(String utterance) {
        return cleanBody(Classifier.WHATSAPP.matcher(Classifier.normalize(utterance)).replaceAll(" "));
    }

    /**
     * One saved alias, or a chooser. Same-length aliases at the same distance are a chooser,
     * including two people who share an alias. Never picks one of them silently.
     */
    public static Resolved resolve(String utterance, List<Person> people) {
        String text = Classifier.normalize(utterance);
        String plain = plainBody(utterance);
        if (people == null || people.isEmpty()) return new Resolved(Code.CHOOSE, null, plain);
        List<Hit> hits = new ArrayList<>();
        for (Person p : people) {
            String known = Classifier.normalize(p.alias);
            if (known.length() < 2) continue;
            Hit hit = findLabel(text, known, p);
            if (hit != null) hits.add(hit);
        }
        if (hits.isEmpty()) return new Resolved(Code.CHOOSE, null, plain);
        Matcher channel = Classifier.WHATSAPP.matcher(text);
        if (!channel.find()) return new Resolved(Code.CHOOSE, null, plain);
        final int anchor = channel.start();
        final int channelEnd = channel.end();
        hits.sort((a, b) -> {
            int byLength = Integer.compare(b.known.length(), a.known.length());
            if (byLength != 0) return byLength;
            return Integer.compare(distance(a, anchor), distance(b, anchor));
        });
        Hit winner = hits.get(0);
        int winnerDistance = distance(winner, anchor);
        for (int i = 1; i < hits.size(); i++) {
            Hit other = hits.get(i);
            if (other.person.id.equals(winner.person.id)) continue;
            if (other.known.length() == winner.known.length() && distance(other, anchor) == winnerDistance)
                return new Resolved(Code.CHOOSE, null, plain);
        }
        String raw = cut(text, anchor, channelEnd, winner.start, winner.end);
        raw = Classifier.WHATSAPP.matcher(raw).replaceAll(" ");
        return new Resolved(Code.UNIQUE, winner.person, cleanBody(raw));
    }

    /** Body for a person the user picked in the chooser. Strips that alias only when it is actually there. */
    public static String bodyFor(Person person, String utterance) {
        if (person == null) return plainBody(utterance);
        Resolved one = resolve(utterance, Collections.singletonList(person));
        return one.unique() ? one.body : plainBody(utterance);
    }

    private static int distance(Hit hit, int anchor) {
        return anchor < 0 ? hit.start : Math.abs(hit.start - anchor);
    }

    private static Hit findLabel(String text, String known, Person person) {
        String[] needles = {"ל" + known, "אל " + known};
        Hit best = null;
        for (String needle : needles) {
            int from = 0;
            while (from < text.length()) {
                int at = indexOf(text, needle, from);
                if (at < 0) break;
                int end = at + needle.length();
                if (boundaryBefore(text, at) && boundaryAfter(text, end)) {
                    if (best == null || needle.length() > (best.end - best.start))
                        best = new Hit(at, end, known, person);
                }
                from = at + 1;
            }
        }
        return best;
    }

    private static int indexOf(String text, String needle, int from) {
        String hay = text.toLowerCase(Locale.ROOT);
        String n = needle.toLowerCase(Locale.ROOT);
        if (hay.length() != text.length() || n.length() != needle.length()) return text.indexOf(needle, from);
        return hay.indexOf(n, from);
    }

    private static boolean boundaryBefore(String text, int index) {
        return index == 0 || Character.isWhitespace(text.charAt(index - 1));
    }

    private static boolean boundaryAfter(String text, int end) {
        if (end >= text.length()) return true;
        char c = text.charAt(end);
        return Character.isWhitespace(c) || c == ':' || c == ',' || c == '.' || c == '،';
    }

    private static String cut(String text, int aStart, int aEnd, int bStart, int bEnd) {
        int firstStart = Math.min(aStart, bStart);
        int firstEnd = firstStart == aStart ? aEnd : bEnd;
        int secondStart = Math.max(aStart, bStart);
        int secondEnd = secondStart == aStart ? aEnd : bEnd;
        if (secondStart < firstEnd) {
            int end = Math.max(firstEnd, secondEnd);
            return text.substring(0, Math.max(0, firstStart)) + text.substring(Math.min(text.length(), end));
        }
        StringBuilder out = new StringBuilder();
        out.append(text, 0, firstStart);
        out.append(text, firstEnd, secondStart);
        out.append(text, secondEnd, text.length());
        return out.toString();
    }

    private static String cleanBody(String raw) {
        String text = Classifier.normalize(raw);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String word : LEADING) {
                if (text.equals(word)) return "";
                if (text.startsWith(word + " ")) {
                    text = Classifier.normalize(text.substring(word.length()));
                    changed = true;
                }
            }
        }
        if (text.startsWith(":")) text = Classifier.normalize(text.substring(1));
        return Classifier.stripConnectorShin(text).trim();
    }

    private static final class Hit {
        final int start;
        final int end;
        final String known;
        final Person person;

        Hit(int start, int end, String known, Person person) {
            this.start = start;
            this.end = end;
            this.known = known;
            this.person = person;
        }
    }

    private WhatsAppRecipients() {}
}
