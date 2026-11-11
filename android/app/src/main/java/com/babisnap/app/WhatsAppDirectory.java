package com.babisnap.app;

import android.content.Context;

import java.util.List;

/** Recipient list in the app's private prefs. Cleared with "מחק את כל הנתונים". */
final class WhatsAppDirectory {
    static List<WhatsAppRecipients.Person> load(Context c) {
        return WhatsAppRecipients.parse(Core.prefs(c).getString(WhatsAppRecipients.PREF, "[]"));
    }

    static void save(Context c, List<WhatsAppRecipients.Person> people) {
        Core.prefs(c).edit().putString(WhatsAppRecipients.PREF, WhatsAppRecipients.toJson(people)).apply();
    }

    private WhatsAppDirectory() {}
}
