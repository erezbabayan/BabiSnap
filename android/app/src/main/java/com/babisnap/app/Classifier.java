package com.babisnap.app;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Decides what a dictated phrase is. Pure Java, unit-testable.
 * MEETING -> Google Calendar only (existing flow). TASK / NOTE -> BabiTk "notebook" board.
 * Task vs note is decided by CONTENT (something to do vs information), never by presence of a date.
 */
public final class Classifier {
    public enum Kind { MEETING, TASK, NOTE;
        public String api() { return this == TASK ? "task" : "note"; }
        public String he() { return this == MEETING ? "פגישה" : this == TASK ? "משימה" : "הערה"; } }

    public static final class Result {
        public final Kind kind; public final String stripped;
        Result(Kind k, String s) { kind = k; stripped = s; }
    }

    // Explicit leading trigger words (longest first). Stripped from the title.
    static final String[] MEETING_PREFIX = {"קבע פגישה", "תקבע פגישה", "תקבעי פגישה", "קבעי", "תקבע", "תקבעי", "קבע", "תזמן", "תזמני"};
    static final String[] TASK_PREFIX = {"תזכיר לי ל", "תזכירי לי ל", "תזכיר לי", "תזכירי לי", "תזכורת", "משימה חדשה", "משימה", "לעשות", "אל תשכח ל", "אל תשכחי ל", "לא לשכוח ל", "לא לשכוח"};
    static final String[] NOTE_PREFIX = {"תרשום הערה", "תרשמי הערה", "רשום הערה", "תרשום פתק", "תרשמי פתק", "תכתוב הערה", "הערה חדשה", "הערה", "פתק", "תרשום ש", "תרשמי ש", "תרשום", "תרשמי", "רשום ש", "רשום", "לזכור ש", "שים לב ש", "לידיעה"};

    // Words with leading ל that are NOT infinitive verbs.
    static final Set<String> NOT_VERBS = new HashSet<>(Arrays.asList(
        "לא", "לי", "לך", "לו", "לה", "לנו", "לכם", "לכן", "להם", "להן", "למה", "לפני", "לפי", "לגבי", "ליד", "לכל",
        "לאן", "למרות", "לבד", "לפעמים", "לעולם", "לפחות", "לגמרי", "לאט", "למעלה", "למטה", "לשם", "לכאן", "לילה", "לחם", "לוח"));
    static final Set<String> MEETING_VERBS = new HashSet<>(Arrays.asList("להיפגש", "לפגוש", "להפגש", "ניפגש", "נפגש", "נפגשים", "ניפגשים", "ונפגש", "וניפגש"));
    static final Set<String> IMPERATIVES = new HashSet<>(Arrays.asList(
        "תקנה", "תקני", "קנה", "תתקשר", "תתקשרי", "תשלח", "תשלחי", "תשלם", "תשלמי", "תבדוק", "תבדקי", "תזמין", "תזמיני",
        "תביא", "תביאי", "תחזיר", "תחזירי", "תסדר", "תסדרי", "תכין", "תכיני", "תנקה", "תנקי", "תעשה", "תעשי", "תאסוף", "תאספי",
        "תוריד", "תורידי", "תעדכן", "תעדכני", "תחדש", "תחדשי", "תגיש", "תגישי", "תמלא", "תמלאי", "תאשר", "תאשרי", "תברר", "תבררי"));
    static final Pattern OBLIGATION = Pattern.compile("(^|\\s)(צריך|צריכה|צריכים|חייב|חייבת|חייבים|דחוף|אל תשכח|אל תשכחי|לא לשכוח|דדליין|עד סוף|עד מחר|עד יום|עד ה)(\\s|$)");
    static final Pattern GATHERING = Pattern.compile("(^|\\s)(ו?[הל]?(?:פגישה|פגישת|ישיבה|ישיבת|מפגש|זימון|ראיון|סטנדאפ|ספרינט|אירוע)|ראיון|שיחה|זום|שיחת זום|שיחת וידאו|ארוחה|ארוחת|כנס|תור|אסיפה|מסיבה|יום הולדת)(\\s|$)");
    /** A phrase that opens with a meeting word (also ו/ה/ל-prefixed) is a calendar event: "זימון מחר עם דני", "תור לרופא", "ישיבת צוות". */
    static final Pattern STARTS_MEETING = Pattern.compile("^ו?[הל]?(?:פגישה|פגישת|ישיבה|ישיבת|מפגש|זימון|ראיון|כנס|תור|סטנדאפ|ספרינט|זום|שיחת זום|שיחת וידאו|אירוע|קביעה עם)(?:\\s|$)");
    /** "תזמין את תמר לפגישה מחר" is a meeting with an invite (stage 2), not a "תזמין" (order) task. */
    static final Pattern INVITE_TO_MEETING = Pattern.compile("^ו?(?:תזמין|תזמיני|תזמינו|הזמן|הזמיני|הזמינו|לזמן|תזמן|תזמני|תזמנו|זמן|זמני)\\s+(?:גם\\s+)?את\\s.*(?:^|\\s)ל?(?:פגישה|פגישת|ישיבה|ישיבת|מפגש)(?:\\s|$)");
    /** "יומן" family (any variation, with ו/ב/ל/ה/מ prefixes) = calendar, same idea as פגישה: always MEETING unless the phrase opens with an explicit note word. */
    static final Pattern CALENDAR = Pattern.compile("(^|\\s)(?:ו?[בלהמ]?(?:יומן|יומנים|יומני|יומנך|יומנו|היומן|לוח שנה|לוח השנה|קלנדר|קלנדאר|גוגל קלנדר|יומן גוגל)|calendar|google calendar)(?=\\s|$)", Pattern.CASE_INSENSITIVE);
    static final String[] EXPLICIT_NOTE = {"הערה", "פתק", "תרשום הערה", "תרשמי הערה", "רשום הערה", "תרשום פתק", "תרשמי פתק", "תכתוב הערה", "הערה חדשה"};
    static final Pattern WITH_PERSON = Pattern.compile("(^|\\s)(עם|אצל)\\s+\\S+");

