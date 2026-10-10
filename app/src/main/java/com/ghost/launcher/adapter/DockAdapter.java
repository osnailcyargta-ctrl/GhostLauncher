package com.ghost.launcher.adapter;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ghost.launcher.R;
import com.ghost.launcher.model.AppModel;
import com.ghost.launcher.util.AppLoader;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for the 5-slot dock bar. Always has 5 cells — empty slots are just
 * empty ImageViews that accept drag-drop.
 */
public class DockAdapter extends RecyclerView.Adapter<DockAdapter.VH> {
    public interface Callback {
        void onAppClicked(AppModel app, int slot);
        void onAppLongClicked(AppModel app, View anchor, int slot);
        void onEmptySlotClicked(int slot);
        void onEmptySlotLongClicked(int slot);
    }

    private static final int SLOT_COUNT = 5;
    private final Context ctx;
    private final List<AppModel> items = new ArrayList<>(SLOT_COUNT);
    private final Callback cb;

    public DockAdapter(Context c, Callback cb) {
        this.ctx = c;
        this.cb = cb;
        // pre-fill empty
        for (int i = 0; i < SLOT_COUNT; i++) items.add(null);
        setHasStableIds(true);
    }

    public void setSlot(int slot, AppModel m) {
        if (slot < 0 || slot >= SLOT_COUNT) return;
        items.set(slot, m);
        notifyItemChanged(slot);
    }

    public AppModel getSlot(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) return null;
        return items.get(slot);
    }

    public List<AppModel> snapshot() { return new ArrayList<>(items); }

    public int findSlotOf(AppModel m) {
        if (m == null) return -1;
        for (int i = 0; i < items.size(); i++) {
            if (m.equals(items.get(i))) return i;
        }
        return -1;
    }

    public int findEmptySlot() {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i) == null) return i;
        }
        return -1;
    }

    public void removeAt(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) return;
        items.set(slot, null);
        notifyItemChanged(slot);
    }

    @Override
    public long getItemId(int pos) { return pos; /* stable: slot index */ }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_dock_slot, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int slot) {
        AppModel m = items.get(slot);
        if (m == null) {
            h.icon.setImageDrawable(null);
            h.icon.setOnClickListener(v -> { if (cb != null) cb.onEmptySlotClicked(slot); });
            h.icon.setOnLongClickListener(v -> { if (cb != null) cb.onEmptySlotLongClicked(slot); return true; });
            h.removeBtn.setVisibility(View.GONE);
        } else {
            Drawable d = AppLoader.get(ctx).loadIconFor(m);
            if (d == null) {
                int color = 0xFF666666 ^ (m.label.hashCode() & 0x00404040);
                h.icon.setImageDrawable(new android.graphics.drawable.ColorDrawable(color));
            } else {
                h.icon.setImageDrawable(d);
            }
            h.icon.setOnClickListener(v -> { if (cb != null) cb.onAppClicked(m, slot); });
            h.icon.setOnLongClickListener(v -> { if (cb != null) cb.onAppLongClicked(m, v, slot); return true; });
            h.removeBtn.setVisibility(View.GONE); // shown only in edit mode (later)
        }
    }

    @Override
    public int getItemCount() { return SLOT_COUNT; }

    static final class VH extends RecyclerView.ViewHolder {
        final ImageView icon;
        final ImageView removeBtn;
        VH(@NonNull View v) {
            super(v);
            icon = v.findViewById(R.id.dockIcon);
            removeBtn = v.findViewById(R.id.dockRemoveBtn);
        }
    }
}
