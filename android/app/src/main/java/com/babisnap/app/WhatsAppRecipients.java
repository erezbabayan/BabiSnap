package com.babisnap.app;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
    private static final Set<String> POSSESSIVE = new HashSet<>(Arrays.asList(
        "של", "שלי", "שלו", "שלה", "שלנו", "שלכם", "שלכן", "שלהם", "שלהן"));
    private static final Set<String> STANDALONE_POSSESSIVE = new HashSet<>(Arrays.asList(
        "שלי", "שלו", "שלה", "שלנו", "שלכם", "שלכן", "שלהם", "שלהן"));
    private static final int EXTRA_NAME_TOKENS = 4;
    private static final Pattern GROUP = Pattern.compile(
        "(?:^|\\s)((?:אל\\s+)?ל?(?:קבוצת|קבוצה))(?=\\s|$)");
    /** «אל תמרי», and a ל the recognizer split off: «ל תמרי» / «ל- תמרי». */
    private static final Pattern EL = Pattern.compile("(?:^|\\s)(?:אל|ל-?)\\s+(\\S+)");
    /** «לתמרי», also with a hyphen: «ל-תמרי». */
    private static final Pattern LAMED = Pattern.compile("(?:^|\\s)ל-?([^\\s-]\\S*)");
    /** Words a connector ש may stand in front of («שאני בדרך»). Any other ש-word is the message itself. */
    private static final Set<String> AFTER_CONNECTOR = new HashSet<>(Arrays.asList(
        "אני", "את", "אתה", "אתם", "אתן", "אנחנו", "הוא", "היא", "הם", "הן",
        "יש", "אין", "לא", "כבר", "עוד", "אולי", "מחר", "היום", "אתמול"));
    /** Words tried right after the channel word when no «ל+name» was said. */
    private static final int BARE_NAME_TOKENS = 3;

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

        final String usedKey;

        Matches(MatchCode code, List<Contact> hits, int tier) {
            this(code, hits, tier, "");
        }

        Matches(MatchCode code, List<Contact> hits, int tier, String usedKey) {
            this.code = code;
            this.hits = hits;
            this.tier = tier;
            this.usedKey = usedKey == null ? "" : usedKey;
        }

        Matches withUsed(String key) { return new Matches(code, hits, tier, key); }

        public boolean one() { return code == MatchCode.ONE && hits.size() == 1; }
    }

    /** A parsed command after the name hypotheses were tried against the contacts. */
    public static final class Resolution {
        public final Command command;
        public final Matches matches;
        Resolution(Command command, Matches matches) {
            this.command = command;
            this.matches = matches == null ? none() : matches;
        }
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
     * Best contacts for a spoken name. Prefixes ל/ב/ה/ו/ש/אל are optional.
     * Contacts are ranked: exact full name, then every content word present (gaps allowed; של-words
     * are ignored when comparing but may also match literally), then the highest partial word-count.
     * A unique top contact opens the confirm card. A tie opens a picker of only the top contacts.
     * A trailing message word is dropped by trying the longest prefix first.
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
     * Several spoken hypotheses. The first unique exact or all-words contact wins,
     * and the command name/body consume those tokens. Otherwise the first non-empty match stays.
     */
    public static Resolution resolveHeard(List<String> utterances, String fallback, List<Contact> contacts) {
        List<String> keys = new ArrayList<>();
        if (utterances != null) for (String u : utterances) {
            if (u == null || u.trim().isEmpty()) continue;
            String t = u.trim();
            if (!keys.contains(t)) keys.add(t);
        }
        if (fallback != null && !fallback.trim().isEmpty() && !keys.contains(fallback.trim())) keys.add(fallback.trim());
        Resolution found = null;
        for (String u : keys) {
            Resolution r = resolve(u, contacts);
            if (r.matches.one()) return r;
            if (found == null && r.matches.code != MatchCode.NONE) found = r;
        }
        if (found != null) return found;
        Command cmd = parse(keys.isEmpty() ? (fallback == null ? "" : fallback) : keys.get(0));
        return new Resolution(cmd, none());
    }

    /**
     * Try the parsed name plus up to four following tokens. Matching keeps the longest prefix
     * that is a strong name (exact, all content words, or a higher partial score) and leaves the rest in the body.
     */
    public static Resolution resolve(String utterance, List<Contact> contacts) {
        Command cmd = parse(utterance);
        if (cmd.target == Target.NONE) {
            Resolution bare = resolveBareName(utterance, contacts);
            if (bare != null) return bare;
        }
        if (cmd.target != Target.PERSON || cmd.name.isEmpty()) return new Resolution(cmd, none());
        String longKey = longestPersonKey(cmd);
        // The words after the name are the message here, so one saved first name confirms on its own.
        Matches m = matchAmong(longKey, withNumber(contacts), true);
        String used = m.usedKey != null && !m.usedKey.isEmpty() ? m.usedKey : cmd.name;
        return new Resolution(withConsumedName(cmd, used), m);
    }

    /**
     * Same name rules as {@link #match}, against a list the caller already filtered.
     * A phone call passes every contact with a dialable number, including numbers outside Israel.
     */
    public static Matches matchAmong(String spoken, List<Contact> eligible) {
        return matchAmong(spoken, eligible, false);
    }

    /**
     * @param tailIsMessage true when the words after the name are the message (WhatsApp body).
     *     Then a unique saved first name confirms. False (a call: every word is the name) keeps
     *     a leftover unmatched word as a picker.
     */
    static Matches matchAmong(String spoken, List<Contact> eligible, boolean tailIsMessage) {
        List<Contact> list = new ArrayList<>();
        if (eligible != null) for (Contact c : eligible) if (c != null) list.add(c);
        String raw = spoken == null ? "" : spoken.replaceAll("\\s+", " ").trim();
        Matches direct = matchWindows(raw, list, tailIsMessage);
        String stripped = stripOnePrefix(raw);
        if (stripped.equals(raw)) return direct;
        Matches alt = matchWindows(stripped, list, tailIsMessage);
        if (direct.code == MatchCode.NONE) return alt;
        if (alt.code == MatchCode.NONE) return direct;
        if (alt.tier < direct.tier) return alt;
        if (direct.tier < alt.tier) return direct;
        if (alt.usedKey.length() > direct.usedKey.length()) return alt;
        return direct;
    }

    /** Longest prefix first, so a trailing message word («היי») is not kept as part of the name. */
    private static Matches matchWindows(String key, List<Contact> contacts, boolean tailIsMessage) {
        if (key == null || fold(key).length() < 2) return none();
        List<String> toks = new ArrayList<>();
        for (String w : key.split(" ")) if (!w.isEmpty()) toks.add(w);
        if (toks.isEmpty()) return none();
        if (toks.size() == 1) return matchSingle(toks.get(0), contacts).withUsed(toks.get(0));
        for (int len = toks.size(); len >= 2; len--) {
            String window = joinWords(toks, len);
            if (!windowUseful(window, contacts)) continue;
            Matches m = belongingHits(window, matchKey(window, contacts));
            if (m.code == MatchCode.NONE) continue;
            return m.withUsed(window);
        }
        Matches firstName = matchSingle(toks.get(0), contacts);
        if (firstName.code == MatchCode.NONE) return none();
        // WhatsApp: the rest is the message, so one saved name confirms. A call keeps the picker,
        // because an unmatched word after the name is probably a surname that is not saved.
        if (tailIsMessage) return firstName.withUsed(toks.get(0));
        return new Matches(MatchCode.MANY, firstName.hits, 1).withUsed(toks.get(0));
    }

    /**
     * Strict cutoff: a longer window counts only for contacts whose own name holds every spoken
     * content word of it (spelling variants and ל/ב/ה/ו prefixes as in {@link #wordIn}; possessive
     * words pass). «תמרי מה» never consumes «מה» because another contact is called מיה.
     */
    private static Matches belongingHits(String window, Matches m) {
        if (m == null || m.code == MatchCode.NONE) return none();
        List<Contact> keep = new ArrayList<>();
        for (Contact c : m.hits) if (wordsBelong(window, c)) keep.add(c);
        if (keep.isEmpty()) return none();
        if (keep.size() == m.hits.size()) return m;
        return new Matches(MatchCode.MANY, keep, Math.max(1, m.tier));
    }

    private static boolean wordsBelong(String window, Contact c) {
        if (c == null) return false;
        List<String> got = nameWords(c);
        for (String raw : tokenList(window)) {
            String w = fold(raw);
            if (w.length() < 2 || isPossessive(w)) continue;
            if (!wordIn(w, got)) return false;
        }
        return true;
    }

    /**
     * «וואטסאפ תמרי מה שלומך» — no ל. The first one to three words after the channel word are
     * tried as a saved name, exact words only (no near names, no spelling variants), longest first.
     * A match leaves the message without the name. Null when nothing matches.
     */
    private static Resolution resolveBareName(String utterance, List<Contact> contacts) {
        String prepared = prep(utterance);
        int bar = prepared.indexOf('¦');
        String left = side(bar < 0 ? prepared : prepared.substring(0, bar));
        String right = bar < 0 ? "" : side(prepared.substring(bar + 1));
        Matcher ch = Classifier.WHATSAPP.matcher(left);
        if (!ch.find()) return null;
        String before = left.substring(0, ch.start()).trim();
        List<String> toks = tokenList(left.substring(ch.end()).trim());
        List<Contact> list = withNumber(contacts);
        if (toks.isEmpty() || list.isEmpty()) return null;
        for (int len = Math.min(BARE_NAME_TOKENS, toks.size()); len >= 1; len--) {
            List<String> win = toks.subList(0, len);
            List<Contact> hits = bareHits(win, list);
            if (hits.isEmpty()) continue;
            String name = String.join(" ", win);
            String rest = String.join(" ", toks.subList(len, toks.size()));
            String body = cleanBody(stripChannel((before + " " + rest + " " + right).trim()));
            Matches m = new Matches(hits.size() == 1 ? MatchCode.ONE : MatchCode.MANY, hits, 0, name);
            return new Resolution(new Command(Target.PERSON, name, body), m);
        }
        return null;
    }

    /** Contacts whose saved name is the spoken words exactly, or holds each of them as a whole word. */
    private static List<Contact> bareHits(List<String> words, List<Contact> contacts) {
        List<Contact> exact = new ArrayList<>();
        List<Contact> all = new ArrayList<>();
        String key = String.join(" ", words);
        for (Contact c : contacts) {
            if (c == null) continue;
            if (exactFull(key, c)) { exact.add(c); continue; }
            List<String> got = new ArrayList<>();
            for (String w : nameWords(c)) got.add(fold(w));
            boolean every = true;
            for (String raw : words) {
                String w = fold(raw);
                if (w.length() < 2 || isPossessive(w) || Classifier.WHATSAPP.matcher(raw).matches() || !got.contains(w)) {
                    every = false;
                    break;
                }
            }
            if (every) all.add(c);
        }
        List<Contact> out = exact.isEmpty() ? all : exact;
        byUse(out);
        return out;
    }

    private static boolean windowUseful(String window, List<Contact> contacts) {
        List<String> toks = new ArrayList<>();
        for (String w : window.split(" ")) if (!w.isEmpty()) toks.add(w);
        if (toks.isEmpty()) return false;
        String last = toks.get(toks.size() - 1);
        if (isPossessive(fold(last))) return true;
        for (Contact c : contacts) {
            if (c == null) continue;
            if (exactFull(window, c)) return true;
            if (wordIn(fold(last), nameWords(c))) return true;
        }
        return false;
    }

    private static String joinWords(List<String> toks, int len) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < len && i < toks.size(); i++) {
            if (i > 0) b.append(' ');
            b.append(toks.get(i));
        }
        return b.toString();
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
     * Exact full name, then every content word (של-words ignored when comparing), then the
     * highest partial word-count. A unique top contact confirms unless a possessive clashes
     * («אמא שלי» must not confirm «אמא של דני»). First-name matching is the last fallback.
     */
    private static Matches matchFull(String key, String first, List<Contact> contacts) {
        List<Contact> exact = new ArrayList<>();
        for (Contact c : contacts) if (c != null && exactFull(key, c)) exact.add(c);
        byUse(exact);
        if (exact.size() == 1) return decide(exact, key, 0);
        if (!exact.isEmpty()) return new Matches(MatchCode.MANY, exact, 0);

        List<Contact> allContent = new ArrayList<>();
        int bestAll = 0;
        for (Contact c : contacts) {
            if (c == null || !allContentWords(key, c)) continue;
            int pts = score(key, c);
            if (pts > bestAll) { bestAll = pts; allContent.clear(); allContent.add(c); }
            else if (pts == bestAll) allContent.add(c);
        }
        byUse(allContent);
        if (!allContent.isEmpty()) return decide(allContent, key, 0);

        List<Contact> partial = new ArrayList<>();
        int bestPts = 0;
        for (Contact c : contacts) {
            if (c == null) continue;
            NameScore s = nameScore(key, c);
            if (!s.beyondFirst) continue;
            if (s.points > bestPts) { bestPts = s.points; partial.clear(); partial.add(c); }
            else if (s.points == bestPts) partial.add(c);
        }
        byUse(partial);
        if (!partial.isEmpty()) return decide(partial, key, 0);

        if (standalonePossessiveTail(key)) {
            String base = stripTrailingPossessives(key);
            if (!base.equals(key) && fold(base).length() >= 2) {
                Matches narrowed = matchKey(base, contacts);
                if (narrowed.code != MatchCode.NONE) return decide(narrowed.hits, key, narrowed.tier);
            }
        }

        Matches firstName = matchSingle(first, contacts);
        if (firstName.code == MatchCode.NONE) return none();
        return new Matches(MatchCode.MANY, firstName.hits, 1);
    }

    /** Unique top confirms unless the only hit is a different possessive phrase. */
    private static Matches decide(List<Contact> hits, String key, int tier) {
        if (hits.isEmpty()) return none();
        if (hits.size() == 1 && possessiveClash(key, hits.get(0)))
            return new Matches(MatchCode.MANY, hits, Math.max(tier, 1));
        if (hits.size() == 1) return new Matches(MatchCode.ONE, hits, tier);
        return new Matches(MatchCode.MANY, hits, tier);
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

    /** Exact folded token, a diminutive, a medial ו/י spelling variant, or a ל/ב/ה/ו prefix. */
    private static boolean wordIn(String want, List<String> words) {
        if (want == null || want.isEmpty()) return false;
        if (wordHit(want, words)) return true;
        String stripped = stripWordPrefix(want);
        return !stripped.equals(want) && wordHit(stripped, words);
    }

    private static boolean wordHit(String want, List<String> words) {
        for (String token : words) {
            String got = fold(token);
            if (got.length() < 2) continue;
            if (got.equals(want) || diminutive(want, got) || spellingVariant(want, got)) return true;
            String gotBare = stripWordPrefix(got);
            if (!gotBare.equals(got) && (gotBare.equals(want) || diminutive(want, gotBare) || spellingVariant(want, gotBare)))
                return true;
        }
        return false;
    }

    /** One leading ל/ב/ה/ו, never on a short remainder. */
    private static String stripWordPrefix(String folded) {
        if (folded == null || folded.length() < 3) return folded == null ? "" : folded;
        char c = folded.charAt(0);
        if (c == 'ל' || c == 'ב' || c == 'ה' || c == 'ו') return folded.substring(1);
        return folded;
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
        if (possessiveShel(a) || possessiveShel(b)) return false;
        return shortTail(a, b) || shortTail(b, a);
    }

    /** «של» / «שלי» must not count as a diminutive of each other. */
    private static boolean possessiveShel(String folded) {
        return "של".equals(folded) || "שלי".equals(folded);
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
            if ((c == 'ל' || c == 'ב' || c == 'ה' || c == 'ש' || c == 'ו') && n.charAt(1) != ' ') return n.substring(1);
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
            int from = el.start();
            if (from < left.length() && Character.isWhitespace(left.charAt(from))) from++;
            found.add(new Span(from, s.end, s.name));
        }
        Matcher lam = LAMED.matcher(left);
        while (lam.find()) {
            if (lam.group(1).startsWith("קבוצ")) continue;
            int lamed = lam.start();
            if (lamed < left.length() && Character.isWhitespace(left.charAt(lamed))) lamed++;
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
                String more = firstTokens(gap, EXTRA_NAME_TOKENS);
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
        return dropConnectorShin(text).trim();
    }

    /**
     * The message keeps its own first letter: «שלומך», «שמעתי» stay. Only a lone «ש», or a ש in
     * front of a short clause word («שאני בדרך» → «אני בדרך»), is a connector and is dropped.
     */
    static String dropConnectorShin(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.equals("ש")) return "";
        if (s.startsWith("ש ")) return s.substring(2).trim();
        if (s.length() > 1 && s.charAt(0) == 'ש') {
            int sp = s.indexOf(' ');
            String first = sp < 0 ? s : s.substring(0, sp);
            if (AFTER_CONNECTOR.contains(first.substring(1))) return s.substring(1).trim();
        }
        return s;
    }

    /** The parsed name plus up to four body tokens, for longest-prefix matching. */
    private static String longestPersonKey(Command cmd) {
        if (cmd == null || cmd.name == null || cmd.name.isEmpty()) return "";
        List<String> extra = new ArrayList<>();
        if (cmd.body != null && !cmd.body.isEmpty()) {
            for (String w : cmd.body.trim().split("\\s+")) {
                if (w.isEmpty()) continue;
                extra.add(w);
                if (extra.size() == EXTRA_NAME_TOKENS) break;
            }
        }
        if (extra.isEmpty()) return cmd.name;
        return cmd.name + " " + String.join(" ", extra);
    }

    private static List<String> nameWords(Contact c) {
        List<String> out = new ArrayList<>();
        if (c == null) return out;
        for (String field : new String[]{c.displayName, c.given, c.family, c.nickname}) {
            if (field == null) continue;
            for (String w : ContactMatcher.norm(field).split(" ")) {
                if (fold(w).length() >= 2 && !out.contains(w)) out.add(w);
            }
        }
        return out;
    }

    private static boolean isPossessive(String folded) {
        return POSSESSIVE.contains(folded);
    }

    private static boolean allContentWords(String key, Contact c) {
        List<String> want = contentWords(key);
        if (want.isEmpty()) return false;
        List<String> got = nameWords(c);
        for (String w : want) if (!wordIn(w, got)) return false;
        return true;
    }

    private static int score(String key, Contact c) {
        return nameScore(key, c).points;
    }

    private static final class NameScore {
        final int points;
        final int contentHits;
        final boolean beyondFirst;
        NameScore(int points, int contentHits, boolean beyondFirst) {
            this.points = points;
            this.contentHits = contentHits;
            this.beyondFirst = beyondFirst;
        }
    }

    private static NameScore nameScore(String key, Contact c) {
        List<String> spoken = tokenList(key);
        List<String> got = nameWords(c);
        int contentHits = 0, connectorHits = 0;
        String firstContent = "";
        boolean firstHit = false;
        for (String raw : spoken) {
            String w = fold(raw);
            if (w.length() < 2) continue;
            boolean poss = isPossessive(w);
            boolean hit = wordIn(w, got);
            if (poss) {
                if (hit) connectorHits++;
            } else {
                if (firstContent.isEmpty()) {
                    firstContent = w;
                    if (hit) firstHit = true;
                }
                if (hit) contentHits++;
            }
        }
        boolean beyond = firstHit && contentHits >= 2;
        return new NameScore(contentHits + connectorHits, contentHits, beyond);
    }

    private static boolean wordEqual(String a, String b) {
        if (a.equals(b)) return true;
        return diminutive(a, b) || spellingVariant(a, b);
    }

    private static List<String> contentWords(String key) {
        List<String> out = new ArrayList<>();
        for (String raw : tokenList(key)) {
            String w = fold(raw);
            if (w.length() < 2 || isPossessive(w)) continue;
            if (!out.contains(w)) out.add(w);
        }
        return out;
    }

    private static List<String> tokenList(String key) {
        List<String> out = new ArrayList<>();
        if (key == null) return out;
        for (String w : key.split(" ")) if (!w.isEmpty()) out.add(w);
        return out;
    }

    private static boolean possessiveClash(String key, Contact c) {
        if (c == null) return false;
        List<String> spoken = possessiveSig(tokenList(key));
        List<String> saved = possessiveSig(nameWords(c));
        if (spoken.isEmpty() || saved.isEmpty()) return false;
        return !possessiveCompatible(spoken, saved);
    }

    /** «שלי» / «של דני» / «שלו»… extracted from a token list. */
    private static List<String> possessiveSig(List<String> words) {
        List<String> sig = new ArrayList<>();
        for (int i = 0; i < words.size(); i++) {
            String w = fold(words.get(i));
            if (w.equals("של")) {
                sig.add("של");
                for (int j = i + 1; j < words.size(); j++) {
                    String n = fold(words.get(j));
                    if (n.length() < 2 || isPossessive(n)) break;
                    sig.add(n);
                }
                return sig;
            }
            if (STANDALONE_POSSESSIVE.contains(w)) {
                sig.add(w);
                return sig;
            }
        }
        return sig;
    }

    private static boolean possessiveCompatible(List<String> spoken, List<String> contact) {
        if (spoken.isEmpty() || contact.isEmpty()) return true;
        if (!spoken.get(0).equals(contact.get(0))) return false;
        if (!"של".equals(spoken.get(0))) return spoken.equals(contact);
        List<String> got = new ArrayList<>(contact.subList(1, contact.size()));
        for (int i = 1; i < spoken.size(); i++) {
            if (!wordIn(spoken.get(i), got)) return false;
        }
        return true;
    }

    private static boolean standalonePossessiveTail(String key) {
        List<String> toks = tokenList(key);
        boolean sawContent = false, sawStandalone = false;
        for (String raw : toks) {
            String w = fold(raw);
            if (w.length() < 2) continue;
            if (w.equals("של")) return false;
            if (STANDALONE_POSSESSIVE.contains(w)) { if (sawContent) sawStandalone = true; }
            else { if (sawStandalone) return false; sawContent = true; }
        }
        return sawContent && sawStandalone;
    }

    private static String stripTrailingPossessives(String key) {
        List<String> toks = tokenList(key);
        while (!toks.isEmpty() && STANDALONE_POSSESSIVE.contains(fold(toks.get(toks.size() - 1))))
            toks.remove(toks.size() - 1);
        return String.join(" ", toks);
    }

    private static Command withConsumedName(Command cmd, String name) {
        if (cmd == null) return new Command(Target.NONE, "", "");
        if (name == null || name.isEmpty() || name.equals(cmd.name)) return cmd;
        String leftover = trailingAfter(name, cmd.name);
        String body;
        if (!leftover.isEmpty()) {
            body = (leftover + " " + (cmd.body == null ? "" : cmd.body)).trim();
        } else {
            body = stripLeadingWords(cmd.body, trailingAfter(cmd.name, name));
        }
        return new Command(cmd.target, name, body);
    }

    /** If {@code longer} starts with {@code prefix}, the leftover tokens; otherwise empty. */
    private static String trailingAfter(String prefix, String longer) {
        String a = prefix == null ? "" : prefix.trim();
        String b = longer == null ? "" : longer.trim();
        if (!a.isEmpty() && b.startsWith(a + " ")) return b.substring(a.length()).trim();
        return "";
    }

    private static String stripLeadingWords(String body, String extra) {
        if (body == null || body.isEmpty() || extra == null || extra.isEmpty()) return body == null ? "" : body;
        List<String> have = new ArrayList<String>();
        for (String w : body.trim().split("\s+")) if (!w.isEmpty()) have.add(w);
        for (String w : extra.trim().split("\s+")) {
            if (w.isEmpty() || have.isEmpty()) break;
            if (!fold(have.get(0)).equals(fold(w))) break;
            have.remove(0);
        }
        return String.join(" ", have);
    }

    private WhatsAppRecipients() {}
}
