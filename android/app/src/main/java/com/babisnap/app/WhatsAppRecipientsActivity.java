package com.babisnap.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * נמעני וואטסאפ. Add via the system contact picker (one granted row). The screen does not ask for the contacts permission.
 * Alias, search, delete. Private chats only.
 */
public class WhatsAppRecipientsActivity extends Activity {
    private static final int REQ_PICK = 42;
    private final List<WhatsAppRecipients.Person> all = new ArrayList<>();
    private EditText search;
    private LinearLayout list;
    private TextView empty;

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        setTitle(getString(R.string.wa_recipients));
        float d = getResources().getDisplayMetrics().density;
        int pad = (int) (16 * d);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setPadding(pad, pad, pad, pad);
        TextView hint = new TextView(this);
        hint.setText("הכינוי הוא מה שאומרים בהקלטה, למשל «הודעה לתמרי בוואטסאפ». הרשימה נשארת בטלפון. בלי קבוצות.");
        hint.setTextSize(15f);
        hint.setTextColor(0xFF2A1B7A);
        root.addView(hint);
        Button pick = new Button(this);
        pick.setText("בחר מאנשי קשר");
        pick.setAllCaps(false);
        pick.setOnClickListener(v -> pickFromContacts());
        root.addView(pick);
        search = new EditText(this);
        search.setHint("חיפוש");
        search.setSingleLine(true);
        search.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { render(); }
        });
        root.addView(search);
        empty = new TextView(this);
        empty.setText("עדיין אין נמענים. לחצו «בחר מאנשי קשר».");
        empty.setTextSize(15f);
        empty.setPadding(0, pad, 0, 0);
        root.addView(empty);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
    }

    @Override protected void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        all.clear();
        all.addAll(WhatsAppDirectory.load(this));
        render();
    }

    private void save(List<WhatsAppRecipients.Person> people) {
        WhatsAppDirectory.save(this, people);
        reload();
    }

    private void pickFromContacts() {
        Intent pick = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
        try {
            startActivityForResult(pick, REQ_PICK);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "בורר אנשי הקשר לא נמצא", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != REQ_PICK) return;
        if (result != RESULT_OK || data == null) return;
        List<WhatsAppRecipients.Person> next = new ArrayList<>(all);
        WhatsAppRecipients.Person added = null;
        int count = 0;
        int skipped = 0;
        for (Uri uri : pickedUris(data)) {
            Row row = readPicked(uri);
            if (row == null) { skipped++; continue; }
            List<WhatsAppRecipients.Person> saved = WhatsAppRecipients.upsert(next, row.name, row.alias, row.phone);
            if (saved == null) { skipped++; continue; }
            next = saved;
            count++;
            for (WhatsAppRecipients.Person p : next) if (row.phoneDigits.equals(p.e164)) added = p;
        }
        if (count == 0) {
            Toast.makeText(this, "המספר לא נשמר. צריך מספר ישראלי (05…).", Toast.LENGTH_LONG).show();
            return;
        }
        save(next);
        if (skipped > 0) Toast.makeText(this, "מספר שלא זוהה כישראלי לא נשמר", Toast.LENGTH_SHORT).show();
        if (count == 1 && added != null) editAlias(added, true);
        else Toast.makeText(this, "נוספו " + count + ". ערכו כינוי לכל אחד.", Toast.LENGTH_LONG).show();
    }

    static List<Uri> pickedUris(Intent data) {
        List<Uri> uris = new ArrayList<>();
        if (data == null) return uris;
        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++) {
                Uri uri = clip.getItemAt(i).getUri();
                if (uri != null && !uris.contains(uri)) uris.add(uri);
            }
        }
        Uri one = data.getData();
        if (one != null && !uris.contains(one)) uris.add(one);
        return uris;
    }

    /** The picker grants this URI only. The rest of the address book is not read. */
    private Row readPicked(Uri uri) {
        if (uri == null) return null;
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor == null || !cursor.moveToFirst()) return null;
            String number = column(cursor, ContactsContract.CommonDataKinds.Phone.NUMBER, "data1");
            String e164 = WhatsAppRecipients.toIlE164(number);
            if (e164 == null) return null;
            String name = column(cursor, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.Contacts.DISPLAY_NAME, "display_name");
            if (name == null || name.trim().isEmpty()) name = e164;
            else name = name.trim();
            String alias = "";
            String digits = e164.substring(1);
            for (WhatsAppRecipients.Person saved : all) {
                if (digits.equals(saved.e164) && !saved.alias.trim().isEmpty()) alias = saved.alias;
            }
            if (alias.isEmpty()) alias = WhatsAppRecipients.suggestAlias(name);
            return new Row(name, alias, e164, digits);
        } catch (SecurityException e) {
            Toast.makeText(this, "לא ניתן לקרוא את איש הקשר שנבחר", Toast.LENGTH_LONG).show();
            return null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String column(Cursor cursor, String... names) {
        for (String name : names) {
            int index = cursor.getColumnIndex(name);
            if (index < 0) continue;
            String value = cursor.getString(index);
            if (value != null && !value.trim().isEmpty()) return value;
        }
        return null;
    }

    private void render() {
        list.removeAllViews();
        String q = search.getText() == null ? "" : search.getText().toString();
        List<WhatsAppRecipients.Person> shown = WhatsAppRecipients.search(all, q);
        empty.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
        if (all.isEmpty()) empty.setText("עדיין אין נמענים. לחצו «בחר מאנשי קשר».");
        else if (shown.isEmpty()) empty.setText("אין תוצאות לחיפוש");
        float d = getResources().getDisplayMetrics().density;
        int pad = (int) (10 * d);
        for (WhatsAppRecipients.Person p : shown) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            row.setPadding(0, pad, 0, pad);
            TextView title = new TextView(this);
            title.setText(p.alias.trim().isEmpty() ? p.spokenName() : p.alias.trim());
            title.setTextSize(18f);
            title.setTextColor(0xFF1D1B26);
            row.addView(title);
            TextView sub = new TextView(this);
            sub.setText(p.detail().isEmpty() ? p.prettyPhone() : p.detail());
            sub.setTextSize(13f);
            sub.setTextColor(0xFF6A5FA8);
            row.addView(sub);
            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            actions.setGravity(Gravity.CENTER_VERTICAL);
            Button edit = new Button(this);
            edit.setText("כינוי");
            edit.setAllCaps(false);
            edit.setMinHeight((int) (48 * d));
            edit.setOnClickListener(v -> editAlias(p, false));
            Button del = new Button(this);
            del.setText("מחיקה");
            del.setAllCaps(false);
            del.setMinHeight((int) (48 * d));
            del.setOnClickListener(v -> confirmDelete(p));
            actions.addView(edit);
            actions.addView(del);
            row.addView(actions);
            View line = new View(this);
            line.setBackgroundColor(0xFFEEECF5);
            list.addView(row);
            list.addView(line, new LinearLayout.LayoutParams(-1, 1));
        }
    }

    private void editAlias(WhatsAppRecipients.Person person, boolean justAdded) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(person.alias);
        input.setHint("כינוי, למשל תמרי");
        input.setSelection(input.getText().length());
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(justAdded ? "כינוי לנמען" : "עריכת כינוי")
            .setMessage(person.name + "\n" + person.prettyPhone() + "\nבלי ל׳ בהתחלה. «לתמרי» בהקלטה מזהה את «תמרי».")
            .setView(input)
            .setPositiveButton("שמור", (d, w) -> {
                save(WhatsAppRecipients.withAlias(WhatsAppDirectory.load(this), person.id, input.getText().toString()));
                Toast.makeText(this, "הכינוי נשמר", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("ביטול", null)
            .create();
        dialog.show();
        if (dialog.getWindow() != null) dialog.getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    }

    private void confirmDelete(WhatsAppRecipients.Person person) {
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("למחוק את " + person.spokenName() + "?")
            .setMessage("הנמען יימחק מהמכשיר. השיחות בוואטסאפ לא נמחקות.")
            .setPositiveButton("מחיקה", (d, w) -> {
                save(WhatsAppRecipients.without(WhatsAppDirectory.load(this), person.id));
                Toast.makeText(this, "נמחק", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("ביטול", null)
            .create();
        dialog.show();
        if (dialog.getWindow() != null) dialog.getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(0xFFB3261E);
    }

    private static final class Row {
        final String name;
        final String alias;
        final String phone;
        final String phoneDigits;
        Row(String name, String alias, String phone, String phoneDigits) {
            this.name = name;
            this.alias = alias;
            this.phone = phone;
            this.phoneDigits = phoneDigits;
        }
    }
}
