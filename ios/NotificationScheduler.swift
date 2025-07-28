import Foundation
import UIKit
import UserNotifications

class NotificationScheduler : NotificationSchedulerDelegate
{
    private let alarms: Alarms = Store.shared.alarms
    private var manualTriggerTimer: DispatchSourceTimer?

    
    // we need to request user for notifiction permission first
    func requestAuthorization() {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound]) {
            (authorized, _) in
            if authorized {
                print("notification authorized")
            } else {
                // may need to try other way to make user authorize your app
                print("not authorized")
            }
        }
    }
    
    
    func registerNotificationCategories() {
        // Define the custom actions
        let snoozeAction = UNNotificationAction(identifier: Identifier.snoozeActionIdentifier, title: "Snooze", options: [.foreground])
        let stopAction = UNNotificationAction(identifier: Identifier.stopActionIdentifier, title: "OK", options: [.foreground])
        
        let snoonzeActions = [snoozeAction, stopAction]
        let nonSnoozeActions = [stopAction]
        
        let snoozeAlarmCategory = UNNotificationCategory(identifier: Identifier.snoozeAlarmCategoryIndentifier,
                                                         actions: snoonzeActions,
                                                         intentIdentifiers: [],
                                                         hiddenPreviewsBodyPlaceholder: "",
                                                         options: .customDismissAction)

        let nonSnoozeAlarmCategroy = UNNotificationCategory(identifier: Identifier.alarmCategoryIndentifier,
                                                            actions: nonSnoozeActions,
                                                            intentIdentifiers: [],
                                                            hiddenPreviewsBodyPlaceholder: "",
                                                            options: .customDismissAction)
        // Register the notification category
        UNUserNotificationCenter.current().setNotificationCategories([snoozeAlarmCategory, nonSnoozeAlarmCategroy])
    }
    
    // sync alarm state to scheduled notifications for some situation (app in background and user didn't click notification to bring the app to foreground) that
    // alarm state is not updated correctly
    func syncAlarmStateWithNotification() {
        UNUserNotificationCenter.current().getPendingNotificationRequests(completionHandler: {
            requests in
            print(requests)
            let alarms = Store.shared.alarms
            let uuidNotificationsSet = Set(requests.map({$0.content.userInfo["uid"] as! String}))
            let uuidAlarmsSet = alarms.uids
            let uuidDeltaSet = uuidAlarmsSet.subtracting(uuidNotificationsSet)
            
            for uid in uuidDeltaSet {
                if let alarm = alarms.getAlarm(ByUUIDStr: uid) {
                    if alarm.active {
                        alarm.active = false
                        // since this method will cause UI change, make sure run on main thread
                        DispatchQueue.main.async {
                            alarms.update(alarm)
                        }
                    }
                }
            }
        })
    }
    
    private func getNotificationDates(baseDate date: Date) -> [Date]
    {
        var notificationDates: [Date] = [Date]()
        let calendar = Calendar(identifier: Calendar.Identifier.gregorian)
        let now = Date()
        let flags: NSCalendar.Unit = [NSCalendar.Unit.weekday, NSCalendar.Unit.weekdayOrdinal, NSCalendar.Unit.day]
        let dateComponents = (calendar as NSCalendar).components(flags, from: date)
        
        //scheduling date is eariler than current date
        if date < now {
            //plus one day, otherwise the notification will be fired righton
            notificationDates.append((calendar as NSCalendar).date(byAdding: NSCalendar.Unit.day, value: 1, to: date, options:.matchStrictly)!)
        } else {
            notificationDates.append(date)
        }
        
        return notificationDates
    }
    
    static func correctSecondComponent(date: Date, calendar: Calendar = Calendar(identifier: Calendar.Identifier.gregorian)) -> Date {
        let second = calendar.component(.second, from: date)
        let d = (calendar as NSCalendar).date(byAdding: NSCalendar.Unit.second, value: -second, to: date, options:.matchStrictly)!
        return d
    }
    
    func setNotification(alarm: Alarm) {
        let datesForNotification = getNotificationDates(baseDate: alarm.date)

        // 🔽 If remote URL, download and cache it
        var localSoundFile: String? = nil
        if alarm.sound.hasPrefix("http://") || alarm.sound.hasPrefix("https://") {
            print("🌐 Detected remote sound. Pre-downloading: \(alarm.sound)")

            if let cachedURL = downloadAndCacheSoundSync(urlString: alarm.sound) {
                localSoundFile = cachedURL.path
                print("✅ Sound downloaded and cached: \(localSoundFile!)")
            } else {
                print("❌ Failed to pre-download sound: \(alarm.sound)")
            }
        } else if alarm.sound.hasPrefix("file://") {
                localSoundFile = alarm.sound
        }


        for d in datesForNotification {
            let notificationContent = UNMutableNotificationContent()
            notificationContent.title = alarm.title
            notificationContent.body = alarm.description
            notificationContent.categoryIdentifier = alarm.snoozeEnabled
                ? Identifier.snoozeAlarmCategoryIndentifier
                : Identifier.alarmCategoryIndentifier

            // Use critical alert to wake device (but this won’t play custom sound in background)
            if #available(iOS 12.0, *) {
                notificationContent.sound = .defaultCritical
            } else {
                notificationContent.sound = .default
            }

            notificationContent.userInfo = [
                "snooze": alarm.snoozeEnabled,
                "uid": alarm.uid,
                "soundName": alarm.sound,
                "localSoundPath": localSoundFile ?? ""
            ]


            let dateComponents = Calendar.current.dateComponents([.weekday, .hour, .minute, .second], from: d)
            let trigger = UNCalendarNotificationTrigger(dateMatching: dateComponents, repeats: false)

            let request = UNNotificationRequest(identifier: alarm.uid, content: notificationContent, trigger: trigger)

            print("📅 Scheduling iOS notification with UID: \(alarm.uid), sound: \(alarm.sound), fireDate: \(d)")
            
            startManualAlarmTrigger(at: d, soundName: alarm.sound, localPath: localSoundFile, uid: alarm.uid)

            UNUserNotificationCenter.current().add(request) { error in
                if let e = error {
                    print("❌ Error scheduling notification: \(e.localizedDescription)")
                } else {
                    print("✅ Notification scheduled: \(alarm.uid)")
                }
            }
            
        }
    }
    
    func startManualAlarmTrigger(at date: Date, soundName: String, localPath: String?, uid: String) {
        let secondsRemaining = Int(date.timeIntervalSinceNow)
        guard secondsRemaining > 0 else {
            print("⚠️ Alarm \(uid) was scheduled in the past.")
            return
        }

        print("⏳ Scheduling manual alarm trigger in \(secondsRemaining)s")

        let timer = DispatchSource.makeTimerSource(queue: DispatchQueue.global(qos: .background))
        timer.schedule(deadline: .now() + .seconds(secondsRemaining), repeating: .never)

        timer.setEventHandler { [weak self] in
            print("🚨 Manual trigger reached for alarm \(uid), playing custom sound")

            var bgTaskID: UIBackgroundTaskIdentifier = .invalid
            bgTaskID = UIApplication.shared.beginBackgroundTask(withName: "ManualAlarmPlayback") {
                UIApplication.shared.endBackgroundTask(bgTaskID)
                bgTaskID = .invalid
            }
            DispatchQueue.main.async {
                let appState = UIApplication.shared.applicationState
                if (appState == .background) {
                    ExpoAlarmModule().playSound(soundName, localPath: localPath, uuid: uid) {
                        UIApplication.shared.endBackgroundTask(bgTaskID)
                        bgTaskID = .invalid
                    }
                }
            }
            
            self?.manualTriggerTimer?.cancel()
            self?.manualTriggerTimer = nil
        }

        manualTriggerTimer = timer // ✅ Retain the timer
        timer.resume()
    }


    func downloadAndCacheSoundSync(urlString: String) -> URL? {
        guard let url = URL(string: urlString) else { return nil }

        let filename = urlString.sha256() + ".m4a"
        let cacheDir = FileManager.default.temporaryDirectory.appendingPathComponent("alarms", isDirectory: true)

        // Ensure directory exists
        try? FileManager.default.createDirectory(at: cacheDir, withIntermediateDirectories: true)

        let targetURL = cacheDir.appendingPathComponent(filename)

        if FileManager.default.fileExists(atPath: targetURL.path) {
            print("📦 Sound already cached: \(targetURL.path)")
            return targetURL
        }

        // Synchronous download using semaphore
        var resultURL: URL? = nil
        let semaphore = DispatchSemaphore(value: 0)

        URLSession.shared.downloadTask(with: url) { tempURL, _, error in
            defer { semaphore.signal() }
            if let tempURL = tempURL, error == nil {
                do {
                    try FileManager.default.copyItem(at: tempURL, to: targetURL)
                    resultURL = targetURL
                } catch {
                    print("❌ Failed to copy downloaded sound: \(error.localizedDescription)")
                }
            } else {
                print("❌ Failed to download sound: \(error?.localizedDescription ?? "unknown")")
            }
        }.resume()

        _ = semaphore.wait(timeout: .now() + 20) // wait max 20 sec
        return resultURL
    }


    
    func setNotificationForSnooze(ringtoneName: String, snoozeMinute: Int, uid: String) {
        let currentAlarm = alarms.getAlarm(ByUUIDStr: uid);
        if(currentAlarm != nil) {
            let calendar = Calendar(identifier: Calendar.Identifier.gregorian)
            let now = Date()
            let snoozeDate = (calendar as NSCalendar).date(byAdding: NSCalendar.Unit.minute, value: snoozeMinute, to: now, options:.matchStrictly)!
            setNotification(alarm: currentAlarm!)
        } else {
            print("Error when setting notification for snooze")
        }
    }
    
    func cancelNotification(ByUUIDStr uid: String) {
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: [uid])
    }
    
    func updateNotification(ByUUIDStr uid: String, date: Date, ringtoneName: String, snoonzeEnabled: Bool) {
        cancelNotification(ByUUIDStr: uid)
        let currentAlarm = alarms.getAlarm(ByUUIDStr: uid);
        if(currentAlarm != nil) {
            setNotification(alarm: currentAlarm!)
        } else {
            print("Error updating notification")
        }
    }
    
    enum weekdaysComparisonResult {
        case before
        case same
        case after
    }
    
    // 1 == Sunday, 2 == Monday and so on
    func compare(weekday w1: Int, with w2: Int) -> weekdaysComparisonResult
    {
        if w1 != 1 && (w1 < w2 || w2 == 1) {return .before}
        else if w1 == w2 {return .same}
        else {return .after}
    }
}


import Foundation
import CommonCrypto

extension String {
    func sha256() -> String {
        guard let data = self.data(using: .utf8) else { return self }
        var hash = [UInt8](repeating: 0, count: Int(CC_SHA256_DIGEST_LENGTH))
        
        data.withUnsafeBytes {
            _ = CC_SHA256($0.baseAddress, CC_LONG(data.count), &hash)
        }

        return hash.map { String(format: "%02x", $0) }.joined()
    }
}
