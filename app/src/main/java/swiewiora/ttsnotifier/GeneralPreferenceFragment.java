package swiewiora.ttsnotifier;

import android.annotation.TargetApi;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.support.v4.app.NotificationManagerCompat;
import android.view.MenuItem;

/**
 * This fragment shows general preferences only. It is used when the
 * activity is showing a two-pane settings UI.
 */
@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class GeneralPreferenceFragment extends PreferenceFragment
        implements Preference.OnPreferenceClickListener,
        SharedPreferences.OnSharedPreferenceChangeListener {
    private Preference pStatus;
    private final Service.OnStatusChangeListener statusListener =
            new Service.OnStatusChangeListener() {
        @Override
        public void onStatusChanged() {
            updateStatus();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Common.init(getActivity());
        addPreferencesFromResource(R.xml.pref_general);
        setHasOptionsMenu(true);

        // Bind the summaries of EditText/List/Dialog/Ringtone preferences
        // to their values. When their values change, their summaries are
        // updated to reflect the new value, per the Android Design
        // guidelines.
//            bindPreferenceSummaryToValue(findPreference("example_text"));
//            bindPreferenceSummaryToValue(findPreference("example_list"));

        pStatus = findPreference(getString(R.string.key_status));
        pStatus.setOnPreferenceClickListener(this);
        findPreference(getString(R.string.key_appList))
                .setIntent(new Intent(getActivity(), AppList.class));

        updateStatus();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            startActivity(new Intent(getActivity(), MainActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
        if (key.equals(getString(R.string.key_ttsStream))) {
            Common.setVolumeStream(getActivity());
        }
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        if (preference == pStatus && Service.isRunning() && Service.isSuspended()) {
            Service.toggleSuspend();
            return true;
        }
        return false;
    }

    private void updateStatus() {
        if (Service.isSuspended() && Service.isRunning()) {
            pStatus.setTitle(R.string.service_suspended);
            pStatus.setSummary(R.string.status_summary_suspended);
            pStatus.setIntent(null);
        } else {
            pStatus.setTitle(Service.isRunning() ? R.string.service_running : R.string.service_disabled);
            if (NotificationManagerCompat.getEnabledListenerPackages(getActivity()).contains(getActivity().getPackageName())) {
                pStatus.setSummary(R.string.status_summary_notification_access_enabled);
            } else {
                pStatus.setSummary(R.string.status_summary_notification_access_disabled);
            }
            pStatus.setIntent(Common.getNotificationListenerSettingsIntent());
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Common.getPrefs(getActivity()).registerOnSharedPreferenceChangeListener(this);
        Service.registerOnStatusChangeListener(statusListener);
        updateStatus();
    }

    @Override
    public void onPause() {
        Service.unregisterOnStatusChangeListener(statusListener);
        Common.getPrefs(getActivity()).unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }
}
