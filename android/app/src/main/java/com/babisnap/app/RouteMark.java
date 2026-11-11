package com.babisnap.app;

import android.graphics.drawable.Drawable;
import android.widget.TextView;

/**
 * Small route glyph drawn at the start of the card pill, beside the route name.
 * BabiTk has no logo in this repo, so that route uses a checklist glyph.
 */
public final class RouteMark {
    public enum Route { CALENDAR, BABITK, WHATSAPP }

    public static Route of(Classifier.Kind kind) {
        if (kind == Classifier.Kind.MEETING) return Route.CALENDAR;
        if (kind == Classifier.Kind.WHATSAPP) return Route.WHATSAPP;
        return Route.BABITK;
    }

    public static int drawable(Route route) {
        if (route == Route.CALENDAR) return R.drawable.ic_route_calendar;
        if (route == Route.WHATSAPP) return R.drawable.ic_route_whatsapp;
        return R.drawable.ic_route_babitk;
    }

    /** Icon is the height of the pill text, on the reading-start side (right in RTL). */
    public static void apply(TextView chip, Route route, int tint) {
        Drawable raw = chip.getContext().getDrawable(drawable(route));
        if (raw == null) return;
        Drawable icon = raw.mutate();
        icon.setTint(tint);
        float density = chip.getResources().getDisplayMetrics().density;
        int px = Math.round(chip.getTextSize());
        if (px < Math.round(12f * density)) px = Math.round(12f * density);
        icon.setBounds(0, 0, px, px);
        chip.setCompoundDrawablesRelative(icon, null, null, null);
        chip.setCompoundDrawablePadding(Math.round(4f * density));
        chip.setGravity(android.view.Gravity.CENTER_VERTICAL);
    }

    private RouteMark() {}
}
