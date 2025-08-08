import AVFoundation
import CommonCrypto
import Foundation
import UIKit
import UserNotifications
import os.log

class NotificationScheduler: NotificationSchedulerDelegate {
    private let alarms: Alarms = Store.shared.alarms
    private var pollingThreads: [String: Bool] = [:]
    let log = OSLog(subsystem: Bundle.main.bundleIdentifier ?? "com.example.setinc", category: "alarm")

    func requestAuthorization() {
        os_log("SetInc_Log: 📬 Requesting notification authorization", log: log, type: .error)
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound]) { (authorized, _) in
            if authorized {
                os_log("SetInc_Log: ✅ Notification authorized", log: self.log, type: .error)
                print("notification authorized")
            } else {
                os_log("SetInc_Log: ❌ Notification not authorized", log: self.log, type: .error)
                print("not authorized")
            }
        }
    }

    func registerNotificationCategories() {
        os_log("SetInc_Log: 📋 Registering notification categories", log: log, type: .error)
        let snoozeAction = UNNotificationAction(identifier: Identifier.snoozeActionIdentifier, title: "Snooze", options: [.foreground])
        let stopAction = UNNotificationAction(identifier: Identifier.stopActionIdentifier, title: "Dismiss", options: [.destructive])

        let snoozeActions = [snoozeAction, stopAction]
        let nonSnoozeActions = [stopAction]

        let snoozeAlarmCategory = UNNotificationCategory(identifier: Identifier.snoozeAlarmCategoryIndentifier, actions: snoozeActions, intentIdentifiers: [], hiddenPreviewsBodyPlaceholder: "", options: .customDismissAction)
        let nonSnoozeAlarmCategory = UNNotificationCategory(identifier: Identifier.alarmCategoryIndentifier, actions: nonSnoozeActions, intentIdentifiers: [], hiddenPreviewsBodyPlaceholder: "", options: .customDismissAction)

        UNUserNotificationCenter.current().setNotificationCategories([snoozeAlarmCategory, nonSnoozeAlarmCategory])
        os_log("SetInc_Log: ✅ Notification categories registered", log: log, type: .error)
    }

    // sync alarm state to scheduled notifications for some situation (app in background and user didn't click notification to bring the app to foreground) that
    // alarm state is not updated correctly
    func syncAlarmStateWithNotification() {
        os_log("SetInc_Log: 🔄 Syncing alarm state with notifications", log: log, type: .error)
        UNUserNotificationCenter.current().getPendingNotificationRequests { requests in
            let uuidNotificationsSet = Set(requests.compactMap { $0.content.userInfo["uid"] as? String })
            let uuidAlarmsSet = self.alarms.uids
            let uuidDeltaSet = uuidAlarmsSet.subtracting(uuidNotificationsSet)

            for uid in uuidDeltaSet {
                if let alarm = self.alarms.getAlarm(ByUUIDStr: uid) {
                    if alarm.active {
                        os_log("SetInc_Log: 📴 Deactivating alarm with UID: %{public}@", log: self.log, type: .error, uid)
                        alarm.active = false
                        DispatchQueue.main.async {
                            self.alarms.update(alarm)
                            os_log("SetInc_Log: ✅ Alarm updated for UID: %{public}@", log: self.log, type: .error, uid)
                        }
                    }
                } else {
                    os_log("SetInc_Log: ❌ No alarm found for UID: %{public}@", log: self.log, type: .error, uid)
                }
            }
        }
    }

    private func getNotificationDates(for alarm: Alarm) -> [Date] {
        var notificationDates: [Date] = []
        var calendar = Calendar(identifier: .gregorian)
        if let tz = TimeZone(identifier: alarm.timeZone) {
            calendar.timeZone = tz
        }

        let now = Date()
        guard let repeatDays = alarm.days, !repeatDays.isEmpty else {
            let corrected = NotificationScheduler.correctSecondComponent(date: alarm.date)
            let result = corrected < now ? [calendar.date(byAdding: .day, value: 1, to: corrected)!] : [corrected]
            return result
        }

        let numberOfWeeksToSchedule = 6
        for weekOffset in 0..<numberOfWeeksToSchedule {
            for weekday in repeatDays {
                var components = calendar.dateComponents([.yearForWeekOfYear, .weekOfYear], from: now)
                components.weekday = weekday

                if let weekdayDate = calendar.date(from: components),
                   let targetDate = calendar.date(byAdding: .weekOfYear, value: weekOffset, to: weekdayDate) {
                    var alarmComponents = calendar.dateComponents([.hour, .minute], from: alarm.date)
                    var finalDateComponents = calendar.dateComponents([.year, .month, .day], from: targetDate)
                    finalDateComponents.hour = alarmComponents.hour
                    finalDateComponents.minute = alarmComponents.minute
                    finalDateComponents.second = 0

                    if let finalDate = calendar.date(from: finalDateComponents), finalDate >= now {
                        let corrected = NotificationScheduler.correctSecondComponent(date: finalDate)
                        notificationDates.append(corrected)
                    }
                }
            }
        }
        return notificationDates
    }

    static func correctSecondComponent(date: Date, calendar: Calendar = Calendar(identifier: .gregorian)) -> Date {
        let second = calendar.component(.second, from: date)
        let corrected = (calendar as NSCalendar).date(byAdding: .second, value: -second, to: date, options: .matchStrictly)!
        return corrected
    }

    func setNotification(alarm: Alarm) {
        os_log("SetInc_Log: 🔔 Setting notification for alarm UID: %{public}@", log: log, type: .error, alarm.uid)
        let datesForNotification = getNotificationDates(for: alarm)
        var localSoundFile: String? = nil
        if alarm.sound.hasPrefix("http://") || alarm.sound.hasPrefix("https://") {
            if let cachedURL = downloadAndCacheSoundSync(urlString: alarm.sound) {
                localSoundFile = cachedURL.path
                os_log("SetInc_Log: ✅ Sound cached: %{public}@", log: log, type: .error, localSoundFile!)
            } else {
                os_log("SetInc_Log: ❌ Sound download failed: %{public}@", log: log, type: .error, alarm.sound)
                DispatchQueue.main.async {
                    self.showDownloadFailedAlert(for: alarm.sound)
                }
            }
        } else if alarm.sound.hasPrefix("file://") {
            localSoundFile = alarm.sound
        }

        UNUserNotificationCenter.current().getPendingNotificationRequests { requests in
            let existingIDs = Set(requests.map { $0.identifier })
            let formatter = DateFormatter()
            formatter.dateFormat = "EEE, MMM d yyyy 'at' HH:mm"

            for fireDate in datesForNotification {
                let identifierSuffix = Int(fireDate.timeIntervalSince1970)
                let requestID = "\(alarm.uid)_\(identifierSuffix)"

                if existingIDs.contains(requestID) {
                    continue
                }

                let notificationContent = UNMutableNotificationContent()
                notificationContent.title = alarm.title
                notificationContent.body = alarm.description
                notificationContent.categoryIdentifier = alarm.snoozeEnabled ? Identifier.snoozeAlarmCategoryIndentifier : Identifier.alarmCategoryIndentifier

                if #available(iOS 12.0, *) {
                    notificationContent.sound = .defaultCritical
                } else {
                    notificationContent.sound = .default
                }

                notificationContent.userInfo = [
                    "snooze": alarm.snoozeEnabled,
                    "uid": alarm.uid,
                    "soundName": alarm.sound,
                    "localSoundPath": localSoundFile ?? "",
                    "vibration": alarm.vibration,
                    "volumeLevel": alarm.volumeLevel,
                    "timeZone": alarm.timeZone,
                ]

                let dateComponents = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute, .second], from: fireDate)
                let trigger = UNCalendarNotificationTrigger(dateMatching: dateComponents, repeats: false)
                let request = UNNotificationRequest(identifier: requestID, content: notificationContent, trigger: trigger)

                formatter.timeZone = TimeZone(identifier: alarm.timeZone)
                self.startManualAlarmTrigger(at: fireDate, soundName: alarm.sound, localPath: localSoundFile, uid: alarm.uid)

                UNUserNotificationCenter.current().add(request) { error in
                    if let e = error {
                        os_log("SetInc_Log: ❌ Failed to schedule notification %{public}@: %{public}@", log: self.log, type: .error, requestID, e.localizedDescription)
                    }
                }
            }
        }
    }

    func showDownloadFailedAlert(for url: String) {
        os_log("SetInc_Log: 📢 Preparing download failed alert for URL: %{public}@", log: log, type: .error, url)
        let alert = UIAlertController(title: "Download Failed", message: "Failed to download the alarm sound. The default sound will be played instead.", preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "OK", style: .default, handler: nil))

        if let topVC = topMostViewController() {
            topVC.present(alert, animated: true, completion: nil)
        } else {
            os_log("SetInc_Log: ❌ Could not find top view controller to present alert", log: log, type: .error)
        }
    }

    func topMostViewController() -> UIViewController? {
        guard var topController = UIApplication.shared.keyWindow?.rootViewController else {
            os_log("SetInc_Log: ❌ No root view controller found", log: log, type: .error)
            return nil
        }

        while let presentedViewController = topController.presentedViewController {
            topController = presentedViewController
        }
        return topController
    }

    func getLatestScheduledFireDate(forUID uid: String, completion: @escaping (Date?) -> Void) {
        UNUserNotificationCenter.current().getPendingNotificationRequests { requests in
            var latest: Date? = nil
            for request in requests {
                if request.identifier.hasPrefix(uid + "_"), let trigger = request.trigger as? UNCalendarNotificationTrigger, let date = trigger.nextTriggerDate() {
                    if latest == nil || date > latest! {
                        latest = date
                    }
                }
            }
            completion(latest)
        }
    }

    func startManualAlarmTrigger(at date: Date, soundName: String, localPath: String?, uid: String) {
        let timestamp = Int(date.timeIntervalSince1970)
        let pollingKey = "\(uid)_\(timestamp)"

        guard date > Date() else {
            os_log("SetInc_Log: ⚠️ Alarm %{public}@ is in the past — won't trigger", log: log, type: .error, pollingKey)
            return
        }

        if pollingThreads[pollingKey] == true {
            return
        }

        os_log("SetInc_Log: 🕒 Starting alarm polling for %{public}@ scheduled at %@", log: log, type: .error, pollingKey, date.description)
        pollingThreads[pollingKey] = true
        SilentAudioManager.shared.startSilentAudio()

        DispatchQueue.global(qos: .background).async {
            let log = OSLog(subsystem: Bundle.main.bundleIdentifier ?? "com.example.setinc", category: "alarm")
            while self.pollingThreads[pollingKey] == true {
                let now = Date()
                if now >= date {
                    let timeSinceAlarm = now.timeIntervalSince(date)
                    if timeSinceAlarm > 2 {
                        os_log("SetInc_Log: ⚠️ Alarm %{public}@ is too old (%fs) — skipping playback", log: log, type: .error, pollingKey, timeSinceAlarm)
                        self.stopPolling(for: pollingKey)
                        break
                    }

                    DispatchQueue.main.async {
                        os_log("SetInc_Log: ⏰ Polling triggered for alarm %{public}@ at %@", log: log, type: .error, pollingKey, now.description)
                        self.playAlarmSound(soundName: soundName, localPath: localPath, uid: uid)
                        self.stopPolling(for: pollingKey)
                    }
                    break
                }

                if let alarm = Store.shared.alarms.getAlarm(ByUUIDStr: uid), !alarm.active {
                    os_log("SetInc_Log: ❌ Alarm %{public}@ was deactivated — cancelling", log: log, type: .error, pollingKey)
                    self.stopPolling(for: pollingKey)
                    break
                }

                Thread.sleep(forTimeInterval: 1)
            }
        }
    }

    func playAlarmSound(soundName: String, localPath: String?, uid: String) {
        os_log("SetInc_Log: 🔊 Playing alarm sound for UID: %{public}@", log: log, type: .error, uid)
        let volumeLevel = Store.shared.alarms.getAlarm(ByUUIDStr: uid)?.volumeLevel ?? 1.0
        let vibration = Store.shared.alarms.getAlarm(ByUUIDStr: uid)?.vibration ?? true
        ExpoAlarmModule().playSound(soundName, localPath: localPath, uuid: uid, volume: volumeLevel, vibrate: vibration) {
            os_log("SetInc_Log: ✅ Play sound completed for UID: %{public}@", log: self.log, type: .error, uid)
        }
    }

    func stopPolling(for pollingKey: String) {
        os_log("SetInc_Log: 🛑 Stopping polling for %{public}@", log: log, type: .error, pollingKey)
        pollingThreads[pollingKey] = false
        os_log("SetInc_Log: ✅ Polling stopped for %{public}@", log: log, type: .error, pollingKey)
    }

    func restorePollingThreadsFromScheduledNotifications() {
        os_log("SetInc_Log: 🔄 Restoring polling threads from scheduled notifications", log: log, type: .error)
        UNUserNotificationCenter.current().getPendingNotificationRequests { requests in
            for request in requests {
                let id = request.identifier
                let components = id.split(separator: "_")
                guard components.count == 2 else {
                    continue
                }

                let uid = String(components[0])
                let timestampStr = String(components[1])
                guard let timestamp = TimeInterval(timestampStr) else {
                    continue
                }

                let fireDate = Date(timeIntervalSince1970: timestamp)
                let now = Date()
                if fireDate <= now {
                    continue
                }

                guard let alarm = Store.shared.alarms.getAlarm(ByUUIDStr: uid), alarm.active else {
                    continue
                }

                let localPath = request.content.userInfo["localSoundPath"] as? String
                let soundName = request.content.userInfo["soundName"] as? String ?? "default"
                DispatchQueue.main.async {
                    self.startManualAlarmTrigger(at: fireDate, soundName: soundName, localPath: localPath, uid: uid)
                }
            }
        }
    }

    func downloadAndCacheSoundSync(urlString: String) -> URL? {
        os_log("SetInc_Log: 🌐 Downloading and caching sound: %{public}@", log: log, type: .error, urlString)
        guard let url = URL(string: urlString) else {
            os_log("SetInc_Log: ❌ Invalid URL: %{public}@", log: log, type: .error, urlString)
            return nil
        }

        let filename = urlString.sha256() + ".wav"
        let cacheDir = FileManager.default.temporaryDirectory.appendingPathComponent("alarms", isDirectory: true)
        try? FileManager.default.createDirectory(at: cacheDir, withIntermediateDirectories: true)
        let targetURL = cacheDir.appendingPathComponent(filename)

        if FileManager.default.fileExists(atPath: targetURL.path) {
            os_log("SetInc_Log: 📦 Sound already cached: %{public}@", log: log, type: .error, targetURL.path)
            return targetURL
        }

        var resultURL: URL? = nil
        let semaphore = DispatchSemaphore(value: 0)

        URLSession.shared.downloadTask(with: url) { tempURL, _, error in
            defer { semaphore.signal() }
            if let tempURL = tempURL, error == nil {
                do {
                    try FileManager.default.copyItem(at: tempURL, to: targetURL)
                    os_log("SetInc_Log: ✅ Copied sound to: %{public}@", log: self.log, type: .error, targetURL.path)
                    resultURL = targetURL
                } catch {
                    os_log("SetInc_Log: ❌ Failed to copy downloaded sound: %{public}@", log: self.log, type: .error, error.localizedDescription)
                }
            } else {
                os_log("SetInc_Log: ❌ Failed to download sound: %{public}@", log: self.log, type: .error, error?.localizedDescription ?? "unknown")
            }
        }.resume()

        _ = semaphore.wait(timeout: .now() + 20)
        return resultURL
    }

    func setNotificationForSnooze(ringtoneName: String, snoozeMinute: Int, uid: String) {
        os_log("SetInc_Log: 😴 Setting snooze notification for UID: %{public}@", log: log, type: .error, uid)
        let currentAlarm = alarms.getAlarm(ByUUIDStr: uid)

        if let alarm = currentAlarm {
            let calendar = Calendar(identifier: .gregorian)
            let now = Date()
            let snoozeDate = (calendar as NSCalendar).date(byAdding: .minute, value: snoozeMinute, to: now, options: .matchStrictly)!
            setNotification(alarm: alarm)
            os_log("SetInc_Log: ✅ Snooze notification set for UID: %{public}@", log: log, type: .error, uid)
        } else {
            os_log("SetInc_Log: ❌ Error when setting notification for snooze, no alarm found for UID: %{public}@", log: log, type: .error, uid)
        }
    }

    func cancelNotification(ByUUIDStr uid: String) {
        os_log("SetInc_Log: 🗑️ Cancelling notifications for UID: %{public}@", log: log, type: .error, uid)
        UNUserNotificationCenter.current().getPendingNotificationRequests { requests in
            let matchingRequests = requests.filter { $0.identifier == uid || $0.identifier.hasPrefix("\(uid)_") }
            if matchingRequests.isEmpty {
                os_log("SetInc_Log: ⚠️ No matching notifications found to cancel for UID: %{public}@", log: self.log, type: .error, uid)
            } else {
                let identifiersToRemove = matchingRequests.map { $0.identifier }
                UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: identifiersToRemove)
                os_log("SetInc_Log: ✅ Removed %d notifications", log: self.log, type: .error, identifiersToRemove.count)
            }
        }
    }

    func updateNotification(ByUUIDStr uid: String, date: Date, ringtoneName: String, snoozeEnabled: Bool) {
        os_log("SetInc_Log: 🔄 Updating notification for UID: %{public}@", log: log, type: .error, uid)
        cancelNotification(ByUUIDStr: uid)
        let currentAlarm = alarms.getAlarm(ByUUIDStr: uid)

        if let alarm = currentAlarm {
            setNotification(alarm: alarm)
            os_log("SetInc_Log: ✅ Notification updated for UID: %{public}@", log: log, type: .error, uid)
        } else {
            os_log("SetInc_Log: ❌ Error updating notification, no alarm found for UID: %{public}@", log: log, type: .error, uid)
        }
    }

    enum weekdaysComparisonResult {
        case before
        case same
        case after
    }

    func compare(weekday w1: Int, with w2: Int) -> weekdaysComparisonResult {
        if w1 != 1 && (w1 < w2 || w2 == 1) {
            return .before
        } else if w1 == w2 {
            return .same
        } else {
            return .after
        }
    }
}

