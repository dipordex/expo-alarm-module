// AlarmService.java
package com.expoalarmmodule;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

public class AlarmService extends Service {

    private static final String TAG = "AlarmService";

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

        Manager.start(getApplicationContext(), alarmUid);
        startForeground(notificationId, notification);
        Log.d(TAG, "Foreground service started with notification ID: " + notificationId);

        return START_STICKY;
    }
}