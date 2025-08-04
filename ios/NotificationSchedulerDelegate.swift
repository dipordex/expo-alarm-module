import Foundation
import UIKit

protocol NotificationSchedulerDelegate {
    func requestAuthorization()
    func registerNotificationCategories()
    func setNotification(alarm: Alarm)
    func setNotificationForSnooze(ringtoneName: String, snoozeMinute: Int, uid: String)
    func getLatestScheduledFireDate(forUID uid: String, completion: @escaping (Date?) -> Void)
    func cancelNotification(ByUUIDStr uid: String)
    func updateNotification(ByUUIDStr uid: String, date: Date, ringtoneName: String, snoonzeEnabled: Bool)
    func syncAlarmStateWithNotification()
    func restorePollingThreadsFromScheduledNotifications()
}

