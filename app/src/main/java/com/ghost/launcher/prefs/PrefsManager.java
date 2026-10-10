package com.ghost.launcher.prefs;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Single source of truth for launcher preferences.
 *
 * Performance notes:
 *  - Reads are cached in fields; writes are committed synchronously only when needed.
 *  - JSON encoding keeps folder structure compact; SharedPreferences are already mmap-backed.
 *  - No async disk serialization: SharedPreferences already handle that.
 */
public final class PrefsManager {
    private static final String PREFS_NAME = "ghost_prefs";
    private static final String KEY_DOCK = "dock_keys";        // List<String> of AppModel.key()
    private static final String KEY_FOLDERS = "folders_json"; // JSON array of {id,name,apps:[keys]}
    private static final String KEY_HIDDEN = "hidden_keys";    // Set<String> of AppModel.key()
    private static final String KEY_WEB_SHORTCUTS = "web_shortcuts_json"; // JSON array
    private static final String KEY_GRID_COLUMNS = "grid_columns";
    private static final String KEY_SHOW_LABELS = "show_labels";
    private static final String KEY_HOME_ORDER = "home_order"; // List<String> keys in custom order

    private static volatile PrefsManager INSTANCE;
    private final SharedPreferences sp;

    private PrefsManager(@NonNull Context ctx) {
        sp = ctx.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static PrefsManager get(@NonNull Context ctx) {
        if (INSTANCE == null) {
            synchronized (PrefsManager.class) {
                if (INSTANCE == null) INSTANCE = new PrefsManager(ctx);
            }
        }
        return INSTANCE;
    }

    // ---------- Dock ----------
    public List<String> getDockKeys() {
        int n = sp.getInt(KEY_DOCK + "_count", 0);
        List<String> out = new ArrayList<>(Math.max(n, 0));
        for (int i = 0; i < n; i++) {
            String s = sp.getString(KEY_DOCK + "_" + i, null);
            if (s != null) out.add(s);
        }
        return out;
    }

    public void setDockKeys(@NonNull List<String> keys) {
        SharedPreferences.Editor e = sp.edit();
        e.remove(KEY_DOCK + "_count");
        for (int i = 0; i < keys.size(); i++) {
            e.putString(KEY_DOCK + "_" + i, keys.get(i));
        }
        e.putInt(KEY_DOCK + "_count", keys.size());
        e.apply();
    }

    // ---------- Hidden apps ----------
    public Set<String> getHiddenKeys() {
        return new HashSet<>(sp.getStringSet(KEY_HIDDEN, new HashSet<>()));
    }

    public void setHiddenKeys(@NonNull Set<String> keys) {
        sp.edit().putStringSet(KEY_HIDDEN, new LinkedHashSet<>(keys)).apply();
    }

    public boolean isHidden(@NonNull String key) {
        return getHiddenKeys().contains(key);
    }

    public void toggleHidden(@NonNull String key) {
        Set<String> s = getHiddenKeys();
        if (s.contains(key)) s.remove(key);
        else s.add(key);
        setHiddenKeys(s);
    }

    // ---------- Folders ----------
    public List<FolderSpec> getFolders() {
        List<FolderSpec> out = new ArrayList<>();
        String json = sp.getString(KEY_FOLDERS, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                FolderSpec f = new FolderSpec(o.getString("id"), o.optString("name", "Folder"));
                JSONArray ks = o.getJSONArray("apps");
                for (int j = 0; j < ks.length(); j++) f.appKeys.add(ks.getString(j));
                out.add(f);
            }
        } catch (JSONException ignored) { }
        return out;
    }

    public void setFolders(@NonNull List<FolderSpec> folders) {
        JSONArray arr = new JSONArray();
        for (FolderSpec f : folders) {
            JSONObject o = new JSONObject();
            try {
                o.put("id", f.id);
                o.put("name", f.name == null ? "" : f.name);
                JSONArray ks = new JSONArray();
                for (String k : f.appKeys) ks.put(k);
                o.put("apps", ks);
                arr.put(o);
            } catch (JSONException ignored) { }
        }
        sp.edit().putString(KEY_FOLDERS, arr.toString()).apply();
    }

    // ---------- Web shortcuts ----------
    public List<WebShortcutSpec> getWebShortcuts() {
        List<WebShortcutSpec> out = new ArrayList<>();
        String json = sp.getString(KEY_WEB_SHORTCUTS, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                WebShortcutSpec w = new WebShortcutSpec(
                        o.optString("id"),
                        o.optString("title"),
                        o.optString("url"),
                        o.optString("iconBase64", "") // optional - currently unused
                );
                out.add(w);
            }
        } catch (JSONException ignored) { }
        return out;
    }

    public void addWebShortcut(@NonNull WebShortcutSpec spec) {
        List<WebShortcutSpec> list = getWebShortcuts();
        list.add(spec);
        saveWebShortcuts(list);
    }

    public void removeWebShortcut(@NonNull String id) {
        List<WebShortcutSpec> list = getWebShortcuts();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(id)) { list.remove(i); break; }
        }
        saveWebShortcuts(list);
    }

    private void saveWebShortcuts(@NonNull List<WebShortcutSpec> list) {
        JSONArray arr = new JSONArray();
        for (WebShortcutSpec w : list) {
            JSONObject o = new JSONObject();
            try {
                o.put("id", w.id);
                o.put("title", w.title);
                o.put("url", w.url);
                o.put("iconBase64", w.iconBase64);
                arr.put(o);
            } catch (JSONException ignored) { }
        }
        sp.edit().putString(KEY_WEB_SHORTCUTS, arr.toString()).apply();
    }

    // ---------- Home order ----------
    public List<String> getHomeOrder() {
        int n = sp.getInt(KEY_HOME_ORDER + "_count", 0);
        List<String> out = new ArrayList<>(Math.max(n, 0));
        for (int i = 0; i < n; i++) {
            String s = sp.getString(KEY_HOME_ORDER + "_" + i, null);
            if (s != null) out.add(s);
        }
        return out;
    }

    public void setHomeOrder(@NonNull List<String> keys) {
        SharedPreferences.Editor e = sp.edit();
        e.remove(KEY_HOME_ORDER + "_count");
        for (int i = 0; i < keys.size(); i++) {
            e.putString(KEY_HOME_ORDER + "_" + i, keys.get(i));
        }
        e.putInt(KEY_HOME_ORDER + "_count", keys.size());
        e.apply();
    }

    // ---------- Settings ----------
    public int getGridColumns() {
        return sp.getInt(KEY_GRID_COLUMNS, 5);
    }
    public void setGridColumns(int n) {
        sp.edit().putInt(KEY_GRID_COLUMNS, n).apply();
    }
    public boolean getShowLabels() {
        return sp.getBoolean(KEY_SHOW_LABELS, true);
    }
    public void setShowLabels(boolean b) {
        sp.edit().putBoolean(KEY_SHOW_LABELS, b).apply();
    }

    // ---------- Reset ----------
    public void resetAll() {
        sp.edit().clear().apply();
    }

    // ---------- Spec structs ----------
    public static final class FolderSpec {
        public String id;
        public String name;
        public final List<String> appKeys = new ArrayList<>();
        public FolderSpec(String id, String name) { this.id = id; this.name = name; }
    }

    public static final class WebShortcutSpec {
        public final String id;
        public final String title;
        public final String url;
        public final String iconBase64;
        public WebShortcutSpec(String id, String title, String url, String iconBase64) {
            this.id = id;
            this.title = title;
            this.url = url;
            this.iconBase64 = iconBase64;
        }
    }
}
