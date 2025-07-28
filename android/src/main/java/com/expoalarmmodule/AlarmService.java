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
        super.onStartCommand(intent, flags, startId);
        Log.d(TAG, "onStartCommand called");

        if (intent == null) {
            Log.e(TAG, "Intent is null, cannot proceed");
            return START_NOT_STICKY;
        }

        String alarmUid = intent.getStringExtra("ALARM_UID");
        if (alarmUid == null) {
            Log.e(TAG, "ALARM_UID is missing in the intent extras");
            return START_NOT_STICKY;
        }

        Log.d(TAG, "Received ALARM_UID: " + alarmUid);
        Alarm alarm = Storage.getAlarm(getApplicationContext(), alarmUid);

        if (alarm == null) {
            Log.e(TAG, "No alarm found for UID: " + alarmUid);
            return START_NOT_STICKY;
        }

        Log.d(TAG, "Alarm retrieved: " + Alarm.toJson(alarm));

        Notification notification = Helper.getAlarmNotification(this, alarm, 1);
        if (notification == null) {
            Log.e(TAG, "Failed to create notification for alarm");
            return START_NOT_STICKY;
        }

        Manager.start(getApplicationContext(), alarmUid);
        startForeground(1, notification);
        Log.d(TAG, "Foreground service started with notification");

        return START_STICKY;
    }
}
