package com.babisnap.grouptest;

import android.app.Notification;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.List;

/**
 * Learns a group id from a WhatsApp notification shortcut id.
 * Message bodies are not stored and are not logged.
 */
public class WaNoticeService extends NotificationListenerService {
    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        ProbeLog.code("NOTIF_CONNECTED");
        try {
            StatusBarNotification[] active = getActiveNotifications();
            if (active != null) {
                for (StatusBarNotification item : active) inspect(item);
            }
        } catch (SecurityException e) {
            ProbeLog.code("NOTIF_ACTIVE_FAIL");
        }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        inspect(sbn);
    }

    private void inspect(StatusBarNotification sbn) {
        if (sbn == null) return;
        String pkg = sbn.getPackageName();
        if (!Access.WA.equals(pkg) && !Access.W4B.equals(pkg)) return;
        Notification notification = sbn.getNotification();
        if (notification == null) return;
        if ((notification.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;

        Store store = Store.get(this);
        boolean changed = false;
        if (hasReply(notification) && !store.bool("directReply")) {
            store.putBool("directReply", true);
            ProbeLog.code("NOTIF_DIRECT_REPLY");
            changed = true;
        }

        List<String> blobs = new ArrayList<>();
        blobs.add(notification.getShortcutId());
        addExtraStrings(notification, blobs);
        addPeople(notification, blobs);
        ProbeLogic.Found found = ProbeLogic.findJid(blobs);
        boolean groupFlag = notification.extras != null
                && notification.extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false);

        if (found != null && found.group) {
            int before = store.groupCount();
            store.upsertGroup(found.jid, titleOf(notification), "notification");
            if (store.groupCount() > before) {
                ProbeLog.code("NOTIF_GROUP_JID");
                changed = true;
            }
        } else if (groupFlag && !store.bool("sawGroupNoJid")) {
            store.putBool("sawGroupNoJid", true);
            ProbeLog.code("NOTIF_GROUP_NO_JID");
            changed = true;
        } else if (found != null && !store.bool("sawContactJid")) {
            store.putBool("sawContactJid", true);
            ProbeLog.code("NOTIF_CONTACT_JID");
            changed = true;
        }
        if (changed) UiHook.ping();
    }

    private static boolean hasReply(Notification notification) {
        if (notification.actions == null) return false;
        for (Notification.Action action : notification.actions) {
            if (action == null) continue;
            if (action.getRemoteInputs() != null && action.getRemoteInputs().length > 0) return true;
            if (action.getDataOnlyRemoteInputs() != null && action.getDataOnlyRemoteInputs().length > 0) {
                return true;
            }
        }
        return false;
    }

    /** Title only. Text, big text and message arrays are skipped so bodies are not copied. */
    private static void addExtraStrings(Notification notification, List<String> blobs) {
        Bundle extras = notification.extras;
        if (extras == null) return;
        for (String key : extras.keySet()) {
            if (skip(key)) continue;
            Object value;
            try {
                value = extras.get(key);
            } catch (RuntimeException e) {
                continue;
            }
            if (value instanceof CharSequence) blobs.add(value.toString());
        }
    }

    private static boolean skip(String key) {
        return Notification.EXTRA_TEXT.equals(key)
                || Notification.EXTRA_BIG_TEXT.equals(key)
                || Notification.EXTRA_SUB_TEXT.equals(key)
                || Notification.EXTRA_SUMMARY_TEXT.equals(key)
                || Notification.EXTRA_INFO_TEXT.equals(key)
                || Notification.EXTRA_MESSAGES.equals(key)
                || Notification.EXTRA_HISTORIC_MESSAGES.equals(key)
                || Notification.EXTRA_TITLE.equals(key)
                || Notification.EXTRA_TITLE_BIG.equals(key)
                || Notification.EXTRA_CONVERSATION_TITLE.equals(key)
                || "android.textLines".equals(key);
    }

    private static void addPeople(Notification notification, List<String> blobs) {
        if (Build.VERSION.SDK_INT < 28 || notification.extras == null) return;
        ArrayList<android.app.Person> people;
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                people = notification.extras.getParcelableArrayList(Notification.EXTRA_PEOPLE_LIST, android.app.Person.class);
            } else {
                people = notification.extras.getParcelableArrayList(Notification.EXTRA_PEOPLE_LIST);
            }
        } catch (RuntimeException e) {
            return;
        }
        if (people == null) return;
        for (android.app.Person person : people) {
            if (person == null) continue;
            if (person.getKey() != null) blobs.add(person.getKey());
            if (person.getUri() != null) blobs.add(person.getUri());
        }
    }

    private static String titleOf(Notification notification) {
        Bundle extras = notification.extras;
        if (extras == null) return "";
        CharSequence conversation = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE);
        CharSequence title = extras.getCharSequence(Notification.EXTRA_TITLE);
        boolean group = extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false);
        CharSequence chosen = group && conversation != null && conversation.length() > 0 ? conversation : title;
        if ((chosen == null || chosen.length() == 0) && conversation != null) chosen = conversation;
        return chosen == null ? "" : chosen.toString().trim();
    }
}
