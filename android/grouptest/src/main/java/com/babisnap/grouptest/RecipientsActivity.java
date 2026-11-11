package com.babisnap.grouptest;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One list: phone contacts plus captured groups. Selection stays on the device. */
public class RecipientsActivity extends Activity {
    private static final int REQ_CONTACTS = 7;
    private static final int REQ_PICK = 8;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<Row> all = new ArrayList<>();
    private final List<Row> shown = new ArrayList<>();
    private Store store;
    private EditText search;
    private Adapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recipients);
        store = Store.get(this);
        search = findViewById(R.id.search);
        ListView list = findViewById(R.id.list);
        adapter = new Adapter();
        list.setAdapter(adapter);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { applyFilter(); }
        });
        findViewById(R.id.select_all).setOnClickListener(v -> selectAll());
        findViewById(R.id.pick_contact).setOnClickListener(v -> pickFromContacts());
        findViewById(R.id.add_number).setOnClickListener(v -> addNumber());
        findViewById(R.id.perm_button).setOnClickListener(v -> askContacts());
        if (!BuildConfig.LITE
                && checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.READ_CONTACTS}, REQ_CONTACTS);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        store = Store.get(this);
        UiHook.set(() -> runOnUiThread(this::load));
        load();
    }

    @Override
    protected void onPause() {
        UiHook.set(null);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == REQ_CONTACTS) load();
    }

    private void askContacts() {
        if (checkSelfPermission(android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            load();
            return;
        }
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + getPackageName()));
        try {
            startActivity(intent);
        } catch (RuntimeException e) {
            ProbeLog.code("SETTINGS_MISSING");
        }
    }

    private void load() {
        boolean granted = !BuildConfig.LITE
                && checkSelfPermission(android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
        showPermission(granted);
        bind(store.groups(), Collections.emptyList(), store.contacts());
        if (!granted) return;
        executor.execute(() -> {
            List<ContactsRead.Person> people;
            try {
                people = ContactsRead.load(this);
            } catch (RuntimeException e) {
                ProbeLog.code("CONTACTS_FAIL");
                people = Collections.emptyList();
            }
            List<ContactsRead.Person> ready = people;
            runOnUiThread(() -> {
                if (isFinishing()) return;
                bind(store.groups(), ready, store.contacts());
            });
        });
    }

    private void showPermission(boolean granted) {
        TextView status = findViewById(R.id.perm_status);
        Button button = findViewById(R.id.perm_button);
        if (BuildConfig.LITE) {
            status.setText("«בחר מאנשי קשר» נותן שורה אחת שנבחרה, בלי הרשאה קבועה. «הוסף מספר» הוא גיבוי.");
            button.setVisibility(View.GONE);
            return;
        }
        status.setText(granted ? "✅ הרשאת אנשי קשר" : "❌ אין הרשאת אנשי קשר — קבוצות עדיין מוצגות");
        button.setVisibility(granted ? View.GONE : View.VISIBLE);
    }

    /** System picker. EXTRA_ALLOW_MULTIPLE is honored only by some phones; otherwise one row comes back. */
    private void pickFromContacts() {
        Intent pick = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
        pick.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try {
            startActivityForResult(pick, REQ_PICK);
            ProbeLog.code("PICK_LAUNCHED");
        } catch (ActivityNotFoundException e) {
            ProbeLog.code("PICK_MISSING");
            Toast.makeText(this, "בורר אנשי הקשר לא נמצא. השתמשו ב«הוסף מספר».", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != REQ_PICK) return;
        if (result != RESULT_OK || data == null) {
            ProbeLog.code("PICK_CANCELLED");
            return;
        }
        int added = 0;
        int skipped = 0;
        for (Uri uri : pickedUris(data)) {
            int n = readPicked(uri);
            if (n > 0) added += n;
            else skipped++;
        }
        if (added > 0) {
            ProbeLog.code("PICK_ADDED");
            String message = added == 1
                    ? "איש קשר נוסף. לחצו יעד כדי לשלוח בדיקה."
                    : "נוספו " + added + ". לחצו יעד על מי שבודקים.";
            if (skipped > 0) message = message + " מספר שלא זוהה כמספר ישראלי לא נשמר.";
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            load();
        } else {
            ProbeLog.code("PICK_EMPTY");
            Toast.makeText(this, "לא נשמר מספר. נסו «הוסף מספר».", Toast.LENGTH_SHORT).show();
        }
    }

    /** One URI from the data field, plus any extra URIs when the picker returns several. */
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

    /** Reads only the URI the user picked. That grant does not cover the rest of the address book. */
    private int readPicked(Uri uri) {
        if (uri == null) return 0;
        int added = 0;
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor == null) return 0;
            int guard = 0;
            while (cursor.moveToNext() && guard++ < 20) {
                if (storePickedRow(cursor)) added++;
            }
        } catch (SecurityException e) {
            ProbeLog.code("PICK_DENIED");
        } catch (RuntimeException e) {
            ProbeLog.code("PICK_FAIL");
        }
        return added;
    }

    private boolean storePickedRow(Cursor cursor) {
        String number = column(cursor, ContactsContract.CommonDataKinds.Phone.NUMBER, "data1");
        String e164 = ProbeLogic.toIlE164(number);
        if (e164 == null) return false;
        String digits = e164.substring(1);
        String name = column(cursor,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.Contacts.DISPLAY_NAME,
                "display_name");
        if (name == null || name.trim().isEmpty()) name = e164;
        else name = name.trim();
        String alias = "";
        boolean checked = !BuildConfig.AUTO;
        for (Store.ContactSel saved : store.contacts()) {
            if (digits.equals(saved.e164)) {
                alias = saved.alias;
                checked = saved.checked;
            }
        }
        store.saveContact("", digits, name, alias, checked);
        return true;
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

    private void addNumber() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad, pad, 0);
        EditText name = field("שם שיוצג בכרטיס");
        EditText phone = field("מספר טלפון");
        phone.setInputType(InputType.TYPE_CLASS_PHONE);
        EditText alias = field(getString(R.string.alias_hint));
        box.addView(name);
        box.addView(phone);
        box.addView(alias);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.add_number_title)
                .setView(box)
                .setPositiveButton(R.string.add_number, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (saveManual(name, phone, alias)) dialog.dismiss();
        }));
        dialog.show();
    }

    private boolean saveManual(EditText name, EditText phone, EditText alias) {
        String digits = ProbeLogic.toIlDigits(phone.getText().toString());
        if (digits == null) {
            Toast.makeText(this, "המספר לא זוהה. נסו 05… או +972…", Toast.LENGTH_SHORT).show();
            return false;
        }
        String shown = name.getText().toString().trim();
        if (shown.isEmpty()) shown = "+" + digits;
        store.saveContact("", digits, shown, alias.getText().toString().trim(), true);
        ProbeLog.code("MANUAL_NUMBER");
        Toast.makeText(this, "המספר נוסף. לחצו יעד כדי לשלוח אליו בדיקה.", Toast.LENGTH_SHORT).show();
        load();
        return true;
    }

    private EditText field(String hint) {
        EditText edit = new EditText(this);
        edit.setHint(hint);
        edit.setTextDirection(View.TEXT_DIRECTION_RTL);
        edit.setSingleLine(true);
        return edit;
    }

    private void bind(List<Store.Group> groups, List<ContactsRead.Person> people, List<Store.ContactSel> saved) {
        String target = store.targetId();
        Map<String, Store.ContactSel> byId = new HashMap<>();
        for (Store.ContactSel sel : saved) byId.put(sel.id, sel);
        all.clear();
        Set<String> seen = new HashSet<>();
        for (Store.Group group : groups) {
            Row row = new Row("group", group.id(), group.jid, "", "", group.name,
                    "קבוצה · " + ("notification".equals(group.source) ? "מהתראה" : "מהקיצור"), false);
            row.alias = group.alias;
            row.checked = group.checked;
            row.target = group.id().equals(target);
            all.add(row);
        }
        for (ContactsRead.Person person : people) {
            String id = Store.contactId(person.lookup, person.e164);
            Store.ContactSel sel = byId.get(id);
            String detail = person.detail + " · +" + person.e164 + (person.whatsApp ? " · רמז WhatsApp" : "");
            Row row = new Row("contact", id, ProbeLogic.contactJid(person.e164), person.e164, person.lookup,
                    person.name, detail, person.whatsApp);
            if (sel != null) {
                row.alias = sel.alias;
                row.checked = sel.checked;
            }
            row.target = id.equals(target);
            seen.add(id);
            all.add(row);
        }
        for (Store.ContactSel sel : saved) {
            if (sel.e164.isEmpty() || seen.contains(sel.id)) continue;
            String jid = ProbeLogic.contactJid(sel.e164);
            Row row = new Row("contact", sel.id, jid, sel.e164, sel.lookup, sel.name,
                    "מספר שהוזן · +" + sel.e164, false);
            row.manual = true;
            row.alias = sel.alias;
            row.checked = sel.checked;
            row.target = sel.id.equals(target);
            all.add(row);
        }
        Collator collator = Collator.getInstance(new Locale("he"));
        Collections.sort(all, (a, b) -> {
            if (!a.kind.equals(b.kind)) return "group".equals(a.kind) ? -1 : 1;
            if (a.whatsApp != b.whatsApp) return a.whatsApp ? -1 : 1;
            return collator.compare(a.name == null ? "" : a.name, b.name == null ? "" : b.name);
        });
        applyFilter();
    }

    private void applyFilter() {
        String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        shown.clear();
        for (Row row : all) {
            if (query.isEmpty() || contains(row.name, query) || contains(row.alias, query) || contains(row.detail, query)) {
                shown.add(row);
            }
        }
        adapter.notifyDataSetChanged();
        TextView count = findViewById(R.id.list_count);
        count.setText(shown.isEmpty() ? "אין פריטים להצגה" : "מוצגים " + shown.size());
    }

    private void selectAll() {
        if (shown.isEmpty()) return;
        boolean allOn = true;
        for (Row row : shown) if (!row.checked) allOn = false;
        boolean next = !allOn;
        for (Row row : shown) {
            row.checked = next;
            persist(row);
        }
        adapter.notifyDataSetChanged();
        ProbeLog.code(next ? "SELECT_ALL" : "SELECT_NONE");
    }

    private void persist(Row row) {
        if ("group".equals(row.kind)) store.saveGroup(row.jid, row.alias, row.checked);
        else store.saveContact(row.lookup, row.e164, row.name, row.alias, row.checked);
    }

    private static boolean contains(String hay, String query) {
        return hay != null && hay.toLowerCase(Locale.ROOT).contains(query);
    }

    private static final class Row {
        final String kind;
        final String id;
        final String jid;
        final String e164;
        final String lookup;
        final String name;
        final String detail;
        final boolean whatsApp;
        String alias = "";
        boolean checked;
        boolean target;
        boolean manual;

        Row(String kind, String id, String jid, String e164, String lookup, String name, String detail, boolean whatsApp) {
            this.kind = kind;
            this.id = id;
            this.jid = jid == null ? "" : jid;
            this.e164 = e164 == null ? "" : e164;
            this.lookup = lookup == null ? "" : lookup;
            this.name = name == null ? "" : name;
            this.detail = detail == null ? "" : detail;
            this.whatsApp = whatsApp;
        }
    }

    private final class Adapter extends BaseAdapter {
        @Override public int getCount() { return shown.size(); }
        @Override public Object getItem(int position) { return shown.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convert, ViewGroup parent) {
            View view = convert;
            if (view == null) view = getLayoutInflater().inflate(R.layout.row_recipient, parent, false);
            Row row = shown.get(position);
            TextView name = view.findViewById(R.id.row_name);
            TextView detail = view.findViewById(R.id.row_detail);
            CheckBox check = view.findViewById(R.id.row_check);
            EditText alias = view.findViewById(R.id.row_alias);
            Button target = view.findViewById(R.id.row_target);
            Button remove = view.findViewById(R.id.row_remove);
            String shownName = row.name.isEmpty() ? ("group".equals(row.kind) ? "קבוצה" : "איש קשר") : row.name;
            name.setText(shownName);
            detail.setText(row.detail);
            view.setBackgroundColor(row.target ? 0xFFE4F2EA : 0xFFFFFFFF);
            check.setOnCheckedChangeListener(null);
            check.setText(BuildConfig.AUTO ? "בלי מגע" : "");
            check.setChecked(row.checked);
            check.setOnCheckedChangeListener((button, on) -> {
                row.checked = on;
                persist(row);
            });
            Object old = alias.getTag();
            if (old instanceof TextWatcher) alias.removeTextChangedListener((TextWatcher) old);
            alias.setText(row.alias);
            TextWatcher watcher = new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    row.alias = s.toString();
                    persist(row);
                }
            };
            alias.addTextChangedListener(watcher);
            alias.setTag(watcher);
            target.setVisibility(BuildConfig.AUTO ? View.GONE : View.VISIBLE);
            target.setText(row.target ? R.string.target_on : R.string.make_target);
            target.setOnClickListener(v -> {
                store.setTarget(row.id);
                for (Row other : all) other.target = other.id.equals(row.id);
                row.target = true;
                persist(row);
                notifyDataSetChanged();
                ProbeLog.code("TARGET_SET");
            });
            remove.setVisibility("group".equals(row.kind) || row.manual ? View.VISIBLE : View.GONE);
            remove.setOnClickListener(v -> {
                if ("group".equals(row.kind)) {
                    store.removeGroup(row.jid);
                    ProbeLog.code("GROUP_REMOVED");
                } else {
                    store.removeContact(row.id);
                    ProbeLog.code("NUMBER_REMOVED");
                }
                load();
            });
            return view;
        }
    }
}
