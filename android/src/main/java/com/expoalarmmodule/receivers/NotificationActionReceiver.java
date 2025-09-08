package com.expoalarmmodule.receivers;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.expoalarmmodule.ExpoAlarmModuleModule;
import com.expoalarmmodule.Manager;
import com.expoalarmmodule.Helper;
import com.expoalarmmodule.AlarmService;
import com.facebook.react.bridge.ReactApplicationContext;

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
                removeNotification(context, notificationId);

                // Send event to JS
                ExpoAlarmModuleModule.triggerNotificationTapped(
                        alarmUid,
                        title,time
                );

                // Optionally remove the notification
                if (notificationId != -1) {
                    Helper.cancelNotification(context, notificationId);
                }
                // Start main activity to open the app
                Intent resultIntent = new Intent(context, Helper.getMainActivityClass(context));
                resultIntent.putExtra("ALARM_UID", alarmUid);

                // IMPORTANT: set flags so it works from background
                resultIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

                context.startActivity(resultIntent);

                break;

            case "DISMISS_ACTION":
                Log.d(TAG, "Received DISMISS action for Alarm: " + alarmUid);
                Manager.stop(context);
                // Send event to JS
                ExpoAlarmModuleModule.triggerNotificationDissmissTapped(
                        alarmUid,
                        title,time
                );
                this.removeNotification(context, notificationId);
                break;

            case "SNOOZE_ACTION":
                Log.d(TAG, "Received SNOOZE action for Alarm: " + alarmUid);
                Manager.snooze(context);
                // Send event to JS
                ExpoAlarmModuleModule.triggerNotificationSnoozeTapped(
                        alarmUid,
                        title, time
                );
                this.removeNotification(context, notificationId);
                break;

            default:
                Log.e(TAG, "Unknown action received: " + action);
                break;
        }
    }

    private void removeNotification(Context context, int notificationId) {
        Intent serviceIntentSnooze = new Intent(context, AlarmService.class);
        context.stopService(serviceIntentSnooze);
        if (notificationId != -1) {
            ((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE))
                .cancel(notificationId);
        }
    }

}
