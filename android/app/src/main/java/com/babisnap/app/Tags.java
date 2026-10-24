package com.babisnap.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * BabiTk tags (תיוגים) spoken in a recording. Pure Java, unit-testable.
 * - Explicit command ("תתייג מילואים", "בתגית עבודה", "עם התגית של המילואים") -> tag, and the command is removed from the text.
 * - A tag name used as a normal word ("להגיש טופס למילואים", "קניות לבית") -> tag, the word stays in the title.
 * Only known tag names are sent (embedded list below + the user's list cached from BabiTk when available).
 */
public final class Tags {
    /** Every tag found in BabiTk code (Convex/Supabase default tags, tag-inference rules, seed data) plus "מילואים". */
    public static final List<String> FALLBACK = Arrays.asList(
        "בית", "עבודה", "לימודים", "סטארטאפ", "קודים", "רעיונות", "פיננסי", "משפחה", // DEFAULT_USER_TAG_DEFINITIONS
        "קניות", "בריאות",                                                          // tag-inference.ts rules
        "אישי", "כניסה לביצוע", "נהלים באוויר", "סגירת פרויקטים",                   // babitk-local-seed.json
        "מילואים");
    public static final int MAX_TAGS = 12, TAG_MAX = 40; // BabiTk caps a user at 12 tag definitions

    public static final class Result {
        public final String text; public final List<String> tags;
        Result(String text, List<String> tags) { this.text = text; this.tags = tags; }
    }

    static final String PFX = "(?:ש?[ובלכמה]{0,2}|של\\s+ה?)"; // ו/ה/ל/ב/כ/מ/ש prefixes, "של ה"
    static final String END = "(?=$|[\\s.,!?;:\"'״)])";
    static final String START = "(?:^|(?<=[\\s,.:;!?(\"']))";
    /** Explicit tag command words; the tag name must follow. */
    static final String CMD = "(?:ו?(?:תתייג|תתייגי|תתייגו|תייג|תייגי|לתייג|תתיג|תיג|תסמן|תסמני|סמן)(?:\\s+(?:את|לי|גם|תחת|עם|ב|כ))*(?:\\s+(?:ב|ל|כ|עם\\s+)?ה?(?:תגית|תיוג|תווית))?"
        + "|ו?(?:ב|ל|כ|עם\\s+|תחת\\s+|וגם\\s+|גם\\s+)?ה?(?:תגית|תגיות|תיוג|תווית)(?:\\s+(?:של|את))?)";

    /** Merges the user's cached/fetched list (first, BabiTk display order) with the embedded fallback (cleaned, unique). */
    public static List<String> merge(Collection<String> extra) {
        Set<String> s = new LinkedHashSet<>();
        if (extra != null) for (String t : extra) { String n = clean(t); if (!n.isEmpty()) s.add(n); }
        for (String t : FALLBACK) s.add(t);
        return new ArrayList<>(s);
    }
    static String clean(String t) { return t == null ? "" : t.trim().replaceAll("^#+", "").replaceAll("\\s+", " "); }

    public static Result extract(String text, Collection<String> names) {
        String t = text == null ? "" : text;
        List<String> list = new ArrayList<>();
        if (names != null) for (String n : names) { String c = clean(n); if (!c.isEmpty() && !list.contains(c)) list.add(c); }
        list.sort((a, b) -> b.length() - a.length()); // longest first ("סגירת פרויקטים" before shorter overlaps)
        Set<String> found = new LinkedHashSet<>();
        // 1) explicit commands: may list several tags ("תתייג עבודה ומילואים")
        for (String n : list) {
            Pattern p = Pattern.compile(START + CMD + "\\s+" + PFX + Pattern.quote(n) + END);
            Matcher m = p.matcher(t);
            while (m.find()) { found.add(n); t = (t.substring(0, m.start()) + " " + t.substring(m.end())); m = p.matcher(t); }
        }
        // "תתייג עבודה ומילואים": the 2nd tag right after a removed command lost its command; handled by the natural pass below.
        // 2) natural words keep their place in the text
        for (String n : list) {
            if (found.contains(n)) continue;
            if (Pattern.compile(START + PFX + Pattern.quote(n) + END).matcher(t).find()) found.add(n);
        }
        t = t.replaceAll("\\s+", " ").replaceAll("\\s+([,.!?])", "$1").replaceAll("^[\\s,.]+|[\\s,]+$", "").trim();
        List<String> tags = new ArrayList<>();
        for (String n : names == null ? new ArrayList<String>() : names) { String c = clean(n); if (found.contains(c) && !tags.contains(c) && tags.size() < MAX_TAGS) tags.add(c); }
        return new Result(t.isEmpty() ? (text == null ? "" : text.trim()) : t, tags);
    }

    // ---- cache of the user's own tag list (filled by TagSync from GET voice-ingest) ----
    static final String PREF = "babitk_tag_names";
    public static List<String> names(android.content.Context c) {
        List<String> extra = new ArrayList<>();
        try {
            org.json.JSONArray a = new org.json.JSONArray(c.getSharedPreferences("babisnap", 0).getString(PREF, "[]"));
            for (int i = 0; i < a.length(); i++) extra.add(a.optString(i));
        } catch (Exception ignored) {}
        return merge(extra);
    }
    public static void saveCached(android.content.Context c, Collection<String> names) {
        org.json.JSONArray a = new org.json.JSONArray();
        if (names != null) for (String n : names) { String s = clean(n); if (!s.isEmpty() && s.length() <= TAG_MAX) a.put(s); }
        c.getSharedPreferences("babisnap", 0).edit().putString(PREF, a.toString()).apply();
    }
    private Tags() {}
}
