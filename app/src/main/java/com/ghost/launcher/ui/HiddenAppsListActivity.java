package com.ghost.launcher.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ghost.launcher.R;
import com.ghost.launcher.adapter.AppGridAdapter;
import com.ghost.launcher.model.AppModel;
import com.ghost.launcher.prefs.PrefsManager;
import com.ghost.launcher.util.AppLoader;

import java.util.ArrayList;
import java.util.Set;

/**
 * Lists ONLY the apps that the user has marked as hidden.
 * Tapping launches them anyway — so hidden apps remain usable, just not visible
 * on the home screen or in the regular drawer.
 *
 * Reached only via the secret chooser popup (Ctrl+Alt+M).
 */
public final class HiddenAppsListActivity extends AppCompatActivity
        implements AppGridAdapter.Callback {

    private RecyclerView grid;
    private AppGridAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hidden_list);

        grid = findViewById(R.id.hiddenListGrid);
        grid.setLayoutManager(new GridLayoutManager(this, 4));
        grid.setItemAnimator(null);
        adapter = new AppGridAdapter(this, true, this);
        grid.setAdapter(adapter);

        findViewById(R.id.btnCloseList).setOnClickListener(v -> finish());

        load();
    }

    private void load() {
        PrefsManager prefs = PrefsManager.get(this);
        Set<String> hidden = prefs.getHiddenKeys();
        java.util.List<AppModel> all = AppLoader.get(this).loadAllApps(false);
        java.util.List<AppModel> visible = new ArrayList<>();
        for (AppModel m : all) {
            if (hidden.contains(m.key())) visible.add(m);
        }
        adapter.submit(visible);
    }

    @Override
    public void onAppClicked(AppModel app) {
        if (app == null) return;
        try {
            android.content.Intent i = getPackageManager().getLaunchIntentForPackage(app.packageName);
            if (i != null) {
                i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK |
                           android.content.Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                startActivity(i);
                finish();
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            }
        } catch (Throwable t) { /* */ }
    }

    @Override
    public void onAppLongClicked(AppModel app, View anchor) {
        // long-press = unhide shortcut
        if (app != null) {
            PrefsManager.get(this).toggleHidden(app.key());
            load();
        }
    }

    // Also allow Ctrl+Alt+M to dismiss this activity
    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        if (event.getAction() == android.view.KeyEvent.ACTION_UP
                && event.getKeyCode() == android.view.KeyEvent.KEYCODE_M) {
            boolean ctrl = (event.getMetaState() & android.view.KeyEvent.META_CTRL_ON) != 0;
            boolean alt  = (event.getMetaState() & android.view.KeyEvent.META_ALT_ON) != 0;
            if (ctrl && alt) { finish(); return true; }
        }
        return super.dispatchKeyEvent(event);
    }
}
