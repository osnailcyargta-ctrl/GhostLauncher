package com.ghost.launcher.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.ghost.launcher.util.AppLoader;

/**
 * Receives PACKAGE_ADDED / REMOVED / CHANGED / REPLACED broadcasts and invalidates
 * the cached app list so the launcher reloads on next resume.
 *
 * IMPORTANT for battery: we do NOT start any service here. We only flip a boolean
 * in the AppLoader cache so the next onResume picks up new apps. Zero background
 * work, zero wakeups, zero polling.
 */
public final class PackageChangeReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getData() == null) return;
        // Just invalidate the cache. AppLoader reloads lazily on next resume.
        try {
            AppLoader.get(context).invalidate();
        } catch (Throwable ignored) { }
    }
}
