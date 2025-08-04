package com.expoalarmmodule;

import android.content.Context;
import android.util.Log;
import java.util.Date;
import java.util.Objects;

public class Manager {

    private static final String TAG = "AlarmManager";
    private static Sound sound;
    private static String activeAlarmUid;

    static String getActiveAlarm() {
        return activeAlarmUid;
    }

    static void schedule(Context context, Alarm alarm) {
        Log.d(TAG, "🔔 Scheduling alarm → UID: " + alarm.uid);
        AlarmDates dates = alarm.getAlarmDates();

        if (dates.getDates().isEmpty()) {
            Log.w(TAG, "⚠️ No dates generated for alarm UID: " + alarm.uid + ". Check 'days' or 'date' fields.");
        }

        for (Date date : dates.getDates()) {
            int notificationId = dates.getNotificationId(date);
            Log.d(TAG, "📆 → Scheduling for: " + date.toString() +
                    " | Notification ID: " + notificationId +
                    " | Alarm UID: " + alarm.uid);
            Helper.scheduleAlarm(context, alarm.uid, date.getTime(), notificationId);
        }

        Log.d(TAG, "💾 Saving alarm & dates for UID: " + alarm.uid);
        Storage.saveAlarm(context, alarm);
        Storage.saveDates(context, dates);
    }

    public static void reschedule(Context context) {
        Log.d(TAG, "Rescheduling all alarms");
        Alarm[] alarms = Storage.getAllAlarms(context);
        for (Alarm alarm : alarms) {
            if (!alarm.active) {
                Log.d(TAG, "Skipping inactive alarm: " + alarm.uid);
                continue;
            }
            Storage.removeDates(context, alarm.uid);
            AlarmDates dates = alarm.getAlarmDates();
            Storage.saveDates(context, dates);
            for (Date date : dates.getDates()) {
                int notificationId = dates.getNotificationId(date);
                Helper.scheduleAlarm(context, alarm.uid, date.getTime(), notificationId);
                Log.d(TAG, "Rescheduled alarm: " + alarm.uid + " for " + date + " with ID " + notificationId);
            }
        }
    }

    static void update(Context context, Alarm alarm) {
        Log.d(TAG, "Updating alarm: " + alarm.uid);
        AlarmDates prevDates = Storage.getDates(context, alarm.uid);
        if (prevDates != null) {
            for (Date date : prevDates.getDates()) {
                Helper.cancelAlarm(context, prevDates.getNotificationId(date));
            }
            Storage.removeDates(context, alarm.uid);
        }
        AlarmDates dates = alarm.getAlarmDates();
        for (Date date : dates.getDates()) {
            int notificationId = dates.getNotificationId(date);
            Helper.scheduleAlarm(context, alarm.uid, date.getTime(), notificationId);
            Log.d(TAG, "Scheduled updated alarm " + alarm.uid + " for " + date + " with ID " + notificationId);
        }
        Storage.saveAlarm(context, alarm);
        Storage.saveDates(context, dates);
    }

    static void removeAll(Context context) {
        Log.d(TAG, "Removing all alarms");
        Alarm[] alarms = Storage.getAllAlarms(context);
        for (Alarm alarm : alarms) {
            remove(context, alarm.uid);
        }
    }

    static void remove(Context context, String alarmUid) {
        Log.d(TAG, "Removing alarm: " + alarmUid);
        // Stop and clean up if this is the active alarm
        if (sound != null) {
            sound.stop();
        }
        if (Objects.equals(activeAlarmUid, alarmUid)) {
            activeAlarmUid = null;
        }

        // Retrieve and cancel all notifications
        Alarm alarm = Storage.getAlarm(context, alarmUid);
        if (alarm != null) {
            AlarmDates dates = Storage.getDates(context, alarmUid);
            if (dates != null) {
                for (Date date : dates.getDates()) {
                    int notificationId = dates.getNotificationId(date);
                    Helper.cancelAlarm(context, notificationId);
                    Helper.cancelNotification(context, notificationId);
                    Log.d(TAG, "Cancelled notification ID: " + notificationId);
                }
            }
            Storage.removeAlarm(context, alarmUid);
            Storage.removeDates(context, alarmUid);
            Log.d(TAG, "✅ Native alarm '" + alarmUid + "' removed");
        } else {
            Log.w(TAG, "❌ No alarm found with UID: " + alarmUid + " to delete");
        }
    }

