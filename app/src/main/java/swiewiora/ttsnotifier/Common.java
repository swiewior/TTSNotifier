package swiewiora.ttsnotifier;

import android.content.Intent;
import android.provider.Settings;

/**
 * Created by Seba on 2017-12-08.
 */

public class Common {

    static Intent getNotificationListenerSettingsIntent() {
        return new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
    }
}