package com.ghost.launcher.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ghost.launcher.R;
import com.ghost.launcher.adapter.HiddenAppsAdapter;
import com.ghost.launcher.model.AppModel;
import com.ghost.launcher.prefs.PrefsManager;
import com.ghost.launcher.util.AppLoader;

import java.util.List;

/**
 * Secret popup activity — hidden feature triggered by Ctrl+Alt+M.
 *
 * Allows the user to mark apps as "hidden" — they will no longer appear on the
 * home screen or in the app drawer. The list of hidden app keys is persisted in
 * SharedPreferences (no metadata stored elsewhere — UI doesn't expose the toggle).
 *
 * Not mentioned in README or any user-facing string intentionally.
 */
public final class HiddenAppsActivity extends AppCompatActivity
        implements HiddenAppsAdapter.Callback {

    private RecyclerView grid;
    private HiddenAppsAdapter adapter;
    private EditText search;
    private List<AppModel> allApps;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hidden);

        grid = findViewById(R.id.hiddenGrid);
        search = findViewById(R.id.hiddenSearch);

        grid.setLayoutManager(new GridLayoutManager(this, 4));
        grid.setItemAnimator(null);
        adapter = new HiddenAppsAdapter(this, this);
        grid.setAdapter(adapter);

        // Load all installed apps (hidden + visible)
        allApps = AppLoader.get(this).loadAllApps(true);
        adapter.setAll(allApps);

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilter(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        findViewById(R.id.btnClose).setOnClickListener(v -> finish());
        findViewById(R.id.btnDone).setOnClickListener(v -> {
            // persist
            PrefsManager.get(this).setHiddenKeys(adapter.currentHiddenKeys());
            setResult(RESULT_OK);
            finish();
        });
    }

    private void applyFilter(String q) {
        if (q == null || q.isEmpty()) {
            adapter.setAll(allApps);
            return;
        }
        String ql = q.toLowerCase();
        java.util.List<AppModel> filtered = new java.util.ArrayList<>();
        for (AppModel m : allApps) {
            if (m.label.toLowerCase().contains(ql) ||
                (m.packageName != null && m.packageName.toLowerCase().contains(ql))) {
                filtered.add(m);
            }
        }
        adapter.setAll(filtered);
    }

    @Override
    public void onToggle(AppModel m, boolean nowHidden) {
        // No-op: persisted on Done
    }

    @Override
    public void onBackPressed() {
        // persist on back too
        PrefsManager.get(this).setHiddenKeys(adapter.currentHiddenKeys());
        setResult(RESULT_OK);
        super.onBackPressed();
    }

    // Also allow Ctrl+Alt+M to dismiss
    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        if (event.getAction() == android.view.KeyEvent.ACTION_UP
                && event.getKeyCode() == android.view.KeyEvent.KEYCODE_M) {
            boolean ctrl = (event.getMetaState() & android.view.KeyEvent.META_CTRL_ON) != 0;
            boolean alt  = (event.getMetaState() & android.view.KeyEvent.META_ALT_ON) != 0;
            if (ctrl && alt) {
                PrefsManager.get(this).setHiddenKeys(adapter.currentHiddenKeys());
                setResult(RESULT_OK);
                finish();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }
}
