package com.expoalarmmodule;

import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableType;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.module.annotations.ReactModule;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;

@ReactModule(name = ExpoAlarmModuleModule.NAME)
public class ExpoAlarmModuleModule extends ReactContextBaseJavaModule {

    private final ReactApplicationContext reactContext;
    private static ReactApplicationContext reactContextStatic;  // <-- static React context reference
    public static final String NAME = "ExpoAlarmModule";

    public ExpoAlarmModuleModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
        reactContextStatic = reactContext;  // assign static context here
        Helper.createNotificationChannel(reactContext);
    }


    // Static helper so other classes can trigger it
    public static void triggerNotificationTapped(String uid, String title, String time) {
        if (reactContextStatic == null) {
            Log.e(NAME, "ReactApplicationContext is null! Cannot emit event.");
            return;
        }

        WritableMap params = new WritableNativeMap();
        params.putString("uid", uid);
        params.putString("title", title);
        params.putString("time", time);

        reactContextStatic
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit("onAlarmNotificationTapped", params);
    }

    public static void triggerNotificationSnoozeTapped(String uid, String title, String time) {
        if (reactContextStatic == null) {
            Log.e(NAME, "ReactApplicationContext is null! Cannot emit event.");
            return;
        }

        WritableMap params = new WritableNativeMap();
        params.putString("uid", uid);
        params.putString("title", title);
        params.putString("time", time);

        reactContextStatic
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit("onAlarmSnoozeTapped", params);
    }


    public static void triggerNotificationDissmissTapped(String uid, String title, String time) {
        if (reactContextStatic == null) {
            Log.e(NAME, "ReactApplicationContext is null! Cannot emit event.");
            return;
        }

        WritableMap params = new WritableNativeMap();
        params.putString("uid", uid);
        params.putString("title", title);
        params.putString("time", time);

        reactContextStatic
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit("onAlarmDismissTapped", params);
    }

    @Override
    @NonNull
    public String getName() {
        return NAME;
    }

    @ReactMethod
    public void getState(Promise promise) {
        promise.resolve(Manager.getActiveAlarm());
    }

    @ReactMethod
    public void set(ReadableMap details, Promise promise) {
        try {
            Alarm alarm = parseAlarmObject(details);
            Manager.schedule(reactContext, alarm);
            promise.resolve(null);
        } catch (Exception e) {
            promise.reject("ERROR_SET_ALARM", e.getMessage(), e);
        }
    }

    @ReactMethod
    public void update(ReadableMap details, Promise promise) {
        try {
            Alarm alarm = parseAlarmObject(details);
            Manager.update(reactContext, alarm);
            promise.resolve(null);
        } catch (Exception e) {
            promise.reject("ERROR_UPDATE_ALARM", e.getMessage(), e);
        }
    }

    @ReactMethod
    public void remove(String alarmUid, Promise promise) {
        Manager.remove(reactContext, alarmUid);
        promise.resolve(null);
    }

    @ReactMethod
    public void removeAll(Promise promise) {
        Manager.removeAll(reactContext);
        promise.resolve(null);
    }

    @ReactMethod
    public void enable(String alarmUid, Promise promise) {
        Manager.enable(reactContext, alarmUid);
        promise.resolve(null);
    }

    @ReactMethod
    public void disable(String alarmUid, Promise promise) {
        Manager.disable(reactContext, alarmUid);
        promise.resolve(null);
    }

    @ReactMethod
    public void stop(Promise promise) {
        Manager.stop(reactContext);
        Intent serviceIntent = new Intent(reactContext, AlarmService.class);
        reactContext.stopService(serviceIntent);
        promise.resolve(null);
    }

    @ReactMethod
    public void snooze(Promise promise) {
        Manager.snooze(reactContext);
        Intent serviceIntent = new Intent(reactContext, AlarmService.class);
        reactContext.stopService(serviceIntent);
        promise.resolve(null);
    }

    @ReactMethod
    public void get(String alarmUid, Promise promise) {
        try {
            Alarm alarm = Storage.getAlarm(reactContext, alarmUid);
            promise.resolve(serializeAlarmObject(alarm));
        } catch (Exception e) {
            promise.reject("ERROR_GET_ALARM", e.getMessage(), e);
        }
    }

    @ReactMethod
    public void getAll(Promise promise) {
        try {
            Alarm[] alarms = Storage.getAllAlarms(reactContext);
            WritableNativeArray serializedAlarms = serializeArray(alarms);
            promise.resolve(serializedAlarms);
        } catch (Exception e) {
            promise.reject("ERROR_GET_ALL_ALARMS", e.getMessage(), e);
        }
    }

    private Alarm parseAlarmObject(ReadableMap alarm) {
        Log.d("ExpoAlarmModule", "📥 Incoming alarm ReadableMap: " + alarm.toString());

        String uid = alarm.getString("uid");
        String title = alarm.hasKey("title") ? alarm.getString("title") : "";
        String description = alarm.hasKey("description") ? alarm.getString("description") : "";
        int hour = alarm.hasKey("hour") ? alarm.getInt("hour") : -1;
        int minutes = alarm.hasKey("minutes") ? alarm.getInt("minutes") : -1;
        boolean repeating = alarm.hasKey("repeating") && alarm.getBoolean("repeating");
        boolean active = !alarm.hasKey("active") || alarm.getBoolean("active");
        boolean showDismiss = alarm.hasKey("showDismiss") && alarm.getBoolean("showDismiss");
        boolean showSnooze = alarm.hasKey("showSnooze") && alarm.getBoolean("showSnooze");
        int snoozeInterval = alarm.hasKey("snoozeInterval") ? alarm.getInt("snoozeInterval") : 5;
        String dismissText = alarm.hasKey("dismissText") ? alarm.getString("dismissText") : "Dismiss";
        String snoozeText = alarm.hasKey("snoozeText") ? alarm.getString("snoozeText") : "Snooze";
        String sound = alarm.hasKey("sound") ? alarm.getString("sound") : "default";
        String timeZone = alarm.hasKey("timeZone") ? alarm.getString("timeZone") : "Asia/Kolkata";
        int volumeLevel = alarm.hasKey("volumeLevel") ? alarm.getInt("volumeLevel") : 100;
        boolean vibration = alarm.hasKey("vibration") ? alarm.getBoolean("vibration") : true;

        ArrayList<Integer> days = new ArrayList<>();
        ZonedDateTime date = null;

        // Parse days for repeating alarms
        if (alarm.hasKey("days") && !alarm.isNull("days")) {
            try {
                ReadableArray rawDays = alarm.getArray("days");
                for (int i = 0; i < rawDays.size(); i++) {
                    int weekday = rawDays.getInt(i);
                    days.add(weekday);
                }
                Log.d("ExpoAlarmModule", "✅ Parsed repeating weekdays: " + days.toString());
                repeating = true; // Force repeating if days are provided
            } catch (Exception e) {
                Log.e("ExpoAlarmModule", "❌ Error parsing 'days': " + e.getMessage(), e);
            }
        }

        // Parse day for time, even if repeating
        if (alarm.hasKey("day") && !alarm.isNull("day")) {
            try {
//                if (alarm.getType("day") == ReadableType.String && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//                    date = ZonedDateTime.parse(alarm.getString("day"));
//                    Log.d("ExpoAlarmModule", "✅ Parsed ZonedDateTime: " + date.toString());
//                    // Extract hour and minutes from date if not set
//                    if (hour == -1) hour = date.getHour();
//                    if (minutes == -1) minutes = date.getMinute();
//                }


                if (alarm.getType("day") == ReadableType.String && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ZoneId systemZone = ZoneId.of(timeZone);
                    date = ZonedDateTime.parse(alarm.getString("day"))
                            .withZoneSameInstant(systemZone);
                    Log.d("ExpoAlarmModule", "✅ Parsed ZonedDateTime: " + date.toString());

                    if (hour == -1) hour = date.getHour();
                    if (minutes == -1) minutes = date.getMinute();
                }

            } catch (Exception e) {
                Log.e("ExpoAlarmModule", "❌ Error parsing 'day': " + e.getMessage(), e);
            }
        }



        // Default hour and minutes for repeating alarms if not set
        if (repeating && (hour == -1 || minutes == -1)) {
            hour = (hour == -1) ? 20 : hour; // Default to 20:05 IST if not set
            minutes = (minutes == -1) ? 5 : minutes;
            Log.d("ExpoAlarmModule", "✅ Applied default time for repeating alarm: " + hour + ":" + minutes);
        }

        Alarm parsedAlarm = new Alarm(
                uid,
                days,
                date,
                hour,
                minutes,
                showDismiss,
                showSnooze,
                snoozeInterval,
                title,
                description,
                repeating,
                active,
                dismissText,
                snoozeText,
                sound,
                timeZone,
                vibration,
                volumeLevel

        );

        Log.d("ExpoAlarmModule", "✅ Constructed Alarm → uid: " + uid + ", hour: " + hour + ", minutes: " + minutes +
                ", days: " + days + ", date: " + date + ", repeating: " + repeating);

        return parsedAlarm;
    }

    private WritableMap serializeAlarmObject(Alarm alarm) throws Exception {
        WritableNativeMap map = new WritableNativeMap();
        map.putString("uid", alarm.uid);
        map.putString("title", alarm.title);
        map.putString("description", alarm.description);
        map.putBoolean("repeating", alarm.repeating);
        map.putBoolean("active", alarm.active);
        map.putBoolean("showDismiss", alarm.showDismiss);
        map.putBoolean("showSnooze", alarm.showSnooze);
        map.putInt("snoozeInterval", alarm.snoozeInterval);
        map.putString("dismissText", alarm.dismissText);
        map.putString("snoozeText", alarm.snoozeText);
        map.putString("sound", alarm.sound);

        if (alarm.date != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            map.putString("day", alarm.date.toString());
            map.putInt("hour", alarm.date.getHour());
            map.putInt("minutes", alarm.date.getMinute());
        } else if (!alarm.days.isEmpty()) {
            map.putInt("hour", alarm.hour);
            map.putInt("minutes", alarm.minutes);
            map.putArray("days", serializeArray(alarm.days));
        }

        return map;
    }

    private WritableNativeArray serializeArray(ArrayList<Integer> a) {
        WritableNativeArray array = new WritableNativeArray();
        for (int value : a) array.pushInt(value);
        return array;
    }

    private WritableNativeArray serializeArray(Alarm[] a) throws Exception {
        WritableNativeArray array = new WritableNativeArray();
        for (Alarm alarm : a) array.pushMap(serializeAlarmObject(alarm));
        return array;
    }
}