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
import com.ghost.launcher.prefs.PrefsManager;
import com.ghost.launcher.util.AppLoader;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Adapter for the secret Hidden-Apps popup grid.
 * Same look as home grid, but tapping toggles a hide flag.
 */
public class HiddenAppsAdapter extends RecyclerView.Adapter<HiddenAppsAdapter.VH> {
    public interface Callback {
        void onToggle(AppModel m, boolean nowHidden);
    }

    private final Context ctx;
    private final Callback cb;
    private final List<AppModel> items = new ArrayList<>();
    private final Set<String> hiddenKeys;

    public HiddenAppsAdapter(Context c, Callback cb) {
        this.ctx = c;
        this.cb = cb;
        this.hiddenKeys = new HashSet<>(PrefsManager.get(ctx).getHiddenKeys());
        setHasStableIds(true);
    }

    public void submit(List<AppModel> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    public void filter(String q) {
        if (q == null || q.isEmpty()) {
            // show all
        } else {
            String ql = q.toLowerCase();
            List<AppModel> filtered = new ArrayList<>();
            for (AppModel m : items) {
                if (m.label.toLowerCase().contains(ql) ||
                    (m.packageName != null && m.packageName.toLowerCase().contains(ql))) {
                    filtered.add(m);
                }
            }
            items.clear();
            items.addAll(filtered);
            notifyDataSetChanged();
            return;
        }
        notifyDataSetChanged();
    }

    public void setAll(List<AppModel> all) {
        items.clear();
        items.addAll(all);
        notifyDataSetChanged();
    }

    @Override public long getItemId(int pos) {
        return items.get(pos).key().hashCode();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_hidden_app, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        AppModel m = items.get(position);
        Drawable d = AppLoader.get(ctx).loadIconFor(m);
        if (d == null) {
            int color = 0xFF666666 ^ (m.label.hashCode() & 0x00404040);
            h.icon.setImageDrawable(new android.graphics.drawable.ColorDrawable(color));
        } else {
            h.icon.setImageDrawable(d);
        }
        h.label.setText(m.label);
        boolean hidden = hiddenKeys.contains(m.key());
        h.overlay.setVisibility(hidden ? View.VISIBLE : View.GONE);
        h.badge.setVisibility(hidden ? View.VISIBLE : View.GONE);
        h.itemView.setOnClickListener(v -> {
            boolean nowHidden = !hidden;
            if (nowHidden) hiddenKeys.add(m.key()); else hiddenKeys.remove(m.key());
            if (cb != null) cb.onToggle(m, nowHidden);
            notifyItemChanged(h.getAdapterPosition());
        });
    }

    @Override
    public int getItemCount() { return items.size(); }

    public Set<String> currentHiddenKeys() { return hiddenKeys; }

    static final class VH extends RecyclerView.ViewHolder {
        final ImageView icon;
        final ImageView badge;
        final View overlay;
        final android.widget.TextView label;
        VH(@NonNull View v) {
            super(v);
            icon = v.findViewById(R.id.appIcon);
            badge = v.findViewById(R.id.hideBadge);
            overlay = v.findViewById(R.id.hideOverlay);
            label = v.findViewById(R.id.appLabel);
        }
    }
}
