package com.babisnap.app;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Nickname;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Phone contacts for a WhatsApp name, read with the existing READ_CONTACTS grant.
 * Nothing is stored or logged. A WhatsApp raw contact (com.whatsapp) supplies the number when it has one.
 */
final class WhatsAppContacts {
    static List<WhatsAppRecipients.Contact> load(Context c) {
        ContentResolver cr = c.getContentResolver();
        Set<Long> waRaw = new HashSet<>();
        try (Cursor raw = cr.query(RawContacts.CONTENT_URI,
                new String[]{RawContacts._ID, RawContacts.ACCOUNT_TYPE},
                RawContacts.DELETED + "=0", null, null)) {
            while (raw != null && raw.moveToNext()) {
                if (WhatsAppRecipients.isWhatsAppAccount(raw.getString(1))) waRaw.add(raw.getLong(0));
            }
        } catch (SecurityException e) {
            return new ArrayList<>();
        } catch (Exception ignored) {
            return new ArrayList<>();
        }

        Map<Long, Draft> byId = new HashMap<>();
        try (Cursor phones = cr.query(Phone.CONTENT_URI,
                new String[]{Phone.CONTACT_ID, Phone.DISPLAY_NAME, Phone.NUMBER, Phone.TYPE, Phone.RAW_CONTACT_ID},
                null, null, null)) {
            while (phones != null && phones.moveToNext()) {
                long id = phones.getLong(0);
                Draft d = byId.get(id);
                if (d == null) {
                    d = new Draft();
                    String name = phones.getString(1);
                    d.display = name == null ? "" : name;
                    byId.put(id, d);
                }
                String number = phones.getString(2);
                int type = phones.getInt(3);
                long rawId = phones.getLong(4);
                d.phones.add(new WhatsAppRecipients.Phone(number,
                    waRaw.contains(rawId), WhatsAppRecipients.isMobileType(type)));
            }
        } catch (SecurityException e) {
            return new ArrayList<>();
        } catch (Exception ignored) {
            return new ArrayList<>();
        }

        String[] proj = {Data.CONTACT_ID, Data.MIMETYPE, Data.DATA1, Data.DATA2, Data.DATA3};
        String sel = Data.MIMETYPE + " IN (?,?)";
        String[] args = {StructuredName.CONTENT_ITEM_TYPE, Nickname.CONTENT_ITEM_TYPE};
        try (Cursor names = cr.query(Data.CONTENT_URI, proj, sel, args, null)) {
            while (names != null && names.moveToNext()) {
                Draft d = byId.get(names.getLong(0));
                if (d == null) continue;
                String mime = names.getString(1);
                if (StructuredName.CONTENT_ITEM_TYPE.equals(mime)) {
                    if (d.given.isEmpty()) d.given = text(names.getString(3));
                    if (d.family.isEmpty()) d.family = text(names.getString(4));
                } else if (Nickname.CONTENT_ITEM_TYPE.equals(mime) && d.nickname.isEmpty()) {
                    d.nickname = text(names.getString(2));
                }
            }
        } catch (Exception ignored) { /* names are optional; the display name from the phone row still matches */ }

        List<WhatsAppRecipients.Contact> out = new ArrayList<>();
        for (Draft d : byId.values()) {
            WhatsAppRecipients.Contact contact = new WhatsAppRecipients.Contact(
                d.display, d.given, d.family, d.nickname, d.phones);
            if (contact.digits() != null) out.add(contact);
        }
        return out;
    }

    private static String text(String s) { return s == null ? "" : s.trim(); }

    private static final class Draft {
        String display = "";
        String given = "";
        String family = "";
        String nickname = "";
        final List<WhatsAppRecipients.Phone> phones = new ArrayList<>();
    }

    private WhatsAppContacts() {}
}
