package com.babisnap.app;

import java.util.ArrayList;
import java.util.List;

/**
 * «לאן לשלוח?» (approved sample F, plus «שיחה» approved 2026-11-22). Shown only when the phrase had no routing word, no date or time,
 * no contact and no tag, i.e. the classifier fell back to a guessed NOTE. Every other phrase routes exactly as before.
 * No answer within {@link #TIMEOUT_MS} keeps the old behaviour: a BabiTk note.
 */
public final class RouteChooser {
    public enum Choice { CALENDAR, WHATSAPP, CALL, TASK, NOTE }

    public static final long TIMEOUT_MS = 8000L;
    public static final Choice DEFAULT = Choice.NOTE;
    public static final String TITLE = "לאן לשלוח?";
    public static final String DEFAULT_HINT = "אם לא תבחר – יישמר כהערה ב־BabiTk";
    public static final String CANCELLED = "בוטל – לא נשמר";

    /**
     * @param dateDefaulted parser found no date
     * @param timeDefaulted parser found no time
     * @param hasTags the phrase carried #tags (those belong to BabiTk, so no question)
     */
    public static boolean shouldAsk(Classifier.Result c, boolean dateDefaulted, boolean timeDefaulted, boolean hasTags) {
        if (c == null || !c.guessed || c.kind != Classifier.Kind.NOTE) return false;
        if (c.stripped == null || c.stripped.trim().isEmpty()) return false;
        return dateDefaulted && timeDefaulted && !hasTags;
    }

    /** Circles in reading order. WhatsApp only when the user turned it on in Settings. */
    public static List<Choice> choices(boolean whatsappEnabled) {
        List<Choice> out = new ArrayList<>();
        out.add(Choice.CALENDAR);
        if (whatsappEnabled) out.add(Choice.WHATSAPP);
        out.add(Choice.CALL);
        out.add(Choice.TASK);
        out.add(Choice.NOTE);
        return out;
    }

    /** Circles per row: 5 → 3+2, 4 → 2+2, 3 → 2+1. */
    public static int perRow(int count) { return count >= 5 ? 3 : 2; }

    /** Circle edge in dp: a little smaller when three share a row. */
    public static int circleDp(int count) { return perRow(count) == 3 ? 66 : 76; }

    public static Classifier.Kind kind(Choice c) {
        switch (c) {
            case CALENDAR: return Classifier.Kind.MEETING;
            case WHATSAPP: return Classifier.Kind.WHATSAPP;
            case CALL: return Classifier.Kind.CALL;
            case TASK: return Classifier.Kind.TASK;
            default: return Classifier.Kind.NOTE;
        }
    }

    /** The same text, now with the destination the user picked. Never guessed again, so it never re-asks. */
    public static Classifier.Result chosen(Choice c, Classifier.Result original) {
        return new Classifier.Result(kind(c), original == null ? "" : original.stripped, false);
    }

    public static DestIcon.Dest dest(Choice c) {
        switch (c) {
            case CALENDAR: return DestIcon.Dest.CALENDAR;
            case WHATSAPP: return DestIcon.Dest.WHATSAPP;
            case CALL: return DestIcon.Dest.CALL;
            case TASK: return DestIcon.Dest.TASK;
            default: return DestIcon.Dest.NOTE;
        }
    }

    public static String label(Choice c) {
        switch (c) {
            case CALENDAR: return "יומן";
            case WHATSAPP: return "וואטסאפ";
            case CALL: return "שיחה";
            case TASK: return "משימה ב־BabiTk";
            default: return "הערה ב־BabiTk";
        }
    }

    private RouteChooser() {}
}
