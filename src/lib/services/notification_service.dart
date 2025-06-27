import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:timezone/timezone.dart' as tz;
import 'package:timezone/data/latest_all.dart' as tz;
import 'package:flutter_background_service/flutter_background_service.dart';
import '../models/alarm_model.dart';
import '../main.dart';

class NotificationService {
  static final NotificationService _instance = NotificationService._internal();
  factory NotificationService() => _instance;
  NotificationService._internal();

  final FlutterLocalNotificationsPlugin _flutterLocalNotificationsPlugin =
      FlutterLocalNotificationsPlugin();

  Future<void> init() async {
    tz.initializeTimeZones();
    // Use your local time zone
    tz.setLocalLocation(tz.getLocation('Asia/Jerusalem'));

    const AndroidInitializationSettings initializationSettingsAndroid =
        AndroidInitializationSettings('@mipmap/ic_launcher');

    final DarwinInitializationSettings initializationSettingsIOS =
        DarwinInitializationSettings(
      requestAlertPermission: true,
      requestBadgePermission: true,
      requestSoundPermission: true,
    );

    final InitializationSettings initializationSettings =
        InitializationSettings(
      android: initializationSettingsAndroid,
      iOS: initializationSettingsIOS,
    );

    await _flutterLocalNotificationsPlugin.initialize(
      initializationSettings,
      onDidReceiveNotificationResponse: (details) {
        if (details.payload != null) {
          navigatorKey.currentState
              ?.pushNamed('/ring', arguments: details.payload);
        }
      },
    );
  }

  tz.TZDateTime _nextInstanceOfTime(Alarm alarm) {
    final tz.TZDateTime now = tz.TZDateTime.now(tz.local);
    tz.TZDateTime scheduledDate = tz.TZDateTime(tz.local, now.year, now.month,
        now.day, alarm.time.hour, alarm.time.minute);

    // If it's a one-time alarm
    if (!alarm.days.any((day) => day)) {
      if (scheduledDate.isBefore(now)) {
        scheduledDate = scheduledDate.add(const Duration(days: 1));
      }
      return scheduledDate;
    }

    // If it's a repeating alarm
    while (
        scheduledDate.isBefore(now) || !alarm.days[scheduledDate.weekday % 7]) {
      scheduledDate = scheduledDate.add(const Duration(days: 1));
    }
    return scheduledDate;
  }

  Future<void> scheduleAlarmNotification(Alarm alarm) async {
    final tz.TZDateTime scheduledDate = _nextInstanceOfTime(alarm);

    final AndroidNotificationDetails androidPlatformChannelSpecifics =
        AndroidNotificationDetails(
      'alarm_channel',
      'Alarms',
      channelDescription: 'Channel for alarm notifications',
      importance: Importance.max,
      priority: Priority.high,
      sound: RawResourceAndroidNotificationSound('alarm_sound'),
      playSound: true,
      fullScreenIntent: true, // This is crucial
      ongoing: true, // Make the notification persistent
      autoCancel: false, // Prevent the notification from being dismissed
    );

    final NotificationDetails platformChannelSpecifics = NotificationDetails(
      android: androidPlatformChannelSpecifics,
    );

    await _flutterLocalNotificationsPlugin.zonedSchedule(
      alarm.id.hashCode,
      alarm.label,
      'זמן לקום!',
      scheduledDate,
      platformChannelSpecifics,
      payload: alarm.id,
      androidScheduleMode: AndroidScheduleMode.exactAllowWhileIdle,
      matchDateTimeComponents: alarm.days.any((day) => day)
          ? DateTimeComponents.time
          : null, // Repeat weekly if days are set
    );
  }

  Future<void> cancelNotification(String alarmId) async {
    await _flutterLocalNotificationsPlugin.cancel(alarmId.hashCode);
    // Stop foreground service when the alarm is cancelled
    FlutterBackgroundService().invoke("stopService");
  }
}
