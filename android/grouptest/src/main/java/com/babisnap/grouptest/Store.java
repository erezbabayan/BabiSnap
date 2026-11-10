package com.babisnap.grouptest;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * App-private storage. Names and jids live here only.
 * The clipboard report is built from codes, never by dumping this document.
 */
final class Store {
    private static final Object LOCK = new Object();
    private static final String PREFS = "k0_probe";
    private static final String KEY = "state";

    static final class Group {
        final String jid;
        final String name;
        final String alias;
        final boolean checked;
        final String source;

        Group(String jid, String name, String alias, boolean checked, String source) {
            this.jid = jid;
            this.name = name == null ? "" : name;
            this.alias = alias == null ? "" : alias;
            this.checked = checked;
            this.source = source == null ? "" : source;
        }

        String id() {
            return "g:" + jid;
        }
    }

    static final class ContactSel {
        final String id;
        final String lookup;
        final String e164;
        final String name;
        final String alias;
        final boolean checked;

        ContactSel(String id, String lookup, String e164, String name, String alias, boolean checked) {
            this.id = id;
            this.lookup = lookup == null ? "" : lookup;
            this.e164 = e164 == null ? "" : e164;
            this.name = name == null ? "" : name;
            this.alias = alias == null ? "" : alias;
            this.checked = checked;
        }
    }

    static final class Target {
        final String id;
        final String kind;
        final String name;
        final String alias;
        final String jid;
        final String e164;
        final boolean checked;

        Target(String id, String kind, String name, String alias, String jid, String e164, boolean checked) {
            this.id = id;
            this.kind = kind;
            this.name = name == null ? "" : name;
            this.alias = alias == null ? "" : alias;
            this.jid = jid == null ? "" : jid;
            this.e164 = e164 == null ? "" : e164;
            this.checked = checked;
        }
    }

    private interface Editor {
        void apply(JSONObject root) throws Exception;
    }

    private final SharedPreferences prefs;

    private Store(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static Store get(Context context) {
        return new Store(context);
    }

    static String contactId(String lookup, String e164) {
        return "c:" + (lookup == null ? "" : lookup) + ":" + (e164 == null ? "" : e164);
    }

    private JSONObject read() {
        String raw = prefs.getString(KEY, "");
        if (raw == null || raw.isEmpty()) return new JSONObject();
        try {
            return new JSONObject(raw);
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private void write(JSONObject root) {
        prefs.edit().putString(KEY, root.toString()).apply();
    }

    private void edit(Editor editor) {
        synchronized (LOCK) {
            try {
                JSONObject root = read();
                editor.apply(root);
                write(root);
            } catch (Exception e) {
                ProbeLog.code("STORE_FAIL");
            }
        }
    }

    void upsertGroup(String jid, String name, String source) {
        if (!ProbeLogic.isGroupJid(jid)) return;
        edit(root -> {
            JSONArray next = new JSONArray();
            JSONArray arr = root.optJSONArray("groups");
            boolean found = false;
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject g = arr.optJSONObject(i);
                    if (g == null) continue;
                    if (jid.equals(g.optString("jid"))) {
                        found = true;
                        if (name != null && !name.trim().isEmpty()) g.put("name", name.trim());
                        if (source != null && !source.isEmpty()) g.put("source", source);
                    }
                    next.put(g);
                }
            }
            if (!found) {
                JSONObject g = new JSONObject();
                g.put("jid", jid);
                g.put("name", name == null ? "" : name.trim());
                g.put("alias", "");
                g.put("checked", false);
                g.put("source", source == null ? "" : source);
                next.put(g);
            }
            root.put("groups", next);
            if (name != null && !name.trim().isEmpty()) root.put("lastAddedName", name.trim());
        });
    }

    void saveGroup(String jid, String alias, boolean checked) {
        edit(root -> {
            JSONArray arr = root.optJSONArray("groups");
            if (arr == null) return;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject g = arr.optJSONObject(i);
                if (g != null && jid.equals(g.optString("jid"))) {
                    g.put("alias", alias == null ? "" : alias);
                    g.put("checked", checked);
                }
            }
        });
    }

