// Helper.java
package com.expoalarmmodule;

import android.app.ActivityManager;
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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

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

//    static void createNotificationChannel(Context context) {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            String id = context.getResources().getString(R.string.notification_channel_id);
//            String name = context.getResources().getString(R.string.notification_channel_name);
//            String description = context.getResources().getString(R.string.notification_channel_desc);
//            int importance = NotificationManager.IMPORTANCE_HIGH;
//            NotificationChannel channel = new NotificationChannel(id, name, importance);
//            channel.setDescription(description);
//            channel.enableLights(true);
//            channel.setLightColor(Color.RED);
//            channel.enableVibration(true);
//            channel.setVibrationPattern(new long[]{100, 200, 300, 400, 500, 400, 300, 200, 400});
//            NotificationManager notificationManager = ContextCompat.getSystemService(context, NotificationManager.class);
//            notificationManager.createNotificationChannel(channel);
//            Log.d(TAG, "Created notification channel: " + channel.toString());
//        } else {
//            Log.d(TAG, "No need to create notification channel for SDK " + Build.VERSION.SDK_INT);
//        }
//    }

    static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = ContextCompat.getSystemService(context, NotificationManager.class);
            // Default importance (foreground)
            String defaultId = context.getString(R.string.notification_channel_id_default);
            NotificationChannel defaultChannel =
                    new NotificationChannel(defaultId, "Expo Alarms (Default)", NotificationManager.IMPORTANCE_DEFAULT);
            defaultChannel.setDescription("Alarms when app is in foreground");
            manager.createNotificationChannel(defaultChannel);

            // High importance (background/terminated)
            String highId = context.getString(R.string.notification_channel_id_high);
            NotificationChannel highChannel =
                    new NotificationChannel(highId, "Expo Alarms (High)", NotificationManager.IMPORTANCE_HIGH);
            highChannel.setDescription("Alarms when app is in background");
            manager.createNotificationChannel(highChannel);

            Log.d(TAG, "Created both notification channels: default + high");
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
//        String channelId = context.getResources().getString(R.string.notification_channel_id);
                String channelId;
        if (isAppInForeground(context)) {
            channelId = context.getString(R.string.notification_channel_id_default);
        } else {
            channelId = context.getString(R.string.notification_channel_id_high);
        }

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
        if (isAppInForeground(context)) {
            // Foreground: only status bar entry, no popup
            builder.setPriority(NotificationCompat.PRIORITY_LOW);
            builder.setDefaults(0); // no sound/vibration popup
        } else {
            // Background: show popup with sound/vibration
            builder.setPriority(NotificationCompat.PRIORITY_HIGH);
            builder.setDefaults(NotificationCompat.DEFAULT_ALL);
        }


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
            int percent = Math.max(0, Math.min(percentVolume, 101));

            // Convert percent to stream volume range
            int scaledVolume = (int) ((percent / 100.0f) * maxVolume);

            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, scaledVolume, AudioManager.FLAG_PLAY_SOUND);

            Log.d(TAG, "🔊 Alarm volume set to: " + scaledVolume + " / " + maxVolume + " (from " + percent + "%)");
        } else {
            Log.e(TAG, "❌ AudioManager is null, cannot set alarm volume");
        }
    }


//    private static PendingIntent createOnClickedIntent(Context context, String alarmUid, int notificationID) {
//        Intent resultIntent = new Intent(context, Helper.getMainActivityClass(context));
//        resultIntent.putExtra("ALARM_UID", alarmUid);
//        resultIntent.setAction("CLICK_ACTION");
//        Log.d("Notification clicked","hello");
//        return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
//                ? PendingIntent.getActivity(context, notificationID, resultIntent, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT)
//                : PendingIntent.getActivity(context, notificationID, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT);
//    }

     static PendingIntent createOnClickedIntent(Context context, String alarmUid, int notificationID) {
        Intent intent = new Intent(context, NotificationActionReceiver.class);
        intent.setAction("CLICK_ACTION");
        intent.putExtra("ALARM_UID", alarmUid);
        intent.putExtra("NOTIFICATION_ID", notificationID);
         Alarm alarm = Storage.getAlarm(context, alarmUid);
         Log.d("Notification is Alarm",alarm.description);
         intent.putExtra("ALARM_TITLE", alarm.description);
         intent.putExtra("ALARM_TIME", Helper.getTimeInZone(alarm.date.toString(), alarm.timeZone));

        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ? PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
                : PendingIntent.FLAG_UPDATE_CURRENT;

        return PendingIntent.getBroadcast(context, notificationID, intent, flags);
    }

    private static PendingIntent createActionIntent(Context context, String alarmUid, int notificationId, String actionReceived) {
        Intent intent = new Intent(context, NotificationActionReceiver.class);
        intent.setAction(actionReceived);
        intent.putExtra("NOTIFICATION_ID", notificationId);
        intent.putExtra("ALARM_UID", alarmUid);
        Alarm alarm = Storage.getAlarm(context, alarmUid);
        Log.d("Notification is Alarm",alarm.description);
        intent.putExtra("ALARM_TITLE", alarm.description);
        intent.putExtra("ALARM_TIME", Helper.getTimeInZone(alarm.date.toString(), alarm.timeZone));

        Log.d("Notification is tapped",actionReceived);
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

    public static Class getMainActivityClass(Context context) {
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

    public static String getTimeInZone(String alarmDay, String timeZone) {
        if (alarmDay == null || timeZone == null) return null;

        try {
            // Parse ISO-8601 datetime with zone
            ZonedDateTime zonedDateTime = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                zonedDateTime = ZonedDateTime.parse(alarmDay);
                // Convert to target timezone
                ZonedDateTime targetZoneTime = zonedDateTime.withZoneSameInstant(ZoneId.of(timeZone));
                // Format time in HH:mm
                DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("HH:mm");
                return targetZoneTime.format(outputFormatter);
            }

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return alarmDay;
    }

    public static boolean isAppInForeground(Context context) {
        ActivityManager activityManager =
                (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> appProcesses = activityManager.getRunningAppProcesses();
        if (appProcesses == null) {
            return false;
        }
        final String packageName = context.getPackageName();
        for (ActivityManager.RunningAppProcessInfo appProcess : appProcesses) {
            if (appProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND &&
                    appProcess.processName.equals(packageName)) {
                return true;
            }
        }
        return false;
    }

    public static Notification getForegroundServiceNotification(Context context) {
        Resources res = context.getResources();
        String packageName = context.getPackageName();
        int smallIconResId = res.getIdentifier("ic_launcher", "mipmap", packageName);

        return new NotificationCompat.Builder(context, "NotificationsChannelId")
                .setSmallIcon(smallIconResId)
                .setContentTitle("Task Alarm")
                .setContentText("Task Start Reminder Alarm")
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setOngoing(true)
                .build();
    }
}