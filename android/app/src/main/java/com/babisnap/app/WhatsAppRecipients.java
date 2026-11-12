package com.babisnap.app;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Spoken WhatsApp target: a phone-contact name, or a group name the user will pick inside WhatsApp.
 * Pure Java. Nothing here is stored or logged. No alias list.
 */
public final class WhatsAppRecipients {
    /** Phone.TYPE_MOBILE and Phone.TYPE_WORK_MOBILE. */
    public static final int TYPE_MOBILE = 2;
    public static final int TYPE_WORK_MOBILE = 17;

    private static final String[] LEADING = {
        "הודעה", "תשלח", "שלח", "תשלחי", "תכתוב", "כתוב", "תכתבי", "תגיד", "תגידי"
    };
    private static final String[] SHORT_TAIL = {"י", "ה", "רי", "ית", "יה", "ון"};
    private static final Pattern GROUP = Pattern.compile(
        "(?:^|\\s)((?:אל\\s+)?ל?(?:קבוצת|קבוצה))(?=\\s|$)");
    private static final Pattern EL = Pattern.compile("(?:^|\\s)אל\\s+(\\S+)");
    private static final Pattern LAMED = Pattern.compile("(?:^|\\s)ל(\\S+)");

    public enum Target { PERSON, GROUP, NONE }

    public static final class Command {
        public final Target target;
        public final String name;
        public final String body;

        Command(Target target, String name, String body) {
            this.target = target;
            this.name = name == null ? "" : name;
            this.body = body == null ? "" : body;
        }
    }

    /** One number already read off the device. */
    public static final class Phone {
        public final String raw;
        public final boolean whatsApp;
        public final boolean mobile;

        public Phone(String raw, boolean whatsApp, boolean mobile) {
            this.raw = raw == null ? "" : raw;
            this.whatsApp = whatsApp;
            this.mobile = mobile;
        }
    }

    /** One aggregated contact. Only contacts with a usable Israeli number are offered. */
    public static final class Contact {
        public final String displayName;
        public final String given;
        public final String family;
        public final String nickname;
        public final List<Phone> phones;
        /** How often this contact was used. 0 when the device does not provide it. Order only, never a selection. */
        public final int timesContacted;
        /** Last time the contact was used, milliseconds. 0 when unavailable. Order only. */
        public final long lastTimeContacted;

        public Contact(String displayName, String given, String family, String nickname, List<Phone> phones) {
            this(displayName, given, family, nickname, phones, 0, 0L);
        }

        public Contact(String displayName, String given, String family, String nickname, List<Phone> phones,
                       int timesContacted, long lastTimeContacted) {
            this.displayName = displayName == null ? "" : displayName;
            this.given = given == null ? "" : given;
            this.family = family == null ? "" : family;
            this.nickname = nickname == null ? "" : nickname;
            this.phones = phones == null ? new ArrayList<>() : phones;
            this.timesContacted = Math.max(0, timesContacted);
            this.lastTimeContacted = Math.max(0L, lastTimeContacted);
        }

        public String digits() { return chooseNumber(this); }
        public String pretty() {
            String d = digits();
            return d == null ? "" : "+" + d;
        }
        public String label() {
            String n = displayName.trim();
            return n.isEmpty() ? pretty() : n;
        }
    }

    public enum MatchCode { ONE, MANY, NONE }

    public static final class Matches {
        public final MatchCode code;
        public final List<Contact> hits;
        final int tier;

        Matches(MatchCode code, List<Contact> hits, int tier) {
            this.code = code;
            this.hits = hits;
            this.tier = tier;
        }

        public boolean one() { return code == MatchCode.ONE && hits.size() == 1; }
    }

    public static boolean isWhatsAppAccount(String type) {
        return "com.whatsapp".equals(type) || "com.whatsapp.w4b".equals(type);
    }

