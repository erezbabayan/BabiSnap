package com.babisnap.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Immediate voice dial ("תתקשר לתמרי"). Pure Java.
 * A phrase with a future date or time is not a call — the classifier keeps the old task or calendar route.
 * A call never becomes a calendar event or a BabiTk item.
 * The confirm card renders {@link #actions()}. Today that is only {@link Action#DIAL}
 * (system dialer, the user taps call). A later WhatsApp voice-call button is another
 * {@link Action} with the label "וואטסאפ" plus a branch in VoiceActivity — not built here.
 */
public final class CallDial {
    public static final long CONFIRM_MS = 8000L;
    /** Settings switch. Default off. Auto-call also needs CALL_PHONE. */
    public static final String PREF_DIRECT = "call_direct";
    public static final String TOGGLE_TITLE = "אישור לבצע שיחות";
    public static final String TOGGLE_HINT = "בסוף הספירה השיחה תצא ישירות, בלי חייגן";
    public static final String HINT = "המספר מולא מראש. הלחיצה בחייגן היא זו שמתחילה את השיחה.";
    public static final String HINT_AUTO = "השיחה תצא אוטומטית בסוף הספירה";
    public static final String NO_NAME = "לא שמעתי למי להתקשר";
    public static final String NO_PERMISSION = "אין גישה לאנשי הקשר, אז אי אפשר לחייג";
    public static final String NO_DIALER = "אין אפליקציית חייגן בטלפון";
    public static final String NO_NUMBER = "אין מספר לחייג";
    public static final String CANCELLED = "בוטל – לא בוצעה שיחה";
    public static final String PICK = "למי לחייג?";
    public static final String DENIED_MSG = "בלי אישור לשיחות החיוג נשאר דרך החייגן";
    public static final String BLOCKED_MSG = "האישור לשיחות חסום. הרשאות ← טלפון ← אפשר";

    /** Where a single resolved number goes. NONE places nothing. */
    public enum Handset { CALL, DIAL, NONE }

    /** TIMEOUT and TAP can dial. CANCEL and LEAVE never do. */
    public enum Moment { TIMEOUT, TAP, CANCEL, LEAVE }

    /** Settings switch when the user flips «אישור לבצע שיחות». */
    public enum ToggleResult { SAVED_OFF, SAVED_ON, NEED_PROMPT, NEED_SETTINGS }

    /** Buttons on the confirm card, in order. Only the dialer button exists today. */
    public enum Action {
        DIAL("חייג");

        public final String label;
        Action(String label) { this.label = label; }
    }

    static final Set<String> VERBS = new HashSet<>(Arrays.asList("תתקשר", "תתקשרי", "תתקשרו", "להתקשר"));
    private static final Set<String> FILLER = new HashSet<>(Arrays.asList(
        "עכשיו", "בבקשה", "בדחיפות", "דחוף", "מיד", "מהר", "כבר", "נא", "אנא", "בטלפון", "טלפון", "לי", "לנו"));
    private static final Set<String> STOP = new HashSet<>(Arrays.asList(
        "לגבי", "על", "בשביל", "כדי", "אחרי", "לפני", "כי", "וגם", "גם", "של", "שלי", "שלנו", "שלו", "שלה", "שלהם", "שלהן"));
    private static final Set<String> WHEN = new HashSet<>(Arrays.asList(
        "היום", "להיום", "מחר", "למחר", "במחר", "המחר", "מחרתיים",
        "הערב", "הלילה", "הבוקר", "הצהריים",
        "בבוקר", "בערב", "בצהריים", "בצהרים", "בלילה",
        "בשעה", "בתאריך", "מוצאי", "מוצש"));
    private static final String[] MONTHS = {
        "ינואר", "פברואר", "מרץ", "מרס", "אפריל", "מאי", "יוני", "יולי",
        "אוגוסט", "ספטמבר", "אוקטובר", "נובמבר", "דצמבר"
    };

    private static final Pattern CLOCK = Pattern.compile("(?:^|\\s)(?:ב-?)?\\d{1,2}:\\d{2}(?=\\s|$)");
    private static final Pattern NUMERIC_DATE = Pattern.compile("(?:^|\\s)(?:ב-?)?\\d{1,2}[./]\\d{1,2}(?:[./]\\d{2,4})?(?=\\s|$)");
    private static final Pattern B_NUMBER = Pattern.compile("(?:^|\\s)ב-?\\d{1,2}(?=\\s|$)");
    private static final Pattern HOUR = Pattern.compile("(?:^|\\s)ב(?:שעה|עשרה|עשר|אחת|אחד|שתיים|שתים|שלוש|ארבע|חמש|שש|שבע|שמונה|תשע)(?=\\s|$)");
    private static final Pattern B_WEEKDAY = Pattern.compile("(?:^|\\s)ב(?:ראשון|שני|שלישי|רביעי|חמישי|שישי|שבת)(?=\\s|$)");
    private static final Pattern YOM_WEEKDAY = Pattern.compile("(?:^|\\s)(?:ב|ל|ה)?יום\\s+(?:ראשון|שני|שלישי|רביעי|חמישי|שישי|שבת)(?=\\s|$)");
    private static final Pattern RELATIVE = Pattern.compile("(?:^|\\s)(?:בעוד|עוד)\\s+(?:\\d+|שעתיים|שעה|שעות|דקות|דקה|יום|יומיים|ימים|שבוע|שבועות)(?=\\s|$)");
    private static final Pattern NEXT_WEEK = Pattern.compile("(?:^|\\s)(?:ב|ל|ה)?שבוע\\s+הבא(?=\\s|$)");
    private static final Pattern ALL_DAY = Pattern.compile("(?:^|\\s)(?:כל\\s+היום|יום\\s+שלם|יום\\s+מלא)(?=\\s|$)");

    public static List<Action> actions() { return Collections.singletonList(Action.DIAL); }

    /** The switch is on for real only when the user asked and CALL_PHONE is still granted. */
    public static boolean effective(boolean toggleOn, boolean permissionGranted) {
        return toggleOn && permissionGranted;
    }

    /**
     * Timeout and «חייג» place the call directly only for one resolved number when the switch is on
     * and CALL_PHONE is granted. Otherwise they open the dialer. Cancel, leaving the card, and a
     * picker (no single number) place nothing.
     */
    public static Handset place(Moment moment, boolean toggleOn, boolean permissionGranted, boolean singleResolvedNumber) {
        if (moment != Moment.TIMEOUT && moment != Moment.TAP) return Handset.NONE;
        if (!singleResolvedNumber) return Handset.NONE;
        return effective(toggleOn, permissionGranted) ? Handset.CALL : Handset.DIAL;
    }

    public static String intentAction(Handset handset) {
        if (handset == Handset.CALL) return "android.intent.action.CALL";
        if (handset == Handset.DIAL) return "android.intent.action.DIAL";
        return null;
    }

    public static ToggleResult onToggle(boolean wantOn, boolean permissionGranted, boolean permanentlyDenied) {
        if (!wantOn) return ToggleResult.SAVED_OFF;
        if (permissionGranted) return ToggleResult.SAVED_ON;
        if (permanentlyDenied) return ToggleResult.NEED_SETTINGS;
        return ToggleResult.NEED_PROMPT;
    }

    /**
     * @param normalized text after {@link Classifier#normalize} and the leading command strip
     * @param explicitTime parser saw a clock time (or an all-day span)
     * @param commandPrefixed the phrase opened with "תוסיף" / "תרשום" / "שים" — that is a saved item, not a dial
     */
    public static boolean isImmediate(String normalized, boolean explicitTime, boolean commandPrefixed) {
        if (explicitTime || commandPrefixed) return false;
        if (normalized == null || normalized.isEmpty()) return false;
        if (Classifier.WHATSAPP.matcher(normalized).find()) return false;
        if (Classifier.GATHERING.matcher(normalized).find()) return false;
        if (Classifier.OBLIGATION.matcher(normalized).find()) return false;
        if (Classifier.CALENDAR.matcher(normalized).find()) return false;
        if (scheduled(normalized)) return false;
        if (recipients(normalized) > 1) return false;
        return onlyCall(normalized);
    }

    /** True when the phrase names a date or a time of day. "עכשיו" is not one of those. */
    static boolean scheduled(String t) {
        if (t == null || t.isEmpty()) return false;
        if (CLOCK.matcher(t).find() || NUMERIC_DATE.matcher(t).find() || B_NUMBER.matcher(t).find()) return true;
        if (HOUR.matcher(t).find() || B_WEEKDAY.matcher(t).find() || YOM_WEEKDAY.matcher(t).find()) return true;
        if (RELATIVE.matcher(t).find() || NEXT_WEEK.matcher(t).find() || ALL_DAY.matcher(t).find()) return true;
        for (String w : t.split(" ")) {
            if (WHEN.contains(w)) return true;
            if (monthCue(w)) return true;
        }
        return false;
    }

    /** "במאי" is a month. "למאי" / "מאי" can be a person's name, so they are not. */
    private static boolean monthCue(String token) {
        if (token == null || token.length() < 3 || token.charAt(0) != 'ב') return false;
        String rest = token.substring(1);
        for (String m : MONTHS) if (m.equals(rest)) return true;
        return false;
    }

    private static boolean onlyCall(String t) {
        boolean saw = false;
        for (String w : t.split(" ")) {
            if (w.isEmpty()) continue;
            String bare = w.charAt(0) == 'ו' && w.length() > 1 ? w.substring(1) : w;
            boolean call = VERBS.contains(w) || VERBS.contains(bare);
            if (call) { saw = true; continue; }
            boolean action = Classifier.ACTION_VERBS.contains(w) || Classifier.ACTION_VERBS.contains(bare)
                || Classifier.IMPERATIVES.contains(w) || Classifier.IMPERATIVES.contains(bare);
            if (action) return false;
        }
        return saw;
    }

    private static boolean recipientToken(String w) {
        if (w.equals("אל") || w.equals("עם") || w.equals("אצל")) return true;
        String x = w.startsWith("ו") && w.length() > 2 ? w.substring(1) : w;
        if (x.equals("אל") || x.equals("עם") || x.equals("אצל")) return true;
        if (VERBS.contains(w) || VERBS.contains(x)) return false;
        if (!x.startsWith("ל") || x.length() < 3) return false;
        if (Classifier.NOT_VERBS.contains(x) || WHEN.contains(x) || FILLER.contains(x) || STOP.contains(x)) return false;
        return true;
    }

    private static int recipients(String t) {
        int n = 0;
        for (String w : t.split(" ")) if (recipientToken(w)) n++;
        return n;
    }

    /** Spoken name with the ל / אל / עם marker removed. Empty when no person was said. */
    public static String nameOf(String normalized) {
        if (normalized == null || normalized.isEmpty()) return "";
        List<String> toks = new ArrayList<>();
        for (String w : normalized.split(" ")) {
            if (w.isEmpty()) continue;
            String bare = w.charAt(0) == 'ו' && VERBS.contains(w.substring(1)) ? w.substring(1) : w;
            if (VERBS.contains(bare) || FILLER.contains(w)) continue;
            toks.add(w);
        }
        while (!toks.isEmpty() && (toks.get(0).equals("את") || toks.get(0).equals("נא"))) toks.remove(0);
        if (toks.isEmpty()) return "";
        int i = 0;
        String first = toks.get(0);
        if (first.equals("אל") || first.equals("עם") || first.equals("אצל")) i = 1;
        else if (first.startsWith("ל") && first.length() >= 3 && !Classifier.NOT_VERBS.contains(first) && !STOP.contains(first))
            toks.set(0, first.substring(1));
        StringBuilder b = new StringBuilder();
        int n = 0;
        for (; i < toks.size() && n < 3; i++) {
            String w = toks.get(i);
            if (STOP.contains(w) || WHEN.contains(w) || scheduled(w)) break;
            if (n > 0 && recipientToken(w)) break;
            if (n > 0) b.append(' ');
            b.append(w);
            n++;
        }
        return b.toString().trim();
    }

    public static String notFound(String name) {
        String who = name == null ? "" : name.trim();
        if (who.isEmpty()) return NO_NAME;
        return "לא מצאתי את «" + who + "» באנשי הקשר";
    }

    /** Same name and nickname rules as WhatsApp, including a leading ל. Any dialable number qualifies. */
    public static WhatsAppRecipients.Matches match(String spoken, List<WhatsAppRecipients.Contact> contacts) {
        List<WhatsAppRecipients.Contact> eligible = new ArrayList<>();
        if (contacts != null) for (WhatsAppRecipients.Contact c : contacts) if (hasNumber(c)) eligible.add(c);
        return WhatsAppRecipients.matchAmong(spoken, eligible);
    }

    public static boolean hasNumber(WhatsAppRecipients.Contact contact) { return chooseRaw(contact) != null; }

    /** Mobile first, then any other number. Not limited to Israeli or WhatsApp numbers. */
    public static String chooseRaw(WhatsAppRecipients.Contact contact) {
        if (contact == null) return null;
        String mobile = null, any = null;
        for (WhatsAppRecipients.Phone p : contact.phones) {
            String d = dialable(p.raw);
            if (d == null) continue;
            if (p.mobile && mobile == null) mobile = d;
            if (any == null) any = d;
        }
        return mobile != null ? mobile : any;
    }

    /** Digits, with a leading + when the number is international. Null when there is nothing to dial. */
    public static String dialable(String raw) {
        if (raw == null) return null;
        StringBuilder b = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c >= '0' && c <= '9') b.append(c);
            else if (c == '+' && b.length() == 0) b.append('+');
        }
        String s = b.toString();
        if (s.startsWith("+")) {
            String d = s.substring(1);
            if (d.startsWith("00")) d = d.substring(2);
            return d.length() >= 3 ? "+" + d : null;
        }
        if (s.startsWith("00") && s.length() >= 6) return "+" + s.substring(2);
        if (s.startsWith("972") && s.length() >= 11 && s.length() <= 13) return "+" + s;
        return s.length() >= 3 ? s : null;
    }

    public static String tel(WhatsAppRecipients.Contact contact) {
        String n = chooseRaw(contact);
        return n == null ? null : "tel:" + n;
    }

    /**
     * 0501234567 and +972501234567 both become 050-•••-4567.
     * The middle digits stay hidden. A short number keeps only the last two.
     */
    public static String mask(String raw) {
        String dial = dialable(raw);
        if (dial == null) return "";
        boolean plus = dial.startsWith("+");
        String d = plus ? dial.substring(1) : dial;
        if (d.startsWith("972") && d.length() >= 11) {
            d = "0" + d.substring(3);
            plus = false;
        }
        int n = d.length();
        if (n < 8) return (plus ? "+" : "") + "•••-" + d.substring(n - 2);
        int head = n >= 10 ? 3 : 2;
        int tail = 4;
        if (head + tail >= n) { head = 2; tail = 2; }
        return (plus ? "+" : "") + d.substring(0, head) + "-•••-" + d.substring(n - tail);
    }

    public static String headline(WhatsAppRecipients.Contact contact) { return headline(contact, false); }

    public static String headline(WhatsAppRecipients.Contact contact, boolean direct) {
        String who = contact == null || contact.label().isEmpty() ? "איש הקשר" : contact.label();
        return direct ? "שיחה · מתקשר ל" + who : "שיחה · ייפתח החייגן אצל " + who;
    }

    public static String summary(WhatsAppRecipients.Contact contact) { return summary(contact, false); }

    public static String summary(WhatsAppRecipients.Contact contact, boolean direct) {
        String who = contact == null ? "" : contact.label();
        String hidden = mask(chooseRaw(contact));
        return who + "\n\u2066" + hidden + "\u2069\n" + (direct ? HINT_AUTO : HINT);
    }

    private CallDial() {}
}
