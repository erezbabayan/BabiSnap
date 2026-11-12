package com.babisnap.app;

import android.content.SharedPreferences;

/** Five sizes for the 1×1 record tile, as a percent of the launcher cell. Default is רגיל, 72%. */
public final class WidgetSize {
    private WidgetSize() {}

    public static final String PREF = "record_button_percent";
    public static final int[] PERCENT = {64, 68, 72, 76, 80};
    public static final String[] LABELS = {"קטן מאוד", "קטן", "רגיל", "גדול", "גדול מאוד"};
    public static final int DEFAULT_PERCENT = 72;
    /** A typical launcher cell, so the fallback icon scales with the chosen percent. */
    public static final int CELL_DP = 90;
    public static final int TOP_OFFSET_DP = 3;
    public static final int LABEL_DP = 18;
    public static final int MIN_DP = 40, MAX_DP = 110;

    public static int percent(int stored) {
        for (int p : PERCENT) if (p == stored) return p;
        return DEFAULT_PERCENT;
    }

    public static int index(int stored) {
        int p = percent(stored);
        for (int i = 0; i < PERCENT.length; i++) if (PERCENT[i] == p) return i;
        return 2;
    }

    public static String label(int stored) { return LABELS[index(stored)]; }

    public static double fraction(int stored) { return percent(stored) / 100.0; }

    public static int saved(SharedPreferences prefs) {
        return percent(prefs == null ? DEFAULT_PERCENT : prefs.getInt(PREF, DEFAULT_PERCENT));
    }

    /** Used when the launcher does not report a cell size. 72% → 65dp. */
    public static int fallbackDp(int stored) {
        return Math.round(CELL_DP * (float) fraction(stored));
    }

    /** Tile edge in dp. 0 cell size means unknown. Never taller than the cell minus the label. */
    public static int iconDp(int cellW, int cellH, int stored) {
        int size = cellW > 0 ? (int) Math.round(cellW * fraction(stored)) : fallbackDp(stored);
        if (cellH > 0) size = Math.min(size, cellH - TOP_OFFSET_DP - LABEL_DP);
        return Math.max(MIN_DP, Math.min(MAX_DP, size));
    }
}