extension String {
    func sha256() -> String {
        guard let data = self.data(using: .utf8) else {
            os_log("SetInc_Log: ❌ Failed to convert string to data", log: OSLog(subsystem: Bundle.main.bundleIdentifier ?? "com.example.setinc", category: "alarm"), type: .error)
            return self
        }
        var hash = [UInt8](repeating: 0, count: Int(CC_SHA256_DIGEST_LENGTH))
        data.withUnsafeBytes {
            _ = CC_SHA256($0.baseAddress, CC_LONG(data.count), &hash)
        }
        let result = hash.map { String(format: "%02x", $0) }.joined()
        return result
    }
}

class SilentAudioManager {
    static let shared = SilentAudioManager()
    private var player: AVAudioPlayer?
    private let log = OSLog(subsystem: Bundle.main.bundleIdentifier ?? "com.example.setinc", category: "alarm")

    func startSilentAudio() {
        os_log("SetInc_Log: 🔊 Starting silent audio", log: log, type: .error)
        guard player == nil else {
            os_log("SetInc_Log: ℹ️ Silent audio already playing", log: log, type: .error)
            return
        }

        guard let path = Bundle.main.path(forResource: "bell", ofType: "mp3") else {
            os_log("SetInc_Log: ❌ Silent audio file not found in bundle", log: log, type: .error)
            return
        }

        let url = URL(fileURLWithPath: path)
        do {
            try AVAudioSession.sharedInstance().setCategory(.playback, mode: .default)
            try AVAudioSession.sharedInstance().setActive(true)
            player = try AVAudioPlayer(contentsOf: url)
            player?.numberOfLoops = -1
            player?.volume = 0.0
            player?.prepareToPlay()
            let success = player?.play() ?? false
            os_log("SetInc_Log: %@", log: log, type: success ? .info : .error, success ? "🎵 Silent audio started playing" : "❌ Failed to play silent audio")
        } catch {
            os_log("SetInc_Log: ❌ Error starting silent audio: %{public}@", log: log, type: .error, error.localizedDescription)
        }
    }

    func stopSilentAudio() {
        os_log("SetInc_Log: 🛑 Stopping silent audio", log: log, type: .error)
        player?.stop()
        player = nil
        os_log("SetInc_Log: ✅ Silent audio stopped", log: log, type: .error)
    }
}