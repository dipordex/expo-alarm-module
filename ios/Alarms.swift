import Foundation

class Alarms: Codable {
    private var alarms: [Alarm]
    
    enum CodingKeys: CodingKey {
        case alarms
    }
    
    init() {
        self.alarms = [Alarm]()
    }
    
    required init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.alarms = try container.decode([Alarm].self, forKey: .alarms)
    }
    
    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(self.alarms, forKey: .alarms)
    }
    
    func add(_ alarm: Alarm) {
        alarms.append(alarm)
        let newIndex = alarms.firstIndex { $0.uid == alarm.uid }!
        Store.shared.save(self, notifying: alarm, userInfo: [
            Alarm.changeReasonKey: Alarm.added,
            Alarm.newValueKey: newIndex
        ])
    }
    
    func remove(_ uid: String) {
        guard let index = alarms.firstIndex(where: { $0.uid == uid }) else { return }
        let alarm = alarms[index]
        let uuidStr = alarm.uid
        alarms.remove(at: index)
        Store.shared.save(self, notifying: nil, userInfo: [
            Alarm.changeReasonKey: Alarm.removed,
            Alarm.oldValueKey: index,
            Alarm.newValueKey: uuidStr
        ])
    }
    
    func update(_ alarm: Alarm) {
        guard let index = alarms.firstIndex(where: { $0.uid == alarm.uid }) else { return }
        alarms[index] = alarm
        Store.shared.save(self, notifying: alarm, userInfo: [
            Alarm.changeReasonKey: Alarm.updated,
            Alarm.oldValueKey: index,
            Alarm.newValueKey: index
        ])
    }
    
    func getAlarm(ByUUIDStr uuidString: String) -> Alarm? {
        return alarms.first(where: { $0.uid == uuidString })
    }

    func getAlarms() -> [Alarm] {
        return alarms
    }
    
    var count: Int {
        return alarms.count
    }

    var all: [Alarm] {
        return alarms
    }

    var uids: Set<String> {
        return Set(alarms.map { $0.uid })
    }
    
    subscript(index: Int) -> Alarm {
        return alarms[index]
    }

    func logStoredAlarms() {
        print("🧠 Stored alarm UIDs: \(uids)")
        for alarm in alarms {
            print(" • UID: \(alarm.uid), Title: \(alarm.title), Active: \(alarm.active), Days: \(alarm.days ?? [])")
        }
    }
}
