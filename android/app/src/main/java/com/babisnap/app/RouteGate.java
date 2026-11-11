package com.babisnap.app;

/**
 * Where a classified phrase is allowed to go.
 * WHATSAPP never enters the calendar queue or the BabiTk queue.
 */
public final class RouteGate {
    public enum Dest { CALENDAR, BABITK, WHATSAPP }

    public static Dest dest(Classifier.Kind kind) {
        if (kind == Classifier.Kind.MEETING) return Dest.CALENDAR;
        if (kind == Classifier.Kind.WHATSAPP) return Dest.WHATSAPP;
        return Dest.BABITK;
    }

    public static boolean toCalQueue(Classifier.Kind kind) { return dest(kind) == Dest.CALENDAR; }
    public static boolean toBabiTk(Classifier.Kind kind) { return dest(kind) == Dest.BABITK; }
    public static boolean toWhatsApp(Classifier.Kind kind) { return dest(kind) == Dest.WHATSAPP; }

    private RouteGate() {}
}
