// AlarmService.java
package com.expoalarmmodule;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import com.expoalarmmodule.receivers.NotificationActionReceiver;

public class AlarmService extends Service {

    private static final String TAG = "AlarmService";

    // Static references to allow canceling the missed alarm timer from outside
    private static Runnable missedAlarmRunnable = null;
    private static Handler mainHandler = null;

    @Override
    public IBinder onBind(final Intent intent) {
        Log.d(TAG, "onBind called with intent: " + (intent != null ? intent.getExtras() : "null"));
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");
    }

    @Override
    public void onDestroy() {
        cancelMissedAlarmTimer(); // Always clean up on destroy
        super.onDestroy();
        Log.d(TAG, "Service destroyed");
    }

    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {
        Log.d(TAG, "onStartCommand called with intent: " + (intent != null ? intent.getExtras() : "null"));

        if (intent == null) {
            Log.e(TAG, "Intent is null, cannot proceed");
            return START_NOT_STICKY;
        }

        String alarmUid = intent.getStringExtra("ALARM_UID");
        int notificationId = intent.getIntExtra("NOTIFICATION_ID", -1);
        boolean vibrateOnly = intent.getBooleanExtra("VIBRATE_ONLY", false);

        /**
         * ==================================================
         * 🚨 FOREGROUND SERVICE REQUIREMENT (MANDATORY)
         * ==================================================
         * startForeground() MUST be called on ALL code paths
         * ==================================================
         */
        Notification foregroundNotification;

        if (vibrateOnly) {
            // Minimal notification for vibration-only mode
            foregroundNotification = Helper.getForegroundServiceNotification(this);
            startForeground(1, foregroundNotification);

            Log.d(TAG, "Started foreground service for vibrate-only mode");

            Manager.vibrate(this, 10_000);

            // Stop service after vibration
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                stopForeground(true);
                stopSelf();
            }, 10_000);

            return START_NOT_STICKY;
        }

        /**
         * ==================================================
         * 🔁 REGULAR ALARM FLOW (UNCHANGED)
         * ==================================================
         */

        if (Manager.getActiveAlarm() != null) {
            Log.w(TAG, "⚠️ Alarm " + alarmUid + " ignored because alarm "
                    + Manager.getActiveAlarm() + " is already active");
            return START_NOT_STICKY;
        }

        if (alarmUid == null || notificationId == -1) {
            Log.e(TAG, "ALARM_UID or NOTIFICATION_ID missing in intent extras");
            return START_NOT_STICKY;
        }

        Log.d(TAG, "Received ALARM_UID: " + alarmUid + ", NOTIFICATION_ID: " + notificationId);

        Alarm alarm = Storage.getAlarm(getApplicationContext(), alarmUid);
        if (alarm == null) {
            Log.e(TAG, "No alarm found for UID: " + alarmUid);
            return START_NOT_STICKY;
        }

        Log.d(TAG, "Alarm retrieved: " + Alarm.toJson(alarm));

        Notification notification = Helper.getAlarmNotification(this, alarm, notificationId);
        if (notification == null) {
            Log.e(TAG, "Failed to create notification for alarm");
            return START_NOT_STICKY;
        }

        // Cancel any previously running missed alarm timer
        cancelMissedAlarmTimer();

        ExpoAlarmModuleModule.triggerNotificationTapped(
                alarmUid,
                alarm.description,
                Helper.getTimeInZone(
                        alarm.date != null ? alarm.date.toString() : null,
                        alarm.timeZone
                ),
                Integer.toString(notificationId)
        );

        Manager.start(getApplicationContext(), alarmUid);

        // ✅ REQUIRED for foreground service
        startForeground(notificationId, notification);

        Log.d(TAG, "Foreground service started with notification ID: " + notificationId);

        final String finalAlarmUid = alarmUid;
        final int finalNotificationId = notificationId;

        missedAlarmRunnable = () -> {
            String active = Manager.getActiveAlarm();
            Log.w(TAG, "⏰ Checking for missed alarm: active=" + active + ", expected=" + finalAlarmUid);

            if (active != null && active.equals(finalAlarmUid)) {
                Log.w(TAG, "⏰ Alarm missed after 60 seconds: " + finalAlarmUid);
                ExpoAlarmModuleModule.triggerNotificationMissed(finalAlarmUid, alarm.description);

                NotificationActionReceiver.removeNotification(getApplicationContext(), finalNotificationId);
                Manager.stop(getApplicationContext());
                stopSelf();
            }

            missedAlarmRunnable = null;
            mainHandler = null;
        };

        mainHandler = new Handler(Looper.getMainLooper());
        mainHandler.postDelayed(missedAlarmRunnable, 60_000);

        return START_STICKY;
    }

    /**
     * Cancels the currently running 60-second missed alarm timer if any.
     * Safe to call multiple times.
     */
    public static void cancelMissedAlarmTimer() {
        if (mainHandler != null && missedAlarmRunnable != null) {
            mainHandler.removeCallbacks(missedAlarmRunnable);
            Log.d(TAG, "Missed alarm timer cancelled successfully");
            missedAlarmRunnable = null;
            mainHandler = null;
        }
    }
}