import AVFoundation
import CommonCrypto
import EventKit
import os.log


@objc(ExpoAlarmModule)
final class ExpoAlarmModule: RCTEventEmitter, UNUserNotificationCenterDelegate, AVAudioPlayerDelegate
{
    var isEditMode = false
    public static var audioPlayer: AVAudioPlayer?
    //    private static var instanceCounter = 0
    //    private let instanceID: Int
    
    private static weak var _shared: ExpoAlarmModule?
    
    static var shared: ExpoAlarmModule {
        guard let instance = _shared else {
            fatalError("ExpoAlarmModule.shared accessed before initialization")
        }
        return instance
    }
    
    var alarmMissTimeout: TimeInterval = 60
    
    
    private let notificationScheduler: NotificationSchedulerDelegate = NotificationScheduler()
    private let manager: Manager = Manager()
    private let log = OSLog(subsystem: Bundle.main.bundleIdentifier ?? "com.example.setinc", category: "alarm")
    
    private override init() {
        
        //        ExpoAlarmModule.instanceCounter += 1
        //        self.instanceID = ExpoAlarmModule.instanceCounter
        
        super.init()
        ExpoAlarmModule._shared = self
        
        //        os_log("SetInc_Log: 🆕 Initializing ExpoAlarmModule instance #%d", log: log, type: .info, instanceID)
        do {
            try AVAudioSession.sharedInstance().setCategory(AVAudioSession.Category.playback)
        } catch let error as NSError {
            os_log("SetInc_Log: ❌ Failed to set AVAudioSession category: %{public}@", log: log, type: .error, error.localizedDescription)
            print("could not set session. err:\(error.localizedDescription)")
        }
        do {
            try AVAudioSession.sharedInstance().setActive(true)
        } catch let error as NSError {
            os_log("SetInc_Log: ❌ Failed to activate AVAudioSession: %{public}@", log: log, type: .error, error.localizedDescription)
            print("could not active session. err:\(error.localizedDescription)")
        }
        
        notificationScheduler.requestAuthorization()
        notificationScheduler.registerNotificationCategories()
        notificationScheduler.restorePollingThreadsFromScheduledNotifications()
        //        startBridgeStatusLogging()
        UNUserNotificationCenter.current().delegate = self
        
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(applicationDidBecomeActive),
            name: UIApplication.didBecomeActiveNotification,
            object: nil
        )
        
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(applicationWillTerminate),
            name: UIApplication.willTerminateNotification,
            object: nil
        )
        
    }
    
    //    private func startBridgeStatusLogging() {
    //        Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { [weak self] _ in
    //            guard let self = self else { return }
    //            os_log("SetInc_Log: ℹ️ [Instance #%d] Bridge status: %{public}@",
    //                   log: self.log,
    //                   type: .info,
    //                   self.instanceID,
    //                   self.bridge != nil ? "Available" : "Nil")
    //        }
    //    }
    
    override static func requiresMainQueueSetup() -> Bool {
        return true
    }
    
    @objc
    override func supportedEvents() -> [String]! {
        return ["onAlarmNotificationTapped", "onAlarmSnoozeTapped", "onAlarmDismissTapped", "onAlarmMissed", "onNotificationTapped"]
    }
    
    // Call this from AppDelegate or Notification Delegate
    func emitAlarmTappedEvent(uid: String, title: String, timeString: String, id: String) {
        sendEvent(withName: "onAlarmNotificationTapped", body: [
            "uid": uid,
            "title": title,
            "time": timeString,
            "notificationId": id
        ])
    }
    
    func emitAlarmSnoozeTappedEvent(uid: String) {
        sendEvent(withName: "onAlarmSnoozeTapped", body: [
            "uid": uid,
        ])
    }
    
    func emitAlarmDismissTappedEvent(uid: String) {
        sendEvent(withName: "onAlarmDismissTapped", body: [
            "uid": uid,
        ])
    }
    
    func emitAlarmMissedEvent(uid: String, title: String) {
        sendEvent(withName: "onAlarmMissed", body: [
            "uid": uid,
            "title": title,
        ])
    }
    
    func emitNotificationTappedEvent(userInfo: [AnyHashable : Any]) {
        sendEvent(withName: "onNotificationTapped", body: [
            "userInfo": userInfo
        ])
    }
    
    
    @objc(multiply:withB:withResolver:withRejecter:)
    func multiply(a: Float, b: Float, resolve: RCTPromiseResolveBlock, reject: RCTPromiseRejectBlock) {
        resolve((a * b))
    }
    
    @objc(set:withResolver:withRejecter:)
    func set(alarm: NSDictionary, resolve: RCTPromiseResolveBlock, reject: RCTPromiseRejectBlock) {
        let alarmToUse = Alarm(dictionary: alarm as! NSMutableDictionary)
        manager.schedule(alarmToUse)
        resolve(nil)
    }
    
    @objc(enable:withResolver:withRejecter:)
    func enable(uid: String, resolve: RCTPromiseResolveBlock, reject: RCTPromiseRejectBlock) {
        if uid.isEmpty {
            os_log("SetInc_Log: ❌ enable rejected due to empty UID", log: log, type: .error)
            print("⚠️ UID is empty. Aborting.")
            reject("E_UID_EMPTY", "UID is empty", nil)
            return
        }
        manager.enable(uid)
        resolve(nil)
    }
    
    @objc(disable:withResolver:withRejecter:)
    func disable(uid: String, resolve: RCTPromiseResolveBlock, reject: RCTPromiseRejectBlock) {
        manager.disable(uid)
        resolve(nil)
    }
    
    @objc(stop)
    func stop() {
        manager.stop()
    }
    
    @objc(get:withResolver:withRejecter:)
    func get(uid: String, resolve: RCTPromiseResolveBlock, reject: RCTPromiseRejectBlock) {
        let alarm: Alarm! = manager.getAlarm(uid)
        if alarm != nil {
            let alarmSerialized: NSDictionary = alarm.toDictionary()
            resolve(alarmSerialized)
        } else {
            os_log("SetInc_Log: ⚠️ No alarm found for UID: %{public}@", log: log, type: .error, uid)
            resolve(nil)
        }
    }
    
    @objc(getAll:withRejecter:)
    func getAll(resolve: RCTPromiseResolveBlock, reject: RCTPromiseRejectBlock) {
        let alarmArray: [Alarm] = manager.getAllAlarms()
        let alarmDictionaryArray = alarmArray.map { $0.toDictionary() }
        if alarmArray.count > 0 {
            resolve(alarmDictionaryArray)
        } else {
            os_log("SetInc_Log: ⚠️ No alarms found", log: log, type: .error)
            resolve(nil)
        }
    }
    
    @objc(remove:withResolver:withRejecter:)
    func remove(uid: String, resolve: RCTPromiseResolveBlock, reject: RCTPromiseRejectBlock) {
        if let currentlyPlayingUID = manager.getCurrentPlayingAlarm() {
            manager.stop()
            deleteCachedSound(for: currentlyPlayingUID)
        } else {
            manager.stop()
            deleteCachedSound(for: uid)
        }
        manager.remove(uid)
        resolve(nil)
    }
    
    @objc(snooze:withRejecter:)
    func snooze(resolve: RCTPromiseResolveBlock, reject: RCTPromiseRejectBlock) {
        // Get the currently playing alarm UID
        guard let uid = manager.getCurrentPlayingAlarm(),
              let alarm = manager.getAlarm(uid) else {
            reject("E_NO_ALARM", "No alarm is currently playing", nil)
            return
        }
        
        // Stop the current alarm sound
        self.stop()
        
        // Use a default snooze interval (e.g., 5 minutes)
        let defaultSnoozeMinutes = 5
        notificationScheduler.setNotificationForSnooze(
            ringtoneName: alarm.sound,
            snoozeMinute: defaultSnoozeMinutes,
            uid: uid
        )
        
        resolve(nil)
    }
    
    @objc(removeAll:withRejecter:)
    func removeAll(resolve: RCTPromiseResolveBlock, reject: RCTPromiseRejectBlock) {
        manager.removeAll()
        resolve(nil)
    }
    
    @objc(getState:withRejecter:)
    func getState(resolve: RCTPromiseResolveBlock, reject: RCTPromiseRejectBlock) {
        let currentAlarm = manager.getCurrentPlayingAlarm()
        resolve(currentAlarm)
    }
    
    // The method will be called on the delegate only if the application is in the foreground. If the method is not implemented or the handler is not called in a timely manner then the notification will not be presented. The application can choose to have the notification presented as a sound, badge, alert and/or in the notification list. This decision should be based on whether the information in the notification is otherwise visible to the user.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        let userInfo = notification.request.content.userInfo
        print("🔔 willPresent triggered for notification: \(userInfo)")
        
        guard
            let title = userInfo["title"] as? String,
            let snoozeEnabled = userInfo["snooze"] as? Bool,
            let soundName = userInfo["soundName"] as? String,
            let uidStr = userInfo["uid"] as? String
        else {
            os_log("SetInc_Log: ❌ Missing expected fields in notification userInfo", log: log, type: .error)
            if #available(iOS 14.0, *) {
                completionHandler([.sound, .banner, .list])
            } else {
                completionHandler(.alert)
            }
            return
        }
        
        manager.setCurrentPlayingAlarm(uidStr)
        let localPath = userInfo["localSoundPath"] as? String
        let volumeLevel = userInfo["volumeLevel"] as? Float ?? 1.0
        let vibration = userInfo["vibration"] as? Bool ?? true
        self.playSound(soundName, localPath: localPath, uuid: uidStr, volume: volumeLevel, vibrate: vibration) {
            DispatchQueue.main.async {
                if #available(iOS 14.0, *) {
                    completionHandler([.list])
                } else {
                    completionHandler(.alert)
                }
            }
        }
        let timeString = userInfo["timeString"] as? String ?? ""
        self.emitAlarmTappedEvent(uid: uidStr, title: title, timeString: timeString,id: notification.request.identifier)
    }
    
    @objc func applicationDidBecomeActive() {
        notificationScheduler.syncAlarmStateWithNotification()
        rescheduleAllActiveAlarms()
        SilentAudioManager.shared.startSilentAudio()
    }
    
    @objc func applicationWillTerminate() {
        print("App will terminate!")
        // Check if any active alarms exist
        let alarms = manager.getAllAlarms()
        let activeAlarms = alarms.filter { $0.active }
        
        guard !activeAlarms.isEmpty else {
            print("🚫 No active alarms → Skipping termination warning notification")
            return
        }
        // Only schedule warning if alarms are active
        let content = UNMutableNotificationContent()
        content.title = "⏰ Alarm notice"
        content.body = "Alarms will not ring if the app is terminated."
        content.sound = UNNotificationSound.default
        
        // Fire after 5 seconds (or any time you choose)
        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: 1, repeats: false)
        let request = UNNotificationRequest(
            identifier: "termination_warning",
            content: content,
            trigger: trigger
        )
        UNUserNotificationCenter.current().add(request) { error in
            if let error = error {
                print("Failed to schedule warning: \(error)")
            } else {
                print("⚠️ Scheduled termination warning notification")
            }
        }
    }
    
    private func rescheduleAllActiveAlarms() {
        let alarms = manager.getAllAlarms()
        let now = Date()
        let oneWeekFromNow = now.addingTimeInterval(7 * 24 * 60 * 60)
        
        for alarm in alarms {
            guard alarm.active else {
                print("🚫 Alarm \(alarm.uid) is inactive. Skipping reschedule.")
                continue
            }
            
            notificationScheduler.getLatestScheduledFireDate(forUID: alarm.uid) { latest in
                guard let latest = latest else {
                    os_log("SetInc_Log: ⚠️ No scheduled fireDate for alarm %@. Scheduling fresh...", log: self.log, type: .error, alarm.uid)
                    self.notificationScheduler.setNotification(alarm: alarm)
                    return
                }
                
                if latest >= now && latest <= oneWeekFromNow {
                    print("🔄 Latest scheduled fireDate \(latest) is within last scheduled week → Rescheduling alarm \(alarm.uid)...")
                    self.notificationScheduler.setNotification(alarm: alarm)
                } else {
                    print("✅ Alarm \(alarm.uid) is already scheduled beyond one week (until \(latest)). Skipping reschedule.")
                }
            }
        }
    }
    
    // The method will be called on the delegate when the user responded to the notification by opening the application, dismissing the notification or choosing a UNNotificationAction. The delegate must be set before the application returns from application:didFinishLaunchingWithOptions:.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let userInfo = response.notification.request.content.userInfo
        print("📲 [didReceive] Notification: \(response.notification) \(response.notification.request)", userInfo)
        guard
            let title = userInfo["title"] as? String,
            let soundName = userInfo["soundName"] as? String,
            let uid = userInfo["uid"] as? String,
            let snoozeInterval = userInfo["snoozeInterval"] as? Int
        else {
            os_log("SetInc_Log: ❌ Missing soundName or uid in userInfo", log: log, type: .error)
            completionHandler()
            emitNotificationTappedEvent(userInfo: userInfo)
            return
        }
        
        switch response.actionIdentifier {
        case Identifier.snoozeActionIdentifier:
            self.stop()
            notificationScheduler.setNotificationForSnooze(ringtoneName: soundName, snoozeMinute: snoozeInterval, uid: uid)
            self.emitAlarmSnoozeTappedEvent(uid: uid)
        case Identifier.stopActionIdentifier:
            self.stop()
            self.emitAlarmDismissTappedEvent(uid: uid)
            
        default:
            os_log("SetInc_Log: ⚠️ Unknown action identifier: %{public}@", log: log, type: .error, response.actionIdentifier)
            let timeString = userInfo["timeString"] as? String ?? ""
            self.emitAlarmTappedEvent(uid: uid, title: title, timeString: timeString, id: response.notification.request.identifier)
        }
        completionHandler()
    }
    
    private func isSoundAlreadyPlaying() -> Bool {
        return ExpoAlarmModule.audioPlayer?.isPlaying == true
    }
    
    func playSound(
        _ soundName: String, localPath: String? = nil, uuid: String,
        volume: Float = 1.0, vibrate: Bool = true,
        completion: @escaping () -> Void
    ) {
        let alarm = manager.getAlarm(uuid)
        guard let active = alarm?.active else {
            os_log("SetInc_Log: ❌ No alarm found or inactive for UID: %{public}@", log: log, type: .error, uuid)
            completion()
            return
        }
        print("is Alarm active? \(active) with uuid: \(uuid)")
        guard !isSoundAlreadyPlaying() else {
            os_log("SetInc_Log: 🔇 Suppressing sound for alarm %{public}@ — another sound is already playing", log: log, type: .error, uuid)
            completion()
            return
        }
        print("🔊 Attempting to play sound: \(soundName)")
        if vibrate {
            AudioServicesPlaySystemSound(SystemSoundID(kSystemSoundID_Vibrate))
            AudioServicesAddSystemSoundCompletion(SystemSoundID(kSystemSoundID_Vibrate), nil, nil, { _, _ in
                AudioServicesPlaySystemSound(SystemSoundID(kSystemSoundID_Vibrate))
            }, nil)
        }
        
        func playBundledFallbackSound() {
            let fallbackName = "bell"
            if let fallbackURL = Bundle.main.url(forResource: fallbackName, withExtension: "mp3") {
                do {
                    ExpoAlarmModule.audioPlayer = try AVAudioPlayer(contentsOf: fallbackURL)
                    ExpoAlarmModule.audioPlayer?.delegate = self
                    ExpoAlarmModule.audioPlayer?.numberOfLoops = -1
                    ExpoAlarmModule.audioPlayer?.volume = volume
                    ExpoAlarmModule.audioPlayer?.prepareToPlay()
                    ExpoAlarmModule.audioPlayer?.play()
                    print("✅ Playing fallback bundled sound: \(fallbackName).mp3")
                    manager.setCurrentPlayingAlarm(uuid)
                    
                    // Add a timer to stop the alarm
                      Timer.scheduledTimer(withTimeInterval: alarmMissTimeout, repeats: false) { [weak self] _ in
                    guard let self = self else { return }
                    self.stop()
                    guard let alarm = self.manager.getAlarm(uuid) else {
                        print("No alarm found, not firing missed event")
                        return
                    }
                    guard alarm.active == true else {
                        print("Alarm is not active, not firing missed event")
                        return
                    }
                    self.emitAlarmMissedEvent(uid: uuid, title: alarm.description)
                    os_log("SetInc_Log: ⏰ Alarm stopped automatically after %d seconds for UID: %{public}@",
                           log: self.log, type: .info, Int(self.alarmMissTimeout),uuid)
                }
                } catch {
                    os_log("SetInc_Log: ❌ AVAudioPlayer fallback error: %{public}@", log: log, type: .error, error.localizedDescription)
                }
            } else {
                os_log("SetInc_Log: ❌ Fallback bell.mp3 not found in bundle", log: log, type: .error)
            }
            completion()
        }
        
        func playLocalFile(from url: URL) {
            do {
                ExpoAlarmModule.audioPlayer = try AVAudioPlayer(contentsOf: url)
                ExpoAlarmModule.audioPlayer?.delegate = self
                ExpoAlarmModule.audioPlayer?.numberOfLoops = -1
                ExpoAlarmModule.audioPlayer?.prepareToPlay()
                ExpoAlarmModule.audioPlayer?.volume = volume
                ExpoAlarmModule.audioPlayer?.play()
                print("✅ Playing sound from: \(url.path)")
                manager.setCurrentPlayingAlarm(uuid)
                // Add a timer to stop the alarm
                 Timer.scheduledTimer(withTimeInterval: alarmMissTimeout, repeats: false) { [weak self] _ in
                    guard let self = self else { return }
                    self.stop()
                    guard let alarm = self.manager.getAlarm(uuid) else {
                        print("No alarm found, not firing missed event")
                        return
                    }
                    guard alarm.active == true else {
                        print("Alarm is not active, not firing missed event")
                        return
                    }
                    self.emitAlarmMissedEvent(uid: uuid, title: alarm.description)
                    os_log("SetInc_Log: ⏰ Alarm stopped automatically after %d seconds for UID: %{public}@",
                           log: self.log, type: .info, Int(self.alarmMissTimeout),uuid)
                }
            } catch {
                os_log("SetInc_Log: ❌ AVAudioPlayer error: %{public}@", log: log, type: .error, error.localizedDescription)
                if active {
                    playBundledFallbackSound()
                }
            }
            completion()
        }
        
        if let localPath = localPath {
            let cleanedPath = localPath.replacingOccurrences(of: "file://", with: "")
            let fileURL = URL(fileURLWithPath: cleanedPath)
            if FileManager.default.fileExists(atPath: fileURL.path) {
                print("📦 Using system or local file at: \(fileURL.path)")
                if active {
                    playLocalFile(from: fileURL)
                }
                return
            } else {
                os_log("SetInc_Log: ❌ File not found at path: %{public}@", log: log, type: .error, fileURL.path)
            }
        }
        
        if let bundledURL = Bundle.main.url(forResource: soundName, withExtension: "mp3") {
            print("📁 Playing bundled resource: \(soundName).mp3")
            if active {
                playLocalFile(from: bundledURL)
            }
            return
        }
        
        os_log("SetInc_Log: ⚠️ Neither local nor bundled file found — falling back", log: log, type: .error)
        if active {
            playBundledFallbackSound()
        }
    }
    
    func deleteCachedSound(for uid: String) {
        guard let alarm = manager.getAlarm(uid) else {
            os_log("SetInc_Log: ❌ No alarm found with uid %{public}@ to delete cached file", log: log, type: .error, uid)
            return
        }
        guard alarm.sound.hasPrefix("http://") || alarm.sound.hasPrefix("https://") else {
            print("🛑 Not a remote sound, skipping delete for: \(alarm.sound)")
            return
        }
        let hashedFileName = alarm.sound.sha256() + ".wav"
        let fileURL = FileManager.default.temporaryDirectory.appendingPathComponent("alarms", isDirectory: true).appendingPathComponent(hashedFileName)
        if FileManager.default.fileExists(atPath: fileURL.path) {
            do {
                try FileManager.default.removeItem(at: fileURL)
                print("🗑️ Deleted cached sound at: \(fileURL.path)")
            } catch {
                os_log("SetInc_Log: ❌ Failed to delete cached sound: %{public}@", log: log, type: .error, error.localizedDescription)
            }
        } else {
            os_log("SetInc_Log: ⚠️ No cached file to delete at: %{public}@", log: log, type: .error, fileURL.path)
        }
    }
}
