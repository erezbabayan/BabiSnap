package com.babisnap.grouptest;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pulls a private-chat recipient and a message body out of a Hebrew command.
 * Labels come from the on-device recipient list. Nothing here is logged.
 */
public final class CommandParse {
    private static final Pattern CHANNEL = Pattern.compile(
            "(?:^|\\s)((?:[בלו])?(?:וואטסאפ|ווטסאפ|וואצאפ|ווצאפ|whatsapp))(?=$|[\\s:،,.])",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final String[] LEADING = {
            "הודעה", "תשלח", "שלח", "תשלחי", "תכתוב", "כתוב", "תגיד", "תגידי"
    };
    private static final String[] KEEP_SHIN = {"שלום", "שבת", "שנה", "שבוע"};

    public static final class Result {
        public final String code;
        public final String label;
        public final String body;

        private Result(String code, String label, String body) {
            this.code = code;
            this.label = label;
            this.body = body;
        }

        public static Result of(String code) {
            return new Result(code, null, null);
        }

        public static Result ok(String label, String body) {
            return new Result("OK", label, body);
        }
    }

    /** Whitespace collapsed, Hebrew points removed. */
    public static String normalize(String raw) {
        if (raw == null) return "";
        StringBuilder out = new StringBuilder(raw.length());
        boolean space = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c >= 0x0591 && c <= 0x05C7) continue;
            if (Character.isWhitespace(c)) {
                space = out.length() > 0;
            } else {
                if (space) out.append(' ');
                space = false;
                out.append(c);
            }
        }
        return out.toString();
    }

    public static Result parse(String utterance, List<String> labels) {
        String text = normalize(utterance);
        if (text.isEmpty()) return Result.of("NONE");
        Matcher channel = CHANNEL.matcher(text);
        if (!channel.find()) return Result.of("NONE");
        int channelStart = channel.start(1);
        int channelEnd = channel.end(1);
        if (labels == null || labels.isEmpty()) return Result.of("NONE");

        List<Hit> hits = new ArrayList<>();
        for (String label : labels) {
            String known = normalize(label);
            if (known.isEmpty()) continue;
            Hit hit = findLabel(text, known, label);
            if (hit != null) hits.add(hit);
        }
        if (hits.isEmpty()) return Result.of("NONE");
        hits.sort((a, b) -> {
            int byLength = Integer.compare(b.known.length(), a.known.length());
            if (byLength != 0) return byLength;
            int da = Math.abs(a.start - channelStart);
            int db = Math.abs(b.start - channelStart);
            return Integer.compare(da, db);
        });
        Hit winner = hits.get(0);
        int winnerDistance = Math.abs(winner.start - channelStart);
        for (int i = 1; i < hits.size(); i++) {
            Hit other = hits.get(i);
            if (other.known.equals(winner.known)) continue;
            if (other.known.length() == winner.known.length()
                    && Math.abs(other.start - channelStart) == winnerDistance) {
                return Result.of("AMBIGUOUS");
            }
        }
        String body = cleanBody(cut(text, channelStart, channelEnd, winner.start, winner.end));
        if (body.isEmpty()) return Result.of("EMPTY_BODY");
        return Result.ok(winner.rawLabel, body);
    }

    private static Hit findLabel(String text, String known, String rawLabel) {
        String[] needles = {"ל" + known, "אל " + known};
        Hit best = null;
        for (String needle : needles) {
            int from = 0;
            while (from < text.length()) {
                int at = text.indexOf(needle, from);
                if (at < 0) break;
                int end = at + needle.length();
                if (boundaryBefore(text, at) && boundaryAfter(text, end)) {
                    if (best == null || needle.length() > (best.end - best.start)) {
                        best = new Hit(at, end, known, rawLabel);
                    }
                }
                from = at + 1;
            }
        }
        return best;
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
        StringBuilder out = new StringBuilder();
        out.append(text, 0, firstStart);
        out.append(text, firstEnd, secondStart);
        out.append(text, secondEnd, text.length());
        return out.toString();
    }

    private static String cleanBody(String raw) {
        String text = normalize(raw);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String word : LEADING) {
                if (text.equals(word)) return "";
                if (text.startsWith(word + " ")) {
                    text = normalize(text.substring(word.length()));
                    changed = true;
                }
            }
        }
        if (text.startsWith(":")) text = normalize(text.substring(1));
        int space = text.indexOf(' ');
        String first = space < 0 ? text : text.substring(0, space);
        if (first.length() > 1 && first.charAt(0) == 'ש' && !kept(first)) {
            String rest = first.substring(1) + (space < 0 ? "" : text.substring(space));
            text = normalize(rest);
        }
        return text;
    }

    private static boolean kept(String word) {
        for (String keep : KEEP_SHIN) {
            if (keep.equals(word)) return true;
        }
        return false;
    }

    private static final class Hit {
        final int start;
        final int end;
        final String known;
        final String rawLabel;

        Hit(int start, int end, String known, String rawLabel) {
            this.start = start;
            this.end = end;
            this.known = known;
            this.rawLabel = rawLabel;
        }
    }

    private CommandParse() {}
}
