package swiewiora.ttsnotifier;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Build;
import android.preference.PreferenceManager;
import android.provider.Settings;

/**
 * Created by Seba on 2017-12-08.
 */

public class Common {
    private static SharedPreferences prefs;
    /** Preference key name. */
    static final String
            KEY_SPEAK_SCREEN_OFF = "speakScreenOff",
            KEY_SPEAK_SCREEN_ON = "speakScreenOn",
            KEY_SPEAK_HEADSET_OFF = "speakHeadsetOff",
            KEY_SPEAK_HEADSET_ON = "speakHeadsetOn",
            KEY_SPEAK_SILENT_ON = "speakSilentOn";

    /**
     * Initializes default {@link SharedPreferences} and {@link Database} if needed and sets volume control stream.
     */
    static void init(Activity activity) {
        init(activity.getApplicationContext());
        setVolumeStream(activity);
    }

    /**
     * Initializes default {@link SharedPreferences} and {@link Database} if needed.
     */
    static void init(Context context) {
        if (prefs == null) {
            PreferenceManager.setDefaultValues(context, R.xml.pref_data_sync, true);
            PreferenceManager.setDefaultValues(context, R.xml.pref_device, true);
            PreferenceManager.setDefaultValues(context, R.xml.pref_general, true);
            PreferenceManager.setDefaultValues(context, R.xml.pref_notification, true);
            PreferenceManager.setDefaultValues(context, R.xml.pref_others, true);
            PreferenceManager.setDefaultValues(context, R.xml.pref_quiet_time, true);
            PreferenceManager.setDefaultValues(context, R.xml.pref_tts, true);
            prefs = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
//            convertOldStreamPref(context);
        }
        Database.init(context);
    }

    static Intent getNotificationListenerSettingsIntent() {
        return new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
    }

    /**
     * Sets the volume control stream defined in preferences.
     */
    static void setVolumeStream(Activity activity) {
        activity.setVolumeControlStream(getSelectedAudioStream(activity));
    }

    /**
     * @param c Context used to get the preference key name from resources.
     * @return The selected audio stream matching the STREAM_ constant from {@link AudioManager}.
     */
    static int getSelectedAudioStream(Context c) {
        return Integer.parseInt(prefs.getString(c.getString(R.string.key_ttsStream),
                Integer.toString(AudioManager.STREAM_MUSIC)));
    }

    /**
     * @param context Context used to get a default {@link SharedPreferences} instance if we don't already have one.
     * @return A default {@link SharedPreferences} instance.
     */
    static SharedPreferences getPrefs(Context context) {
        if (prefs == null) {
            prefs = PreferenceManager.getDefaultSharedPreferences(context);
        }
        return prefs;
    }
}