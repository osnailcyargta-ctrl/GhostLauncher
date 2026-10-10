package com.ghost.launcher.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.ghost.launcher.R;
import com.ghost.launcher.prefs.PrefsManager;
import com.ghost.launcher.util.WebShortcutHelper;

/**
 * Receives intents from browsers (e.g. Chrome "Add to Home screen") to install
 * a web shortcut. Also reachable when another app fires the legacy
 * INSTALL_SHORTCUT broadcast.
 *
 * For maximum compatibility we accept two intent shapes:
 *  - ACTION_CREATE_SHORTCUT (modern picker flow — used by launchers)
 *  - com.android.launcher.action.INSTALL_SHORTCUT (legacy Chrome flow)
 *
 * On valid input we persist the shortcut via {@link WebShortcutHelper#install} and
 * return RESULT_OK. The shortcut then appears on the home grid.
 */
public final class WebShortcutInstallerActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        if (intent == null) { finish(); return; }

        // Try to extract URL + title from intent
        String url = extractUrl(intent);
        String title = extractTitle(intent);

        if (url == null || url.isEmpty()) {
            // Ask user to type a URL manually
            promptManual();
            return;
        }
        confirmInstall(title, url);
    }

    private void promptManual() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(16), dp(20), dp(8));

        final EditText titleInput = new EditText(this);
        titleInput.setHint(getString(R.string.folder_rename_hint));
        titleInput.setText("Pintasan Web");

        final EditText urlInput = new EditText(this);
        urlInput.setHint("URL (https://…)");
        urlInput.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_URI);
        urlInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                String t = s.toString();
                if (!t.isEmpty() && !t.startsWith("http://") && !t.startsWith("https://")) {
                    if (!t.contains("://")) {
                        // auto-prepend https://
                        // (don't setText on change to avoid recursion; just hint)
                    }
                }
            }
        });

        root.addView(titleInput);
        root.addView(urlInput);

        new AlertDialog.Builder(this)
                .setTitle(R.string.install_shortcut_label)
                .setView(root)
                .setPositiveButton(R.string.ok, (DialogInterface d, int w) -> {
                    String t = titleInput.getText().toString().trim();
                    String u = urlInput.getText().toString().trim();
                    if (u.isEmpty()) { Toast.makeText(this, "URL kosong", Toast.LENGTH_SHORT).show(); finish(); return; }
                    if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://" + u;
                    if (t.isEmpty()) t = u;
                    WebShortcutHelper.install(this, t, u);
                    Toast.makeText(this, "Terkirim", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                })
                .setNegativeButton(R.string.cancel, (DialogInterface d, int w) -> finish())
                .setOnCancelListener(d -> finish())
                .show();
    }

    private void confirmInstall(String title, final String url) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.install_shortcut_label)
                .setMessage("Tambahkan \"" + title + "\" ke layar utama?")
                .setPositiveButton(R.string.ok, (d, w) -> {
                    WebShortcutHelper.install(this, title, url);
                    Toast.makeText(this, "Terkirim", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                })
                .setNegativeButton(R.string.cancel, (d, w) -> finish())
                .setOnCancelListener(d -> finish())
                .show();
    }

    private String extractUrl(Intent intent) {
        // Try Android.intent.extra.shortcut.INTENT (legacy)
        Intent inner = intent.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT);
        if (inner == null) inner = intent.getParcelableExtra("android.intent.extra.shortcut.INTENT");
        if (inner != null && inner.getData() != null) {
            return inner.getData().toString();
        }
        // Try direct data on this intent
        if (intent.getData() != null) return intent.getData().toString();
        return null;
    }

    private String extractTitle(Intent intent) {
        CharSequence name = intent.getStringExtra(Intent.EXTRA_SHORTCUT_NAME);
        if (name == null) name = intent.getStringExtra("android.intent.extra.shortcut.NAME");
        if (name != null) return name.toString();
        return "Pintasan Web";
    }

    private int dp(int n) {
        return (int) (getResources().getDisplayMetrics().density * n);
    }
}
