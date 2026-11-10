package com.babisnap.grouptest;

import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.telephony.PhoneNumberUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** On-device contacts only. Rows are not logged. */
final class ContactsRead {
    static final class Person {
        final String lookup;
        final String e164;
        final String name;
        final String detail;
        final boolean whatsApp;

        Person(String lookup, String e164, String name, String detail, boolean whatsApp) {
            this.lookup = lookup == null ? "" : lookup;
            this.e164 = e164;
            this.name = name == null ? "" : name;
            this.detail = detail == null ? "" : detail;
            this.whatsApp = whatsApp;
        }
    }

    static List<Person> load(Context context) {
        Map<String, Person> byNumber = new HashMap<>();
        Set<String> whatsAppIds = whatsAppContactIds(context);
        String[] projection = {
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY,
                ContactsContract.CommonDataKinds.Phone.TYPE
        };
        try (Cursor cursor = context.getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection, null, null, null)) {
            if (cursor == null) return new ArrayList<>();
            int nameCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
            int numberCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
            int lookupCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY);
            int idCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID);
            int primaryCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY);
            int typeCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE);
            int guard = 0;
            while (cursor.moveToNext() && guard++ < 5000) {
                String raw = numberCol >= 0 ? cursor.getString(numberCol) : null;
                String digits = e164(raw);
                if (digits == null) continue;
                String name = nameCol >= 0 ? cursor.getString(nameCol) : "";
                String lookup = lookupCol >= 0 ? cursor.getString(lookupCol) : "";
                String contactId = idCol >= 0 ? cursor.getString(idCol) : "";
                boolean primary = primaryCol >= 0 && cursor.getInt(primaryCol) == 1;
                int type = typeCol >= 0 ? cursor.getInt(typeCol) : 0;
                boolean wa = contactId != null && whatsAppIds.contains(contactId);
                Person person = new Person(lookup, digits, name, typeLabel(type), wa);
                Person existing = byNumber.get(digits);
                if (existing == null || primary || (wa && !existing.whatsApp)) byNumber.put(digits, person);
            }
        } catch (SecurityException e) {
            ProbeLog.code("CONTACTS_DENIED");
            return new ArrayList<>();
        } catch (RuntimeException e) {
            ProbeLog.code("CONTACTS_FAIL");
            return new ArrayList<>();
        }
        return new ArrayList<>(byNumber.values());
    }

    private static String e164(String raw) {
        if (raw == null) return null;
        try {
            String country = Locale.getDefault().getCountry();
            String formatted = country != null && country.length() == 2
                    ? PhoneNumberUtils.formatNumberToE164(raw, country) : null;
            if (formatted == null) formatted = PhoneNumberUtils.formatNumberToE164(raw, "IL");
            return ProbeLogic.toIlDigits(formatted != null ? formatted : raw);
        } catch (RuntimeException e) {
            return ProbeLogic.toIlDigits(raw);
        }
    }

    private static Set<String> whatsAppContactIds(Context context) {
        Set<String> ids = new HashSet<>();
        try (Cursor cursor = context.getContentResolver().query(
                ContactsContract.Data.CONTENT_URI,
                new String[]{ContactsContract.Data.CONTACT_ID},
                ContactsContract.Data.MIMETYPE + "=?",
                new String[]{"vnd.android.cursor.item/vnd.com.whatsapp.profile"},
                null)) {
            if (cursor == null) return ids;
            int col = cursor.getColumnIndex(ContactsContract.Data.CONTACT_ID);
            while (cursor.moveToNext()) {
                if (col >= 0) {
                    String id = cursor.getString(col);
                    if (id != null) ids.add(id);
                }
            }
        } catch (RuntimeException e) {
            ProbeLog.code("CONTACTS_WA_HINT_FAIL");
        }
        return ids;
    }

    private static String typeLabel(int type) {
        if (type == ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE) return "נייד";
        if (type == ContactsContract.CommonDataKinds.Phone.TYPE_HOME) return "בית";
        if (type == ContactsContract.CommonDataKinds.Phone.TYPE_WORK) return "עבודה";
        return "טלפון";
    }

    private ContactsRead() {}
}
