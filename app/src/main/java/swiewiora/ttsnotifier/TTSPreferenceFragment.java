package swiewiora.ttsnotifier;

import android.annotation.TargetApi;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.view.MenuItem;
import android.widget.Toast;

/**
 * This fragment shows notification preferences only. It is used when the
 * activity is showing a two-pane settings UI.
 */
@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class TTSPreferenceFragment extends PreferenceFragment
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
