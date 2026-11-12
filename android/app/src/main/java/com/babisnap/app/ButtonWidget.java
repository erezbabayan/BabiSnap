package com.babisnap.app;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.widget.RemoteViews;

/** 1x1 record button: one launcher-icon size, with «BabiSnap» under it, top-aligned with icons. */
public class ButtonWidget extends MicWidget {
    @Override protected int layout() { return R.layout.widget_button; }

    @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        WidgetSize.forgetOldPref(Core.prefs(c));
        super.onUpdate(c, m, ids);
    }

    @Override protected void bindClicks(RemoteViews v, android.app.PendingIntent pi) {
        super.bindClicks(v, pi);
        v.setOnClickPendingIntent(R.id.label, pi);
    }

    @Override protected void customize(AppWidgetManager m, int id, RemoteViews v) {
        if (Build.VERSION.SDK_INT < 31) return; // older Android: the layout's 70dp
        Bundle o = m.getAppWidgetOptions(id);
        boolean land = android.content.res.Resources.getSystem().getConfiguration().orientation
            == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        int w = o.getInt(land ? AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH : AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
        int h = o.getInt(land ? AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT : AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT);
        int dp = WidgetSize.iconDp(w, h);
        v.setViewLayoutWidth(R.id.mic, dp, TypedValue.COMPLEX_UNIT_DIP);
        v.setViewLayoutHeight(R.id.mic, dp, TypedValue.COMPLEX_UNIT_DIP);
    }

    @Override public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int id, Bundle o) {
        onUpdate(c, m, new int[]{id});
    }
}
