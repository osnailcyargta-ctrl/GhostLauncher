package com.ghost.launcher.util;

import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherApps;
import android.graphics.drawable.Drawable;

import com.ghost.launcher.model.AppModel;
import com.ghost.launcher.prefs.PrefsManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Helper for installing / uninstalling / opening web shortcuts.
 *
 * A "web shortcut" is an app-like icon that, when tapped, opens the user's
 * preferred browser at a specific URL. We persist it in SharedPreferences so it
 * appears on the home grid alongside other apps.
 *
 * Also responds to the legacy {@code INSTALL_SHORTCUT} broadcast intent that
 * browsers like Chrome emit when the user picks "Add to Home screen".
 */
public final class WebShortcutHelper {
    private WebShortcutHelper() {}

    public static PrefsManager.WebShortcutSpec install(Context ctx, String title, String url) {
        PrefsManager.WebShortcutSpec spec = new PrefsManager.WebShortcutSpec(
                "sc:" + UUID.randomUUID().toString(),
                title == null ? url : title,
                url,
                "" // no icon; we synthesize a tinted letter icon at draw time
        );
        PrefsManager.get(ctx).addWebShortcut(spec);
        return spec;
    }

    public static void uninstall(Context ctx, String id) {
        PrefsManager.get(ctx).removeWebShortcut(id);
    }

    public static AppModel toAppModel(PrefsManager.WebShortcutSpec spec) {
        return new AppModel(spec.id, null, spec.title, true, 0L);
    }

    public static Intent openIntent(PrefsManager.WebShortcutSpec spec) {
        Intent i = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(spec.url));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return i;
    }

    /** Build a ShortcutInfo-compatible intent for pinning via ShortcutManager. */
    public static Intent shortcutInstallIntent(String title, String url) {
        Intent webIntent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url));
        webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Intent install = new Intent("com.android.launcher.action.INSTALL_SHORTCUT");
        install.putExtra(Intent.EXTRA_SHORTCUT_NAME, title);
        // Don't pass a duplicate icon resource; launcher will use default if absent
        install.putExtra("android.intent.extra.shortcut.INTENT", webIntent);
        install.putExtra("duplicate", false);
        return install;
    }
}
