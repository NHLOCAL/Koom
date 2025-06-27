import 'dart:async';
import 'dart:ui';
import 'package:flutter_background_service/flutter_background_service.dart';

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

  // This service is now just a placeholder to keep the app alive if needed,
  // but it no longer handles alarm checking logic.
  // The actual scheduling is now handled by flutter_local_notifications.

  service.on('stopService').listen((event) {
    service.stopSelf();
  });
}
