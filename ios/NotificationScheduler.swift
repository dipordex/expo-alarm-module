import AVFoundation

import CommonCrypto

import Foundation

import UIKit

import UserNotifications

class NotificationScheduler: NotificationSchedulerDelegate {
    private let alarms: Alarms = Store.shared.alarms
    private var pollingThreads: [String: Bool] = [:]

    // we need to request user for notifiction permission first
    func requestAuthorization() {
        UNUserNotificationCenter.current().requestAuthorization(options: [
            .alert, .sound,
        ]) {
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
        let snoozeAction = UNNotificationAction(
            identifier: Identifier.snoozeActionIdentifier, title: "Snooze",
            options: [.foreground])
        let stopAction = UNNotificationAction(
            identifier: Identifier.stopActionIdentifier, title: "OK",
            options: [.foreground])

        let snoonzeActions = [snoozeAction, stopAction]
        let nonSnoozeActions = [stopAction]

        let snoozeAlarmCategory = UNNotificationCategory(
            identifier: Identifier.snoozeAlarmCategoryIndentifier,
            actions: snoonzeActions,
            intentIdentifiers: [],
            hiddenPreviewsBodyPlaceholder: "",
            options: .customDismissAction)

        let nonSnoozeAlarmCategroy = UNNotificationCategory(
            identifier: Identifier.alarmCategoryIndentifier,
            actions: nonSnoozeActions,
            intentIdentifiers: [],
            hiddenPreviewsBodyPlaceholder: "",
            options: .customDismissAction)
        // Register the notification category
        UNUserNotificationCenter.current().setNotificationCategories([
            snoozeAlarmCategory, nonSnoozeAlarmCategroy,
        ])
    }

    // sync alarm state to scheduled notifications for some situation (app in background and user didn't click notification to bring the app to foreground) that
    // alarm state is not updated correctly
    func syncAlarmStateWithNotification() {
        UNUserNotificationCenter.current().getPendingNotificationRequests(
            completionHandler: {
                requests in
                print(requests)
                let alarms = Store.shared.alarms
                let uuidNotificationsSet = Set(
                    requests.map({ $0.content.userInfo["uid"] as! String }))
                let uuidAlarmsSet = alarms.uids
                let uuidDeltaSet = uuidAlarmsSet.subtracting(
                    uuidNotificationsSet)

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

    private func getNotificationDates(for alarm: Alarm) -> [Date] {
        var notificationDates: [Date] = []
        var calendar = Calendar(identifier: .gregorian)
        if let tz = TimeZone(identifier: alarm.timeZone) {
            calendar.timeZone = tz
        }

        let now = Date()

        guard let repeatDays = alarm.days, !repeatDays.isEmpty else {
            let corrected = NotificationScheduler.correctSecondComponent(
                date: alarm.date)
            return corrected < now
                ? [calendar.date(byAdding: .day, value: 1, to: corrected)!]
                : [corrected]
        }

        let numberOfWeeksToSchedule = 6

        for weekOffset in 0..<numberOfWeeksToSchedule {
            for weekday in repeatDays {
                var components = calendar.dateComponents(
                    [.yearForWeekOfYear, .weekOfYear], from: now)
                components.weekday = weekday

                // Get the base weekday (this week + offset)
                if let weekdayDate = calendar.date(from: components),
                    let targetDate = calendar.date(
                        byAdding: .weekOfYear, value: weekOffset,
                        to: weekdayDate)
                {

                    var alarmComponents = calendar.dateComponents(
                        [.hour, .minute], from: alarm.date)
                    var finalDateComponents = calendar.dateComponents(
                        [.year, .month, .day], from: targetDate)
                    finalDateComponents.hour = alarmComponents.hour
                    finalDateComponents.minute = alarmComponents.minute
                    finalDateComponents.second = 0

                    if let finalDate = calendar.date(from: finalDateComponents),
                        finalDate >= now
                    {
                        let corrected =
                            NotificationScheduler.correctSecondComponent(
                                date: finalDate)
                        notificationDates.append(corrected)
                    }
                }
            }
        }

        return notificationDates
    }

    static func correctSecondComponent(
        date: Date, calendar: Calendar = Calendar(identifier: .gregorian)
    ) -> Date {
        let second = calendar.component(.second, from: date)
        return (calendar as NSCalendar).date(
            byAdding: .second, value: -second, to: date, options: .matchStrictly
        )!
    }

    func setNotification(alarm: Alarm) {
        let datesForNotification = getNotificationDates(for: alarm)

        // 📥 Handle sound
        var localSoundFile: String? = nil
        if alarm.sound.hasPrefix("http://") || alarm.sound.hasPrefix("https://")
        {
            print("🌐 Downloading remote sound: \(alarm.sound)")
            if let cachedURL = downloadAndCacheSoundSync(urlString: alarm.sound)
            {
                localSoundFile = cachedURL.path
                print("✅ Sound cached: \(localSoundFile!)")
            } else {
                print("❌ Sound download failed: \(alarm.sound)")
                DispatchQueue.main.async {
                    self.showDownloadFailedAlert(for: alarm.sound)
                }

            }
        } else if alarm.sound.hasPrefix("file://") {
            localSoundFile = alarm.sound
        }

        UNUserNotificationCenter.current().getPendingNotificationRequests {
            requests in
            let existingIDs = Set(requests.map { $0.identifier })

            // 🔔 Begin scheduling
            print(
                "\n🔔 Scheduling alarm '\(alarm.uid)' [\(alarm.title)] for \(datesForNotification.count) dates:"
            )

            let formatter = DateFormatter()
            formatter.dateFormat = "EEE, MMM d yyyy 'at' HH:mm"

            for fireDate in datesForNotification {
                let identifierSuffix = Int(fireDate.timeIntervalSince1970)
                let requestID = "\(alarm.uid)_\(identifierSuffix)"

                if existingIDs.contains(requestID) {
                    print("⏭ Skipping already scheduled: \(requestID)")
                    continue
                }

                let notificationContent = UNMutableNotificationContent()
                notificationContent.title = alarm.title
                notificationContent.body = alarm.description
                notificationContent.categoryIdentifier =
                    alarm.snoozeEnabled
                    ? Identifier.snoozeAlarmCategoryIndentifier
                    : Identifier.alarmCategoryIndentifier

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

                let dateComponents = Calendar.current.dateComponents(
                    [.year, .month, .day, .hour, .minute, .second],
                    from: fireDate)
                let trigger = UNCalendarNotificationTrigger(
                    dateMatching: dateComponents, repeats: false)

                let request = UNNotificationRequest(
                    identifier: requestID, content: notificationContent,
                    trigger: trigger)

                // ✅ Log nicely formatted
                formatter.timeZone = TimeZone(identifier: alarm.timeZone)
                let formattedDate = formatter.string(from: fireDate)
                print(
                    "• 🔔 \(formattedDate) [\(alarm.timeZone)] → ID: \(requestID)"
                )

                self.startManualAlarmTrigger(
                    at: fireDate, soundName: alarm.sound,
                    localPath: localSoundFile, uid: alarm.uid)

                UNUserNotificationCenter.current().add(request) { error in
                    if let e = error {
                        print(
                            "  ❌ Failed to schedule \(requestID): \(e.localizedDescription)"
                        )
                    }
                }
            }

            print("✅ Done scheduling alarm '\(alarm.uid)'\n")
        }
    }

    func showDownloadFailedAlert(for url: String) {
        let alert = UIAlertController(
            title: "Download Failed",
            message:
                "Failed to download the alarm sound. The default sound will be played instead.",
            preferredStyle: .alert)
        alert.addAction(
            UIAlertAction(title: "OK", style: .default, handler: nil))

        if let topVC = topMostViewController() {
            topVC.present(alert, animated: true, completion: nil)
        } else {
            print("⚠️ Could not find top view controller to present alert.")
        }
    }

    func topMostViewController() -> UIViewController? {
        guard
            var topController = UIApplication.shared.keyWindow?
                .rootViewController
        else {
            return nil
        }

        while let presentedViewController = topController
            .presentedViewController
        {
            topController = presentedViewController
        }

        return topController
    }

    func getLatestScheduledFireDate(
        forUID uid: String, completion: @escaping (Date?) -> Void
    ) {
        UNUserNotificationCenter.current().getPendingNotificationRequests {
            requests in
            var latest: Date? = nil

            for request in requests {
                if request.identifier.hasPrefix(uid + "_"),
                    let trigger = request.trigger
                        as? UNCalendarNotificationTrigger,
                    let date = trigger.nextTriggerDate()
                {
                    if latest == nil || date > latest! {
                        latest = date
                    }
                }
            }

            completion(latest)
        }
    }

    func startManualAlarmTrigger(
        at date: Date, soundName: String, localPath: String?, uid: String
    ) {
        // Build a unique pollingKey using UID + timestamp
        let timestamp = Int(date.timeIntervalSince1970)
        let pollingKey = "\(uid)_\(timestamp)"

        guard date > Date() else {
            print("⚠️ Alarm \(pollingKey) is in the past — won't trigger.")
            return
        }

        // Prevent duplicate polling threads for same key
        if pollingThreads[pollingKey] == true {
            print("⏭ Polling already active for alarm \(pollingKey)")
            return
        }

        print("🕒 Starting alarm polling for \(pollingKey) scheduled at \(date)")
        pollingThreads[pollingKey] = true
        SilentAudioManager.shared.startSilentAudio()

        DispatchQueue.global(qos: .background).async { [weak self] in
            while self?.pollingThreads[pollingKey] == true {
                let now = Date()

                if now >= date {

                    // Prevent triggering if the scheduled time is more than 10 seconds in the past
                    let timeSinceAlarm = now.timeIntervalSince(date)
                    if timeSinceAlarm > 2 {  // seconds passed
                        print(
                            "⚠️ Alarm \(pollingKey) is too old (\(timeSinceAlarm)s) — skipping playback."
                        )
                        self?.stopPolling(for: pollingKey)
                        break
                    }

                    DispatchQueue.main.async {
                        print(
                            "⏰ [Polling Triggered] Alarm \(pollingKey) fired at \(now)"
                        )
                        self?.playAlarmSound(
                            soundName: soundName, localPath: localPath, uid: uid
                        )
                        self?.stopPolling(for: pollingKey)
                    }
                    break
                }

                // Cancel polling if alarm is no longer active
                if let alarm = Store.shared.alarms.getAlarm(ByUUIDStr: uid),
                    !alarm.active
                {
                    print("❌ Alarm \(pollingKey) was deactivated — cancelling.")
                    self?.stopPolling(for: pollingKey)
                    break
                }

                Thread.sleep(forTimeInterval: 1)
            }
        }
    }

    func playAlarmSound(soundName: String, localPath: String?, uid: String) {
        let volumeLevel =
            Store.shared.alarms.getAlarm(ByUUIDStr: uid)?.volumeLevel ?? 1.0
        let vibration =
            Store.shared.alarms.getAlarm(ByUUIDStr: uid)?.vibration ?? true
        ExpoAlarmModule().playSound(
            soundName, localPath: localPath, uuid: uid, volume: volumeLevel,
            vibrate: vibration
        ) {
            print("Playing from Notification Scheduler")
        }
    }

    func stopPolling(for pollingKey: String) {
        pollingThreads[pollingKey] = false
        SilentAudioManager.shared.stopSilentAudio()
        print("🛑 Stopped polling for \(pollingKey)")
    }

    func restorePollingThreadsFromScheduledNotifications() {
        print("🔄 [Restore] Checking for pending notification requests...")

        UNUserNotificationCenter.current().getPendingNotificationRequests {
            requests in
            print("📋 Found \(requests.count) pending notifications")

            for request in requests {
                let id = request.identifier
                print("🔍 Processing notification ID: \(id)")

                // Expect ID format: uid_timestamp
                let components = id.split(separator: "_")
                guard components.count == 2 else {
                    print("⚠️ Invalid ID format, skipping: \(id)")
                    continue
                }

                let uid = String(components[0])
                let timestampStr = String(components[1])

                guard let timestamp = TimeInterval(timestampStr) else {
                    print("⚠️ Invalid timestamp in ID: \(timestampStr)")
                    continue
                }

                let fireDate = Date(timeIntervalSince1970: timestamp)
                let now = Date()

                if fireDate <= now {
                    print(
                        "⏩ Alarm \(uid) scheduled at \(fireDate) is in the past — skipping"
                    )
                    continue
                }

                guard let alarm = Store.shared.alarms.getAlarm(ByUUIDStr: uid)
                else {
                    print("❌ No alarm found in store for uid: \(uid)")
                    continue
                }

                if !alarm.active {
                    print("🚫 Alarm \(uid) is not active — skipping")
                    continue
                }

                let localPath =
                    request.content.userInfo["localSoundPath"] as? String
                let soundName =
                    request.content.userInfo["soundName"] as? String
                    ?? "default"

                print(
                    "✅ Restoring polling for alarm \(uid) at \(fireDate) [Sound: \(soundName)]"
                )

                DispatchQueue.main.async {
                    self.startManualAlarmTrigger(
                        at: fireDate, soundName: soundName,
                        localPath: localPath, uid: uid)
                }
            }
        }
    }

    func downloadAndCacheSoundSync(urlString: String) -> URL? {
        guard let url = URL(string: urlString) else { return nil }

        let filename = urlString.sha256() + ".wav"
        let cacheDir = FileManager.default.temporaryDirectory
            .appendingPathComponent("alarms", isDirectory: true)

        // Ensure directory exists
        try? FileManager.default.createDirectory(
            at: cacheDir, withIntermediateDirectories: true)

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
                    print(
                        "❌ Failed to copy downloaded sound: \(error.localizedDescription)"
                    )
                }
            } else {
                print(
                    "❌ Failed to download sound: \(error?.localizedDescription ?? "unknown")"
                )
            }
        }.resume()

        _ = semaphore.wait(timeout: .now() + 20)  // wait max 20 sec
        return resultURL
    }

    func setNotificationForSnooze(
        ringtoneName: String, snoozeMinute: Int, uid: String
    ) {
        let currentAlarm = alarms.getAlarm(ByUUIDStr: uid)

        if currentAlarm != nil {
            let calendar = Calendar(identifier: Calendar.Identifier.gregorian)
            let now = Date()
            let snoozeDate = (calendar as NSCalendar).date(
                byAdding: NSCalendar.Unit.minute, value: snoozeMinute, to: now,
                options: .matchStrictly)!
            setNotification(alarm: currentAlarm!)
        } else {
            print("Error when setting notification for snooze")
        }
    }

    func cancelNotification(ByUUIDStr uid: String) {
        UNUserNotificationCenter.current().getPendingNotificationRequests {
            requests in
            let matchingRequests = requests.filter { request in
                request.identifier == uid
                    || request.identifier.hasPrefix("\(uid)_")
            }

            if matchingRequests.isEmpty {
                print(
                    "⚠️ No matching notifications found to cancel for UID: \(uid)"
                )
            } else {
                for request in matchingRequests {
                    print(
                        "🗑️ Cancelling notification with ID: \(request.identifier)"
                    )
                }
                let identifiersToRemove = matchingRequests.map { $0.identifier }
                UNUserNotificationCenter.current()
                    .removePendingNotificationRequests(
                        withIdentifiers: identifiersToRemove)
            }
        }
    }

    func updateNotification(
        ByUUIDStr uid: String, date: Date, ringtoneName: String,
        snoonzeEnabled: Bool
    ) {
        cancelNotification(ByUUIDStr: uid)
        let currentAlarm = alarms.getAlarm(ByUUIDStr: uid)

        if currentAlarm != nil {
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
        guard let data = self.data(using: .utf8) else { return self }
        var hash = [UInt8](repeating: 0, count: Int(CC_SHA256_DIGEST_LENGTH))

        data.withUnsafeBytes {
            _ = CC_SHA256($0.baseAddress, CC_LONG(data.count), &hash)
        }

        return hash.map { String(format: "%02x", $0) }.joined()
    }
}
class SilentAudioManager {
    static let shared = SilentAudioManager()
    private var player: AVAudioPlayer?

    func startSilentAudio() {
        guard player == nil else {
            print("ℹ️ Silent audio already playing")
            return
        }

        guard let path = Bundle.main.path(forResource: "bell", ofType: "mp3")
        else {
            print("❌ Silent audio file not found in bundle")
            return
        }

        let url = URL(fileURLWithPath: path)

        do {
            try AVAudioSession.sharedInstance().setCategory(
                .playback, mode: .default)
            try AVAudioSession.sharedInstance().setActive(true)

            player = try AVAudioPlayer(contentsOf: url)
            player?.numberOfLoops = -1
            player?.volume = 0.0
            player?.prepareToPlay()

            let success = player?.play() ?? false
            print(
                success
                    ? "🎵 Silent audio started playing"
                    : "❌ Failed to play silent audio")
        } catch {
            print("❌ Error starting silent audio: \(error)")
        }
    }

    func stopSilentAudio() {
        player?.stop()
        print("🛑 Silent audio playback stopped.")
    }
}
