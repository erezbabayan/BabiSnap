package com.babisnap.app;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import android.provider.ContactsContract.CommonDataKinds.Nickname;
import android.provider.ContactsContract.CommonDataKinds.Note;
import android.provider.ContactsContract.CommonDataKinds.Organization;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the phone's contacts (READ_CONTACTS) for stage-2 invites. On-device only: nothing is stored, sent or logged;
 * the list lives only while one meeting card is open.
 */
final class ContactsSource {
    static List<ContactMatcher.Contact> load(Context c) {
        ContentResolver cr = c.getContentResolver();
        Map<Long, String> groupTitles = new HashMap<>();
        try (Cursor g = cr.query(ContactsContract.Groups.CONTENT_URI,
                new String[]{ContactsContract.Groups._ID, ContactsContract.Groups.TITLE}, null, null, null)) {
            while (g != null && g.moveToNext()) {
                String t = g.getString(1);
                if (t != null && !t.isEmpty() && !"My Contacts".equals(t) && !t.startsWith("Starred in")) groupTitles.put(g.getLong(0), t);
            }
        } catch (Exception ignored) {}

        Map<Long, ContactMatcher.Contact> byId = new LinkedHashMap<>();
        String[] proj = {Data.CONTACT_ID, Data.MIMETYPE, Data.DISPLAY_NAME, Data.DATA1, Data.DATA2, Data.DATA3, Data.DATA4, Data.IS_SUPER_PRIMARY};
        String sel = Data.MIMETYPE + " IN (?,?,?,?,?,?)";
        String[] args = {StructuredName.CONTENT_ITEM_TYPE, Nickname.CONTENT_ITEM_TYPE, Organization.CONTENT_ITEM_TYPE,
            GroupMembership.CONTENT_ITEM_TYPE, Email.CONTENT_ITEM_TYPE, Note.CONTENT_ITEM_TYPE};
        try (Cursor d = cr.query(Data.CONTENT_URI, proj, sel, args, Data.CONTACT_ID + "," + Data.IS_SUPER_PRIMARY + " DESC")) {
            while (d != null && d.moveToNext()) {
                long id = d.getLong(0);
                ContactMatcher.Contact k = byId.get(id);
                if (k == null) { k = new ContactMatcher.Contact(d.getString(2)); byId.put(id, k); }
                String mime = d.getString(1), d1 = d.getString(3);
                if (StructuredName.CONTENT_ITEM_TYPE.equals(mime)) {
                    k.field(d.getString(4)).field(d.getString(5));                        // given, family (display name already in)
                } else if (Nickname.CONTENT_ITEM_TYPE.equals(mime) || Note.CONTENT_ITEM_TYPE.equals(mime)) {
                    k.field(d1);
                } else if (Organization.CONTENT_ITEM_TYPE.equals(mime)) {
                    k.org(d1, d.getString(6));                                            // company, job title (DATA4)
                } else if (GroupMembership.CONTENT_ITEM_TYPE.equals(mime)) {
                    try { k.group(groupTitles.get(Long.parseLong(d1))); } catch (Exception ignored) {}
                } else if (Email.CONTENT_ITEM_TYPE.equals(mime)) {
                    k.email(d1);
                }
            }
        } catch (Exception ignored) {}
        return new ArrayList<>(byId.values());
    }

    private ContactsSource() {}
}