    void removeContact(String id) {
        if (id == null || id.isEmpty()) return;
        edit(root -> {
            JSONArray arr = root.optJSONArray("contacts");
            JSONArray next = new JSONArray();
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject c = arr.optJSONObject(i);
                    if (c != null && !id.equals(c.optString("id"))) next.put(c);
                }
            }
            root.put("contacts", next);
            if (id.equals(root.optString("targetId"))) root.put("targetId", "");
        });
    }

    void removeGroup(String jid) {
        edit(root -> {
            JSONArray arr = root.optJSONArray("groups");
            JSONArray next = new JSONArray();
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject g = arr.optJSONObject(i);
                    if (g != null && !jid.equals(g.optString("jid"))) next.put(g);
                }
            }
            root.put("groups", next);
            if (("g:" + jid).equals(root.optString("targetId"))) root.put("targetId", "");
        });
    }

    void saveContact(String lookup, String e164, String name, String alias, boolean checked) {
        if (e164 == null || e164.isEmpty()) return;
        String id = contactId(lookup, e164);
        edit(root -> {
            JSONArray arr = root.optJSONArray("contacts");
            JSONArray next = new JSONArray();
            boolean found = false;
            boolean target = id.equals(root.optString("targetId"));
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject c = arr.optJSONObject(i);
                    if (c == null) continue;
                    if (id.equals(c.optString("id"))) {
                        found = true;
                        c.put("name", name == null ? c.optString("name") : name);
                        c.put("alias", alias == null ? "" : alias);
                        c.put("checked", checked);
                        c.put("lookup", lookup == null ? "" : lookup);
                        c.put("e164", e164);
                        boolean keep = checked || target || (alias != null && !alias.trim().isEmpty());
                        if (keep) next.put(c);
                        continue;
                    }
                    next.put(c);
                }
            }
            if (!found && (checked || target || (alias != null && !alias.trim().isEmpty()))) {
                JSONObject c = new JSONObject();
                c.put("id", id);
                c.put("lookup", lookup == null ? "" : lookup);
                c.put("e164", e164);
                c.put("name", name == null ? "" : name);
                c.put("alias", alias == null ? "" : alias);
                c.put("checked", checked);
                next.put(c);
            }
            root.put("contacts", next);
        });
    }

    void setTarget(String id) {
        edit(root -> root.put("targetId", id == null ? "" : id));
    }

    void putCode(String field, String code) {
        String safe = ProbeLogic.sanitizeCode(code, "UNKNOWN");
        edit(root -> root.put(field, safe));
    }

    void putBool(String field, boolean value) {
        edit(root -> root.put(field, value));
    }

    void setDraft(String text) {
        edit(root -> root.put("draft", text == null ? "" : text));
    }

    void setShortcutActivity(String className) {
        edit(root -> root.put("shortcutActivity", className == null ? "" : className));
    }

    void setObservation(String titleMatch, String sendButton, String sendLabel, String observed) {
        edit(root -> {
            root.put("titleMatch", ProbeLogic.sanitizeCode(titleMatch, "UNKNOWN"));
            root.put("sendButton", ProbeLogic.sanitizeCode(sendButton, "UNKNOWN"));
            root.put("sendLabel", ProbeLogic.sanitizeCode(sendLabel, "NONE"));
            root.put("observedTitle", observed == null ? "" : observed);
        });
    }

    String code(String field, String fallback) {
        synchronized (LOCK) {
            return ProbeLogic.sanitizeCode(read().optString(field, fallback), fallback);
        }
    }

    boolean bool(String field) {
        synchronized (LOCK) {
            return read().optBoolean(field, false);
        }
    }

    String draft() {
        synchronized (LOCK) {
            return read().optString("draft", "");
        }
    }

    String shortcutActivity() {
        synchronized (LOCK) {
            return read().optString("shortcutActivity", "");
        }
    }

    String observedTitle() {
        synchronized (LOCK) {
            return read().optString("observedTitle", "");
        }
    }

    String lastAddedName() {
        synchronized (LOCK) {
            return read().optString("lastAddedName", "");
        }
    }

    String targetId() {
        synchronized (LOCK) {
            return read().optString("targetId", "");
        }
    }

    List<Group> groups() {
        synchronized (LOCK) {
            List<Group> list = new ArrayList<>();
            JSONArray arr = read().optJSONArray("groups");
            if (arr == null) return list;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject g = arr.optJSONObject(i);
                if (g == null) continue;
                String jid = g.optString("jid", "");
                if (!ProbeLogic.isGroupJid(jid)) continue;
                list.add(new Group(jid, g.optString("name", ""), g.optString("alias", ""),
                        g.optBoolean("checked", false), g.optString("source", "")));
            }
            return list;
        }
    }

    List<ContactSel> contacts() {
        synchronized (LOCK) {
            List<ContactSel> list = new ArrayList<>();
            JSONArray arr = read().optJSONArray("contacts");
            if (arr == null) return list;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject c = arr.optJSONObject(i);
                if (c == null) continue;
                list.add(new ContactSel(
                        c.optString("id", ""),
                        c.optString("lookup", ""),
                        c.optString("e164", ""),
                        c.optString("name", ""),
                        c.optString("alias", ""),
                        c.optBoolean("checked", false)));
            }
            return list;
        }
    }

    int groupCount() {
        return groups().size();
    }

    int selectedCount() {
        int n = 0;
        for (Group g : groups()) if (g.checked) n++;
        for (ContactSel c : contacts()) if (c.checked) n++;
        return n;
    }

    Target target() {
        String id;
        synchronized (LOCK) {
            id = read().optString("targetId", "");
        }
        if (id.isEmpty()) return null;
        for (Group g : groups()) {
            if (g.id().equals(id)) {
                return new Target(id, "group", g.name, g.alias, g.jid, "", g.checked);
            }
        }
        for (ContactSel c : contacts()) {
            if (c.id.equals(id)) {
                String jid = ProbeLogic.contactJid(c.e164);
                return new Target(id, "contact", c.name, c.alias, jid == null ? "" : jid, c.e164, c.checked);
            }
        }
        return null;
    }

    ProbeLogic.Report report(int sdk, String waVersion, boolean wa, boolean business,
                              boolean notifOn, boolean contactsPerm, boolean a11yOn) {
        ProbeLogic.Report report = new ProbeLogic.Report();
        report.androidSdk = sdk;
        report.waVersion = waVersion;
        report.pkg = ProbeLogic.pkgCode(wa, business);
        report.shortcut = code("shortcut", "NOT_TRIED");
        report.shortcutRoute = code("shortcutRoute", "NONE");
        report.shortcutActivity = shortcutActivity();
        report.notifOn = notifOn;
        report.groups = groupCount();
        report.groupNoJid = bool("sawGroupNoJid");
        report.directReply = bool("directReply");
        report.contactJidSeen = bool("sawContactJid");
        report.contactsPerm = contactsPerm;
        report.selected = selectedCount();
        report.sendKind = code("sendKind", "NONE");
        report.sendPath = code("sendPath", "NONE");
        report.sendMark = code("sendMark", "NONE");
        report.flow = code("flow", "NONE");
        report.a11yOn = a11yOn;
        report.autoPress = bool("autoPress");
        report.titleMatch = code("titleMatch", "UNKNOWN");
        report.sendButton = code("sendButton", "UNKNOWN");
        report.sendLabel = code("sendLabel", "NONE");
        report.autoResult = code("autoResult", "NONE");
        return report;
    }
}
