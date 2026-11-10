package com.babisnap.grouptest;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure helpers for the ק0 probe. No Android types, so the JVM tests can run them.
 * Nothing here writes a log.
 */
public final class ProbeLogic {
    private static final Pattern JID = Pattern.compile(
            "([0-9]{5,30}(?:-[0-9]{5,30})?@(?:g\\.us|s\\.whatsapp\\.net|lid))",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CODE = Pattern.compile("[A-Z0-9_]{1,32}");
    private static final Pattern ACTIVITY = Pattern.compile("com\\.whatsapp(\\.[A-Za-z0-9_]+)+");
    private static final Pattern VERSION = Pattern.compile("[0-9]+(\\.[0-9]+){1,6}");

    /** Activities to try when WhatsApp does not publish ACTION_CREATE_SHORTCUT. */
    public static final String[] SHORTCUT_ACTIVITIES = {
            "com.whatsapp.contact.picker.ContactPicker",
            "com.whatsapp.ContactPicker",
            "com.whatsapp.contact.picker.ContactPickerBottomSheetActivity",
            "com.whatsapp.Conversation",
            "com.whatsapp.conversation.Conversation",
            "com.whatsapp.home.ui.HomeActivity",
            "com.whatsapp.HomeActivity"
    };

    public static final class Found {
        public final String jid;
        public final boolean group;

        public Found(String jid, boolean group) {
            this.jid = jid;
            this.group = group;
        }
    }

    /** Prefer a group id (@g.us) when several identifiers appear in the same payload. */
    public static Found findJid(Iterable<String> blobs) {
        String group = null;
        String other = null;
        if (blobs == null) return null;
        for (String raw : blobs) {
            if (raw == null || raw.isEmpty()) continue;
            for (String decoded : decodes(raw)) {
                Matcher m = JID.matcher(decoded);
                while (m.find()) {
                    String jid = m.group(1);
                    if (jid.toLowerCase(Locale.US).endsWith("@g.us")) group = jid;
                    else if (other == null) other = jid;
                }
            }
        }
        if (group != null) return new Found(group, true);
        if (other != null) return new Found(other, false);
        return null;
    }

    public static boolean isGroupJid(String jid) {
        Found found = findJid(Collections.singleton(jid));
        return found != null && found.group && jid != null && jid.equals(found.jid);
    }

    private static List<String> decodes(String raw) {
        List<String> out = new ArrayList<>(3);
        out.add(raw);
        String cur = raw;
        for (int i = 0; i < 2; i++) {
            try {
                String next = URLDecoder.decode(cur, StandardCharsets.UTF_8.name());
                if (next.equals(cur)) break;
                out.add(next);
                cur = next;
            } catch (Exception e) {
                break;
            }
        }
        return out;
    }

    /** Trim and collapse whitespace only. Letters, emoji and punctuation stay. */
    public static String normalizeTitle(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length());
        boolean pending = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) {
                pending = b.length() > 0;
            } else {
                if (pending) b.append(' ');
                pending = false;
                b.append(c);
            }
        }
        return b.toString();
    }

    public static boolean titlesMatch(String screen, String expected) {
        String a = normalizeTitle(screen);
        String b = normalizeTitle(expected);
        return !a.isEmpty() && a.equals(b);
    }

    /** Israeli number to digits without '+', or null when it is not one we can send to. */
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
        if (s.startsWith("0") && s.length() >= 9 && s.length() <= 10) {
            s = "972" + s.substring(1);
        } else if (s.startsWith("5") && s.length() == 9) {
            s = "972" + s;
        }
        if (!s.startsWith("972") || s.length() < 11 || s.length() > 13) return null;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') return null;
        }
        return s;
    }

    public static String contactJid(String e164Digits) {
        if (e164Digits == null || !e164Digits.matches("972[0-9]{8,10}")) return null;
        return e164Digits + "@s.whatsapp.net";
    }

    public static boolean idIsTitle(String viewId) {
        if (viewId == null) return false;
        String id = viewId.toLowerCase(Locale.US);
        return id.endsWith("/conversation_contact_name")
                || id.endsWith("/toolbar_title")
                || id.endsWith("/action_bar_title");
    }

    public static boolean idIsSend(String viewId) {
        if (viewId == null) return false;
        String id = viewId.toLowerCase(Locale.US);
        int slash = id.lastIndexOf('/');
        String leaf = slash >= 0 ? id.substring(slash + 1) : id;
        return leaf.equals("send") || leaf.equals("send_button") || leaf.equals("send_btn");
    }

    /** Voice / attach / camera controls are never the send button. */
    public static boolean rejectedControl(String contentDesc) {
        if (contentDesc == null || contentDesc.isEmpty()) return false;
        String he = contentDesc;
        String en = contentDesc.toLowerCase(Locale.US);
        return contains(he, "הקלט") || contains(he, "קולית") || contains(he, "מיקרופון")
                || contains(he, "מצלמה") || contains(he, "צרף") || contains(he, "אמוג")
                || contains(en, "voice") || contains(en, "record") || contains(en, "attach")
                || contains(en, "emoji") || contains(en, "camera") || contains(en, "microphone");
    }

    public static boolean descConfirmsSend(String contentDesc) {
        if (contentDesc == null || rejectedControl(contentDesc)) return false;
        String n = normalizeTitle(contentDesc);
        return n.equals("שלח") || n.equals("שליחה") || n.equals("שלח הודעה")
                || n.equalsIgnoreCase("Send") || n.equalsIgnoreCase("Send message");
    }

    /**
     * A send control we are willing to report. Clicking still requires {@link #descConfirmsSend}
     * so an unlabeled mic button that reuses the send id is not pressed.
     */
    public static boolean isSendControl(String viewId, String contentDesc, boolean clickable) {
        if (rejectedControl(contentDesc)) return false;
        if (idIsSend(viewId)) return true;
        return clickable && descConfirmsSend(contentDesc);
    }

    /** Fixed codes only. Raw descriptions never leave the phone in the report. */
    public static String descCode(String contentDesc) {
        if (contentDesc == null || contentDesc.isEmpty()) return "EMPTY";
        if (descConfirmsSend(contentDesc)) {
            String n = normalizeTitle(contentDesc);
            if (n.equals("שלח") || n.equals("שליחה") || n.equals("שלח הודעה")) return "SEND_HE";
            return "SEND_EN";
        }
        if (rejectedControl(contentDesc)) return "VOICE";
        return "OTHER";
    }

    private static boolean contains(String hay, String needle) {
        return hay != null && hay.contains(needle);
    }

    public static String cardHeadline(String kind, String name, String alias, int seconds) {
        boolean hasAlias = alias != null && !normalizeTitle(alias).isEmpty();
        String who = hasAlias ? normalizeTitle(alias) : normalizeTitle(name);
        if (who.isEmpty()) who = "group".equals(kind) ? "קבוצה" : "נמען";
        String when = seconds <= 0 ? "עכשיו" : "בעוד " + seconds + (seconds == 1 ? " שנייה" : " שניות");
        if ("group".equals(kind) && !hasAlias) return "שולח לקבוצה: " + who + " · " + when;
        return "שולח ל" + who + " " + when;
    }

    public static String checkLine(String expectedName) {
        String name = normalizeTitle(expectedName);
        if (name.isEmpty()) name = "—";
        return "הכותרת בצ׳אט חייבת להיות זהה ל: " + name;
    }

    public static String autoReason(String code, String kind) {
        if ("TITLE_MISMATCH".equals(code) && "group".equals(kind)) return "שם הקבוצה השתנה – אמת מחדש";
        if ("TITLE_MISMATCH".equals(code)) return "הכותרת לא תואמת את השם השמור – לא נשלח";
        if ("NO_BUTTON".equals(code)) return "לא נמצא כפתור שלח – לא נשלח";
        if ("SEND_UNCONFIRMED".equals(code)) return "נמצא כפתור בלי הכיתוב «שלח» – לא נלחץ";
        if ("NO_TITLE".equals(code)) return "לא נמצאה כותרת הצ׳אט – לא נשלח";
        if ("CLICK_FAILED".equals(code)) return "הכפתור נמצא אבל הלחיצה נכשלה – לא נשלח";
        if ("CANCELLED".equals(code)) return "בוטל";
        if ("NOT_CHECKED".equals(code)) return "הנמען לא מסומן ברשימה – לא נשלח";
        if ("A11Y_OFF".equals(code)) return "שירות הנגישות כבוי – לא נלחץ";
        if ("NOT_FOREGROUND".equals(code)) return "WhatsApp לא בקדמה – לא נשלח";
        if ("NO_ACTIVITY".equals(code)) return "WhatsApp לא נפתח – לא נשלח";
        if ("OVERLAY_FAIL".equals(code)) return "כרטיס הביטול לא עלה – לא נשלח";
        if ("PRESSED".equals(code)) return "נלחץ שלח";
        return "לא נשלח";
    }

    public static String sanitizeCode(String code, String fallback) {
        if (code != null && CODE.matcher(code).matches()) return code;
        return fallback != null && CODE.matcher(fallback).matches() ? fallback : "UNKNOWN";
    }

    public static String activityCode(String className) {
        if (className != null && ACTIVITY.matcher(className).matches()) return className;
        return "NONE";
    }

    public static String versionCode(String version) {
        if (version != null && VERSION.matcher(version).matches()) return version;
        return "UNKNOWN";
    }

    public static String pkgCode(boolean wa, boolean business) {
        if (wa && business) return "BOTH";
        if (wa) return "WA";
        if (business) return "W4B";
        return "NONE";
    }

    /** Clipboard report. Every value is a code, a count, or a version. No names, jids, or text. */
    public static final class Report {
        public int androidSdk;
        public String waVersion;
        public String pkg;
        public String shortcut;
        public String shortcutRoute;
        public String shortcutActivity;
        public boolean notifOn;
        public int groups;
        public boolean groupNoJid;
        public boolean directReply;
        public boolean contactJidSeen;
        public boolean contactsPerm;
        public int selected;
        public String sendKind;
        public String sendPath;
        public String sendMark;
        public String flow;
        public boolean a11yOn;
        public boolean autoPress;
        public String titleMatch;
        public String sendButton;
        public String sendLabel;
        public String autoResult;

        public String format() {
            StringBuilder sb = new StringBuilder();
            line(sb, "אנדרואיד (ANDROID_SDK)", Integer.toString(Math.max(0, androidSdk)));
            line(sb, "גרסת וואטסאפ (WA_VERSION)", versionCode(waVersion));
            line(sb, "חבילה (PKG)", sanitizeCode(pkg, "NONE"));
            line(sb, "קיצור (SHORTCUT)", sanitizeCode(shortcut, "NOT_TRIED"));
            line(sb, "נתיב קיצור (SHORTCUT_ROUTE)", sanitizeCode(shortcutRoute, "NONE"));
            line(sb, "פעילות קיצור (SHORTCUT_ACTIVITY)", activityCode(shortcutActivity));
            line(sb, "התראות (NOTIF)", notifOn ? "ON" : "OFF");
            line(sb, "קבוצות שנשמרו (GROUPS)", Integer.toString(Math.max(0, groups)));
            line(sb, "קבוצה בלי מזהה (GROUP_NO_JID)", groupNoJid ? "YES" : "NO");
            line(sb, "מענה ישיר (DIRECT_REPLY)", directReply ? "YES" : "NO");
            line(sb, "מזהה איש קשר מהתראה (CONTACT_JID_SEEN)", contactJidSeen ? "YES" : "NO");
            line(sb, "אנשי קשר (CONTACTS_PERM)", contactsPerm ? "ON" : "OFF");
            line(sb, "נבחרו (SELECTED)", Integer.toString(Math.max(0, selected)));
            line(sb, "סוג יעד (SEND_KIND)", sanitizeCode(sendKind, "NONE"));
            line(sb, "נתיב שליחה (SEND_PATH)", sanitizeCode(sendPath, "NONE"));
            line(sb, "סימון פתיחה (SEND_MARK)", sanitizeCode(sendMark, "NONE"));
            line(sb, "מהלך (FLOW)", sanitizeCode(flow, "NONE"));
            line(sb, "נגישות (A11Y)", a11yOn ? "ON" : "OFF");
            line(sb, "לחיצה אוטומטית (AUTO_PRESS)", autoPress ? "ON" : "OFF");
            line(sb, "כותרת (TITLE_MATCH)", sanitizeCode(titleMatch, "UNKNOWN"));
            line(sb, "כפתור שלח (SEND_BUTTON)", sanitizeCode(sendButton, "UNKNOWN"));
            line(sb, "כיתוב כפתור (SEND_LABEL)", sanitizeCode(sendLabel, "NONE"));
            line(sb, "תוצאת לחיצה (AUTO_RESULT)", sanitizeCode(autoResult, "NONE"));
            return sb.toString();
        }

        private static void line(StringBuilder sb, String label, String value) {
            sb.append(label).append(": ").append(value).append('\n');
        }
    }

    private ProbeLogic() {}
}
