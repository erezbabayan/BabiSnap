package com.babisnap.app;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Decides what a dictated phrase is. Pure Java, unit-testable.
 * MEETING -> Google Calendar only (existing flow). TASK / NOTE -> BabiTk "notebook" board.
 * Task vs note is decided by CONTENT (something to do vs information), never by presence of a date.
 * Exception: an explicit date+time with no action verb and no destination word is a calendar item.
 * WHATSAPP is returned only when the caller passes whatsappEnabled (the setting, default off).
 * {@link #classify(String, boolean)} passes false and never returns WHATSAPP.
 * The live path is {@link #classify(String, boolean, boolean)} with the saved setting.
 */
public final class Classifier {
    public enum Kind { MEETING, TASK, NOTE, WHATSAPP;
        public String api() {
            if (this == TASK) return "task";
            if (this == WHATSAPP) return "whatsapp";
            return "note";
        }
        public String he() {
            if (this == MEETING) return "פגישה";
            if (this == TASK) return "משימה";
            if (this == WHATSAPP) return "וואטסאפ";
            return "הערה";
        } }

    public static final class Result {
        public final Kind kind; public final String stripped;
        Result(Kind k, String s) { kind = k; stripped = s; }
    }

    // Explicit leading command words (longest first). Stripped from the title.
    // Do not include "קבע פגישה": "פגישה" is kept in the title.
    static final String[] MEETING_PREFIX = {"קבעי", "תקבע", "תקבעי", "קבע", "תזמן", "תזמני"};
    static final String[] TASK_PREFIX = {"תזכיר לי ל", "תזכירי לי ל", "תזכיר לי", "תזכירי לי", "תזכורת", "משימה חדשה", "למשימה", "משימה", "לעשות", "אל תשכח ל", "אל תשכחי ל", "לא לשכוח ל", "לא לשכוח"};
    static final String[] NOTE_PREFIX = {"תרשום הערה", "תרשמי הערה", "רשום הערה", "תרשום פתק", "תרשמי פתק", "תכתוב הערה", "הערה חדשה", "להערה", "הערה", "פתק", "תרשום ש", "תרשמי ש", "תרשום", "תרשמי", "רשום ש", "רשום", "לזכור ש", "שים לב ש", "לידיעה"};

    // Words with leading ל that are NOT infinitive verbs.
    static final Set<String> NOT_VERBS = new HashSet<>(Arrays.asList(
        "לא", "לי", "לך", "לו", "לה", "לנו", "לכם", "לכן", "להם", "להן", "למה", "לפני", "לפי", "לגבי", "ליד", "לכל",
        "לאן", "למרות", "לבד", "לפעמים", "לעולם", "לפחות", "לגמרי", "לאט", "למעלה", "למטה", "לשם", "לכאן", "לילה", "לחם", "לוח",
        "למשימה", "להערה", "ליומן", "ליומנים"));
    static final Set<String> MEETING_VERBS = new HashSet<>(Arrays.asList("להיפגש", "לפגוש", "להפגש", "ניפגש", "נפגש", "נפגשים", "ניפגשים", "ונפגש", "וניפגש"));
    static final Set<String> IMPERATIVES = new HashSet<>(Arrays.asList(
        "תקנה", "תקני", "קנה", "תתקשר", "תתקשרי", "תשלח", "תשלחי", "תשלם", "תשלמי", "תבדוק", "תבדקי", "תזמין", "תזמיני",
        "תביא", "תביאי", "תחזיר", "תחזירי", "תסדר", "תסדרי", "תכין", "תכיני", "תנקה", "תנקי", "תעשה", "תעשי", "תאסוף", "תאספי",
        "תוריד", "תורידי", "תעדכן", "תעדכני", "תחדש", "תחדשי", "תגיש", "תגישי", "תמלא", "תמלאי", "תאשר", "תאשרי", "תברר", "תבררי"));
    static final Pattern OBLIGATION = Pattern.compile("(^|\\s)(צריך|צריכה|צריכים|חייב|חייבת|חייבים|דחוף|אל תשכח|אל תשכחי|לא לשכוח|דדליין|עד סוף|עד מחר|עד יום|עד ה)(\\s|$)");
    static final Pattern GATHERING = Pattern.compile("(^|\\s)(ו?[הל]?(?:פגישה|פגישת|פגישות|ישיבה|ישיבת|מפגש|זימון|ראיון|סטנדאפ|ספרינט|אירוע|אסיפה|אסיפת)|ראיון|שיחה|זום|שיחת זום|שיחת וידאו|ארוחה|ארוחת|כנס|תור|אסיפה|אסיפת|מסיבה|יום הולדת|מילואים|חתונה|חוג)(\\s|$)");
    /** A phrase that opens with a meeting or event word (also ו/ה/ל-prefixed) is a calendar event. */
    static final Pattern STARTS_MEETING = Pattern.compile("^ו?[הל]?(?:פגישה|פגישת|פגישות|ישיבה|ישיבת|מפגש|זימון|ראיון|כנס|תור|סטנדאפ|ספרינט|זום|שיחת זום|שיחת וידאו|שיחה|אירוע|קביעה עם|אסיפה|אסיפת|מילואים|חתונה|חוג|ארוחה|ארוחת|מסיבה|יום הולדת)(?:\\s|$)");
    /** "תזמין את תמר לפגישה מחר" is a meeting with an invite (stage 2), not a "תזמין" (order) task. */
    static final Pattern INVITE_TO_MEETING = Pattern.compile("^ו?(?:תזמין|תזמיני|תזמינו|הזמן|הזמיני|הזמינו|לזמן|תזמן|תזמני|תזמנו|זמן|זמני)\\s+(?:גם\\s+)?את\\s.*(?:^|\\s)ל?(?:פגישה|פגישת|ישיבה|ישיבת|מפגש)(?:\\s|$)");
    /** "יומן" family (any variation, with ו/ב/ל/ה/מ prefixes) = calendar destination; stripped from the title. */
    static final Pattern CALENDAR = Pattern.compile("(^|\\s)(?:ו?[בלהמ]?(?:יומן|יומנים|יומני|יומנך|יומנו|היומן|לוח שנה|לוח השנה|קלנדר|קלנדאר|גוגל קלנדר|יומן גוגל)|calendar|google calendar)(?=\\s|$)", Pattern.CASE_INSENSITIVE);
    /**
     * WhatsApp channel word, whole token only. Listed forms plus the same ו/ב/ל/ה/מ prefix
     * the calendar words use ("לוואטסאפ", "בוואטסאפ"). Removed from the message, not from other routes.
     */
    static final Pattern WHATSAPP = Pattern.compile(
        "(?<=^|\\s)(?:ובוואטסאפ|לוואטסאפ|בוואטסאפ|הוואטסאפ|מוואטסאפ|ווואטסאפ|וואטסאפ"
            + "|ובווטסאפ|לווטסאפ|בווטסאפ|הווטסאפ|מווטסאפ|וווטסאפ|ווטסאפ"
            + "|ובואטסאפ|לבואטסאפ|בואטסאפ"
            + "|ל-וואטסאפ|ב-וואטסאפ|ל-ווטסאפ|ב-ווטסאפ"
            + "|whatsapp)(?=\\s|$)",
        Pattern.CASE_INSENSITIVE);
    /** Event names that go to the calendar and stay in the title; with no clock time the parser marks them all-day. */
    static final Pattern EVENT_NAME = Pattern.compile("(^|\\s)(?:ו?[הל]?(?:מילואים|חתונה|חוג|אסיפה|אסיפת|כנס|מסיבה|ראיון)|יום הולדת|ארוחה|ארוחת)(?:\\s|$)");
    static final String[] EXPLICIT_NOTE = {"תרשום הערה", "תרשמי הערה", "רשום הערה", "תרשום פתק", "תרשמי פתק", "תכתוב הערה", "הערה חדשה", "להערה", "הערה", "פתק"};
    static final Pattern WITH_PERSON = Pattern.compile("(^|\\s)(עם|אצל)\\s+\\S+");

    /** Leading command words ("תוסיף לי", "תקבע לי", "שים לי", a leftover "לי") carry no meaning for classification. */
    static final Pattern COMMAND = Pattern.compile("^(?:(?:תוסיף|תוסיפי|הוסף|הוסיפי|שים|תשים|תשימי|תכניס|תכניסי|הכנס|תיצור|צור)(?:\\s+(?:לי|לנו))?\\s+|(?:תרשום|תרשמי|רשום)\\s+(?:לי|לנו)\\s+|(?:תקבע|תקבעי)\\s+(?:לי|לנו)\\s+(?=פגישה|משימה|הערה|תזכורת)|(?:לי|לנו)\\s+)+");

    public static String normalize(String s) {
        if (s == null) return "";
        // Maqaf (U+05BE) and en/em dash after ב/ל should look like ב- to a later re-parse.
        String t = s.replaceAll("([א-ת])[\u05BE\u2013\u2014]([א-ת])", "$1 $2")
                .replace('\u05BE', '-')
                .replaceAll("([בל])[\u2013\u2014]", "$1-");
        // Keep clock times like 17:00 and calendar dates like 15.11 / 15/11 so a later re-parse still sees the separator.
        t = t.replaceAll("(\\d{1,2}):(\\d{2})", "$1\u0000$2");
        t = t.replaceAll("(\\d{1,2})\\.(\\d{1,2})\\.(\\d{2,4})", "$1\u0001$2\u0001$3");
        t = t.replaceAll("(\\d{1,2})\\.(\\d{1,2})", "$1\u0001$2");
        t = t.replaceAll("(\\d{1,2})/(\\d{1,2})/(\\d{2,4})", "$1\u0002$2\u0002$3");
        t = t.replaceAll("(\\d{1,2})/(\\d{1,2})", "$1\u0002$2");
        t = t.replaceAll("[\\u0591-\\u05C7]", "").replaceAll("[.,!?;:\"'״׳]", " ").replaceAll("\\s+", " ").trim();
        return t.replace('\u0000', ':').replace('\u0001', '.').replace('\u0002', '/');
    }

    /** Words that legitimately start with ש; a leftover complementizer ש after a command/route must not eat them. */
    static final Set<String> SHIN_KEEP = new HashSet<>(Arrays.asList(
        "שיעור", "שיעורים", "שיעורי", "שיחת", "שיחה", "שיחות", "שמירה", "שמירת", "שמירות", "שישי", "שבת", "שעה", "שעות", "שעתיים", "שני", "שניה", "שנייה", "שנים", "שנה", "שבוע", "שבועות", "שירות", "שירותים", "שיר", "שירים", "שירה", "שחר", "שחרית", "שחייה", "שחיה", "שחמט", "שוק", "שער", "שערים", "שלום", "שמחה", "שבע", "שמונה", "שש", "שלוש", "שתיים", "שתים", "שותף", "שותפות", "שיט", "שיניים", "שינה", "שינת", "שקד", "שלומי"));

    /** Drop a connector ש left after a stripped command/route ("תרשום ביומן שהרכב" → "הרכב"), not "שיעור"/"שיחה"/"שמירה". */
    public static String stripConnectorShin(String s) {
        if (s == null) return "";
        s = s.replaceAll("^\\s+", "");
        if (s.equals("ש") || s.startsWith("ש ")) return s.substring(1).trim();
        if (s.length() > 1 && s.charAt(0) == 'ש') {
            int cut = s.length();
            for (int i = 1; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == ' ' || c == ',' || c == '.' || c == '!' || c == '?') { cut = i; break; }
            }
            String tok = s.substring(0, cut);
            if (!SHIN_KEEP.contains(tok)) return s.substring(1).trim();
        }
        return s;
    }

    /** @param explicitTime true when the user said a time of day (parser timeDefaulted == false). */
    public static Result classify(String text, boolean explicitTime) {
        return classify(text, explicitTime, false);
    }

    /**
     * @param whatsappEnabled setting, default off. When false the result matches {@link #classify(String, boolean)}.
     *        When true, a channel word is the only destination and is removed from the text.
     */
    public static Result classify(String text, boolean explicitTime, boolean whatsappEnabled) {
        String t = COMMAND.matcher(normalize(text)).replaceFirst("");
        if (whatsappEnabled && WHATSAPP.matcher(t).find()) return new Result(Kind.WHATSAPP, stripWhatsApp(t));
        String s;
        // Explicit note at the start wins over everything, including "יומן".
        if ((s = strip(t, EXPLICIT_NOTE)) != null) return new Result(Kind.NOTE, s);
        if (CALENDAR.matcher(t).find()) {
            String c = CALENDAR.matcher(t).replaceAll(" ").replaceAll("\\s+", " ").trim();
            if (c.isEmpty()) return new Result(Kind.MEETING, t);
            // leftover command word + connector ש ("תרשום שהרכב")
            String afterCmd = strip(c, new String[]{"תוסיף","תוסיפי","הוסף","הוסיפי","תקבע","תקבעי","קבע","קבעי","תרשום","תרשמי","רשום","רשמי","שים","תשים","תשימי","תכניס","תכניסי","הכנס","תיצור","תיצרי","צור","צרי","תזמן","תזמני","תזכיר לי","תזכירי לי"});
            if (afterCmd != null) c = afterCmd;
            return new Result(Kind.MEETING, stripConnectorShin(c));
        }
        if ((s = strip(t, MEETING_PREFIX)) != null) return new Result(Kind.MEETING, stripConnectorShin(s));
        if ((s = strip(t, TASK_PREFIX)) != null) {
            // "תזכיר לי פגישה עם אורי" — meeting cue wins over the reminder prefix.
            s = stripConnectorShin(s);
            if (meetingCue(s)) return new Result(Kind.MEETING, s);
            return new Result(Kind.TASK, s);
        }
        if ((s = strip(t, NOTE_PREFIX)) != null) return new Result(Kind.NOTE, stripConnectorShin(s));
        if (INVITE_TO_MEETING.matcher(t).find()) return new Result(Kind.MEETING, t);
        if (STARTS_MEETING.matcher(t).find()) return new Result(Kind.MEETING, t);
        if (eventAfterTime(t)) return new Result(Kind.MEETING, t);
        String first = t.isEmpty() ? "" : t.split(" ")[0];
        boolean meetingVerb = MEETING_VERBS.contains(first);
        if (meetingVerb) return new Result(Kind.MEETING, t);
        if (hasAction(t)) return new Result(Kind.TASK, t);

        if (EVENT_NAME.matcher(t).find() || GATHERING.matcher(t).find() || WITH_PERSON.matcher(t).find())
            return new Result(Kind.MEETING, t);
        // Date+time with no cue and no action verb → calendar.
        if (explicitTime) return new Result(Kind.MEETING, t);
        if (t.startsWith("פגישה") || t.startsWith("ישיבה")) return new Result(Kind.MEETING, t);

        if (OBLIGATION.matcher(t).find()) return new Result(Kind.TASK, t);
        return new Result(Kind.NOTE, t);
    }

    static boolean meetingCue(String t) {
        if (t == null || t.isEmpty()) return false;
        String first = t.split(" ")[0];
        return CALENDAR.matcher(t).find() || STARTS_MEETING.matcher(t).find()
            || INVITE_TO_MEETING.matcher(t).find() || EVENT_NAME.matcher(t).find()
            || GATHERING.matcher(t).find() || MEETING_VERBS.contains(first) || eventAfterTime(t);
    }

    /** Event/meeting word after leading when-words: "מחר יום הולדת לדני", "מחר יש חוג לנועה". */
    static boolean eventAfterTime(String t) {
        String[] w = t.isEmpty() ? new String[0] : t.split(" ");
        int i = 0;
        while (i < w.length && (TIME_WORDS.contains(w[i]) || w[i].matches("ב?-?\\d.*")
                || w[i].matches("ב(?:שעה|עשר|אחת|שתיים|שלוש|ארבע|חמש|שש|שבע|שמונה|תשע|אחת עשרה|שתים עשרה)"))) i++;
        if (i < w.length && ("יש".equals(w[i]) || "יהיה".equals(w[i]) || "תהיה".equals(w[i]))) i++;
        if (i >= w.length) return false;
        StringBuilder rest = new StringBuilder(w[i]);
        for (int j = i + 1; j < w.length; j++) rest.append(' ').append(w[j]);
        String r = rest.toString();
        return STARTS_MEETING.matcher(r).find() || EVENT_NAME.matcher(r).find();
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

    /** Drop every channel word. The rest of the utterance is the message, including "פגישה" or "למשימה" if spoken. */
    static String stripWhatsApp(String t) {
        String c = WHATSAPP.matcher(t).replaceAll(" ").replaceAll("\\s+", " ").trim();
        return stripConnectorShin(c);
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
