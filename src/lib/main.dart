import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter_background_service/flutter_background_service.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:provider/provider.dart';
import 'package:permission_handler/permission_handler.dart';

import 'providers/alarm_provider.dart';
import 'screens/home_screen.dart';
import 'screens/alarm_edit_screen.dart';
import 'screens/alarm_ring_screen.dart';
import 'models/alarm_model.dart';
import 'services/background_service.dart';
import 'services/notification_service.dart';

final GlobalKey<NavigatorState> navigatorKey = GlobalKey<NavigatorState>();

Future<void> _requestPermissions() async {
  if (await Permission.notification.isDenied) {
    await Permission.notification.request();
  }

  if (await Permission.scheduleExactAlarm.isDenied) {
    await Permission.scheduleExactAlarm.request();
  }
}

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // It's important to initialize NotificationService first to set up listeners.
  await NotificationService().init();

  if (Platform.isAndroid || Platform.isIOS) {
    await _requestPermissions();
    await initializeService();
    // Start the service explicitly after configuring it.
    await FlutterBackgroundService().startService();
  } else if (Platform.isWindows || Platform.isLinux || Platform.isMacOS) {
    await initializeService();
    await FlutterBackgroundService().startService();
  }

  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return ChangeNotifierProvider(
      create: (context) => AlarmProvider(),
      child: MaterialApp(
        navigatorKey: navigatorKey,
        title: 'קום',
        theme: ThemeData(
          primarySwatch: Colors.blue,
          visualDensity: VisualDensity.adaptivePlatformDensity,
        ),
        localizationsDelegates: const [
          GlobalMaterialLocalizations.delegate,
          GlobalWidgetsLocalizations.delegate,
          GlobalCupertinoLocalizations.delegate,
        ],
        supportedLocales: const [
          Locale('he', 'IL'),
        ],
        locale: const Locale('he', 'IL'),
        debugShowCheckedModeBanner: false,
        initialRoute: '/',
        routes: {
          '/': (context) => const HomeScreen(),
          '/ring': (context) {
            final alarmId =
                ModalRoute.of(context)!.settings.arguments as String;
            return AlarmRingScreen(alarmId: alarmId);
          },
        },
        onGenerateRoute: (settings) {
          if (settings.name == '/edit') {
            final alarm = settings.arguments as Alarm?;
            return MaterialPageRoute(
              builder: (context) {
                return AlarmEditScreen(alarm: alarm);
              },
            );
          }
          return null;
        },
      ),
    );
  }
}
