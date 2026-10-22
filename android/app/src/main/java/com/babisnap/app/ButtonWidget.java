package com.babisnap.app;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.widget.RemoteViews;

/** 1x1 record button: top-aligned, sized like a launcher icon (max 54dp, or the cell's min dimension if smaller). */
public class ButtonWidget extends MicWidget {
    static final int ICON_DP = 54;
    @Override protected int layout() { return R.layout.widget_button; }

    /** Pure: icon size in dp for a cell of the given min width/height (0 = unknown). */
    static int iconDp(int minW, int minH) {
        int cell = Math.min(minW > 0 ? minW : ICON_DP, minH > 0 ? minH : ICON_DP);
        return Math.max(24, Math.min(ICON_DP, cell));
    }

    @Override protected void customize(AppWidgetManager m, int id, RemoteViews v) {
        if (Build.VERSION.SDK_INT < 31) return; // older launchers: fixed 54dp from the layout
        Bundle o = m.getAppWidgetOptions(id);
        int dp = iconDp(o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH), o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT));
        v.setViewLayoutWidth(R.id.mic, dp, TypedValue.COMPLEX_UNIT_DIP);
        v.setViewLayoutHeight(R.id.mic, dp, TypedValue.COMPLEX_UNIT_DIP);
    }

    @Override public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int id, Bundle o) {
        onUpdate(c, m, new int[]{id});
    }
}
