package com.ghost.launcher.adapter;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ghost.launcher.R;
import com.ghost.launcher.model.AppModel;
import com.ghost.launcher.util.AppLoader;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for grid views (home, drawer).
 * Uses a single ViewType and reuses ViewHolders aggressively. Icons are loaded
 * synchronously from the AppLoader LruCache — cache miss is rare because we
 * prewarm on first load.
 */
public class AppGridAdapter extends RecyclerView.Adapter<AppGridAdapter.VH> {
    public interface Callback {
        void onAppClicked(AppModel app);
        void onAppLongClicked(AppModel app, View anchor);
    }

    private final Context ctx;
    private final List<AppModel> items = new ArrayList<>();
    private final boolean showLabels;
    private final Callback cb;

    public AppGridAdapter(Context c, boolean showLabels, Callback cb) {
        this.ctx = c;
        this.showLabels = showLabels;
        this.cb = cb;
        setHasStableIds(true);
    }

    public void submit(List<AppModel> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    public List<AppModel> current() { return items; }

    @Override
    public long getItemId(int pos) {
        AppModel m = items.get(pos);
        return m.key().hashCode();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_app, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        AppModel m = items.get(position);
        Drawable icon = AppLoader.get(ctx).loadIconFor(m);
        if (icon == null) {
            // Fallback: tinted placeholder based on label hash for variety
            int color = 0xFF666666 ^ (m.label.hashCode() & 0x00404040);
            h.icon.setImageDrawable(new ColorDrawable(color));
        } else {
            h.icon.setImageDrawable(icon);
        }
        h.label.setVisibility(showLabels ? View.VISIBLE : View.GONE);
        h.label.setText(m.label);
        h.itemView.setOnClickListener(v -> {
            if (cb != null) cb.onAppClicked(m);
        });
        h.itemView.setOnLongClickListener(v -> {
            if (cb != null) cb.onAppLongClicked(m, v);
            return true;
        });
    }

    @Override
    public int getItemCount() { return items.size(); }

    static final class VH extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView label;
        VH(@NonNull View v) {
            super(v);
            icon = v.findViewById(R.id.appIcon);
            label = v.findViewById(R.id.appLabel);
        }
    }
}
