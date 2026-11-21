package com.babisnap.app;

import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * The one destination icon set (approved sample D): gradient rounded square with white line art.
 * Calendar purple, WhatsApp green, call teal, BabiTk task amber, BabiTk note pink-purple.
 * Every surface (card head, card pill, Settings rows) takes its icon from here so they stay identical.
 */
public final class DestIcon {
    public enum Dest { CALENDAR, WHATSAPP, CALL, TASK, NOTE }

    public static Dest of(Classifier.Kind kind) {
        if (kind == Classifier.Kind.MEETING) return Dest.CALENDAR;
        if (kind == Classifier.Kind.WHATSAPP) return Dest.WHATSAPP;
        if (kind == Classifier.Kind.CALL) return Dest.CALL;
        if (kind == Classifier.Kind.NOTE) return Dest.NOTE;
        return Dest.TASK;
    }

    /** Rounded-square tile: card head and Settings rows. */
    public static int tile(Dest d) {
        switch (d) {
            case CALENDAR: return R.drawable.ic_dest_calendar;
            case WHATSAPP: return R.drawable.ic_dest_whatsapp;
            case CALL: return R.drawable.ic_dest_call;
            case NOTE: return R.drawable.ic_dest_note;
            default: return R.drawable.ic_dest_task;
        }
    }

    /** Round badge of the same icon: inside the card pill. */
    public static int round(Dest d) {
        switch (d) {
            case CALENDAR: return R.drawable.ic_dest_calendar_round;
            case WHATSAPP: return R.drawable.ic_dest_whatsapp_round;
            case CALL: return R.drawable.ic_dest_call_round;
            case NOTE: return R.drawable.ic_dest_note_round;
            default: return R.drawable.ic_dest_task_round;
        }
    }

    /** Pill background, the same gradient as the tile. */
    public static int chip(Dest d) {
        switch (d) {
            case CALENDAR: return R.drawable.chip_dest_calendar;
            case WHATSAPP: return R.drawable.chip_dest_whatsapp;
            case CALL: return R.drawable.chip_dest_call;
            case NOTE: return R.drawable.chip_dest_note;
            default: return R.drawable.chip_dest_task;
        }
    }

    /** Pill text is white on every gradient. */
    public static final int CHIP_TEXT = 0xFFFFFFFF;

    /** Card head: the 72dp circle becomes the destination tile (no emoji). */
    public static void head(TextView check, Dest d) {
        check.setText("");
        check.setBackgroundResource(tile(d));
        check.setContentDescription(label(d));
    }

    /** Card pill: gradient background, white text, round icon at the reading start. Icon keeps its colours. */
    public static void chip(TextView chip, Dest d) {
        chip.setBackgroundResource(chip(d));
        chip.setTextColor(CHIP_TEXT);
        Drawable raw = chip.getContext().getDrawable(round(d));
        if (raw == null) return;
        float density = chip.getResources().getDisplayMetrics().density;
        int px = Math.max(Math.round(chip.getTextSize() * 1.25f), Math.round(14f * density));
        Drawable icon = raw.mutate();
        icon.setBounds(0, 0, px, px);
        chip.setCompoundDrawablesRelative(icon, null, null, null);
        chip.setCompoundDrawablePadding(Math.round(4f * density));
        chip.setGravity(android.view.Gravity.CENTER_VERTICAL);
        chip.setPaddingRelative(Math.round(4f * density), chip.getPaddingTop(), chip.getPaddingEnd(), chip.getPaddingBottom());
    }

    public static String label(Dest d) {
        switch (d) {
            case CALENDAR: return "יומן";
            case WHATSAPP: return "וואטסאפ";
            case CALL: return "שיחה";
            case NOTE: return "הערה ב-BabiTk";
            default: return "משימה ב-BabiTk";
        }
    }

    private DestIcon() {}
}
