import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:flutter_background_service/flutter_background_service.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../models/alarm_model.dart';
import '../main.dart';

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

  // --- Desktop-only Timer Logic ---
  // This part runs only on Windows/Linux/macOS because they don't support
  // scheduled notifications like mobile platforms do.
  if (Platform.isWindows || Platform.isLinux || Platform.isMacOS) {
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
        int today = now.weekday % 7; // Sunday = 0, Monday = 1, etc.

        bool isRepeatToday = alarm.days.any((d) => d) && alarm.days[today];
        bool isOneTimeAlarm = !alarm.days.any((d) => d);

        if (alarm.time.hour == currentTime.hour &&
            alarm.time.minute == currentTime.minute &&
            (isRepeatToday || isOneTimeAlarm)) {
          final lastTriggered = prefs.getString('last_triggered_${alarm.id}');
          final nowStr =
              '${now.year}-${now.month}-${now.day}-${now.hour}-${now.minute}';

          if (lastTriggered != nowStr) {
            // On desktop, we can try to navigate if the app is open.
            final navigator = navigatorKey.currentState;
            if (navigator != null) {
              navigator.pushNamed('/ring', arguments: alarm.id);
            }
            await prefs.setString('last_triggered_${alarm.id}', nowStr);

            if (isOneTimeAlarm) {
              alarm.isActive = false;
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

  service.on('stopService').listen((event) {
    service.stopSelf();
  });
}
