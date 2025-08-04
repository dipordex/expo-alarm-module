// Helper.java
package com.expoalarmmodule;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import com.expoalarmmodule.receivers.AlarmReceiver;
import com.expoalarmmodule.receivers.NotificationActionReceiver;
import java.util.Calendar;
import java.util.Date;

public class Helper {

    private static final String TAG = "AlarmHelper";

    static void scheduleAlarm(Context context, String alarmUid, long triggerAtMillis, int notificationID) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.putExtra("ALARM_UID", alarmUid);
        intent.putExtra("NOTIFICATION_ID", notificationID);

        PendingIntent pendingIntent = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                ? PendingIntent.getBroadcast(context, notificationID, intent, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT)
                : PendingIntent.getBroadcast(context, notificationID, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
        }

        Log.d(TAG, "Scheduled alarm " + alarmUid + " with ID " + notificationID + " for " + new Date(triggerAtMillis));
    }

    static void cancelAlarm(Context context, int notificationID) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, AlarmReceiver.class);
        PendingIntent pendingIntent = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                ? PendingIntent.getBroadcast(context, notificationID, intent, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT)
                : PendingIntent.getBroadcast(context, notificationID, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        alarmManager.cancel(pendingIntent);
        pendingIntent.cancel();
        Log.d(TAG, "Cancelled alarm with notification ID: " + notificationID);
    }

    static void sendNotification(Context context, Alarm alarm, int notificationID) {
        try {
            Notification notification = getAlarmNotification(context, alarm, notificationID);
            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.notify(notificationID, notification);
            Log.d(TAG, "Sent notification for alarm " + alarm.uid + " with ID " + notificationID);
        } catch (Exception e) {
            Log.e(TAG, "Failed to send notification for alarm " + alarm.uid, e);
        }
    }

    static Notification getAlarmNotification(Context context, Alarm alarm, int notificationID) {
        return getNotification(
                context,
                notificationID,
                alarm.uid,
                alarm.title,
                alarm.description,
                alarm.showDismiss,
                alarm.showSnooze,
                alarm.dismissText,
                alarm.snoozeText,
                alarm.sound,
                alarm.vibration,
                alarm.volumeLevel
        );
    }

    public static void cancelNotification(Context context, int notificationId) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.cancel(notificationId);
        Log.d(TAG, "Cancelled notification with ID: " + notificationId);
    }

    static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String id = context.getResources().getString(R.string.notification_channel_id);
            String name = context.getResources().getString(R.string.notification_channel_name);
            String description = context.getResources().getString(R.string.notification_channel_desc);
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(id, name, importance);
            channel.setDescription(description);
            channel.enableLights(true);
            channel.setLightColor(Color.RED);
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{100, 200, 300, 400, 500, 400, 300, 200, 400});
            NotificationManager notificationManager = ContextCompat.getSystemService(context, NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
            Log.d(TAG, "Created notification channel: " + channel.toString());
        } else {
            Log.d(TAG, "No need to create notification channel for SDK " + Build.VERSION.SDK_INT);
        }
    }

    protected static Notification getNotification(
            Context context,
            int id,
            String alarmUid,
            String title,
            String description,
            boolean showDismiss,
            boolean showSnooze,
            String dismissText,
            String snoozeText,
            String sound,
            boolean isVibration,
            int volumeLevel
    ) {
        Resources res = context.getResources();
        String packageName = context.getPackageName();
        int smallIconResId = res.getIdentifier("ic_launcher", "mipmap", packageName);
        String channelId = context.getResources().getString(R.string.notification_channel_id);

        PendingIntent pendingIntentDismiss = createActionIntent(context, alarmUid, id, "DISMISS_ACTION");

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(smallIconResId)
                .setContentTitle(title)
                .setContentText(description)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setOngoing(false)
                .setContentIntent(createOnClickedIntent(context, alarmUid, id))
                .setDeleteIntent(pendingIntentDismiss);

        if (volumeLevel > -1) {
            setAlarmVolume(context, volumeLevel);
        }

        if (sound != null && !sound.isEmpty()) {
            Uri soundUri = Uri.parse(sound);
            builder.setSound(soundUri);
        } else {
            builder.setSound(null);
        }

        if (isVibration) {
            long[] vibrationPattern = new long[]{0, 400, 200, 400};
            builder.setVibrate(vibrationPattern);
        } else {
            builder.setVibrate(null);
        }

        if (showDismiss) {
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, dismissText, pendingIntentDismiss);
        }

        if (showSnooze) {
            PendingIntent pendingIntentSnooze = createActionIntent(context, alarmUid, id, "SNOOZE_ACTION");
            builder.addAction(android.R.drawable.ic_menu_recent_history, snoozeText, pendingIntentSnooze);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            int largeIconResId = res.getIdentifier("ic_launcher", "mipmap", packageName);
            Bitmap largeIconBitmap = BitmapFactory.decodeResource(res, largeIconResId);
            if (largeIconResId != 0) builder.setLargeIcon(largeIconBitmap);
            builder.setCategory(NotificationCompat.CATEGORY_ALARM);
            builder.setColor(Color.BLUE);
        }

        return builder.build();
    }

    static void setAlarmVolume(Context context, int percentVolume) {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM);

            // Clamp to 0–100
            int percent = Math.max(0, Math.min(percentVolume, 100));

            // Convert percent to stream volume range
            int scaledVolume = (int) ((percent / 100.0f) * maxVolume);

            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, scaledVolume, AudioManager.FLAG_PLAY_SOUND);

            Log.d(TAG, "🔊 Alarm volume set to: " + scaledVolume + " / " + maxVolume + " (from " + percent + "%)");
        } else {
            Log.e(TAG, "❌ AudioManager is null, cannot set alarm volume");
        }
    }


    private static PendingIntent createOnClickedIntent(Context context, String alarmUid, int notificationID) {
        Intent resultIntent = new Intent(context, Helper.getMainActivityClass(context));
        resultIntent.putExtra("ALARM_UID", alarmUid);
        return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                ? PendingIntent.getActivity(context, notificationID, resultIntent, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT)
                : PendingIntent.getActivity(context, notificationID, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static PendingIntent createActionIntent(Context context, String alarmUid, int notificationId, String actionReceived) {
        Intent intent = new Intent(context, NotificationActionReceiver.class);
        intent.setAction(actionReceived);
        intent.putExtra("NOTIFICATION_ID", notificationId);
        intent.putExtra("ALARM_UID", alarmUid);
        return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                ? PendingIntent.getBroadcast(context, notificationId, intent, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT)
                : PendingIntent.getBroadcast(context, notificationId, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    static Calendar getDate(int day, int hour, int minute) {
        Calendar date = Calendar.getInstance();
        Calendar today = Calendar.getInstance();
        date.set(Calendar.DAY_OF_WEEK, day);
        date.set(Calendar.HOUR_OF_DAY, hour);
        date.set(Calendar.MINUTE, minute);
        date.set(Calendar.SECOND, 0);
        date.set(Calendar.MILLISECOND, 0);
        if (date.before(today)) {
            date.add(Calendar.DATE, 7);
        }
        return date;
    }

    static Class getMainActivityClass(Context context) {
        String packageName = context.getPackageName();
        Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(packageName);
        try {
            String className = launchIntent.getComponent().getClassName();
            return Class.forName(className);
        } catch (Exception e) {
            Log.e(TAG, "Failed to get main activity class", e);
            return null;
        }
    }
}