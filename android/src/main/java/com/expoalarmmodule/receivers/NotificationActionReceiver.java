// NotificationActionReceiver.java
package com.expoalarmmodule.receivers;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.expoalarmmodule.AlarmService;
import com.expoalarmmodule.ExpoAlarmModuleModule;
import com.expoalarmmodule.Manager;
import com.expoalarmmodule.Helper;

public class NotificationActionReceiver extends BroadcastReceiver {

    private static final String TAG = "AlarmNotificationActionReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {

        if (intent == null || intent.getAction() == null) {
            Log.e(TAG, "Intent or Action is null");
            return;
        }

        String action = intent.getAction();
        String alarmUid = intent.getStringExtra("ALARM_UID");
        int notificationId = intent.getIntExtra("NOTIFICATION_ID", -1);
        String title = intent.getStringExtra("ALARM_TITLE");
        String time = intent.getStringExtra("ALARM_TIME");

        if (alarmUid == null) {
            Log.e(TAG, "Alarm UID is missing!");
            return;
        }

        switch (action) {
            case "CLICK_ACTION":
                Log.d(TAG, "Received CLICK action for Alarm: " + alarmUid);

                // Cancel notification on tap (if desired)
                cancelNotification(context, notificationId);

                // Emit event to JS
                ExpoAlarmModuleModule.triggerNotificationTapped(
                        alarmUid,
                        title,
                        time,
                        Integer.toString(notificationId)
                );

                // Open the app
                Intent resultIntent = new Intent(context, Helper.getMainActivityClass(context));
                resultIntent.putExtra("ALARM_UID", alarmUid);
                resultIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                context.startActivity(resultIntent);

                break;

            case "DISMISS_ACTION":
                Log.d(TAG, "Received DISMISS action for Alarm: " + alarmUid);

                // Critical: Cancel the 60-second missed alarm timer
                AlarmService.cancelMissedAlarmTimer();

                Manager.stop(context);

                // Emit dismiss event to JS
                ExpoAlarmModuleModule.triggerNotificationDissmissTapped(alarmUid, title, time);

                // Clean up notification and service
                cancelNotification(context, notificationId);
                stopAlarmService(context);

                break;

            case "SNOOZE_ACTION":
                Log.d(TAG, "Received SNOOZE action for Alarm: " + alarmUid);

                // Critical: Cancel the 60-second missed alarm timer
                AlarmService.cancelMissedAlarmTimer();

                Manager.snooze(context);

                // Emit snooze event to JS
                ExpoAlarmModuleModule.triggerNotificationSnoozeTapped(alarmUid, title, time);

                // Clean up notification and service
                cancelNotification(context, notificationId);
                stopAlarmService(context);

                break;

            default:
                Log.e(TAG, "Unknown action received: " + action);
                break;
        }
    }

    /**
     * Cancels the notification using NotificationManager
     */
    private void cancelNotification(Context context, int notificationId) {
        if (notificationId != -1) {
            NotificationManager notificationManager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.cancel(notificationId);
            Log.d(TAG, "Notification cancelled: ID = " + notificationId);
        }
    }

    /**
     * Stops the foreground AlarmService
     */
    private void stopAlarmService(Context context) {
        Intent serviceIntent = new Intent(context, AlarmService.class);
        context.stopService(serviceIntent);
        Log.d(TAG, "AlarmService stopped");
    }

    /**
     * Public static helper for other classes (kept for backward compatibility if used elsewhere)
     */
    public static void removeNotification(Context context, int notificationId) {
        if (notificationId != -1) {
            NotificationManager notificationManager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.cancel(notificationId);
        }
        // Also stop the service for consistency
        Intent serviceIntent = new Intent(context, AlarmService.class);
        context.stopService(serviceIntent);
    }
}