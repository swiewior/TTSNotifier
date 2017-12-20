package swiewiora.ttsnotifier;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.support.v4.app.NotificationCompat;
import android.support.v7.app.ActionBar;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.view.MenuItem;
import android.widget.TimePicker;
import android.widget.Toast;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * A {@link PreferenceActivity} that presents a set of application settings. On
 * handset devices, settings are presented as a single list. On tablets,
 * settings are split by category, with category headers shown to the left of
 * the list of settings.
 * <p>
 * See <a href="http://developer.android.com/design/patterns/settings.html">
 * Android Design: Settings</a> for design guidelines and the <a
 * href="http://developer.android.com/guide/topics/ui/settings.html">Settings
 * API Guide</a> for more information on developing a Settings UI.
 */
public class MainActivity extends AppCompatPreferenceActivity {

    /**
     * A preference value change listener that updates the preference's summary
     * to reflect its new value.
     */
    private static Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener
            = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            String stringValue = value.toString();

            if (preference instanceof ListPreference) {
                // For list preferences, look up the correct display value in
                // the preference's 'entries' list.
                ListPreference listPreference = (ListPreference) preference;
                int index = listPreference.findIndexOfValue(stringValue);

                // Set the summary to reflect the new value.
                preference.setSummary(
                        index >= 0
                        ? listPreference.getEntries()[index]
                        : null);
            } else {
                // For all other preferences, set the summary to the value's
                // simple string representation.
                preference.setSummary(stringValue);
            }
            return true;
        }
    };

    /**
     * Helper method to determine if the device has an extra-large screen. For
     * example, 10" tablets are extra-large.
     */
    private static boolean isXLargeTablet(Context context) {
        return (context.getResources().getConfiguration().screenLayout
            & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_XLARGE;
    }

    /**
     * Binds a preference's summary to its value. More specifically, when the
     * preference's value is changed, its summary (line of text below the
     * preference title) is updated to reflect the value. The summary is also
     * immediately updated upon calling this method. The exact display format is
     * dependent on the type of preference.
     *
     * @see #sBindPreferenceSummaryToValueListener
     */
    private static void bindPreferenceSummaryToValue(Preference preference) {
        // Set the listener to watch for value changes.
        preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);

        // Trigger the listener immediately with the preference's
        // current value.
        sBindPreferenceSummaryToValueListener.onPreferenceChange(preference,
                PreferenceManager
                        .getDefaultSharedPreferences(preference.getContext())
                        .getString(preference.getKey(), ""));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupActionBar();
    }

    /**
     * Set up the {@link android.app.ActionBar}, if the API is available.
     */
    private void setupActionBar() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            // Show the Up button in the action bar.
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onIsMultiPane() {
        return isXLargeTablet(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void onBuildHeaders(List<Header> target) {
        loadHeadersFromResource(R.xml.pref_headers, target);
    }

    /**
     * This method stops fragment injection in malicious applications.
     * Make sure to deny any unknown fragments here.
     */
    protected boolean isValidFragment(String fragmentName) {
        return PreferenceFragment.class.getName().equals(fragmentName)
        || GeneralPreferenceFragment.class.getName().equals(fragmentName)
        || TTSPreferenceFragment.class.getName().equals(fragmentName)
        || NotificationPreferenceFragment.class.getName().equals(fragmentName)
        || DeviceSettingsPreferenceFragment.class.getName().equals(fragmentName)
        || QuietTimePreferenceFragment.class.getName().equals(fragmentName)
        || OthersPreferenceFragment.class.getName().equals(fragmentName);
    }

    /**
     * This fragment shows notification preferences only. It is used when the
     * activity is showing a two-pane settings UI.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class DeviceSettingsPreferenceFragment extends PreferenceFragment
            implements Preference.OnPreferenceClickListener {
        private Preference pDeviceState;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            Common.init(getActivity());
            addPreferencesFromResource(R.xml.pref_device);
            setHasOptionsMenu(true);

            pDeviceState = findPreference(getString(R.string.key_device_state));
            pDeviceState.setOnPreferenceClickListener(this);
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

        @Override
        public boolean onPreferenceClick(Preference preference) {
            if (preference == pDeviceState) {
                MyDialog.show(getFragmentManager(), MyDialog.ID.DEVICE_STATE);
                return true;
            }
            return false;
        }
    }

    /**
     * This fragment shows quiet time preferences only. It is used when the
     * activity is showing a two-pane settings UI.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class QuietTimePreferenceFragment extends PreferenceFragment
            implements Preference.OnPreferenceClickListener {
        private Preference pQuietStart, pQuietEnd;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);Common.init(getActivity());
            Common.init(getActivity());
            addPreferencesFromResource(R.xml.pref_quiet_time);
            setHasOptionsMenu(true);

            pQuietStart = findPreference(getString(R.string.key_quietStart));
            pQuietStart.setOnPreferenceClickListener(this);
            pQuietEnd = findPreference(getString(R.string.key_quietEnd));
            pQuietEnd.setOnPreferenceClickListener(this);
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

        @Override
        public boolean onPreferenceClick(Preference preference) {
            if (preference == pQuietStart) {
                MyDialog.show(getFragmentManager(), MyDialog.ID.QUIET_START);
                return true;
            } else if (preference == pQuietEnd) {
                MyDialog.show(getFragmentManager(), MyDialog.ID.QUIET_END);
                return true;
            }
            return false;
        }
    }

    /**
     * This fragment shows other preferences only. It is used when the
     * activity is showing a two-pane settings UI.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class OthersPreferenceFragment extends PreferenceFragment
            implements Preference.OnPreferenceClickListener {
        private Preference pTest, pNotifyLog;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            Common.init(getActivity());
            addPreferencesFromResource(R.xml.pref_others);
            setHasOptionsMenu(true);

            pTest = findPreference(getString(R.string.key_test));
            pTest.setOnPreferenceClickListener(this);
            pNotifyLog = findPreference(getString(R.string.key_notify_log));
            pNotifyLog.setOnPreferenceClickListener(this);
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

        @Override
        public boolean onPreferenceClick(Preference preference) {
            if (preference == pTest) {
                if (!AppList.findOrAddApp(getActivity().getPackageName(), getActivity()).getEnabled()) {
                    Toast.makeText(getActivity(), getString(R.string.test_ignored), Toast.LENGTH_LONG).show();
                }
                final NotificationManager notificationManager = (NotificationManager)getActivity().getSystemService(Context.NOTIFICATION_SERVICE);
                if (notificationManager != null) {
                    new Timer().schedule(new TimerTask() {
                        @Override
                        public void run() {
                            String id = "test";
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                NotificationChannel channel = notificationManager.getNotificationChannel(id);
                                if (channel == null) {
                                    channel = new NotificationChannel(id, getString(R.string.test), NotificationManager.IMPORTANCE_LOW);
                                    channel.setDescription(getString(R.string.notification_channel_desc));
                                    notificationManager.createNotificationChannel(channel);
                                }
                            }
                            PendingIntent pi = PendingIntent.getActivity(getActivity(),
                                    0, getActivity().getIntent(), PendingIntent.FLAG_UPDATE_CURRENT);
                            NotificationCompat.Builder builder =
                                    new NotificationCompat.Builder(getActivity(), id)
                                            .setAutoCancel(true)
                                            .setContentIntent(pi)
                                            .setSmallIcon(R.drawable.icon)
                                            .setTicker(getString(R.string.test_ticker))
                                            .setSubText(getString(R.string.test_subtext))
                                            .setContentTitle(getString(R.string.test_content_title))
                                            .setContentText(getString(R.string.test_content_text))
                                            .setContentInfo(getString(R.string.test_content_info));
                            notificationManager.notify(0, builder.build());
                        }
                    }, 5000);
                }
                return true;
            } else if (preference == pNotifyLog) {
                MyDialog.show(getFragmentManager(), MyDialog.ID.LOG);
                return true;
            }
            return false;
        }
    }

    public static class MyDialog extends DialogFragment {
        private static final String KEY_ID = "id";

        private enum ID {
            DEVICE_STATE,
            QUIET_START,
            QUIET_END,
            LOG
        }

        private final TimePickerDialog.OnTimeSetListener sTimeSetListener = new TimePickerDialog.OnTimeSetListener() {
            public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                Common.getPrefs(getActivity()).edit().putInt(getString(R.string.key_quietStart), hourOfDay * 60 + minute).apply();
            }
        };
        private final TimePickerDialog.OnTimeSetListener eTimeSetListener = new TimePickerDialog.OnTimeSetListener() {
            public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                Common.getPrefs(getActivity()).edit().putInt(getString(R.string.key_quietEnd), hourOfDay * 60 + minute).apply();
            }
        };

        public MyDialog() {}

        private static void show(FragmentManager fm, ID id) {
            Bundle bundle = new Bundle();
            bundle.putSerializable(KEY_ID, id);
            MyDialog dialogFragment = new MyDialog();
            dialogFragment.setArguments(bundle);
            dialogFragment.show(fm, id.name());
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            ID id = (ID)getArguments().getSerializable(KEY_ID);
            switch (id) {
                case DEVICE_STATE:
                    final CharSequence[] items = getResources().getStringArray(R.array.device_states);
                    return new AlertDialog.Builder(getActivity())
                            .setTitle(R.string.device_state_dialog_title)
                            .setMultiChoiceItems(items,
                                    new boolean[] {
                                            Common.getPrefs(getActivity()).getBoolean(Common.KEY_SPEAK_SCREEN_OFF, true),
                                            Common.getPrefs(getActivity()).getBoolean(Common.KEY_SPEAK_SCREEN_ON, true),
                                            Common.getPrefs(getActivity()).getBoolean(Common.KEY_SPEAK_HEADSET_OFF, true),
                                            Common.getPrefs(getActivity()).getBoolean(Common.KEY_SPEAK_HEADSET_ON, true),
                                            Common.getPrefs(getActivity()).getBoolean(Common.KEY_SPEAK_SILENT_ON, false)
                                    },
                                    new DialogInterface.OnMultiChoiceClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                                            if (which == 0) { // Screen off
                                                Common.getPrefs(getActivity()).edit().putBoolean(Common.KEY_SPEAK_SCREEN_OFF, isChecked).apply();
                                            } else if (which == 1) { // Screen on
                                                Common.getPrefs(getActivity()).edit().putBoolean(Common.KEY_SPEAK_SCREEN_ON, isChecked).apply();
                                            } else if (which == 2) { // Headset off
                                                Common.getPrefs(getActivity()).edit().putBoolean(Common.KEY_SPEAK_HEADSET_OFF, isChecked).apply();
                                            } else if (which == 3) { // Headset on
                                                Common.getPrefs(getActivity()).edit().putBoolean(Common.KEY_SPEAK_HEADSET_ON, isChecked).apply();
                                            } else if (which == 4) { // Silent/vibrate
                                                Common.getPrefs(getActivity()).edit().putBoolean(Common.KEY_SPEAK_SILENT_ON, isChecked).apply();
                                            }
                                        }
                                    }
                            ).create();
                case QUIET_START:
                    int quietStart = Common.getPrefs(getActivity()).getInt(getString(R.string.key_quietStart), 0);
                    return new TimePickerDialog(getActivity(), sTimeSetListener,
                            quietStart / 60, quietStart % 60, true);
                case QUIET_END:
                    int quietEnd = Common.getPrefs(getActivity()).getInt(getString(R.string.key_quietEnd), 0);
                    return new TimePickerDialog(getActivity(), eTimeSetListener,
                            quietEnd / 60, quietEnd % 60, true);
                case LOG:
                    return new AlertDialog.Builder(getActivity())
                            .setTitle(R.string.notify_log)
                            .setView(new NotificationList(getActivity()))
                            .setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    dialog.dismiss();
                                }
                            })
                            .create();
            }
            return null;
        }
    }
}
