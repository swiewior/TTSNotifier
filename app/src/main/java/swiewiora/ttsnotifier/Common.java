package swiewiora.ttsnotifier;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.provider.Settings;

/**
 * Created by Seba on 2017-12-08.
 */

public class Common {

    /**
     * Initializes default {@link SharedPreferences} and {@link Database} if needed and sets volume control stream.
     */
    static void init(Activity activity) {
        init(activity.getApplicationContext());
//        setVolumeStream(activity);
    }

    /**
     * Initializes default {@link SharedPreferences} and {@link Database} if needed.
     */
    static void init(Context context) {
//        if (prefs == null) {
//            PreferenceManager.setDefaultValues(context, R.xml.preferences, true);
//            prefs = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
//            convertOldStreamPref(context);
//        }
        Database.init(context);
    }

    static Intent getNotificationListenerSettingsIntent() {
        return new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
    }
}