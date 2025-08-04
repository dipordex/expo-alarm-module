package com.expoalarmmodule;

import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.google.gson.Gson;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;

import static com.expoalarmmodule.GsonUtil.createSerialize;



public class Alarm implements Cloneable {

    String uid;
    ZonedDateTime date;
    String dateString;
    ArrayList<Integer> days;
    int hour;
    int minutes;
    String title;
    String description;
    boolean repeating;
    boolean active;
    boolean showDismiss;
    boolean showSnooze;
    int snoozeInterval;
    String dismissText;
    String snoozeText;
    String sound;

    String timeZone;
    boolean vibration;
    int volumeLevel;

    Alarm(String uid, ArrayList<Integer> days, ZonedDateTime date, int hour, int minutes, boolean showDismiss, boolean showSnooze, int snoozeInterval, String title, String description, boolean repeating, boolean active, String dismissText, String snoozeText, String sound, String timeZone, boolean vibration, int volumeLevel ) {
        this.uid = uid;
        this.days = days != null ? days : new ArrayList<>();
        this.hour = hour;
        this.minutes = minutes;
        this.showDismiss = showDismiss;
        this.showSnooze = showSnooze;
        this.snoozeInterval = snoozeInterval;
        this.title = title;
        this.description = description;
        this.repeating = repeating;
        this.active = active;
        this.dismissText = dismissText;
        this.snoozeText = snoozeText;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            this.date = date;
        }
        this.sound = sound;
        this.timeZone = timeZone;
        this.volumeLevel = volumeLevel;
        this.vibration = vibration;

    }

    List<Date> getDates() {
        List<Date> dates = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            TimeZone ist = TimeZone.getTimeZone(timeZone != null ? timeZone : TimeZone.getDefault().getID());
            Calendar now = Calendar.getInstance(ist);
            now.setTimeZone(ist);

            int numberOfWeeksToSchedule = 6;

            if (days != null && !days.isEmpty()) {
                for (int weekOffset = 0; weekOffset < numberOfWeeksToSchedule; weekOffset++) {
                    for (int weekday : days) {
                        Calendar base = Calendar.getInstance(ist);
                        base.setTime(now.getTime());
                        base.setFirstDayOfWeek(Calendar.SUNDAY);
                        base.set(Calendar.HOUR_OF_DAY, 0);
                        base.set(Calendar.MINUTE, 0);
                        base.set(Calendar.SECOND, 0);
                        base.set(Calendar.MILLISECOND, 0);

                        // Align to the first day of week, then add the target weekday
                        base.set(Calendar.DAY_OF_WEEK, weekday);
                        base.add(Calendar.WEEK_OF_YEAR, weekOffset);

                        // Inject alarm time
                        base.set(Calendar.HOUR_OF_DAY, hour);
                        base.set(Calendar.MINUTE, minutes);
                        base.set(Calendar.SECOND, 0);
                        base.set(Calendar.MILLISECOND, 0);

                        Date targetDate = base.getTime();
                        if (!targetDate.before(now.getTime())) {
                            dates.add(targetDate);
                            Log.d("Alarm#getDates", "✅ Repeating UID " + uid + " → " + targetDate + " (weekday: " + weekday + ")");
                        } else {
                            Log.d("Alarm#getDates", "⏩ Skipped past date " + targetDate + " for UID: " + uid);
                        }
                    }
                }
            } else if (date != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Calendar triggerDate = Calendar.getInstance(ist);
                triggerDate.setTimeZone(ist);
                triggerDate.setTime(Date.from(date.toInstant()));
                triggerDate.set(Calendar.SECOND, 0);
                triggerDate.set(Calendar.MILLISECOND, 0);

                if (triggerDate.before(now)) {
                    triggerDate.add(Calendar.DATE, 1); // Schedule for tomorrow
                }

                Date finalDate = triggerDate.getTime();
                dates.add(finalDate);
                Log.d("Alarm#getDates", "📆 One-time UID " + uid + " → " + finalDate);
            } else {
                Log.d("Alarm#getDates", "⚠️ No valid date or repeat days for UID: " + uid);
            }
        }

        return dates;
    }




    AlarmDates getAlarmDates() {
        return new AlarmDates(uid, getDates());
    }


    @RequiresApi(api = Build.VERSION_CODES.O)
    static Alarm fromJson(String json) {
        Log.d("AlarmStorage", "Deserializing Alarm JSON: " + json);

        Alarm alarmTemp = createSerialize().fromJson(json, Alarm.class);
        if (alarmTemp.dateString != null) {
            alarmTemp.date = ZonedDateTime.parse(alarmTemp.dateString);
        }

        Log.d("AlarmStorage", "Deserialized Alarm → uid: " + alarmTemp.uid
                + ", days: " + alarmTemp.days.toString()
                + ", date: " + (alarmTemp.date != null ? alarmTemp.date.toString() : "null"));

        return alarmTemp;
    }


    static String toJson(Alarm alarm) {
        if (alarm.date != null) {
            alarm.dateString = alarm.date.toString();
        }
        String json = createSerialize().toJson(alarm);

        Log.d("AlarmStorage", "Serialized Alarm JSON: " + json);
        Log.d("AlarmStorage", "Details → uid: " + alarm.uid
                + ", title: " + alarm.title
                + ", description: " + alarm.description
                + ", hour: " + alarm.hour
                + ", minutes: " + alarm.minutes
                + ", repeating: " + alarm.repeating
                + ", active: " + alarm.active
                + ", showDismiss: " + alarm.showDismiss
                + ", showSnooze: " + alarm.showSnooze
                + ", snoozeInterval: " + alarm.snoozeInterval
                + ", dismissText: " + alarm.dismissText
                + ", snoozeText: " + alarm.snoozeText
                + ", sound: " + alarm.sound
                + ", date: " + (alarm.date != null ? alarm.date.toString() : "null")
                + ", days: " + alarm.days.toString());

        return json;
    }

    public Alarm clone() throws CloneNotSupportedException {
        return (Alarm) super.clone();
    }

    public String getSound() {
        return sound != null ? sound : "default";
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof Alarm)) return false;
        Alarm alarm = (Alarm) o;
        return (
                this.hour == alarm.hour &&
                        this.minutes == alarm.minutes &&
                        this.showDismiss == alarm.showDismiss &&
                        this.showSnooze == alarm.showSnooze &&
                        this.snoozeInterval == alarm.snoozeInterval &&
                        this.dismissText.equals(alarm.dismissText) &&
                        this.snoozeText.equals(alarm.snoozeText) &&
                        this.uid.equals(alarm.uid) &&
                        this.days.equals(alarm.days) &&
                        this.title.equals(alarm.title) &&
                        this.description.equals(alarm.description) &&
                        this.sound.equals(alarm.sound)
        );
    }
}
