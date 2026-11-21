package com.babisnap.app;

import android.content.SharedPreferences;

/**
 * One fixed 1×1 tile size, matching a standard launcher icon.
 * At 82% the yellow tile measured ~153px next to a ~167px BabiTk icon, about 9% smaller.
 * 0.82 × 167/153 ≈ 0.89.
 */
public final class WidgetSize {
    private WidgetSize() {}

    /** Left behind by the removed five-step size setting. Dropped, never read. */
    public static final String OLD_PREF = "record_button_percent";
    public static final double ICON_FRACTION = 0.89;
    /** Pre-API 31, when the launcher does not report a cell size. */
    public static final int FALLBACK_DP = 70;
    public static final int TOP_OFFSET_DP = 3;
    public static final int LABEL_DP = 18;
    public static final int MIN_DP = 40, MAX_DP = 110;

    /** Large widget destination row: icon + label when wide, icons only when medium, none when narrow. */
    public enum DestStyle { LABELS, ICONS, NONE }
    public static final int LABELS_MIN_DP = 250, ICONS_MIN_DP = 180;

    /** Width 0 means the launcher did not say: show icons, they fit the default 2-cell tile. */
    public static DestStyle destStyle(int widthDp) {
        if (widthDp <= 0) return DestStyle.ICONS;
        if (widthDp >= LABELS_MIN_DP) return DestStyle.LABELS;
        if (widthDp >= ICONS_MIN_DP) return DestStyle.ICONS;
        return DestStyle.NONE;
    }

    public static void forgetOldPref(SharedPreferences prefs) {
        if (prefs != null && prefs.contains(OLD_PREF)) prefs.edit().remove(OLD_PREF).apply();
    }

    /** Tile edge in dp. A zero cell size means unknown. Never taller than the cell minus the label. */
    public static int iconDp(int cellW, int cellH) {
        int size = cellW > 0 ? (int) Math.round(cellW * ICON_FRACTION) : FALLBACK_DP;
        if (cellH > 0) size = Math.min(size, cellH - TOP_OFFSET_DP - LABEL_DP);
        return Math.max(MIN_DP, Math.min(MAX_DP, size));
    }
}
