package com.babisnap.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.RemoteViews;

public class MicWidget extends AppWidgetProvider {
    protected int layout() { return R.layout.widget_mic; }
    protected void customize(AppWidgetManager m, int id, RemoteViews v) {}
    /** Same target for the large and the small widget: only the recording screen, in its own task. */
    static PendingIntent recordIntent(Context c) {
        return PendingIntent.getActivity(c, 0, RecordLaunch.recording(c), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** Re-bind every placed widget (large and small). A host that kept an old tile gets the current tap target. */
    static void refreshAll(Context c) {
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(c);
            if (m == null) return;
            new MicWidget().onUpdate(c, m, m.getAppWidgetIds(new ComponentName(c, MicWidget.class)));
            new ButtonWidget().onUpdate(c, m, m.getAppWidgetIds(new ComponentName(c, ButtonWidget.class)));
        } catch (Exception ignored) { /* no widget host: nothing to refresh */ }
    }

    @Override public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int id, Bundle o) {
        onUpdate(c, m, new int[]{id}); // the large tile is resizable: re-bind its tap on every resize
    }

    @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        if (ids == null || ids.length == 0) return;
        PendingIntent pi = recordIntent(c);
        for (int id : ids) {
            RemoteViews v = new RemoteViews(c.getPackageName(), layout());
            bindClicks(v, pi);
            customize(m, id, v);
            m.updateAppWidget(id, v);
        }
    }

    /** The whole tile, including a label when the layout has one, starts the same recording. */
    protected void bindClicks(RemoteViews v, PendingIntent pi) {
        v.setOnClickPendingIntent(R.id.root, pi);
        v.setOnClickPendingIntent(R.id.mic, pi);
    }
}
