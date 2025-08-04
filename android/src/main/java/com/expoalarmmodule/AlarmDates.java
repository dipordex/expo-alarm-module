package com.expoalarmmodule;

import static com.expoalarmmodule.GsonUtil.createSerialize;

import android.annotation.SuppressLint;
import android.util.Log;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

public class AlarmDates {

    private static final String TAG = "AlarmDates";
    private static final String postfix = "_DATES";

    String uid;
    String alarmUid;
    Date[] dates;
    int[] notificationIds;

    // Constructor to initialize with alarm and generate dates
//    public AlarmDates(String alarmUid, Alarm alarm) {
//        this.uid = alarmUid + postfix;
//        this.alarmUid = alarmUid;
//
//        // Generate dates based on alarm properties
//        List<Date> dateList = generateDates(alarm);
//        this.dates = dateList.toArray(new Date[0]);
//        this.notificationIds = new int[dates.length];
//        for (int i = 0; i < dates.length; i++) {
//            this.notificationIds[i] = randomId();
//        }
//        Log.d(TAG, "Created AlarmDates: " + toJson(this));
//    }

    public AlarmDates(String alarmUid, List<Date> dateList) {
        this.uid = alarmUid + postfix;
        this.alarmUid = alarmUid;

        this.dates = dateList.toArray(new Date[0]);
        this.notificationIds = new int[dates.length];
        for (int i = 0; i < dates.length; i++) {
            this.notificationIds[i] = randomId();
        }

        Log.d(TAG, "Created AlarmDates (from date list): " + toJson(this));
    }


    // New method to generate dates based on alarm
//    @SuppressLint("NewApi")
//    private List<Date> generateDates(Alarm alarm) {
//        List<Date> dateList = new ArrayList<>();
//        Calendar calendar = Calendar.getInstance();
//        calendar.setTimeZone(TimeZone.getTimeZone("GMT+05:30")); // Ensure IST
//        Date now = new Date(); // Current time: 08:09 PM IST, July 30, 2025
//
//        // Use alarm.date if available, otherwise use hour and minutes with default date
//        int hour = alarm.hour != -1 ? alarm.hour : 20; // Default to 20:05 if not set
//        int minutes = alarm.minutes != -1 ? alarm.minutes : 5;
//        calendar.setTime(now);
//        calendar.set(Calendar.HOUR_OF_DAY, hour);
//        calendar.set(Calendar.MINUTE, minutes);
//        calendar.set(Calendar.SECOND, 0);
//        calendar.set(Calendar.MILLISECOND, 0);
//
//        if (alarm.date != null) {
//            Calendar dateCal = Calendar.getInstance();
//            dateCal.setTime(Date.from(alarm.date.toInstant()));
//
//            hour = dateCal.get(Calendar.HOUR_OF_DAY);
//            minutes = dateCal.get(Calendar.MINUTE);
//            calendar.set(Calendar.HOUR_OF_DAY, hour);
//            calendar.set(Calendar.MINUTE, minutes);
//        }
//
//        // Handle repeating alarms
//        if (alarm.repeating && alarm.days != null && !alarm.days.isEmpty()) {
//            int currentDay = calendar.get(Calendar.DAY_OF_WEEK); // 1 = Sunday, 7 = Saturday
//            for (int week = 0; week < 6; week++) { // 6 weeks
//                for (int day : alarm.days) {
//                    int targetDay = day; // 1-7 mapping
//                    if (targetDay >= currentDay || week > 0) {
//                        calendar.set(Calendar.DAY_OF_WEEK, targetDay);
//                        if (calendar.getTime().before(now)) {
//                            calendar.add(Calendar.WEEK_OF_YEAR, 1);
//                        }
//                        calendar.add(Calendar.WEEK_OF_YEAR, week * 7);
//                        Date scheduledDate = calendar.getTime();
//                        if (scheduledDate.after(now) || scheduledDate.equals(now)) {
//                            dateList.add(scheduledDate);
//                        }
//                    }
//                }
//                if (week == 0) {
//                    calendar.add(Calendar.WEEK_OF_YEAR, 1); // Move to next week for subsequent iterations
//                    currentDay = 1; // Reset to Sunday
//                }
//            }
//        } else if (alarm.date != null) {
//            // One-time alarm
//            calendar.setTime(Date.from(alarm.date.toInstant()));
//            if (calendar.getTime().before(now)) {
//                calendar.add(Calendar.DAY_OF_YEAR, 1);
//            }
//            dateList.add(calendar.getTime());
//        }
//
//        return dateList;
//    }

    public static String getDatesId(String alarmUid) {
        return alarmUid + postfix;
    }

    public int getNotificationId(Date date) {
        for (int i = 0; i < dates.length; i++) {
            if (dates[i].equals(date)) {
                Log.d(TAG, "Notification ID for date " + date + " is " + notificationIds[i]);
                return notificationIds[i];
            }
        }
        Log.w(TAG, "Notification ID not found for date " + date);
        return -1;
    }

    public static Date setNextWeek(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.DATE, 7);
        Date newDate = calendar.getTime();
        Log.d(TAG, "Set next week: " + newDate);
        return newDate;
    }

    public static Date snooze(Date date, int minutes) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.MINUTE, minutes);
        Date snoozed = calendar.getTime();
        Log.d(TAG, "Snoozed " + minutes + " minutes to: " + snoozed);
        return snoozed;
    }

    public static boolean isDatesId(String id) {
        return id.contains(postfix);
    }

    public int getCurrentNotificationId() {
        Date current = getCurrentDate();
        if (current == null) {
            Log.w(TAG, "No current date found");
            return -1;
        }
        return getNotificationId(current);
    }

    public Date getCurrentDate() {
        Calendar calendar = Calendar.getInstance();
        int currentDay = calendar.get(Calendar.DAY_OF_WEEK);
        for (Date date : dates) {
            Calendar cal = Calendar.getInstance();
            cal.setTime(date);
            if (cal.get(Calendar.DAY_OF_WEEK) == currentDay && !cal.before(calendar)) {
                Log.d(TAG, "Current date match found: " + date);
                return date;
            }
        }
        Log.d(TAG, "No current date match found.");
        return null;
    }

    public ArrayList<Date> getDates() {
        return new ArrayList<>(Arrays.asList(dates));
    }

    public void update(Date old, Date updated) {
        for (int i = 0; i < dates.length; i++) {
            if (dates[i].equals(old)) {
                dates[i] = updated;
                Log.d(TAG, "Updated date from " + old + " to " + updated);
                return;
            }
        }
        Log.w(TAG, "Date to update not found: " + old);
    }

    public static AlarmDates fromJson(String json) {
        if (json == null) {
            Log.w(TAG, "JSON is null, cannot parse AlarmDates");
            return null;
        }
        AlarmDates parsed = createSerialize().fromJson(json, AlarmDates.class);
        Log.d(TAG, "Parsed AlarmDates from JSON: " + json);
        return parsed;
    }

    public static String toJson(AlarmDates dates) {
        String json = createSerialize().toJson(dates);
        Log.d(TAG, "Serialized AlarmDates to JSON: " + json);
        return json;
    }

    private static int randomId() {
        int id = (int) (Math.random() * 10000000);
        Log.d(TAG, "Generated random ID: " + id);
        return id;
    }
}