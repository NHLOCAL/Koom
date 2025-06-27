import 'dart:async';
import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:flutter_background_service/flutter_background_service.dart';
import 'package:flutter_background_service_android/flutter_background_service_android.dart';
import 'notification_service.dart';

Future<void> initializeService() async {
  final service = FlutterBackgroundService();

  // Initialize notification service to ensure channel is created.
  await NotificationService().init();

  await service.configure(
    androidConfiguration: AndroidConfiguration(
      onStart: onStart,
      isForegroundMode: true,
      autoStart: true,
      // This notification is required for a foreground service on Android.
      // It lets the user know the app is running in the background.
      notificationChannelId:
          'alarm_channel', // Must match the one in NotificationService
      initialNotificationTitle: 'WakeWise פעיל',
      initialNotificationContent: 'השעונים המעוררים שלך מוגדרים.',
      foregroundServiceNotificationId: 888,
    ),
    iosConfiguration: IosConfiguration(
      onForeground: onStart,
      autoStart: true,
    ),
  );
}

@pragma('vm:entry-point')
void onStart(ServiceInstance service) {
  DartPluginRegistrant.ensureInitialized();

  // If you are using flutter_background_service for Android,
  // it is better to listen for events from the UI.
  if (service is AndroidServiceInstance) {
    service.on('setAsForeground').listen((event) {
      service.setAsForegroundService();
    });

    service.on('setAsBackground').listen((event) {
      service.setAsBackgroundService();
    });
  }

  service.on('stopService').listen((event) {
    service.stopSelf();
  });

  // The main logic for scheduling alarms is handled by `NotificationService`
  // using `zonedSchedule`. This background service's primary role is to keep
  // the app process alive so alarms are not missed, which is a common issue
  // on some Android manufacturer devices (like Xiaomi, Huawei, etc.).
  // The periodic timer that was here before was inefficient and buggy.
  debugPrint("שירות הרקע של WakeWise התחיל.");
}
