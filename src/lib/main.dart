import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:provider/provider.dart';
import 'package:permission_handler/permission_handler.dart';

import 'providers/alarm_provider.dart';
import 'screens/home_screen.dart';
import 'screens/alarm_edit_screen.dart';
import 'screens/alarm_ring_screen.dart';
import 'models/alarm_model.dart';
import 'services/background_service.dart';

// מפתח גלובלי לגישה ל-Navigator מהרקע
final GlobalKey<NavigatorState> navigatorKey = GlobalKey<NavigatorState>();

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  // בקשת הרשאות
  await Permission.notification.isDenied.then((value) {
    if (value) {
      Permission.notification.request();
    }
  });

  // אתחול שירות הרקע
  await initializeService();

  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return ChangeNotifierProvider(
      create: (context) => AlarmProvider(),
      child: MaterialApp(
        navigatorKey: navigatorKey,
        title: 'Puzzle Alarm Clock',
        theme: ThemeData(
          primarySwatch: Colors.blue,
          visualDensity: VisualDensity.adaptivePlatformDensity,
        ),
        localizationsDelegates: [
          GlobalMaterialLocalizations.delegate,
          GlobalWidgetsLocalizations.delegate,
          GlobalCupertinoLocalizations.delegate,
        ],
        supportedLocales: [
          const Locale('he', 'IL'), // Hebrew
        ],
        locale: const Locale('he', 'IL'),
        debugShowCheckedModeBanner: false,
        initialRoute: '/',
        routes: {
          '/': (context) => HomeScreen(),
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
          return null; // Let routes handle it
        },
      ),
    );
  }
}
