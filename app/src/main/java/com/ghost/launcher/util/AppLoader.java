package com.ghost.launcher.util;

import android.content.Context;
import android.content.pm.LauncherApps;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Process;
import android.os.UserHandle;
import android.util.LruCache;

import com.ghost.launcher.model.AppModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Loads launchable apps using {@link LauncherApps} (the official, battery-friendly API
 * introduced in API 21 that respects user profiles and silent app enumeration).
 *
 * Performance notes:
 *  - Apps are loaded once and cached; refresh on package change broadcast.
 *  - Icons are decoded into a small LruCache keyed by package name — bounded so we never
 *    OOM with hundreds of icons.
 *  - Labels pre-cached to avoid calling PackageManager.loadLabel per bind (slow).
 *  - No background thread / service churn: this loader is invoked synchronously from
 *    onResume only when dirty.
 */
public final class AppLoader {
    private static final int ICON_CACHE_SIZE_KB = 1024;  // ~256 KB worth of icons
    private static volatile AppLoader INSTANCE;

    private final Context ctx;
    private final LauncherApps launcherApps;
    private final PackageManager pm;
    private final LruCache<String, Drawable> iconCache;
    private List<AppModel> cachedApps;
    private long cacheGeneration = 0;
    private long lastLoadAt = 0L;
    private static final long MAX_CACHE_AGE_MS = 60_000L;

    private AppLoader(Context c) {
        this.ctx = c.getApplicationContext();
        this.launcherApps = (LauncherApps) ctx.getSystemService(Context.LAUNCHER_APPS_SERVICE);
        this.pm = ctx.getPackageManager();
        // LruCache sized by bytes (each Drawable ~ few KB after resample)
        this.iconCache = new LruCache<String, Drawable>(ICON_CACHE_SIZE_KB) {
            @Override
            protected int sizeOf(String key, Drawable value) {
                return Math.max(1, value.getIntrinsicWidth() * value.getIntrinsicHeight() / 4096);
            }
        };
    }

    public static AppLoader get(Context c) {
        if (INSTANCE == null) {
            synchronized (AppLoader.class) {
                if (INSTANCE == null) INSTANCE = new AppLoader(c);
            }
        }
        return INSTANCE;
    }

    public List<AppModel> loadAllApps(boolean force) {
        long now = System.currentTimeMillis();
        if (cachedApps != null && !force && now - lastLoadAt < MAX_CACHE_AGE_MS) {
            return cachedApps;
        }
        List<AppModel> out = new ArrayList<>(128);
        if (launcherApps == null) return out;
        try {
            UserHandle me = Process.myUserHandle();
            List<LauncherActivityInfo> list = launcherApps.getActivityList(null, me);
            if (list == null) return out;
            for (LauncherActivityInfo info : list) {
                AppModel m = new AppModel(
                        info.getApplicationInfo().packageName,
                        info.getComponentName().getClassName(),
                        info.getLabel() == null ? info.getApplicationInfo().packageName : info.getLabel().toString(),
                        false,
                        0L
                );
                out.add(m);
            }
            // Sort by label (case-insensitive, locale-aware)
            Collections.sort(out, new Comparator<AppModel>() {
                @Override public int compare(AppModel a, AppModel b) {
                    return a.label.compareToIgnoreCase(b.label);
                }
            });
        } catch (Throwable t) { /* swallow — return what we have */ }
        cachedApps = out;
        lastLoadAt = System.currentTimeMillis();
        cacheGeneration++;
        return out;
    }

    /** Returns cached generation counter (incremented on each reload). */
    public long getCacheGeneration() { return cacheGeneration; }

    /** Loads icon for app, caches it. Safe to call from UI thread (LauncherApps fast-path). */
    public Drawable loadIconFor(AppModel m) {
        if (m == null) return null;
        if (m.getIcon() != null) return m.getIcon();
        Drawable cachedIcon = iconCache.get(m.packageName);
        if (cachedIcon != null) {
            m.setIcon(cachedIcon, true);
            return cachedIcon;
        }
        try {
            // Try resolving via LauncherApps ActivityList (re-querying is cheap due to caching)
            if (launcherApps != null && cachedApps != null) {
                for (LauncherActivityInfo ai :
                        launcherApps.getActivityList(null, Process.myUserHandle())) {
                    if (ai.getApplicationInfo().packageName.equals(m.packageName)) {
                        Drawable d = ai.getBadgedIcon(0);
                        if (d != null) {
                            iconCache.put(m.packageName, d);
                            m.setIcon(d, true);
                            return d;
                        }
                    }
                }
            }
            // fallback to PackageManager
            Drawable d = pm.getApplicationIcon(m.packageName);
            if (d != null) {
                iconCache.put(m.packageName, d);
                m.setIcon(d, true);
            }
            return d;
        } catch (Throwable t) { return null; }
    }

    public void invalidate() {
        cachedApps = null;
        iconCache.evictAll();
        lastLoadAt = 0L;
    }
}
