package com.ghost.launcher.model;

import android.graphics.drawable.Drawable;

/**
 * Lightweight holder for a launchable app. Carries only what the UI needs.
 * Memory-efficient: no context references, no deep Drawable copies.
 */
public final class AppModel {
    public final String packageName;
    public final String className;       // launchable activity, may be null for shortcuts
    public final String label;
    public final boolean isShortcut;     // true = web shortcut, false = installed app
    public final long userSerial;       // currently always 0 (single user)
    public final long firstInstallTime;

    private Drawable icon;              // cached lazily, may be null
    private boolean iconLoaded;

    public AppModel(String packageName, String className, String label,
                    boolean isShortcut, long firstInstallTime) {
        this.packageName = packageName;
        this.className = className;
        this.label = label;
        this.isShortcut = isShortcut;
        this.firstInstallTime = firstInstallTime;
        this.userSerial = 0L;
    }

    public Drawable getIcon() { return icon; }
    public boolean isIconLoaded() { return iconLoaded; }
    public void setIcon(Drawable d, boolean loaded) {
        this.icon = d;
        this.iconLoaded = loaded;
    }

    public String key() {
        return isShortcut ? "sc:" + packageName
                          : "pkg:" + packageName + "/" + (className == null ? "" : className);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AppModel)) return false;
        AppModel a = (AppModel) o;
        return key().equals(a.key());
    }

    @Override
    public int hashCode() { return key().hashCode(); }
}
