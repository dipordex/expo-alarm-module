import EventKit
import AVFoundation
import CommonCrypto

@objc(ExpoAlarmModule)
class ExpoAlarmModule: NSObject, UNUserNotificationCenterDelegate, AVAudioPlayerDelegate  {
    var isEditMode = false
    public static var audioPlayer: AVAudioPlayer?
    
    private let notificationScheduler: NotificationSchedulerDelegate = NotificationScheduler()
    private let manager: Manager = Manager();
    
    public override init() {
        super.init()
        do {
            try AVAudioSession.sharedInstance().setCategory(AVAudioSession.Category.playback)
        } catch let error as NSError{
            print("could not set session. err:\(error.localizedDescription)")
        }
        do {
            try AVAudioSession.sharedInstance().setActive(true)
        } catch let error as NSError{
            print("could not active session. err:\(error.localizedDescription)")
        }
        
        notificationScheduler.requestAuthorization()
        notificationScheduler.registerNotificationCategories()
        notificationScheduler.restorePollingThreadsFromScheduledNotifications()
        UNUserNotificationCenter.current().delegate = self
        
        // Add observer for applicationDidBecomeActive
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(applicationDidBecomeActive),
            name: UIApplication.didBecomeActiveNotification,
            object: nil
        )
    }
    
    @objc(multiply:withB:withResolver:withRejecter:)
    func multiply(a: Float, b: Float, resolve:RCTPromiseResolveBlock, reject:RCTPromiseRejectBlock) -> Void {
        resolve((a*b))
    }
    
    @objc(set:withResolver:withRejecter:)
    func set(alarm: NSDictionary, resolve:RCTPromiseResolveBlock, reject:RCTPromiseRejectBlock) -> Void {
        let alarmToUse = Alarm(dictionary: alarm as! NSMutableDictionary);
        
        manager.schedule(alarmToUse);
        
        resolve(nil)
    }
    
    @objc(enable:withResolver:withRejecter:)
    func enable(uid: String, resolve: RCTPromiseResolveBlock, reject: RCTPromiseRejectBlock) -> Void {
        print("📥 Received enable call with uid =", uid)
        if uid.isEmpty {
            print("⚠️ UID is empty. Aborting.")
            reject("E_UID_EMPTY", "UID is empty", nil)
            return
        }

        manager.enable(uid)
        resolve(nil)
    }

    
    @objc(disable:withResolver:withRejecter:)
    func disable(uid: String, resolve:RCTPromiseResolveBlock, reject:RCTPromiseRejectBlock) -> Void {
        manager.disable(uid)
        
        resolve(nil)
    }
    
    
    @objc(stop)
    func stop() -> Void {
        manager.stop();
    }
    
    @objc(get:withResolver:withRejecter:)
    func get(uid: String, resolve:RCTPromiseResolveBlock, reject:RCTPromiseRejectBlock) -> Void {
        let alarm: Alarm! = manager.getAlarm(uid);
        if(alarm != nil) {
            let alarmSerialized: NSDictionary = alarm.toDictionary();
            resolve(alarmSerialized)
        } else {
            resolve(nil)
        }
    }
    
    @objc(getAll:withRejecter:)
    func getAll(resolve:RCTPromiseResolveBlock, reject:RCTPromiseRejectBlock) -> Void {
        let alarmArray: [Alarm] = manager.getAllAlarms();
        
        let alarmDictionaryArray = alarmArray.map { alarm -> NSDictionary in
            return alarm.toDictionary()
        }
        
        if(alarmArray.count > 0) {
            resolve(alarmDictionaryArray)
        } else {
            resolve(nil)
        }    }
    
    @objc(remove:withResolver:withRejecter:)
    func remove(uid: String, resolve:RCTPromiseResolveBlock, reject:RCTPromiseRejectBlock) -> Void {
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
    
    @objc(removeAll:withRejecter:)
    func removeAll(resolve:RCTPromiseResolveBlock, reject:RCTPromiseRejectBlock) -> Void {
        manager.removeAll();
        
        resolve(nil)
    }
    
    @objc(getState:withRejecter:)
    func getState(resolve:RCTPromiseResolveBlock, reject:RCTPromiseRejectBlock) -> Void {
        resolve(manager.getCurrentPlayingAlarm())
    }
    
    
    // The method will be called on the delegate only if the application is in the foreground. If the method is not implemented or the handler is not called in a timely manner then the notification will not be presented. The application can choose to have the notification presented as a sound, badge, alert and/or in the notification list. This decision should be based on whether the information in the notification is otherwise visible to the user.
    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification, withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        print("🔔 willPresent triggered for notification: \(notification.request.identifier)")
        let userInfo = notification.request.content.userInfo
        print("📦 Notification userInfo: \(userInfo)")

        guard
            let snoozeEnabled = userInfo["snooze"] as? Bool,
            let soundName = userInfo["soundName"] as? String,
            let uidStr = userInfo["uid"] as? String
        else {
            print("❌ Missing expected fields in notification userInfo")
            completionHandler([])
            return
        }

        manager.setCurrentPlayingAlarm(uidStr)

        let localPath = userInfo["localSoundPath"] as? String
        let volumeLevel = userInfo["volumeLevel"] as? Float ?? 1.0
        let vibration = userInfo["vibration"] as? Bool ?? true


        self.playSound(soundName, localPath: localPath, uuid: uidStr, volume: volumeLevel, vibrate: vibration) {
            DispatchQueue.main.async {
                if #available(iOS 14.0, *) {
                    completionHandler([.sound, .banner, .list])
                } else {
                    completionHandler(.alert)
                }
            }
        }
        
    }
    
    
    
    @objc func applicationDidBecomeActive() {
        notificationScheduler.syncAlarmStateWithNotification()
        rescheduleAllActiveAlarms()
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
                    print("⚠️ No scheduled fireDate for alarm \(alarm.uid). Scheduling fresh...")
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
    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse, withCompletionHandler completionHandler: @escaping () -> Void) {
        print("📲 [didReceive] Notification: \(response.notification.request.identifier)")
        let userInfo = response.notification.request.content.userInfo
        guard
            let soundName = userInfo["soundName"] as? String,
            let uid = userInfo["uid"] as? String
        else {return}
        
        switch response.actionIdentifier {
        case Identifier.snoozeActionIdentifier:
            // notification fired when app in background, snooze button clicked
            notificationScheduler.setNotificationForSnooze(ringtoneName: soundName, snoozeMinute: 9, uid: uid)
            break
        case Identifier.stopActionIdentifier:
            // notification fired when app in background, ok button clicked
            let alarms = Store.shared.alarms
            break
        default:
            break
        }
        self.stop()
        completionHandler()
    }
    
    
    //AlarmApplicationDelegate protocol
    func playSound(_ soundName: String, localPath: String? = nil, uuid: String, volume: Float = 1.0, vibrate: Bool = true, completion: @escaping () -> Void) {
        let alarm = manager.getAlarm(uuid)
        guard let active = alarm?.active else { return }
        print("is Alarm active? \(active) with uuid: \(uuid)")
        
        print("🔊 Attempting to play sound: \(soundName)")

        // Vibrate first
        // 🔔 Vibrate if enabled
        if vibrate {
            AudioServicesPlaySystemSound(SystemSoundID(kSystemSoundID_Vibrate))
            AudioServicesAddSystemSoundCompletion(SystemSoundID(kSystemSoundID_Vibrate), nil, nil, { _, _ in
                AudioServicesPlaySystemSound(SystemSoundID(kSystemSoundID_Vibrate))
            }, nil)
        }

        

        // MARK: - Fallback to bell.mp3 from Bundle
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
                } catch {
                    print("❌ AVAudioPlayer fallback error: \(error.localizedDescription)")
                }
            } else {
                print("❌ Fallback bell.mp3 not found in bundle")
            }
            completion()
        }

        // MARK: - Play from local file
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
            } catch {
                print("❌ AVAudioPlayer error: \(error.localizedDescription)")
                if active {
                    playBundledFallbackSound()
                }
            }
            completion()
        }

        // ✅ Play from localPath if exists
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
                print("❌ File not found at path: \(fileURL.path)")
            }
        }

        // ✅ Try to play a bundled sound with the given name
        if let bundledURL = Bundle.main.url(forResource: soundName, withExtension: "mp3") {
            print("📁 Playing bundled resource: \(soundName).mp3")
            if active {
                playLocalFile(from: bundledURL)
            }
            return
        }

        // ❌ Nothing worked, fallback
        print("⚠️ Neither local nor bundled file found — falling back.")
        if active {
            playBundledFallbackSound()
        }
    }
    
    func deleteCachedSound(for uid: String) {
        guard let alarm = manager.getAlarm(uid) else {
            print("❌ No alarm found with uid \(uid) to delete cached file")
            return
        }

        // Only delete if it's a remote URL
        guard alarm.sound.hasPrefix("http://") || alarm.sound.hasPrefix("https://") else {
            print("🛑 Not a remote sound, skipping delete for: \(alarm.sound)")
            return
        }

        let hashedFileName = alarm.sound.sha256() + ".wav"
        let fileURL = FileManager.default.temporaryDirectory
            .appendingPathComponent("alarms", isDirectory: true)
            .appendingPathComponent(hashedFileName)

        if FileManager.default.fileExists(atPath: fileURL.path) {
            do {
                try FileManager.default.removeItem(at: fileURL)
                print("🗑️ Deleted cached sound at: \(fileURL.path)")
            } catch {
                print("❌ Failed to delete cached sound: \(error.localizedDescription)")
            }
        } else {
            print("⚠️ No cached file to delete at: \(fileURL.path)")
        }
    }

}
