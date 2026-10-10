package com.ghost.launcher.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ghost.launcher.R;
import com.ghost.launcher.adapter.AppGridAdapter;
import com.ghost.launcher.adapter.DockAdapter;
import com.ghost.launcher.model.AppModel;
import com.ghost.launcher.model.FolderModel;
import com.ghost.launcher.prefs.PrefsManager;
import com.ghost.launcher.util.AppLoader;
import com.ghost.launcher.util.IntentUtil;
import com.ghost.launcher.util.WebShortcutHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Home screen. Displays all visible (non-hidden) launchable apps + folders.
 *
 * Performance:
 *  - RecyclerView with stable IDs
 *  - Lazy icon loading via LruCache
 *  - No service / no broadcast actions except PackageChangeReceiver (cache invalidation only)
 *  - Wallpaper remains system-managed (we don't redraw it)
 *
 * Secret feature: pressing Ctrl+Alt+M on a physical keyboard opens the
 * HiddenAppsActivity. Not documented in README or anywhere — by design.
 */
public final class HomeActivity extends AppCompatActivity
        implements AppGridAdapter.Callback, DockAdapter.Callback {

    private static final int REQ_HIDDEN = 0xA1;

    private RecyclerView homeGrid;
    private AppGridAdapter gridAdapter;
    private DockAdapter dockAdapter;
    private RecyclerView dockRecycler;
    private View searchPill;

    private PrefsManager prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        prefs = PrefsManager.get(this);

        homeGrid = findViewById(R.id.homeGrid);
        searchPill = findViewById(R.id.searchPill);
        dockRecycler = new RecyclerView(this);

        // home grid layout manager — columns from prefs
        int cols = prefs.getGridColumns();
        GridLayoutManager lm = new GridLayoutManager(this, cols);
        homeGrid.setLayoutManager(lm);
        homeGrid.setHasFixedSize(true);
        homeGrid.setItemAnimator(null);
        homeGrid.setDrawingCacheEnabled(false);
        gridAdapter = new AppGridAdapter(this, prefs.getShowLabels(), this);
        homeGrid.setAdapter(gridAdapter);

        // dockbar: 5-slot horizontal
        LinearLayout dockBar = findViewById(R.id.dockBar);
        dockBar.removeAllViews();
        // Use a horizontal RecyclerView-like inline (we keep it simple as nested LinearLayout)
        // Each dock slot is a fixed-weight ImageView
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        rowLp.setMargins(dp(2), 0, dp(2), 0);

        // Build 5 slot views directly (avoids RecyclerView overhead for fixed 5 items)
        for (int i = 0; i < 5; i++) {
            View slot = LayoutInflater.from(this).inflate(R.layout.item_dock_slot, dockBar, false);
            slot.setLayoutParams(rowLp);
            dockBar.addView(slot);
        }
        // Initialize dock adapter (we'll bind through it)
        dockAdapter = new DockAdapter(this, this);
        // Bind dock items
        bindDock();

        // Search pill: tap to open drawer
        searchPill.setOnClickListener(v -> {
            Intent i = new Intent(this, AppDrawerActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });
        searchPill.setVisibility(View.VISIBLE);

        // Long-press on empty home area opens settings
        findViewById(android.R.id.content).setOnLongClickListener(v -> {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        });

        loadHomeApps(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reload apps on resume if cache invalidated
        if (AppLoader.get(this).getCacheGeneration() == 0
                || gridAdapter.current().isEmpty()) {
            loadHomeApps(true);
        } else {
            // quick path: only filter out hidden apps
            refreshHomeList();
            bindDock();
        }
    }

    private void loadHomeApps(boolean force) {
        List<AppModel> all = AppLoader.get(this).loadAllApps(force);
        refreshHomeList();
    }

    private void refreshHomeList() {
        List<AppModel> all = AppLoader.get(this).loadAllApps(false);
        Set<String> hidden = prefs.getHiddenKeys();
        List<AppModel> visible = new ArrayList<>(all.size());
        // Also include web shortcuts
        for (PrefsManager.WebShortcutSpec w : prefs.getWebShortcuts()) {
            visible.add(WebShortcutHelper.toAppModel(w));
        }
        for (AppModel m : all) {
            if (!hidden.contains(m.key())) visible.add(m);
        }
        // Use custom home order if any
        List<String> order = prefs.getHomeOrder();
        if (!order.isEmpty()) {
            final java.util.Map<String, Integer> orderMap = new java.util.HashMap<>();
            for (int i = 0; i < order.size(); i++) orderMap.put(order.get(i), i);
            java.util.Collections.sort(visible, new java.util.Comparator<AppModel>() {
                @Override public int compare(AppModel a, AppModel b) {
                    Integer oa = orderMap.get(a.key());
                    Integer ob = orderMap.get(b.key());
                    if (oa == null) oa = Integer.MAX_VALUE;
                    if (ob == null) ob = Integer.MAX_VALUE;
                    return oa.compareTo(ob);
                }
            });
        }
        gridAdapter.submit(visible);
    }

    private void bindDock() {
        List<String> dockKeys = prefs.getDockKeys();
        for (int i = 0; i < 5; i++) {
            AppModel m = null;
            if (i < dockKeys.size()) {
                String key = dockKeys.get(i);
                m = resolveAppModelByKey(key);
            }
            dockAdapter.setSlot(i, m);
        }
        // Bind to the actual dock Views (since we built them inline)
        LinearLayout dockBar = findViewById(R.id.dockBar);
        for (int i = 0; i < 5; i++) {
            final int slot = i;
            View slotView = dockBar.getChildAt(slot);
            ImageView icon = slotView.findViewById(R.id.dockIcon);
            ImageView removeBtn = slotView.findViewById(R.id.dockRemoveBtn);
            final AppModel m = dockAdapter.getSlot(slot);
            if (m == null) {
                icon.setImageDrawable(null);
                icon.setOnClickListener(v -> { /* nothing */ });
                icon.setOnLongClickListener(v -> { onEmptySlotLongClicked(slot); return true; });
                removeBtn.setVisibility(View.GONE);
            } else {
                icon.setImageDrawable(AppLoader.get(this).loadIconFor(m));
                icon.setOnClickListener(v -> onAppClicked(m, slot));
                icon.setOnLongClickListener(v -> { onAppLongClicked(m, v, slot); return true; });
                removeBtn.setVisibility(View.GONE);
            }
        }
    }

    private AppModel resolveAppModelByKey(String key) {
        if (key == null) return null;
        if (key.startsWith("sc:")) {
            for (PrefsManager.WebShortcutSpec w : prefs.getWebShortcuts()) {
                if (w.id.equals(key)) return WebShortcutHelper.toAppModel(w);
            }
            return null;
        }
        // parse "pkg:<pkg>/<class>"
        String body = key.substring(4);
        int slash = body.indexOf('/');
        String pkg = slash < 0 ? body : body.substring(0, slash);
        String cls = slash < 0 ? null : body.substring(slash + 1);
        for (AppModel m : AppLoader.get(this).loadAllApps(false)) {
            if (m.packageName.equals(pkg) && (cls == null || cls.equals(m.className))) return m;
        }
        return null;
    }

    // ---------- AppGridAdapter.Callback ----------
    @Override
    public void onAppClicked(AppModel app) {
        if (app == null) return;
        if (app.isShortcut) {
            // web shortcut
            for (PrefsManager.WebShortcutSpec w : prefs.getWebShortcuts()) {
                if (w.id.equals(app.packageName)) {
                    try {
                        Intent i = com.ghost.launcher.util.WebShortcutHelper.openIntent(w);
                        startActivity(i);
                    } catch (Throwable t) {
                        Toast.makeText(this, t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                    return;
                }
            }
            return;
        }
        try {
            Intent launch = getPackageManager().getLaunchIntentForPackage(app.packageName);
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                startActivity(launch);
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            }
        } catch (Throwable t) { /* security exception etc */ }
    }

    @Override
    public void onAppLongClicked(AppModel app, View anchor) {
        showAppOptionsPopup(app, anchor, -1);
    }

    // ---------- DockAdapter.Callback ----------
    @Override
    public void onAppClicked(AppModel app, int slot) {
        onAppClicked(app);
    }

    @Override
    public void onAppLongClicked(AppModel app, View anchor, int slot) {
        showAppOptionsPopup(app, anchor, slot);
    }

    @Override
    public void onEmptySlotClicked(int slot) { /* nothing */ }

    @Override
    public void onEmptySlotLongClicked(int slot) {
        Toast.makeText(this, R.string.empty_dock, Toast.LENGTH_SHORT).show();
    }

    // ---------- App options popup ----------
    private void showAppOptionsPopup(AppModel app, View anchor, int dockSlot) {
        View popup = LayoutInflater.from(this).inflate(R.layout.dialog_app_options, null);
        int width = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 220,
                getResources().getDisplayMetrics());

        PopupWindow pw = new PopupWindow(popup, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        pw.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
        pw.setElevation(10);
        pw.setInputMethodMode(PopupWindow.INPUT_METHOD_NEEDED);
        pw.setFocusable(true);
        pw.setOutsideTouchable(true);

        View btnHide = popup.findViewById(R.id.btnHide);
        View btnPin = popup.findViewById(R.id.btnPin);
        View btnInfo = popup.findViewById(R.id.btnAppInfo);
        View btnUninstall = popup.findViewById(R.id.btnUninstall);

        boolean isHidden = prefs.isHidden(app.key());
        ((TextView) btnHide).setText(isHidden ? R.string.action_unhide : R.string.action_hide);

        boolean inDock = dockSlot >= 0;
        ((TextView) btnPin).setText(inDock ? R.string.action_unpin_dock : R.string.action_pin_dock);

        btnHide.setOnClickListener(v -> {
            prefs.toggleHidden(app.key());
            pw.dismiss();
            refreshHomeList();
            bindDock();
        });

        btnPin.setOnClickListener(v -> {
            if (inDock) {
                // unpin from dock
                List<String> keys = prefs.getDockKeys();
                if (dockSlot < keys.size()) keys.set(dockSlot, null);
                prefs.setDockKeys(keys);
            } else {
                // pin to first empty slot
                List<String> keys = prefs.getDockKeys();
                // Pad to 5
                while (keys.size() < 5) keys.add(null);
                int empty = -1;
                for (int i = 0; i < 5; i++) if (keys.get(i) == null) { empty = i; break; }
                if (empty < 0) {
                    Toast.makeText(this, "Dock penuh", Toast.LENGTH_SHORT).show();
                } else {
                    keys.set(empty, app.key());
                    prefs.setDockKeys(keys);
                }
            }
            pw.dismiss();
            bindDock();
        });

        btnInfo.setOnClickListener(v -> {
            try {
                startActivity(IntentUtil.appInfoIntent(app.packageName));
            } catch (Throwable t) { /* */ }
            pw.dismiss();
        });

        btnUninstall.setOnClickListener(v -> {
            try {
                startActivity(IntentUtil.uninstallIntent(app.packageName));
            } catch (Throwable t) { /* */ }
            pw.dismiss();
        });

        pw.showAsDropDown(anchor, 0, -anchor.getHeight() / 2, Gravity.NO_GRAVITY);
    }

    // ---------- Folder creation ----------
    // Long-press drag is supported via system drag-drop. Two apps overlap → folder.
    // For simplicity in this build, folder creation is triggered by long-pressing
    // one app and choosing "Buat folder dengan…" — we use simple inline dialog here.

    // ---------- SECRET: Ctrl+Alt+M key combo ----------
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // Detect Ctrl+Alt+M on physical keyboard — opens HiddenApps popup
        if (event.getAction() == KeyEvent.ACTION_UP && event.getKeyCode() == KeyEvent.KEYCODE_M) {
            boolean ctrl = (event.getMetaState() & KeyEvent.META_CTRL_ON) != 0;
            boolean alt  = (event.getMetaState() & KeyEvent.META_ALT_ON) != 0;
            if (ctrl && alt) {
                launchHiddenAppsPopup();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void launchHiddenAppsPopup() {
        Intent i = new Intent(this, HiddenAppsActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        } catch (Throwable t) {
            Toast.makeText(this, t.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // ---------- helpers ----------
    private int dp(int n) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, n,
                getResources().getDisplayMetrics());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_HIDDEN) {
            refreshHomeList();
            bindDock();
        }
    }
}
