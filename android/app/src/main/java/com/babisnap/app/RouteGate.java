package com.babisnap.app;

/**
 * Where a classified phrase is allowed to go.
 * WHATSAPP and CALL never enter the calendar queue or the BabiTk queue.
 */
public final class RouteGate {
    public enum Dest { CALENDAR, BABITK, WHATSAPP, CALL }

    public static Dest dest(Classifier.Kind kind) {
        if (kind == Classifier.Kind.MEETING) return Dest.CALENDAR;
        if (kind == Classifier.Kind.WHATSAPP) return Dest.WHATSAPP;
        if (kind == Classifier.Kind.CALL) return Dest.CALL;
        return Dest.BABITK;
    }

    public static boolean toCalQueue(Classifier.Kind kind) { return dest(kind) == Dest.CALENDAR; }
    public static boolean toBabiTk(Classifier.Kind kind) { return dest(kind) == Dest.BABITK; }
    public static boolean toWhatsApp(Classifier.Kind kind) { return dest(kind) == Dest.WHATSAPP; }
    public static boolean toCall(Classifier.Kind kind) { return dest(kind) == Dest.CALL; }

    private RouteGate() {}
}
