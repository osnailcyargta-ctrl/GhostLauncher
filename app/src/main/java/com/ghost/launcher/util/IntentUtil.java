package com.ghost.launcher.util;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.net.Uri;

import java.util.List;

/**
 * Misc helpers for resolving common Android intents used by launchers.
 */
public final class IntentUtil {
    private IntentUtil() {}

    /** Open system Settings → Apps → <pkg> (used by "App info" action). */
    public static Intent appInfoIntent(String pkg) {
        Intent i = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        i.setData(Uri.fromParts("package", pkg, null));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return i;
    }

    /** Uninstall intent (will trigger system uninstall dialog). */
    public static Intent uninstallIntent(String pkg) {
        Intent i = new Intent(Intent.ACTION_DELETE);
        i.setData(Uri.fromParts("package", pkg, null));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return i;
    }

    /** Best-effort: returns true if a default browser is available. */
    public static boolean hasBrowser(Context ctx) {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"));
        List<ResolveInfo> r = ctx.getPackageManager().queryIntentActivities(i, 0);
        return r != null && !r.isEmpty();
    }
}
