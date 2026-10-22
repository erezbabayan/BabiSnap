package com.babisnap.app;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.widget.RemoteViews;

/** 1x1 record button: same size as neighbouring launcher icons (fraction of the real cell width), top-aligned with them. */
public class ButtonWidget extends MicWidget {
    /** Samsung One UI icons fill ~80-85% of the cell width; measured 1.7 screenshot: icon 167px vs tile 117px (x1.43). */
    static final double ICON_FRACTION = 0.82;
    static final int FALLBACK_DP = 76, MIN_DP = 40, MAX_DP = 110;
    @Override protected int layout() { return R.layout.widget_button; }

    /** Pure: tile size in dp from the cell's reported width (portrait = OPTION_APPWIDGET_MIN_WIDTH) and height. 0 = unknown. */
    static int iconDp(int cellW, int cellH) {
        int size = cellW > 0 ? (int) Math.round(cellW * ICON_FRACTION) : FALLBACK_DP;
        if (cellH > 0) size = Math.min(size, cellH - TOP_OFFSET_DP);
        return Math.max(MIN_DP, Math.min(MAX_DP, size));
    }
    /** Icons sit ~6px (~3dp) below the cell top in the screenshot. */
    static final int TOP_OFFSET_DP = 3;

    @Override protected void customize(AppWidgetManager m, int id, RemoteViews v) {
        if (Build.VERSION.SDK_INT < 31) return; // older Android: fixed 76dp from the layout
        Bundle o = m.getAppWidgetOptions(id);
        // Portrait cell = MIN_WIDTH x MAX_HEIGHT; landscape = MAX_WIDTH x MIN_HEIGHT.
        boolean land = android.content.res.Resources.getSystem().getConfiguration().orientation
            == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        int w = o.getInt(land ? AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH : AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
        int h = o.getInt(land ? AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT : AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT);
        int dp = iconDp(w, h);
        v.setViewLayoutWidth(R.id.mic, dp, TypedValue.COMPLEX_UNIT_DIP);
        v.setViewLayoutHeight(R.id.mic, dp, TypedValue.COMPLEX_UNIT_DIP);
    }

    @Override public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int id, Bundle o) {
        onUpdate(c, m, new int[]{id});
    }
}
