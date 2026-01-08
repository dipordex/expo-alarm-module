// AlarmReceiver.java
package com.expoalarmmodule.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import com.expoalarmmodule.Alarm;
import com.expoalarmmodule.AlarmService;
import com.expoalarmmodule.ExpoAlarmModuleModule;
import com.expoalarmmodule.Helper;
import com.expoalarmmodule.Storage;

public class AlarmReceiver extends BroadcastReceiver {

    private static final String TAG = "AlarmReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String alarmUid = intent.getStringExtra("ALARM_UID");
        int notificationId = intent.getIntExtra("NOTIFICATION_ID", -1);
        Log.d(TAG, "Received alarm broadcast for UID: " + alarmUid + ", Notification ID: " + notificationId);

        if (alarmUid == null || notificationId == -1) {
            Log.e(TAG, "Missing ALARM_UID or NOTIFICATION_ID in intent");
            return;
        }

        Alarm alarm = Storage.getAlarm(context, alarmUid);
        if (alarm == null) {
            Log.e(TAG, "No alarm found for UID: " + alarmUid);
            return;
        }

        if (alarm.isTaskAlarm) {
            Log.d(TAG, "Task alarm triggered: " + alarmUid);
            // Emit custom JS event
//            ExpoAlarmModuleModule.triggerTaskAlarm(alarm.uid);
            Intent serviceIntent = new Intent(context, AlarmService.class);
            serviceIntent.putExtra("ALARM_UID", alarmUid);
            serviceIntent.putExtra("VIBRATE_ONLY", true);
            context.startForegroundService(serviceIntent);
            return;
        }

        Intent serviceIntent = new Intent(context, AlarmService.class);
        serviceIntent.putExtra("ALARM_UID", alarmUid);
        serviceIntent.putExtra("NOTIFICATION_ID", notificationId);
        context.startForegroundService(serviceIntent);
        Log.d(TAG, "Started AlarmService for UID: " + alarmUid + ", Notification ID: " + notificationId);
    }
}