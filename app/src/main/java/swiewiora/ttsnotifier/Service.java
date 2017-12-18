package swiewiora.ttsnotifier;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.IllegalFormatException;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by Seba on 2017-12-07.
 */

public class Service extends NotificationListenerService {
    private static final String TAG = Service.class.getSimpleName();
    private final Map<App, String> lastMsg = new HashMap<>();
    private final Map<App, Long> lastMsgTime = new HashMap<>();
    private TextToSpeech mTts;
    private boolean shouldRequestFocus;
    private AudioManager audioMan;
    private TelephonyManager telephony;
    private final DeviceStateReceiver stateReceiver = new DeviceStateReceiver();
    private RepeatTimer repeater;
    private Shake shake;
    private static boolean isInitialized, isSuspended, isScreenOn;
    private final ArrayList<String> ignoreReasons = new ArrayList<>(),
            repeatList = new ArrayList<>();
    /**
     * this is used to determine if we are the first, middle, or last thing to be spoken at the moment, for enabling/disabling shake and audio focus request
     * entries are added right before the call to speak and removed by onDone in the utteranceProgressListener
     * if the list is empty when we enqueue a message, we trigger shaking and audio focus requesting
     * if the list is empty when we finish speaking a message, we untrigger them.
     */
    private final LinkedBlockingQueue<Boolean> messageStatuses = new LinkedBlockingQueue<>();
    private final Handler handler = new Handler();
    private final OnStatusChangeListener statusListener = new OnStatusChangeListener() {
        @Override
        public void onStatusChanged() {
            if (isSuspended && mTts != null) mTts.stop();
        }
    };

    @Override
    public void onCreate() {
        mTts = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.ERROR) {
                    Log.w(TAG, getString(R.string.error_tts_init));
                    Toast.makeText(getApplicationContext(), R.string.error_tts_init, Toast.LENGTH_LONG).show();
                    return;
                }
                mTts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override
                    public void onStart(String utteranceId) {
                    }

                    @Override
                    public void onDone(String utteranceId) {
                        messageStatuses.poll();
                        if (messageStatuses.isEmpty()) {
                            if (shouldRequestFocus) {
                                audioMan.abandonAudioFocus(null);
                            }
                            shake.disable();
                        }
                    }

