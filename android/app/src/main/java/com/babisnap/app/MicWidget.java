package com.babisnap.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

public class MicWidget extends AppWidgetProvider {
    protected int layout() { return R.layout.widget_mic; }
    @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        Intent i = new Intent(c, VoiceActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pi = PendingIntent.getActivity(c, 0, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        for (int id : ids) {
            RemoteViews v = new RemoteViews(c.getPackageName(), layout());
            v.setOnClickPendingIntent(R.id.root, pi);
            v.setOnClickPendingIntent(R.id.mic, pi);
            m.updateAppWidget(id, v);
        }
    }
}