    /** Leading command words ("תוסיף לי", "תקבע לי", "שים לי", a leftover "לי") carry no meaning for classification. */
    static final Pattern COMMAND = Pattern.compile("^(?:(?:תוסיף|תוסיפי|הוסף|הוסיפי|שים|תשים|תשימי|תכניס|תכניסי|הכנס|תיצור|צור)(?:\\s+(?:לי|לנו))?\\s+|(?:תרשום|תרשמי|רשום)\\s+(?:לי|לנו)\\s+|(?:תקבע|תקבעי)\\s+(?:לי|לנו)\\s+(?=פגישה|משימה|הערה|תזכורת)|(?:לי|לנו)\\s+)+");

    public static String normalize(String s) {
        return s == null ? "" : s.replaceAll("[\\u0591-\\u05C7]", "").replaceAll("[.,!?;:\"'״׳]", " ").replaceAll("\\s+", " ").trim();
    }

    /** @param explicitTime true when the user said a time of day (parser timeDefaulted == false). */
    public static Result classify(String text, boolean explicitTime) {
        String t = COMMAND.matcher(normalize(text)).replaceFirst("");
        String s;
        if (CALENDAR.matcher(t).find() && strip(t, EXPLICIT_NOTE) == null) {
            String c = CALENDAR.matcher(t).replaceAll(" ").replaceAll("\\s+", " ").trim();
            return new Result(Kind.MEETING, c.isEmpty() ? t : c);
        }
        if ((s = strip(t, MEETING_PREFIX)) != null) return new Result(Kind.MEETING, t);
        if ((s = strip(t, TASK_PREFIX)) != null) return new Result(Kind.TASK, s);
        if ((s = strip(t, NOTE_PREFIX)) != null) return new Result(Kind.NOTE, s);

        if (INVITE_TO_MEETING.matcher(t).find()) return new Result(Kind.MEETING, t);
        if (STARTS_MEETING.matcher(t).find()) return new Result(Kind.MEETING, t);
        String first = t.isEmpty() ? "" : t.split(" ")[0];
        boolean meetingVerb = MEETING_VERBS.contains(first);
        boolean action = !meetingVerb && hasAction(t);
        if (action) return new Result(Kind.TASK, t);

        boolean gathering = GATHERING.matcher(t).find() || meetingVerb;
        boolean people = WITH_PERSON.matcher(t).find();
        if (explicitTime && (gathering || people)) return new Result(Kind.MEETING, t);
        if (t.startsWith("פגישה") || t.startsWith("ישיבה")) return new Result(Kind.MEETING, t);

        if (OBLIGATION.matcher(t).find()) return new Result(Kind.TASK, t);
        return new Result(Kind.NOTE, t);
    }

