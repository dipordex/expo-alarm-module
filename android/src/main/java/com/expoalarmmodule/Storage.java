package com.expoalarmmodule;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import com.expoalarmmodule.R;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Date;
import java.util.Map;

class Storage {

    private static final String TAG = "AlarmStorage";

    static void saveAlarm(Context context, Alarm alarm) {
        Log.d(TAG, "Saving alarm with UID: " + alarm.uid);
        SharedPreferences.Editor editor = getEditor(context);
        editor.putString(alarm.uid, Alarm.toJson(alarm));
        editor.apply();
    }

    static void saveDates(Context context, AlarmDates dates) {
        Log.d(TAG, "Saving dates for UID: " + dates.uid);
        SharedPreferences.Editor editor = getEditor(context);
        editor.putString(dates.uid, AlarmDates.toJson(dates));
        editor.apply();
    }

    static Alarm[] getAllAlarms(Context context) {
        Log.d(TAG, "Fetching all alarms");
        ArrayList<Alarm> alarms = new ArrayList<>();
        SharedPreferences preferences = getSharedPreferences(context);
        Map<String, ?> keyMap = preferences.getAll();
        for (Map.Entry<String, ?> entry : keyMap.entrySet()) {
            if (AlarmDates.isDatesId(entry.getKey())) continue;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Log.d(TAG, "Reading alarm UID: " + entry.getKey());
                alarms.add(Alarm.fromJson((String)entry.getValue()));
            }
        }
        Log.d(TAG, "Total alarms loaded: " + alarms.size());
        return alarms.toArray(new Alarm[0]);
    }

    static Alarm getAlarm(Context context, String alarmUid) {
        Log.d(TAG, "Fetching alarm with UID: " + alarmUid);
        SharedPreferences preferences = getSharedPreferences(context);
        String preferenceUid = preferences.getString(alarmUid, null);
        if (preferenceUid != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                return Alarm.fromJson(preferenceUid);
            }
        }
        Log.w(TAG, "No alarm found for UID: " + alarmUid);
        return null;
    }

    static AlarmDates getDates(Context context, String alarmUid) {
        Log.d(TAG, "Fetching dates for alarm UID: " + alarmUid);
        SharedPreferences preferences = getSharedPreferences(context);
        String json = preferences.getString(AlarmDates.getDatesId(alarmUid), null);
        if (json == null) {
            Log.w(TAG, "No dates found for UID: " + alarmUid);
        }
        return AlarmDates.fromJson(json);
    }

    static void removeAlarm(Context context, String alarmUid) {
        Log.d(TAG, "Removing alarm with UID: " + alarmUid);
        remove(context, alarmUid);
    }

    static void removeDates(Context context, String alarmUid) {
        Log.d(TAG, "Removing dates for UID: " + alarmUid);
        remove(context, AlarmDates.getDatesId(alarmUid));
    }

    private static void remove(Context context, String id) {
        Log.d(TAG, "Removing key from preferences: " + id);
        SharedPreferences preferences = getSharedPreferences(context);
        SharedPreferences.Editor editor = preferences.edit();
        editor.remove(id);
        editor.apply();
    }

    private static SharedPreferences.Editor getEditor(Context context) {
        SharedPreferences sharedPreferences = getSharedPreferences(context);
        return sharedPreferences.edit();
    }

    private static SharedPreferences getSharedPreferences(Context context) {
        String fileKey = context.getResources().getString(R.string.notification_channel_id);
        return context.getSharedPreferences(fileKey, Context.MODE_PRIVATE);
    }
}
