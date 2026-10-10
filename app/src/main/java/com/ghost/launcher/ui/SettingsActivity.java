package com.ghost.launcher.ui;

import android.os.Bundle;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import androidx.appcompat.app.AppCompatActivity;

import com.ghost.launcher.R;
import com.ghost.launcher.prefs.PrefsManager;

/**
 * Settings — accessible via long-press on empty home area.
 * Allows grid column adjustment, label visibility toggle, and reset.
 */
public final class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();
    }

    public static final class SettingsFragment extends PreferenceFragmentCompat
            implements androidx.preference.Preference.OnPreferenceChangeListener,
                       androidx.preference.Preference.OnPreferenceClickListener {

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.prefs, rootKey);

            SwitchPreferenceCompat labels = findPreference("show_labels");
            if (labels != null) {
                labels.setChecked(PrefsManager.get(getContext()).getShowLabels());
                labels.setOnPreferenceChangeListener(this);
            }
            ListPreference cols = findPreference("grid_columns");
            if (cols != null) {
                cols.setValue(String.valueOf(PrefsManager.get(getContext()).getGridColumns()));
                cols.setSummary(cols.getEntry());
                cols.setOnPreferenceChangeListener(this);
            }
            Preference about = findPreference("about");
            if (about != null) {
                about.setOnPreferenceClickListener(this);
            }
        }

        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            String key = preference.getKey();
            if ("show_labels".equals(key)) {
                PrefsManager.get(getContext()).setShowLabels((Boolean) newValue);
                return true;
            } else if ("grid_columns".equals(key)) {
                int n = Integer.parseInt((String) newValue);
                PrefsManager.get(getContext()).setGridColumns(n);
                if (preference instanceof ListPreference) {
                    ListPreference lp = (ListPreference) preference;
                    lp.setValue(String.valueOf(n));
                    lp.setSummary(lp.getEntry());
                }
                return true;
            }
            return false;
        }

        @Override
        public boolean onPreferenceClick(Preference preference) {
            return false;
        }
    }
}
