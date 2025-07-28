package com.expoalarmmodule;

import android.util.Log;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static com.expoalarmmodule.GsonUtil.createSerialize;

public class AlarmDates {

    private static final String TAG = "AlarmDates";
    private static final String postfix = "_DATES";

    String uid;
    String alarmUid;
    Date[] dates;
    int[] notificationIds;

    public AlarmDates(String alarmUid, List<Date> dates) {
        this.uid = alarmUid + postfix;
        this.alarmUid = alarmUid;
        this.dates = dates.toArray(new Date[0]);
        this.notificationIds = new int[dates.size()];
        for (int i = 0; i < dates.size(); i++) {
            this.notificationIds[i] = randomId();
        }

        Log.d(TAG, "Created AlarmDates: " + toJson(this));
    }

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
        Log.d(TAG, "Notification ID not found for date " + date);
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
        return getNotificationId(current);
    }

    public Date getCurrentDate() {
        Calendar calendar = Calendar.getInstance();
        int currentDay = calendar.get(Calendar.DAY_OF_WEEK);
        for (Date date : dates) {
            Calendar cal = Calendar.getInstance();
            cal.setTime(date);
            if (cal.get(Calendar.DAY_OF_WEEK) == currentDay) {
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
        Log.d(TAG, "Date to update not found: " + old);
    }

    public static AlarmDates fromJson(String json) {
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
