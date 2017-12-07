package swiewiora.ttsnotifier;

import android.service.notification.NotificationListenerService;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;

/**
 * Created by Seba on 2017-12-07.
 */

public class Service extends NotificationListenerService {
    private static boolean isInitialized, isSuspended;

    private static final ArrayList<OnStatusChangeListener> statusListeners = new ArrayList<>();

    static void registerOnStatusChangeListener(OnStatusChangeListener listener) {
        statusListeners.add(listener);
    }
    static void unregisterOnStatusChangeListener(OnStatusChangeListener listener) {
        statusListeners.remove(listener);
    }

    interface OnStatusChangeListener {
        /**
         * Called when the service status has changed.
         * @see Service#isRunning()
         * @see Service#isSuspended()
         */
        void onStatusChanged();
    }

    private static void onStatusChanged() {
        for (OnStatusChangeListener l : statusListeners) {
            l.onStatusChanged();
        }
    }

    static boolean isRunning() {
        return isInitialized;
    }

    private void setInitialized(boolean initialized) {
        isInitialized = initialized;
        onStatusChanged();
    }

    static boolean isSuspended() {
        return isSuspended;
    }

    static boolean toggleSuspend() {
        isSuspended ^= true;
        onStatusChanged();
        return isSuspended;
    }
}