                    @Override
                    public void onError(String utteranceId) {
                        Log.w(TAG, getString(R.string.error_tts_init));
                        Toast.makeText(getApplicationContext(), R.string.error_tts_init, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
        super.onCreate();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        Notification notification = sbn.getNotification();
        long msgTime = System.currentTimeMillis();
        String ticker = notification.tickerText != null ? notification.tickerText.toString() : null;
        @SuppressLint("InlinedApi") Bundle extras = notification.extras;
        @SuppressLint("InlinedApi") String subtext = extras.getString(Notification.EXTRA_SUB_TEXT);
        @SuppressLint("InlinedApi") String contentTitle = extras.getString(Notification.EXTRA_TITLE);
        @SuppressLint("InlinedApi") String contentText = extras.getString(Notification.EXTRA_TEXT);
        @SuppressLint("InlinedApi") String contentInfoText = extras.getString(Notification.EXTRA_INFO_TEXT);
        final String ttsStringPref = Common.getPrefs(this).getString(getString(R.string.key_ttsString), "");
        App app = AppList.findOrAddApp(sbn.getPackageName(), this);
        String ttsUnformattedMsg = ttsStringPref
                .replace("#a", "%1$s") // App Label
                .replace("#t", "%2$s") // Ticker
                .replace("#s", "%3$s") // Subtext
                .replace("#c", "%4$s") // Content Title
                .replace("#m", "%5$s") // Content Text
                .replace("#i", "%6$s"); // Content Info Text
        String ttsMsg = null;
        try {
            ttsMsg = String.format(ttsUnformattedMsg,
                    app == null ? "" : app.getLabel(),
                    ticker == null ? "" : ticker.replaceAll("[|\\[\\]{}*<>]+", " "),
                    subtext == null ? "" : subtext,
                    contentTitle == null ? "" : contentTitle,
                    contentText == null ? "" : contentText,
                    contentInfoText == null ? "" : contentInfoText);
        } catch (IllegalFormatException e) {
            Log.w(TAG, "Error formatting custom TTS string!");
            e.printStackTrace();
        }
        if (app != null && (ttsMsg == null || ttsMsg.equals(app.getLabel()))) {
            ttsMsg = getString(R.string.notification_from, app.getLabel());
        }
        StringBuilder logBuilder = new StringBuilder();
        for (String s : new String[]{ticker, subtext, contentTitle, contentText, contentInfoText}) {
            if (!TextUtils.isEmpty(s)) {
                if (logBuilder.length() > 0) logBuilder.append("\n");
                logBuilder.append(s);
            }
        }
        NotificationList.addNotification(app, logBuilder.toString());
        final String[] ignoreStrings = Common.getPrefs(this).getString(getString(R.string.key_ignore_strings), "").toLowerCase().split("\n");
        boolean stringIgnored = false;
        for (String s : ignoreStrings) {
            if (s.length() != 0 && ttsMsg != null && ttsMsg.toLowerCase().contains(s)) {
                stringIgnored = true;
                break;
            }
        }
        if (isSuspended) {
            ignoreReasons.add(getString(R.string.reason_suspended));
        }
        if (app != null && !app.getEnabled()) {
            ignoreReasons.add(getString(R.string.reason_app));
        }
        if (stringIgnored) {
            ignoreReasons.add(getString(R.string.reason_string));
        }
        if (TextUtils.isEmpty(ttsMsg)
                && Common.getPrefs(this).getBoolean(getString(R.string.key_ignore_empty), false)) {
            ignoreReasons.add(getString(R.string.reason_empty_msg));
        }
        int ignoreRepeat = Integer.parseInt(Common.getPrefs(this).getString(getString(R.string.key_ignore_repeat), "-1"));
        if (lastMsg.containsKey(app)) {
            if (lastMsg.get(app).equals(ttsMsg) && (ignoreRepeat == -1 || msgTime - lastMsgTime.get(app) < ignoreRepeat * 1000)) {
                ignoreReasons.add(MessageFormat.format(getString(R.string.reason_identical), ignoreRepeat));
            }
        }
        if (ignoreReasons.isEmpty()) {
            int delay = Integer.parseInt(Common.getPrefs(this).getString(getString(R.string.key_ttsDelay), "0"));
            if (!isScreenOn()) {
                int interval = Integer.parseInt(Common.getPrefs(this).getString(getString(R.string.key_tts_repeat), "0"));
                if (interval > 0) {
                    repeatList.add(ttsMsg);
                    if (repeater == null) {
                        repeater = new RepeatTimer(interval);
                    }
                }
            }
            if (delay < 0) { // Just in case we get a weird value, don't want to try to make the Timer wait for negative time
                delay = 0;
            }
            int maxLength = Integer.parseInt(Common.getPrefs(this).getString(getString(R.string.key_max_length), "0"));
            final String msg;
            if (ttsMsg != null && maxLength > 0) {
                msg = ttsMsg.substring(0, Math.min(maxLength, ttsMsg.length()));
            } else {
                msg = ttsMsg;
            }
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            speak(msg, true);
                        }
                    });
                }
            }, delay * 1000L); // A delay of 0 works fine, and means that all speak calls anywhere are running in their own thread and not blocking.
            lastMsg.put(app, ttsMsg);
            lastMsgTime.put(app, msgTime);
        } else {
            String reasons = ignoreReasons.toString().replaceAll("[\\[\\]]", "");
            Log.i(TAG, "Notification from " + (app != null ? app.getLabel() : null) + " ignored for reason(s): " + reasons);
            NotificationList.setLastIgnore(reasons, true);
            ignoreReasons.clear();
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
    }

    /**
     * Sends msg to TTS if ignore condition is not met.
     * ALWAYS CALLED INSIDE A SPAWNED THREAD
     *
     * @param msg   The string to be spoken.
     * @param isNew True if the notification was just received, otherwise false if it is being repeated.
     */
    private void speak(final String msg, boolean isNew) {
        if (ignore(isNew)) return;
        if (messageStatuses.isEmpty()) { //if there are no messages in the queue, start up shake detection and audio focus requesting
            shake.enable();
            shouldRequestFocus = Common.getPrefs(getApplicationContext())
                    .getBoolean(getString(R.string.key_audio_focus), false);
            if (shouldRequestFocus) {
                AudioFocus.requestFocus(audioMan);
            }
        }
        //regardless, add the message to the queue, parallelling the TextToSpeech queue since we can't access it.
        try {
            messageStatuses.put(false);
        } catch (InterruptedException e) {
            //we had an error with too many threads trying to speak at once, cancel the whole thing before adding to the mess
            return;
        }
        //once the message is in our queue, send it to the real one with the necessary parameters
        final HashMap<String, String> ttsParams = new HashMap<>();
        //needed because we want to apply stream changes immediately
        ttsParams.put(TextToSpeech.Engine.KEY_PARAM_STREAM,
                Integer.toString(Common.getSelectedAudioStream(getApplicationContext())));
        //not used anywhere, but has to be set to get the UtteranceProgressListener to trigger its submethods
        ttsParams.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, Long.toString(System.currentTimeMillis()));
        mTts.speak(msg, TextToSpeech.QUEUE_ADD, ttsParams);
    }

    /**
     * Checks for any notification-independent ignore states.
     *
     * @param isNew True for a notification that was just received, otherwise false.
     * @return True if an ignore condition is met, false otherwise.
     */
    private boolean ignore(boolean isNew) {
        Calendar c = Calendar.getInstance();
        int calTime = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE),
                quietStart = Common.getPrefs(this).getInt(getString(R.string.key_quietStart), 0),
                quietEnd = Common.getPrefs(this).getInt(getString(R.string.key_quietEnd), 0);
        if ((quietStart < quietEnd & quietStart <= calTime & calTime < quietEnd)
                || (quietEnd < quietStart && (quietStart <= calTime || calTime < quietEnd))) {
            ignoreReasons.add(getString(R.string.reason_quiet));
        }
        if ((audioMan.getRingerMode() == AudioManager.RINGER_MODE_SILENT
                || audioMan.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE)
                && !Common.getPrefs(this).getBoolean(Common.KEY_SPEAK_SILENT_ON, false)) {
            ignoreReasons.add(getString(R.string.reason_silent));
        }
        if (telephony.getCallState() != TelephonyManager.CALL_STATE_IDLE) {
            ignoreReasons.add(getString(R.string.reason_call));
        }
        if (!isScreenOn() && !Common.getPrefs(this).getBoolean(Common.KEY_SPEAK_SCREEN_OFF, true)) {
            ignoreReasons.add(getString(R.string.reason_screen_off));
        }
        if (isScreenOn() && !Common.getPrefs(this).getBoolean(Common.KEY_SPEAK_SCREEN_ON, true)) {
            ignoreReasons.add(getString(R.string.reason_screen_on));
        }
        if (!isHeadsetOn() && !Common.getPrefs(this).getBoolean(Common.KEY_SPEAK_HEADSET_OFF, true)) {
            ignoreReasons.add(getString(R.string.reason_headset_off));
        }
        if (isHeadsetOn() && !Common.getPrefs(this).getBoolean(Common.KEY_SPEAK_HEADSET_ON, true)) {
            ignoreReasons.add(getString(R.string.reason_headset_on));
        }
        if (!ignoreReasons.isEmpty()) {
            String reasons = ignoreReasons.toString().replaceAll("[\\[\\]]", "");
            Log.i(TAG, "Notification ignored for reason(s): " + reasons);
            NotificationList.setLastIgnore(reasons, isNew);
            ignoreReasons.clear();
            return true;
        }
        return false;
    }

    private class RepeatTimer extends TimerTask {
        private RepeatTimer(int minuteInterval) {
            if (minuteInterval <= 0) return;
            long interval = minuteInterval * 60000L;
            new Timer().schedule(this, interval, interval);
        }

        @Override
        public void run() {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    for (String s : repeatList) {
                        speak(s, false);
                    }
                }
            });
        }

        @Override
        public boolean cancel() {
            repeater = null;
            return super.cancel();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (isInitialized) return super.onBind(intent);
        Common.init(this);
        audioMan = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        telephony = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        IntentFilter filter = new IntentFilter(Intent.ACTION_HEADSET_PLUG);
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(stateReceiver, filter);
        shake = new Shake(this);
        shake.setOnShakeListener(new Shake.OnShakeListener() {
            @Override
            public void onShake() {
                Log.i(TAG, "TTS silenced by shake");
                NotificationList.setLastIgnore(getString(R.string.reason_shake), false);
                if (mTts != null) mTts.stop();
            }
        });
        registerOnStatusChangeListener(statusListener);
        setInitialized(true);
        return super.onBind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (isInitialized) {
            if (mTts != null) mTts.stop();
            unregisterReceiver(stateReceiver);
            setInitialized(false);
            unregisterOnStatusChangeListener(statusListener);
        }
        return false;
    }

    @Override
    public void onDestroy() {
        if (mTts != null) {
            mTts.shutdown();
            mTts = null;
        }

        super.onDestroy();
    }

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
         *
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

    private boolean isScreenOn() {
        isScreenOn = CheckScreen.isScreenOn(this);
        return isScreenOn;
    }

    private static class CheckScreen {
        private static PowerManager powerMan;
        private static boolean isScreenOn(Context c) {
            if (powerMan == null) {
                powerMan = (PowerManager)c.getSystemService(Context.POWER_SERVICE);
            }
            return powerMan.isScreenOn();
        }
    }

    private static class AudioFocus {
        private static void abandonFocus(AudioManager audioMan) {
            audioMan.abandonAudioFocus(null);
        }

        private static void requestFocus(AudioManager audioMan) {
            audioMan.requestAudioFocus(null, AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
        }
    }

    private boolean isHeadsetOn() {
        return (audioMan.isBluetoothA2dpOn() || audioMan.isWiredHeadsetOn());
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

    private class DeviceStateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            boolean interruptIfIgnored = true;
            switch (action) {
                case Intent.ACTION_SCREEN_ON:
                    isScreenOn = true;
                    if (repeater != null) {
                        repeater.cancel();
                        repeatList.clear();
                    }
                    interruptIfIgnored = false;
                    break;
                case Intent.ACTION_SCREEN_OFF:
                    isScreenOn = false;
                    interruptIfIgnored = false;
                    break;
            }
            if (interruptIfIgnored && mTts != null && ignore(false)) {
                mTts.stop();
            }
        }
    }
}