    public static boolean isMobileType(int type) {
        return type == TYPE_MOBILE || type == TYPE_WORK_MOBILE;
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

    public static String toIlE164(String raw) {
        String digits = toIlDigits(raw);
        return digits == null ? null : "+" + digits;
    }

    /** Private chat id. Never a group id. */
    public static String contactJid(String raw) {
        String digits = toIlDigits(raw);
        return digits == null ? null : digits + "@s.whatsapp.net";
    }

    public static boolean isPrivateJid(String jid) {
        if (jid == null || !jid.endsWith("@s.whatsapp.net")) return false;
        if (jid.toLowerCase(Locale.US).contains("g.us")) return false;
        return contactJid(jid.substring(0, jid.indexOf('@'))) != null;
    }

    public static String waMeUrl(String e164Digits, String text) {
        String digits = e164Digits == null ? "" : (e164Digits.startsWith("+") ? e164Digits.substring(1) : e164Digits);
        String body = text == null ? "" : text;
        return "https://wa.me/" + digits + "?text=" + URLEncoder.encode(body, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** WhatsApp number if the contact has one, otherwise a mobile number, otherwise any Israeli number. */
    public static String chooseNumber(Contact contact) {
        if (contact == null) return null;
        String wa = null, mobile = null, any = null;
        for (Phone p : contact.phones) {
            String d = toIlDigits(p.raw);
            if (d == null) continue;
            if (p.whatsApp && wa == null) wa = d;
            if (p.mobile && mobile == null) mobile = d;
            if (any == null) any = d;
        }
        if (wa != null) return wa;
        if (mobile != null) return mobile;
        return any;
    }

    /** Same shape as a meeting headline: kind, then what the card will do. */
    public static String cardHeadline(Contact contact) {
        String who = contact == null || contact.label().isEmpty() ? "נמען" : contact.label();
        return "וואטסאפ · ייפתח אצל " + who;
    }

    public static String groupCardHeadline(String name) {
        String who = name == null || name.trim().isEmpty() ? "הקבוצה" : name.trim();
        return "וואטסאפ · קבוצה " + who;
    }

    public static String plainCardHeadline() { return "וואטסאפ · ייפתח בלי נמען"; }

    /** The draft on the card and in the WhatsApp compose box. Not a calendar title. */
    public static String shownText(Command command) {
        return command == null || command.body == null ? "" : command.body;
    }

    public static String plainBody(String utterance) {
        return cleanBody(stripChannel(side(utterance)));
    }

    /** Pull a person name, a group name, or neither. The channel words leave the body. */
    public static Command parse(String utterance) {
        String prepared = prep(utterance);
        int bar = prepared.indexOf('¦');
        String left = side(bar < 0 ? prepared : prepared.substring(0, bar));
        String right = bar < 0 ? null : side(prepared.substring(bar + 1));
        Matcher group = GROUP.matcher(left);
        if (group.find()) {
            String name = groupName(left, group);
            String body = right != null ? cleanBody(stripChannel(right)) : bodyWithout(left, group.start(1), group.end(1), name);
            return new Command(Target.GROUP, name, body);
        }
        Span person = personSpan(left);
        if (person == null) {
            String body = right != null ? cleanBody(stripChannel(right)) : plainBody(left);
            return new Command(Target.NONE, "", body);
        }
        String name = person.name;
        String body = right != null ? cleanBody(stripChannel(right)) : bodyWithout(left, person.start, person.end, name);
        return new Command(Target.PERSON, name, body);
    }

    /**
     * Best contacts for a spoken name. Prefixes ל/ש/ו/אל are optional.
     * One exact display name, first name, or nickname opens the confirm card even if a similar name is saved.
     * A multi-word name that is not exact still confirms when exactly one display name contains every spoken word.
     * A picker is for several exact names, several all-word names, or no exact hit (a near name such as רון/רוני).
     * Only contacts with an Israeli number are considered (WhatsApp).
     */
    public static Matches match(String spoken, List<Contact> contacts) {
        return matchAmong(spoken, withNumber(contacts));
    }

    /**
     * Several speech hypotheses. The first one with a unique exact contact confirms.
     * Otherwise the first hypothesis that matched at all stays a picker. An empty name is skipped.
     */
    public static Matches matchNames(List<String> names, List<Contact> contacts) {
        return matchNamesAmong(names, withNumber(contacts));
    }

    /** {@link #matchNames} against a list the caller already filtered. */
    public static Matches matchNamesAmong(List<String> names, List<Contact> contacts) {
        List<String> keys = new ArrayList<>();
        if (names != null) for (String n : names) {
            if (n == null || n.trim().isEmpty()) continue;
            String t = n.trim();
            if (!keys.contains(t)) keys.add(t);
        }
        if (keys.isEmpty()) return none();
        Matches fallback = null;
        for (String key : keys) {
            Matches m = matchAmong(key, contacts);
            if (m.one()) return m;
            if (fallback == null && m.code != MatchCode.NONE) fallback = m;
        }
        return fallback == null ? none() : fallback;
    }

    /**
     * Same name rules as {@link #match}, against a list the caller already filtered.
     * A phone call passes every contact with a dialable number, including numbers outside Israel.
     */
    public static Matches matchAmong(String spoken, List<Contact> eligible) {
        List<Contact> list = new ArrayList<>();
        if (eligible != null) for (Contact c : eligible) if (c != null) list.add(c);
        String raw = ContactMatcher.norm(spoken);
        Matches direct = matchKey(raw, list);
        String stripped = stripOnePrefix(raw);
        if (stripped.equals(raw)) return direct;
        Matches alt = matchKey(stripped, list);
        if (direct.code == MatchCode.NONE) return alt;
        if (alt.code == MatchCode.NONE) return direct;
        if (alt.tier < direct.tier) return alt;
        return direct;
    }

    /** Contacts that have a number. Empty query returns all of them; otherwise name or digits. */
    public static List<Contact> search(List<Contact> contacts, String query) {
        List<Contact> ready = withNumber(contacts);
        String q = ContactMatcher.norm(query);
        if (q.isEmpty()) return ready;
        Matches hit = match(q, ready);
        if (hit.code != MatchCode.NONE) return hit.hits;
        String digits = query == null ? "" : query.replaceAll("\\D", "");
        List<Contact> out = new ArrayList<>();
        for (Contact c : ready) {
            String blob = ContactMatcher.norm(c.displayName + " " + c.given + " " + c.family + " " + c.nickname);
            if (blob.contains(q)) out.add(c);
            else if (phoneMatches(c.digits(), digits)) out.add(c);
        }
        return out;
    }

    private static Matches matchKey(String key, List<Contact> contacts) {
        if (key == null || fold(key).length() < 2) return none();
        String[] parts = key.split(" ");
        if (parts.length >= 2) return matchFull(key, parts[0], contacts);
        return matchSingle(key, contacts);
    }

    /** One spoken token. Exact wins. A unique spelling variant (רני/רוני) confirms. Near names stay a picker. */
    private static Matches matchSingle(String key, List<Contact> contacts) {
        List<Contact> exact = new ArrayList<>();
        List<Contact> variant = new ArrayList<>();
        List<Contact> near = new ArrayList<>();
        for (Contact c : contacts) {
            if (c == null) continue;
            int rel = tokenRelation(key, c);
            if (rel == 0) exact.add(c);
            else if (variantOf(key, c)) variant.add(c);
            else if (rel == 1) near.add(c);
        }
        byUse(exact);
        byUse(variant);
        byUse(near);
        if (exact.size() == 1) return new Matches(MatchCode.ONE, exact, 0);
        if (exact.size() > 1) return new Matches(MatchCode.MANY, exact, 0);
        if (variant.size() == 1) return new Matches(MatchCode.ONE, variant, 0);
        if (variant.size() > 1) return new Matches(MatchCode.MANY, variant, 0);
        if (near.isEmpty()) return none();
        return new Matches(MatchCode.MANY, near, 1);
    }

    /**
     * "רוני כהן" confirms when that full name is unique, even if another רוני exists.
     * Otherwise every spoken word must appear in one display name (any order, extra words allowed).
     * A full name that only hits a bare first name is a picker, not a miss.
     */
    private static Matches matchFull(String key, String first, List<Contact> contacts) {
        List<Contact> exact = new ArrayList<>();
        for (Contact c : contacts) if (c != null && exactFull(key, c)) exact.add(c);
        byUse(exact);
        if (exact.size() == 1) return new Matches(MatchCode.ONE, exact, 0);
        if (!exact.isEmpty()) return new Matches(MatchCode.MANY, exact, 0);
        List<Contact> allWords = new ArrayList<>();
        for (Contact c : contacts) if (c != null && containsAllWords(key, c)) allWords.add(c);
        byUse(allWords);
        if (allWords.size() == 1) return new Matches(MatchCode.ONE, allWords, 0);
        if (allWords.size() > 1) return new Matches(MatchCode.MANY, allWords, 0);
        Matches firstName = matchSingle(first, contacts);
        if (firstName.code == MatchCode.NONE) return none();
        return new Matches(MatchCode.MANY, firstName.hits, 1);
    }

    /**
     * Every spoken word is a whole token of the display name. Order does not matter.
     * A short diminutive suffix counts, and so does a medial ו/י spelling variant.
     */
    private static boolean containsAllWords(String key, Contact c) {
        List<String> want = spokenWords(key);
        if (want.size() < 2) return false;
        List<String> got = displayWords(c);
        for (String word : want) if (!wordIn(word, got)) return false;
        return true;
    }

    private static List<String> spokenWords(String key) {
        List<String> out = new ArrayList<>();
        if (key == null) return out;
        for (String w : key.split(" ")) {
            String folded = fold(w);
            if (folded.length() >= 2 && !out.contains(folded)) out.add(folded);
        }
        return out;
    }

    private static List<String> displayWords(Contact c) {
        List<String> out = new ArrayList<>();
        if (c == null || c.displayName == null) return out;
        for (String w : ContactMatcher.norm(c.displayName).split(" ")) {
            if (fold(w).length() >= 2) out.add(w);
        }
        return out;
    }

    /** Exact folded token, a diminutive, or a medial ו/י spelling variant. */
    private static boolean wordIn(String want, List<String> words) {
        for (String token : words) {
            String got = fold(token);
            if (got.length() < 2) continue;
            if (got.equals(want) || diminutive(want, got) || spellingVariant(want, got)) return true;
        }
        return false;
    }

    private static boolean variantOf(String key, Contact c) {
        String want = fold(key);
        if (want.length() < 2) return false;
        for (String token : tokens(c)) if (spellingVariant(want, fold(token))) return true;
        return false;
    }

    /**
     * Same name once ו and י in the middle are ignored. The first and last letters stay,
     * so רני/רוני and שרה/שירה match, and an exact pair is not a variant.
     */
    static boolean spellingVariant(String a, String b) {
        if (a == null || b == null || a.equals(b) || a.length() < 2 || b.length() < 2) return false;
        String ka = withoutMedialMater(a);
        String kb = withoutMedialMater(b);
        return ka.length() >= 2 && ka.equals(kb);
    }

    private static String withoutMedialMater(String folded) {
        StringBuilder out = new StringBuilder(folded.length());
        int n = folded.length();
        for (int i = 0; i < n; i++) {
            char c = folded.charAt(i);
            if (i > 0 && i < n - 1 && (c == 'ו' || c == 'י')) continue;
            out.append(c);
        }
        return out.toString();
    }

    /** 0 exact, 1 near, 2 neither. Picker order uses this. Exact is a whole token, never a prefix. */
    static int closeness(String spoken, Contact c) {
        if (c == null) return 2;
        String key = ContactMatcher.norm(spoken);
        String stripped = stripOnePrefix(key);
        if (!stripped.equals(key)) {
            int alt = closenessKey(stripped, c);
            int direct = closenessKey(key, c);
            return Math.min(direct, alt);
        }
        return closenessKey(key, c);
    }

    private static int closenessKey(String key, Contact c) {
        if (c == null || key == null || fold(key).length() < 2) return 2;
        if (exactFull(key, c)) return 0;
        int rel = tokenRelation(fold(key), c);
        return rel < 0 ? 2 : rel;
    }

    /** 0 exact token, 1 near, -1 none. A stored nickname that equals the key is exact. */
    private static int tokenRelation(String key, Contact c) {
        String want = fold(key);
        if (want.length() < 2) return -1;
        boolean near = false;
        for (String token : tokens(c)) {
            String got = fold(token);
            if (got.length() < 2) continue;
            if (got.equals(want)) return 0;
            if (nearTokens(want, got)) near = true;
        }
        return near ? 1 : -1;
    }

    private static boolean exactFull(String key, Contact c) {
        String want = ContactMatcher.norm(key);
        String folded = fold(key);
        if (folded.length() < 2) return false;
        for (String form : new String[]{
            c.displayName, (c.given + " " + c.family).trim(), c.nickname
        }) {
            String n = ContactMatcher.norm(form);
            if (n.equals(want) || fold(form).equals(folded)) return true;
        }
        return false;
    }

    private static List<String> tokens(Contact c) {
        List<String> out = new ArrayList<>();
        addToken(out, c.given);
        addToken(out, c.family);
        addToken(out, c.nickname);
        addToken(out, c.displayName);
        if (c.displayName != null) for (String w : c.displayName.split(" ")) addToken(out, w);
        return out;
    }

    private static void addToken(List<String> out, String raw) {
        if (raw == null || raw.trim().isEmpty() || out.contains(raw)) return;
        out.add(raw.trim());
    }

    private static boolean nearTokens(String a, String b) {
        if (a == null || b == null) return false;
        if (a.equals(b) || a.length() < 2 || b.length() < 2) return false;
        if (prefixEither(a, b) || diminutive(a, b)) return true;
        return edits(a, b) == 1;
    }

    /** Prefix either way. Equality is not a prefix, and a prefix is never an automatic confirm. */
    private static boolean prefixEither(String a, String b) {
        if (a.equals(b) || a.length() < 2 || b.length() < 2) return false;
        return a.startsWith(b) || b.startsWith(a);
    }

    private static boolean diminutive(String a, String b) {
        return shortTail(a, b) || shortTail(b, a);
    }

    private static boolean shortTail(String longer, String shorter) {
        if (shorter.length() < 2 || !longer.startsWith(shorter) || longer.length() == shorter.length()) return false;
        String extra = longer.substring(shorter.length());
        for (String tail : SHORT_TAIL) if (tail.equals(extra)) return true;
        return false;
    }

    /** One insert, delete, or substitution. Longer differences are not near. */
    private static int edits(String a, String b) {
        if (a.equals(b)) return 0;
        int n = a.length(), m = b.length();
        if (Math.abs(n - m) > 1) return 2;
        if (n > m) return edits(b, a);
        int i = 0, j = 0, edits = 0;
        while (i < n && j < m) {
            if (a.charAt(i) == b.charAt(j)) { i++; j++; continue; }
            if (++edits > 1) return 2;
            if (n == m) i++;
            j++;
        }
        if (i < n || j < m) edits++;
        return edits;
    }

    private static void byUse(List<Contact> list) {
        list.sort((a, b) -> {
            int times = Integer.compare(b.timesContacted, a.timesContacted);
            if (times != 0) return times;
            return Long.compare(b.lastTimeContacted, a.lastTimeContacted);
        });
    }

    /** Niqqud and final letters folded. Spaces removed so "תָּמָ ר" matches "תמר". No ו/י deletion. */
    static String fold(String s) { return ContactMatcher.norm(s).replace(" ", ""); }

    private static String stripOnePrefix(String n) {
        if (n.startsWith("אל ") && n.length() > 3) return n.substring(3).trim();
        if (n.length() >= 3) {
            char c = n.charAt(0);
            if ((c == 'ל' || c == 'ש' || c == 'ו') && n.charAt(1) != ' ') return n.substring(1);
        }
        return n;
    }

    private static boolean phoneMatches(String e164, String digits) {
        if (digits == null || digits.isEmpty() || e164 == null) return false;
        if (e164.contains(digits)) return true;
        String national = digits.startsWith("0") ? digits.substring(1) : digits;
        return !national.isEmpty() && e164.startsWith("972") && e164.substring(3).contains(national);
    }

    private static List<Contact> withNumber(List<Contact> contacts) {
        List<Contact> out = new ArrayList<>();
        if (contacts == null) return out;
        for (Contact c : contacts) if (c != null && c.digits() != null) out.add(c);
        return out;
    }

    private static Matches none() { return new Matches(MatchCode.NONE, new ArrayList<>(), 99); }

    /**
     * A colon after the name starts the message («וואטסאפ לתמרי: היי»).
     * A colon inside a clock («16:00», «16 : 00») stays in the message.
     */
    private static String prep(String raw) {
        if (raw == null) return "";
        StringBuilder b = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == ':' || c == '：') b.append(clockColon(raw, i) ? ':' : '¦');
            else b.append(c);
        }
        return b.toString();
    }

    private static boolean clockColon(String raw, int i) {
        int p = i - 1;
        while (p >= 0 && raw.charAt(p) == ' ') p--;
        int n = i + 1;
        while (n < raw.length() && raw.charAt(n) == ' ') n++;
        return p >= 0 && n < raw.length()
            && Character.isDigit(raw.charAt(p)) && Character.isDigit(raw.charAt(n));
    }

    private static String side(String raw) { return Classifier.normalize(raw); }

    private static String stripChannel(String text) {
        return Classifier.WHATSAPP.matcher(text).replaceAll(" ").replaceAll("\\s+", " ").trim();
    }

    private static String groupName(String left, Matcher group) {
        int channelAt = channelStart(left);
        int nameAt = group.end(1);
        if (channelAt > nameAt) {
            String gap = left.substring(nameAt, channelAt).trim();
            return gap;
        }
        return firstTokens(left.substring(nameAt).trim(), 1);
    }

    private static final class Span {
        final int start;
        final int end;
        final String name;
        Span(int start, int end, String name) { this.start = start; this.end = end; this.name = name; }
    }

    private static Span personSpan(String left) {
        int channelAt = channelStart(left);
        List<Span> found = new ArrayList<>();
        Matcher el = EL.matcher(left);
        while (el.find()) {
            Span s = spanAt(left, el.start(1), el.start(1), el.end(1), channelAt);
            int from = left.lastIndexOf("אל", el.start(1));
            if (from < 0) from = el.start(1);
            found.add(new Span(from, s.end, s.name));
        }
        Matcher lam = LAMED.matcher(left);
        while (lam.find()) {
            if (lam.group(1).startsWith("קבוצ")) continue;
            int lamed = lam.start(1) - 1;
            found.add(spanAt(left, lamed, lam.start(1), lam.end(1), channelAt));
        }
        if (found.isEmpty()) return null;
        boolean anyBefore = false;
        if (channelAt >= 0) for (Span s : found) if (s.start < channelAt) anyBefore = true;
        Span best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (Span s : found) {
            if (s.name.isEmpty()) continue;
            if (anyBefore && s.start >= channelAt) continue;
            int d = distance(s, channelAt);
            if (best == null || d < bestDistance) { best = s; bestDistance = d; }
        }
        return best;
    }

    private static Span spanAt(String text, int cutStart, int tokenStart, int tokenEnd, int channelAt) {
        String name = text.substring(tokenStart, tokenEnd);
        int end = tokenEnd;
        if (channelAt > tokenEnd) {
            String gap = text.substring(tokenEnd, channelAt).trim();
            if (!gap.isEmpty()) {
                String more = firstTokens(gap, 2);
                if (!more.isEmpty()) {
                    name = (name + " " + more).trim();
                    end = text.indexOf(more, tokenEnd) + more.length();
                    if (end < tokenEnd) end = tokenEnd;
                }
            }
        }
        return new Span(cutStart, end, name.trim());
    }

    private static int distance(Span s, int channelAt) {
        if (channelAt < 0) return s.start;
        return Math.min(Math.abs(s.start - channelAt), Math.abs(s.end - channelAt));
    }

    private static String firstTokens(String text, int max) {
        if (text == null || text.isEmpty() || max <= 0) return "";
        String[] parts = text.trim().split("\\s+");
        StringBuilder b = new StringBuilder();
        int n = 0;
        for (String p : parts) {
            if (p.isEmpty() || Classifier.WHATSAPP.matcher(p).matches()) break;
            if (n == max) break;
            if (n > 0) b.append(' ');
            b.append(p);
            n++;
        }
        return b.toString();
    }

    private static int channelStart(String text) {
        Matcher m = Classifier.WHATSAPP.matcher(text);
        return m.find() ? m.start() : -1;
    }

    private static String bodyWithout(String text, int cutStart, int cutEnd, String name) {
        String raw = text;
        if (cutStart >= 0 && cutEnd > cutStart && cutEnd <= raw.length()) {
            raw = raw.substring(0, cutStart) + " " + raw.substring(cutEnd);
        }
        raw = stripChannel(raw);
        if (name != null && !name.isEmpty()) raw = raw.replace(name, " ");
        return cleanBody(raw);
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

    private WhatsAppRecipients() {}
}
