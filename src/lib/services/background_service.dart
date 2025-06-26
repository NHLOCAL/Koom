import 'dart:async';
import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:flutter_background_service/flutter_background_service.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:puzzle_alarm/models/alarm_model.dart';
import 'package:puzzle_alarm/main.dart'; // נייבא את ה-navigatorKey
import 'dart:convert';

Future<void> initializeService() async {
  final service = FlutterBackgroundService();
  await service.configure(
    androidConfiguration: AndroidConfiguration(
      onStart: onStart,
      isForegroundMode: true,
      autoStart: true,
    ),
    iosConfiguration: IosConfiguration(onForeground: onStart, autoStart: true),
  );
}

@pragma('vm:entry-point')
void onStart(ServiceInstance service) {
  DartPluginRegistrant.ensureInitialized();

  Timer.periodic(const Duration(seconds: 30), (timer) async {
    final prefs = await SharedPreferences.getInstance();
    final alarmsJson = prefs.getStringList('alarms') ?? [];
    if (alarmsJson.isEmpty) return;

    final List<Alarm> alarms = alarmsJson
        .map((e) => Alarm.fromJson(jsonDecode(e)))
        .where((alarm) => alarm.isActive)
        .toList();

    final now = DateTime.now();
    final currentTime = TimeOfDay.fromDateTime(now);

    for (var alarm in alarms) {
      // יום ראשון הוא 7, שני 1 וכו'. נתאים לאינדקס שלנו (ראשון = 0)
      int today = now.weekday % 7;

      bool isRepeatToday = alarm.days.any((d) => d) && alarm.days[today];
      bool isOneTimeAlarm = !alarm.days.any((d) => d);

      if (alarm.time.hour == currentTime.hour &&
          alarm.time.minute == currentTime.minute &&
          (isRepeatToday || isOneTimeAlarm)) {
        // בדוק אם כבר הפעלנו את השעון בדקה זו
        final lastTriggered = prefs.getString('last_triggered_${alarm.id}');
        final nowStr =
            '${now.year}-${now.month}-${now.day}-${now.hour}-${now.minute}';
        if (lastTriggered != nowStr) {
          // הפעל את מסך הצלצול
          // זו דרך להריץ קוד על ה-UI thread הראשי
          final navigator = navigatorKey.currentState;
          if (navigator != null) {
            navigator.pushNamed('/ring', arguments: alarm.id);
          }
          await prefs.setString('last_triggered_${alarm.id}', nowStr);

          // אם זה שעון חד פעמי, כבה אותו
          if (isOneTimeAlarm) {
            alarm.isActive = false;
            // צריך לשמור את השינוי חזרה
            final alarmIndex = alarmsJson.indexWhere(
              (e) => jsonDecode(e)['id'] == alarm.id,
            );
            if (alarmIndex != -1) {
              alarmsJson[alarmIndex] = jsonEncode(alarm.toJson());
              await prefs.setStringList('alarms', alarmsJson);
            }
          }
        }
      }
    }
  });
}
