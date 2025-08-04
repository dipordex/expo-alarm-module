import Foundation
import AVFoundation

class Manager {
    private let scheduler: NotificationSchedulerDelegate = NotificationScheduler()
    private let alarms: Alarms = Store.shared.alarms
    private var currentPlayingAlarm: String? = nil
    
    func schedule(_ alarm: Alarm) {
        // Needs to save beforing set the notification, since the notification depends on the alarm.
        
        // Stores the alarm in the Store for rescheduling or removing later.
        alarms.add(alarm)
        
        // Creates the notification (the alarm).
        scheduler.setNotification(alarm: alarm)
    }
    
    // Gets alarm from store
    func getAlarm(_ uid: String) -> Alarm? {
        return alarms.getAlarm(ByUUIDStr: uid);
    }
    
    func getAllAlarms() -> [Alarm] {
        return alarms.getAlarms();
    }
    

    func enable(_ uid: String) {
        print("Enable UUID = \(uid)")
        alarms.logStoredAlarms() // new line for debugging
        guard let alarm = self.getAlarm(uid) else { return }
       

        if !alarm.active {
            alarm.active = true
            alarms.update(alarm)

            // Schedule all notifications including repeating ones
            scheduler.setNotification(alarm: alarm)
        }
    }

    func disable(_ uid: String) {
        print("🚫 Disabling alarm with UID: \(uid)")
        print("disable UUID = \(uid)")
        alarms.logStoredAlarms() // new line for debugging


        // Stop currently playing alarm if any
        self.stop()

        guard let alarm = self.getAlarm(uid) else {
            print("❌ No alarm found with UID: \(uid) in storage")
            return
        }

        if alarm.active {
            // Cancel all notifications with this UID
            scheduler.cancelNotification(ByUUIDStr: uid)

            // Disable the alarm in memory/storage
            alarm.active = false
            alarms.update(alarm)
            print("✅ Alarm '\(uid)' disabled")
        } else {
            print("⚠️ Alarm '\(uid)' was already inactive")
        }
    }


    func stop() {
        setCurrentPlayingAlarm(nil)
        ExpoAlarmModule.audioPlayer?.stop()
        AudioServicesRemoveSystemSoundCompletion(kSystemSoundID_Vibrate)
    }

    func remove(_ uid: String) {
        // Cancel notifications first
        scheduler.cancelNotification(ByUUIDStr: uid)
        
        // Check and remove alarm
        guard let alarm = self.getAlarm(uid) else {
            print("❌ No alarm found with uid \(uid) to delete cached file")
            return
        }
        
        // Stop alarm if it's playing
        self.stop()

        alarms.remove(uid)
        print("✅ Native alarm '\(uid)' removed")
    }

    
    func setCurrentPlayingAlarm(_ uid: String?) {
        currentPlayingAlarm = uid;
    }
    
    func getCurrentPlayingAlarm() -> String? {
        return currentPlayingAlarm;
    }
    
    func removeAll() {
        for alarmUuid in alarms.uids {
            self.remove(alarmUuid)
        }
    }
}
