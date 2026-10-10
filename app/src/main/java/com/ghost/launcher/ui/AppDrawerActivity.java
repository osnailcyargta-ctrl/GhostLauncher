package com.ghost.launcher.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ghost.launcher.R;
import com.ghost.launcher.adapter.AppGridAdapter;
import com.ghost.launcher.model.AppModel;
import com.ghost.launcher.prefs.PrefsManager;
import com.ghost.launcher.util.AppLoader;
import com.ghost.launcher.util.WebShortcutHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * All-apps drawer. Lists every launchable app (excluding hidden ones) +
 * web shortcuts. Backed by RecyclerView with a fast text filter on top.
 *
 * Performance: filter is applied in-memory on a cached list — no IPC.
 */
public final class AppDrawerActivity extends AppCompatActivity
        implements AppGridAdapter.Callback {

    private RecyclerView grid;
    private AppGridAdapter adapter;
    private EditText search;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_drawer);

        search = findViewById(R.id.drawerSearch);
        grid = findViewById(R.id.drawerGrid);

        int cols = PrefsManager.get(this).getGridColumns();
        grid.setLayoutManager(new GridLayoutManager(this, cols));
        grid.setItemAnimator(null);
        grid.setHasFixedSize(true);
        adapter = new AppGridAdapter(this, PrefsManager.get(this).getShowLabels(), this);
        grid.setAdapter(adapter);

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filter(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        search.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                // open first matching app
                List<AppModel> items = adapter.current();
                if (!items.isEmpty()) launchApp(items.get(0));
                return true;
            }
            return false;
        });

        load();
    }

    private void load() {
        PrefsManager prefs = PrefsManager.get(this);
        Set<String> hidden = prefs.getHiddenKeys();
        List<AppModel> visible = new ArrayList<>(128);
        for (PrefsManager.WebShortcutSpec w : prefs.getWebShortcuts()) {
            visible.add(WebShortcutHelper.toAppModel(w));
        }
        for (AppModel m : AppLoader.get(this).loadAllApps(false)) {
            if (!hidden.contains(m.key())) visible.add(m);
        }
        adapter.submit(visible);
    }

    private void filter(String q) {
        if (q == null || q.isEmpty()) { load(); return; }
        String ql = q.toLowerCase();
        PrefsManager prefs = PrefsManager.get(this);
        Set<String> hidden = prefs.getHiddenKeys();
        List<AppModel> out = new ArrayList<>();
        for (AppModel m : AppLoader.get(this).loadAllApps(false)) {
            if (hidden.contains(m.key())) continue;
            if (m.label.toLowerCase().contains(ql) ||
                (m.packageName != null && m.packageName.toLowerCase().contains(ql))) {
                out.add(m);
            }
        }
        adapter.submit(out);
    }

    private void launchApp(AppModel app) {
        if (app == null) return;
        try {
            Intent i = getPackageManager().getLaunchIntentForPackage(app.packageName);
            if (i != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                startActivity(i);
                finish();
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            }
        } catch (Throwable t) { /* */ }
    }

    @Override
    public void onAppClicked(AppModel app) {
        if (app != null && app.isShortcut) {
            for (PrefsManager.WebShortcutSpec w : PrefsManager.get(this).getWebShortcuts()) {
                if (w.id.equals(app.packageName)) {
                    try {
                        startActivity(WebShortcutHelper.openIntent(w));
                        finish();
                    } catch (Throwable t) { /* */ }
                    return;
                }
            }
            return;
        }
        launchApp(app);
    }

    @Override
    public void onAppLongClicked(AppModel app, View anchor) {
        // Reuse home's options popup logic
        // For simplicity, just launch app-info here on long-press
        try {
            if (app != null && !app.isShortcut) {
                startActivity(com.ghost.launcher.util.IntentUtil.appInfoIntent(app.packageName));
            }
        } catch (Throwable t) { /* */ }
    }
}
