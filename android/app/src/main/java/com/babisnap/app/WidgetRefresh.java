package com.babisnap.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** After an app update, re-bind the widgets so an old tile never keeps an old tap target. */
public class WidgetRefresh extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        if (i != null && Intent.ACTION_MY_PACKAGE_REPLACED.equals(i.getAction())) MicWidget.refreshAll(c);
    }
}