    static void enable(Context context, String alarmUid) {
        Log.d(TAG, "Enable UUID = " + alarmUid);
        Alarm[] alarms = Storage.getAllAlarms(context);
        for (Alarm alarm : alarms) {
            Log.d(TAG, " • UID: " + alarm.uid + ", Title: " + alarm.title + ", Active: " + alarm.active + ", Days: " + alarm.days);
        }
        Alarm alarm = Storage.getAlarm(context, alarmUid);
        if (alarm != null && !alarm.active) {
            alarm.active = true;
            Storage.saveAlarm(context, alarm);
            Log.d(TAG, "✅ Alarm '" + alarmUid + "' enabled");
            schedule(context, alarm); // Reschedule all notifications
        } else {
            Log.d(TAG, "⚠️ Alarm '" + alarmUid + "' was already active or not found");
        }
    }

    static void disable(Context context, String alarmUid) {
        Log.d(TAG, "🚫 Disabling alarm with UID: " + alarmUid);
        Log.d(TAG, "disable UUID = " + alarmUid);
        Alarm[] alarms = Storage.getAllAlarms(context);
        for (Alarm alarm : alarms) {
            Log.d(TAG, " • UID: " + alarm.uid + ", Title: " + alarm.title + ", Active: " + alarm.active + ", Days: " + alarm.days);
        }

        // Stop currently playing alarm if any
        if (sound != null) {
            sound.stop();
        }
        if (Objects.equals(activeAlarmUid, alarmUid)) {
            activeAlarmUid = null;
        }

        Alarm alarm = Storage.getAlarm(context, alarmUid);
        if (alarm != null && alarm.active) {
            // Cancel all notifications with this UID
            AlarmDates dates = Storage.getDates(context, alarmUid);
            if (dates != null) {
                for (Date date : dates.getDates()) {
                    int notificationId = dates.getNotificationId(date);
                    Helper.cancelAlarm(context, notificationId);
                    Helper.cancelNotification(context, notificationId);
                    Log.d(TAG, "🗑️ Cancelling notification with ID: " + notificationId);
                }
                Storage.removeDates(context, alarmUid);
            }

            // Disable the alarm in storage
            alarm.active = false;
            Storage.saveAlarm(context, alarm);
            Log.d(TAG, "✅ Alarm '" + alarmUid + "' disabled");
        } else {
            Log.d(TAG, "⚠️ Alarm '" + alarmUid + "' was already inactive or not found");
        }
    }

    static void start(Context context, String alarmUid) {
        Log.d(TAG, "Starting alarm: " + alarmUid);
        activeAlarmUid = alarmUid;
        Alarm alarm = Storage.getAlarm(context, alarmUid);
        if (alarm != null) {
            sound = new Sound(context);
            String alarmSound = alarm.getSound();
            sound.play(alarmSound);
            Log.d(TAG, "Started alarm " + activeAlarmUid + " with sound: " + alarmSound);
        } else {
            Log.w(TAG, "Alarm not found: " + alarmUid);
        }
    }

    public static void stop(Context context) {
        Log.d(TAG, "Stopping alarm: " + activeAlarmUid);
        if (sound != null) {
            sound.stop();
        }
        if (activeAlarmUid != null) {
            Alarm alarm = Storage.getAlarm(context, activeAlarmUid);
            AlarmDates dates = Storage.getDates(context, activeAlarmUid);
            if (alarm != null && dates != null) {
                if (alarm.repeating) {
                    Date current = dates.getCurrentDate();
                    if (current != null) {
                        Date updated = AlarmDates.setNextWeek(current);
                        dates.update(current, updated);
                        Storage.saveDates(context, dates);
                        int notificationId = dates.getNotificationId(updated);
                        Helper.scheduleAlarm(context, dates.alarmUid, updated.getTime(), notificationId);
                        Log.d(TAG, "Rescheduled repeating alarm for " + updated);
                    }
                } else {
                    alarm.active = false;
                    Storage.saveAlarm(context, alarm);
                    Storage.removeDates(context, activeAlarmUid);
                    Log.d(TAG, "Non-repeating alarm deactivated: " + activeAlarmUid);
                }
            }
            activeAlarmUid = null;
        }
    }

    public static void snooze(Context context) {
        Log.d(TAG, "Snoozing alarm: " + activeAlarmUid);
        if (sound != null) {
            sound.stop();
        }
        if (activeAlarmUid != null) {
            Alarm alarm = Storage.getAlarm(context, activeAlarmUid);
            AlarmDates dates = Storage.getDates(context, activeAlarmUid);
            if (alarm != null && dates != null) {
                Date current = dates.getCurrentDate();
                if (current != null) {
                    Date updated = AlarmDates.snooze(new Date(), alarm.snoozeInterval);
                    dates.update(current, updated);
                    Storage.saveDates(context, dates);
                    int notificationId = dates.getNotificationId(updated);
                    Helper.scheduleAlarm(context, dates.alarmUid, updated.getTime(), notificationId);
                    Log.d(TAG, "Snoozed alarm to " + updated + " with ID " + notificationId);
                }
            }
            activeAlarmUid = null;
        }
    }
}