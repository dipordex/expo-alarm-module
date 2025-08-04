import { toAndroidDays, fromAndroidDays, getParam, fromIOSDays } from '../utils';

class Alarm {
  uid?: string | undefined;
  title?: string | undefined;
  description?: string | undefined;
  hour?: number | undefined;
  minutes?: number | undefined;
  showDismiss?: boolean | undefined;
  dismissText?: string | undefined;
  showSnooze?: boolean | undefined;
  snoozeInterval?: number | undefined;
  snoozeText?: string | undefined;
  repeating?: boolean | undefined;
  active?: boolean | undefined;
  day?: string | Date | number[] | undefined;
  days?: number[] | undefined;               // For weekly repeating alarms
  sound?: string | undefined
  timeZone?: string;
  volumeLevel?: number;
  vibration?: boolean;

  constructor(params: any = null) {
    this.uid = getParam(params, 'uid');
    this.title = getParam(params, 'title');
    this.description = getParam(params, 'description');
    this.hour = getParam(params, 'hour');
    this.minutes = getParam(params, 'minutes');
    this.showDismiss = getParam(params, 'showDismiss');
    this.dismissText = getParam(params, 'dismissText');
    this.showSnooze = getParam(params, 'showSnooze');
    this.snoozeInterval = getParam(params, 'snoozeInterval');
    this.snoozeText = getParam(params, 'snoozeText');
    this.repeating = getParam(params, 'repeating');
    this.active = getParam(params, 'active');
    this.day = getParam(params, 'day');
    this.days = getParam(params, 'days');
    this.sound = getParam(params, 'sound');
    this.timeZone    = getParam(params, 'timeZone')    ?? Intl.DateTimeFormat().resolvedOptions().timeZone;
    this.volumeLevel = getParam(params, 'volumeLevel') ?? 1.0;
    this.vibration   = getParam(params, 'vibration')   ?? true;
  }

  static getEmpty() {
    return new Alarm({
      title: '',
      description: '',
      hour: 0,
      minutes: 0,
      repeating: false,
      day: [],
       days: [],
       timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone,
      volumeLevel: 1.0,
      vibration: true,
    });
  }

  toAndroid() {
    return {
      ...this,
      day: toAndroidDays(this.day),
      days: this.days,
      timeZone: this.timeZone,
      volumeLevel: this.volumeLevel,
      vibration: this.vibration,
    };
  }

  static fromAndroid(alarm: Alarm) {
    alarm.day = fromAndroidDays(alarm.day);
    return new Alarm(alarm);
  }

  static fromIos(alarm: Alarm) {
    if (typeof alarm.day === 'number') {
      alarm.day = fromIOSDays(alarm.day);
    }
    return new Alarm(alarm);
  }
}

export default Alarm;