    /** Words that only say WHEN; skipped before looking for the leading verb ("מחר בבוקר להתקשר לדני"). */
    static final Set<String> TIME_WORDS = new HashSet<>(Arrays.asList(
        "מחר", "היום", "מחרתיים", "הערב", "הלילה", "בבוקר", "בערב", "בצהריים", "בלילה", "אחר", "הצהריים", "אחרי", "לפני", "בשעה", "ביום",
        "ראשון", "שני", "שלישי", "רביעי", "חמישי", "שישי", "שבת", "בשבוע", "הבא", "השבוע", "בעוד", "שעה", "שעתיים", "דקות", "וחצי", "ורבע", "עכשיו", "אז", "גם"));
    /** Common action infinitives recognised anywhere in the phrase (not the generic ל-heuristic, which would misread "לשער"/"לבית"). */
    static final Set<String> ACTION_VERBS = new HashSet<>(Arrays.asList(
        "לקנות", "להתקשר", "לבדוק", "לשלם", "לסדר", "להזמין", "לשלוח", "להחזיר", "להביא", "לאסוף", "לתקן", "לנקות", "להכין", "לקבוע",
        "לחדש", "להגיש", "למלא", "לאשר", "לברר", "לעדכן", "להוריד", "להעביר", "לטפל", "לכבס", "להוציא", "לקחת", "לתאם", "לבטל",
        "לכתוב", "לענות", "לסיים", "להתחיל", "ללכת", "לנסוע", "לצלם", "לסרוק", "להדפיס", "לחתום", "להתקין", "לארוז", "לבשל", "לסגור", "לפתוח"));
    static final Pattern REMIND = Pattern.compile("(^|\\s)(תזכיר|תזכירי|תזכורת)(\\s|$)");

    /** Task = something to do: a leading verb (after any when-words), a known action verb anywhere, "תזכיר לי", or an obligation word. */
    static boolean hasAction(String t) {
        String[] w = t.isEmpty() ? new String[0] : t.split(" ");
        int i = 0;
        while (i < w.length && (TIME_WORDS.contains(w[i]) || w[i].matches("ב?-?\\d.*") || w[i].matches("ב(?:שעה|עשר|אחת|שתיים|שלוש|ארבע|חמש|שש|שבע|שמונה|תשע|אחת עשרה|שתים עשרה)"))) i++;
        if (i < w.length && (isInfinitive(w[i]) || IMPERATIVES.contains(w[i]))) return true;
        for (String x : w) if (ACTION_VERBS.contains(x) || ACTION_VERBS.contains(x.replaceFirst("^ו", ""))) return true;
        return REMIND.matcher(t).find();
    }

    static boolean isInfinitive(String w) {
        return w.length() >= 4 && w.charAt(0) == 'ל' && !NOT_VERBS.contains(w);
    }

    /** Returns the text after a matching leading trigger (whole-word / prefix-ל match), or null. */
    static String strip(String t, String[] prefixes) {
        for (String p : prefixes) {
            if (t.equals(p)) return "";
            if (p.endsWith(" ל")) { if (t.startsWith(p) && t.length() > p.length()) return t.substring(p.length() - 1).trim(); } // keep the ל of the verb
            else if (p.endsWith(" ש")) { if (t.startsWith(p) && t.length() > p.length()) return t.substring(p.length()).trim(); } // drop the ש
            else if (t.startsWith(p + " ")) return t.substring(p.length() + 1).trim();
        }
        return null;
    }
    private Classifier() {}
}
