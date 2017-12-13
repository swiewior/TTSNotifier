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
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v7.app.ActionBar;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.RingtonePreference;
import android.text.TextUtils;
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
    private static Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = new Preference.OnPreferenceChangeListener() {
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

            } else if (preference instanceof RingtonePreference) {
                // For ringtone preferences, look up the correct display value
                // using RingtoneManager.
                if (TextUtils.isEmpty(stringValue)) {
                    // Empty values correspond to 'silent' (no ringtone).
                    preference.setSummary(R.string.pref_ringtone_silent);

                } else {
                    Ringtone ringtone = RingtoneManager.getRingtone(
                            preference.getContext(), Uri.parse(stringValue));

                    if (ringtone == null) {
                        // Clear the summary if there was a lookup error.
                        preference.setSummary(null);
                    } else {
                        // Set the summary to reflect the new ringtone display
                        // name.
                        String name = ringtone.getTitle(preference.getContext());
                        preference.setSummary(name);
                    }
                }

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
     * This fragment shows general preferences only. It is used when the
     * activity is showing a two-pane settings UI.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class GeneralPreferenceFragment extends PreferenceFragment
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

    /**
     * This fragment shows notification preferences only. It is used when the
     * activity is showing a two-pane settings UI.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class TTSPreferenceFragment extends PreferenceFragment
            implements SharedPreferences.OnSharedPreferenceChangeListener {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_tts);
            setHasOptionsMenu(true);

            Preference pTTS = findPreference(getString(R.string.key_ttsSettings));
            Intent ttsIntent = getTtsIntent();
            if (ttsIntent != null) {
                pTTS.setIntent(ttsIntent);
            } else {
                pTTS.setEnabled(false);
                pTTS.setSummary(R.string.tts_settings_summary_fail);
            }
            EditTextPreference pTtsString = (EditTextPreference)findPreference(getString(R.string.key_ttsString));
            if (pTtsString.getText().contains("%")) {
                Toast.makeText(getActivity(), R.string.tts_message_reset_default, Toast.LENGTH_LONG).show();
                pTtsString.setText(getString(R.string.ttsString_default_value));
            }
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

        private Intent getTtsIntent() {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            if (checkActivityExist("com.android.settings.TextToSpeechSettings")) {
                intent.setClassName("com.android.settings", "com.android.settings.TextToSpeechSettings");
            } else if (checkActivityExist("com.android.settings.Settings$TextToSpeechSettingsActivity")) {
                intent.setClassName("com.android.settings", "com.android.settings.Settings$TextToSpeechSettingsActivity");
            } else if (checkActivityExist("com.google.tv.settings.TextToSpeechSettingsTop")) {
                intent.setClassName("com.google.tv.settings", "com.google.tv.settings.TextToSpeechSettingsTop");
            } else return null;
            return intent;
        }

        private boolean checkActivityExist(String name) {
            try {
                PackageInfo pkgInfo = getActivity().getPackageManager().getPackageInfo(
                        name.substring(0, name.lastIndexOf(".")), PackageManager.GET_ACTIVITIES);
                if (pkgInfo.activities != null) {
                    for (int n = 0; n < pkgInfo.activities.length; n++) {
                        if (pkgInfo.activities[n].name.equals(name)) return true;
                    }
                }
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
            return false;
        }

        public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
            if (key.equals(getString(R.string.key_ttsStream))) {
                Common.setVolumeStream(getActivity());
            }
        }
    }

    /**
     * This fragment shows notification preferences only. It is used when the
     * activity is showing a two-pane settings UI.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class NotificationPreferenceFragment extends PreferenceFragment
            implements SharedPreferences.OnSharedPreferenceChangeListener {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_notification);
            setHasOptionsMenu(true);

            // Bind the summaries of EditText/List/Dialog/Ringtone preferences
            // to their values. When their values change, their summaries are
            // updated to reflect the new value, per the Android Design
            // guidelines.
//            bindPreferenceSummaryToValue(findPreference("notifications_new_message_ringtone"));
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
     * This fragment shows notification preferences only. It is used when the
     * activity is showing a two-pane settings UI.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class OthersPreferenceFragment extends PreferenceFragment {
//            implements Preference.OnPreferenceClickListener,
//            SharedPreferences.OnSharedPreferenceChangeListener {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            Common.init(getActivity());
            addPreferencesFromResource(R.xml.pref_others);
            setHasOptionsMenu(true);

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
    }

    public static class MyDialog extends DialogFragment {
        private static final String KEY_ID = "id";

        private enum ID {
            DEVICE_STATE,
            QUIET_START,
            QUIET_END,
            LOG,
            SUPPORT,
            DONATE,
            WALLET,
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

        /**
         * @return The intent for Google Wallet, otherwise null if installation is not found.
         */
        private Intent getWalletIntent() {
            String walletPackage = "com.google.android.apps.gmoney";
            PackageManager pm = getActivity().getPackageManager();
            try {
                pm.getPackageInfo(walletPackage, PackageManager.GET_ACTIVITIES);
                return pm.getLaunchIntentForPackage(walletPackage);
            } catch (PackageManager.NameNotFoundException e) {
                return null;
            }
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
                case SUPPORT:
                    return new AlertDialog.Builder(getActivity())
                            .setTitle(R.string.support)
                            .setItems(R.array.support_items, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int item) {
                                    switch (item) {
                                        case 0: // Donate
                                            MyDialog.show(getFragmentManager(), ID.DONATE);
                                            break;
                                        case 1: // Rate/Comment
                                            Intent iMarket = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.pilot51.voicenotify"));
                                            iMarket.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                            try {
                                                startActivity(iMarket);
                                            } catch (ActivityNotFoundException e) {
                                                e.printStackTrace();
                                                Toast.makeText(getActivity(), R.string.error_market, Toast.LENGTH_LONG).show();
                                            }
                                            break;
                                        case 2: // Contact developer
                                            Intent iEmail = new Intent(Intent.ACTION_SEND);
                                            iEmail.setType("plain/text");
                                            iEmail.putExtra(Intent.EXTRA_EMAIL, new String[] {getString(R.string.dev_email)});
                                            iEmail.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.email_subject));
                                            String version = null;
                                            try {
                                                version = getActivity().getPackageManager().getPackageInfo(getActivity().getPackageName(), 0).versionName;
                                            } catch (PackageManager.NameNotFoundException e) {
                                                e.printStackTrace();
                                            }
                                            iEmail.putExtra(Intent.EXTRA_TEXT,
                                                    getString(R.string.email_body,
                                                            version,
                                                            Build.VERSION.RELEASE,
                                                            Build.ID,
                                                            Build.MANUFACTURER + " " + Build.BRAND + " " + Build.MODEL));
                                            try {
                                                startActivity(iEmail);
                                            } catch (ActivityNotFoundException e) {
                                                e.printStackTrace();
                                                Toast.makeText(getActivity(), R.string.error_email, Toast.LENGTH_LONG).show();
                                            }
                                            break;
                                        case 3: // Translations
                                            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://getlocalization.com/voicenotify")));
                                            break;
                                        case 4: // Source Code
                                            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/pilot51/voicenotify")));
                                            break;
                                    }
                                }
                            }).create();
                case DONATE:
                    return new AlertDialog.Builder(getActivity())
                            .setTitle(R.string.donate)
                            .setItems(R.array.donate_services, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int item) {
                                    switch (item) {
                                        case 0: // Google Wallet
                                            MyDialog.show(getFragmentManager(), ID.WALLET);
                                            break;
                                        case 1: // PayPal
                                            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://paypal.com/cgi-bin/webscr?"
                                                    + "cmd=_donations&business=pilota51%40gmail%2ecom&lc=US&item_name=Voice%20Notify&"
                                                    + "no_note=0&no_shipping=1&currency_code=USD")));
                                            break;
                                    }
                                }
                            }).create();
                case WALLET:
                    final Intent walletIntent = getWalletIntent();
                    AlertDialog.Builder dlg = new AlertDialog.Builder(getActivity())
                            .setTitle(R.string.donate_wallet_title)
                            .setMessage(R.string.donate_wallet_message)
                            .setNegativeButton(android.R.string.cancel, null);
                    if (walletIntent != null) {
                        dlg.setPositiveButton(R.string.donate_wallet_launch_app, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                startActivity(walletIntent);
                            }
                        });
                    } else {
                        dlg.setPositiveButton(R.string.donate_wallet_launch_web, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://wallet.google.com")));
                            }
                        });
                    }
                    return dlg.create();
            }
            return null;
        }
    }
}
