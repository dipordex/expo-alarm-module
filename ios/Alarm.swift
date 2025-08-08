import Foundation

class Alarm: Codable {
    let uid: String
    var date: Date
    var active: Bool
    var showSnooze: Bool
    var snoozeInterval: Int = 5
    var title: String
    var description: String
    var sound: String = "bell"
    var days: [Int]? = nil // <-- NEW: repeating days (e.g., [2, 4, 6] for Mon/Wed/Fri)
    var vibration: Bool = false
    var volumeLevel: Float = 1.0
    var timeZone: String = "Local Time"
    
    
    convenience init() {
        self.init(uid: "", date: Date(), active: true, showSnooze: false, snoozeInterval: 5, title: "Alarm", description: "", vibration: true, volumeLevel: 1.0, timeZone: "Local Time")
    }
    
    init(uid: String, date: Date, active: Bool, showSnooze: Bool, snoozeInterval: Int, title: String, description: String, sound: String = "bell", days: [Int]? = nil, vibration: Bool, volumeLevel: Float, timeZone: String) {
        self.uid = uid
        self.date = date
        self.active = active
        self.showSnooze = showSnooze
        self.snoozeInterval = snoozeInterval
        self.title = title
        self.description = description
        self.sound = sound
        self.days = days
        self.vibration = vibration
        self.volumeLevel = volumeLevel
        self.timeZone = timeZone
    }
    
    init(dictionary: NSMutableDictionary) {
        
        let dateFormatter = DateFormatter()
        dateFormatter.dateFormat = "yyyy-MM-dd'T'HH:mm:ss.SSSZ"
        
        self.uid = dictionary["uid"] as? String ?? "";
        self.active = dictionary["active"] as? Bool ?? false;
        self.showSnooze = dictionary["showSnooze"] as? Bool ?? false
        self.snoozeInterval = dictionary["snoozeInterval"] as? Int ?? 5
        self.title = dictionary["title"] as? String ?? ""
        self.description = dictionary["description"] as? String ?? ""
        self.sound = dictionary["sound"] as? String ?? "bell"
        
        // Converts the Date
        // Date (for one-time alarms)
        if let dateString = dictionary["day"] as? String,
           let date = dateFormatter.date(from: dateString) {
            self.date = date
        } else {
            self.date = Date()
        }
        
        // Days array (for repeating alarms)
        if let rawDays = dictionary["days"] as? [Int] {
            self.days = rawDays
        }
        
        self.vibration = dictionary["vibration"] as? Bool ?? false
        if let rawVolume = dictionary["volumeLevel"] as? Float {
            self.volumeLevel = min(max(rawVolume / 100.0, 0.0), 1.0)
        } else {
            self.volumeLevel = 1.0
        }
        
        if let tz = dictionary["timeZone"] as? String, !tz.isEmpty {
            self.timeZone = tz
        } else {
            print("⚠️ Missing or invalid timeZone in dictionary")
            self.timeZone = "Local Time"
        }
        
        
        
        print("\u{1F4E5} Alarm initialized from dictionary:")
        print("  • uid: \(uid)")
        print("  • title: \(title)")
        print("  • sound: \(sound)")
        print("  • active: \(active)")
        print("  • showSnooze: \(showSnooze)")
        print("  • snoozeInterval: \(snoozeInterval)")
        print("  • date: \(date)")
        print("  - days: \(String(describing: days))")
        print("  • vibration: \(vibration)")
        print("  • volumeLevel: \(volumeLevel)")
        print("  • timeZone: \(timeZone)")
        
    }
    
    enum CodingKeys: CodingKey {
        case uid
        case date
        case active
        case showSnooze
        case snoozeInterval
        case title
        case description
        case days
        case vibration
        case volumeLevel
        case timeZone
    }
    
    required init(from decoder: Decoder) throws {
        
        let container: KeyedDecodingContainer<Alarm.CodingKeys> = try decoder.container(keyedBy: Alarm.CodingKeys.self)
        
        self.uid = try container.decode(String.self, forKey: Alarm.CodingKeys.uid)
        self.date = try container.decode(Date.self, forKey: Alarm.CodingKeys.date)
        self.active = try container.decode(Bool.self, forKey: Alarm.CodingKeys.active)
        self.showSnooze = try container.decode(Bool.self, forKey: Alarm.CodingKeys.showSnooze)
        self.snoozeInterval = try container.decode(Int.self, forKey: Alarm.CodingKeys.snoozeInterval)
        self.title = try container.decode(String.self, forKey: Alarm.CodingKeys.title)
        self.description = try container.decode(String.self, forKey: Alarm.CodingKeys.description)
        self.days = try container.decodeIfPresent([Int].self, forKey: Alarm.CodingKeys.days)
        self.vibration = try container.decodeIfPresent(Bool.self, forKey: Alarm.CodingKeys.vibration) ?? false
        self.volumeLevel = try container.decodeIfPresent(Float.self, forKey: Alarm.CodingKeys.volumeLevel) ?? 1.0
        self.timeZone = try container.decodeIfPresent(String.self, forKey: Alarm.CodingKeys.timeZone) ?? "Local Time"
        
    }
    
    func encode(to encoder: Encoder) throws {
        var container: KeyedEncodingContainer<Alarm.CodingKeys> = encoder.container(keyedBy: Alarm.CodingKeys.self)
        
        try container.encode(self.uid, forKey: Alarm.CodingKeys.uid)
        try container.encode(self.date, forKey: Alarm.CodingKeys.date)
        try container.encode(self.active, forKey: Alarm.CodingKeys.active)
        try container.encode(self.showSnooze, forKey: Alarm.CodingKeys.showSnooze)
        try container.encode(self.snoozeInterval, forKey: Alarm.CodingKeys.snoozeInterval)
        try container.encode(self.title, forKey: Alarm.CodingKeys.title)
        try container.encode(self.description, forKey: Alarm.CodingKeys.description)
        try container.encode(self.days, forKey: Alarm.CodingKeys.days)
        try container.encode(self.vibration, forKey: Alarm.CodingKeys.vibration)
        try container.encode(self.volumeLevel, forKey: Alarm.CodingKeys.volumeLevel)
        try container.encode(self.timeZone, forKey: Alarm.CodingKeys.timeZone)
        
    }
    
    
    
    func toDictionary() -> NSDictionary {
        let alarm: Alarm = self;
        
        let alarmDictionary: NSDictionary = [
            "uid": alarm.uid,
            "day": alarm.date.timeIntervalSince1970,
            "active": alarm.active,
            "showSnooze": alarm.showSnooze,
            "snoozeInterval": alarm.snoozeInterval,
            "title": alarm.title,
            "description": alarm.description,
            "sound": alarm.sound,
            "days": alarm.days as Any,
            "vibration": alarm.vibration,
            "volumeLevel": alarm.volumeLevel,
            "timeZone": alarm.timeZone
        ]
        
        return alarmDictionary;
    }
}

extension Alarm {
    var formattedTime: String {
        let dateFormatter = DateFormatter()
        dateFormatter.dateFormat = "h:mm a"
        return dateFormatter.string(from: self.date)
    }
}

extension Alarm {
    static let changeReasonKey = "reason"
    static let newValueKey = "newValue"
    static let oldValueKey = "oldValue"
    static let updated = "updated"
    static let added = "added"
    static let removed = "removed"
}

