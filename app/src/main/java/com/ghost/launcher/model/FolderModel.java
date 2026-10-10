package com.ghost.launcher.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight holder for a folder of apps on the home screen.
 */
public final class FolderModel {
    public String id;                 // stable id (uuid-ish string)
    public String name;
    public final List<AppModel> apps = new ArrayList<>(8);

    public FolderModel(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public int size() { return apps.size(); }
    public boolean isEmpty() { return apps.isEmpty(); }
    public void add(AppModel a) { if (!apps.contains(a)) apps.add(a); }
    public void remove(AppModel a) { apps.remove(a); }
}
