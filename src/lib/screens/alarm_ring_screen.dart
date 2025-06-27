import 'package:flutter/material.dart';
import 'package:audioplayers/audioplayers.dart';
import 'package:provider/provider.dart';
import '../models/alarm_model.dart';
import '../models/puzzle_model.dart';
import '../providers/alarm_provider.dart';
import '../utils/puzzles.dart';

class AlarmRingScreen extends StatefulWidget {
  final String alarmId;
  const AlarmRingScreen({super.key, required this.alarmId});

  @override
  _AlarmRingScreenState createState() => _AlarmRingScreenState();
}

class _AlarmRingScreenState extends State<AlarmRingScreen> {
  late final AudioPlayer _audioPlayer;
  late final Alarm? _alarm;
  late final Puzzle _puzzle;

  @override
  void initState() {
    super.initState();
    _audioPlayer = AudioPlayer();
    _startAlarm();

    _alarm = Provider.of<AlarmProvider>(
      context,
      listen: false,
    ).getAlarmById(widget.alarmId);
    if (_alarm != null) {
      if (_alarm!.puzzleType == PuzzleType.image) {
        _puzzle = createPuzzle(_alarm!.puzzleType, imagePath: _alarm!.puzzleImage);
      } else {
        _puzzle = createPuzzle(_alarm!.puzzleType);
      }
    } else {
      // במקרה חירום שהשעון לא נמצא, ניצור חידת ברירת מחדל
      _puzzle = createPuzzle(PuzzleType.math);
    }
  }

  void _startAlarm() async {
    // השתמש ב-AssetSource כדי לנגן קובץ מתיקיית assets
    // או השתמש ב-UrlSource אם יש לך URL
    // כאן נניח שהקובץ נמצא ב-assets/audio/alarm.mp3
    // ודא שהוספת את התיקייה ל-pubspec.yaml
    await _audioPlayer.play(AssetSource('audio/alarm_sound.mp3'));
    _audioPlayer.setReleaseMode(ReleaseMode.loop);
  }

  void _stopAlarm() {
    _audioPlayer.stop();
    // אם השעון הוא חד פעמי, כבה אותו
    if (_alarm != null && !_alarm!.days.any((d) => d)) {
      _alarm!.isActive = false;
      Provider.of<AlarmProvider>(context, listen: false).updateAlarm(_alarm!);
    }
    Navigator.of(context).pop();
  }

  // This method is now called directly by the puzzle when it's solved.
  void _onPuzzleSolved() {
    _stopAlarm();
  }

  @override
  void dispose() {
    _audioPlayer.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    // מונע מהמשתמש לסגור את המסך עם כפתור ה"אחורה"
    return PopScope(
      canPop: false,
      child: Scaffold(
        body: Container(
          width: double.infinity,
          height: double.infinity,
          decoration: BoxDecoration(
            gradient: LinearGradient(
              colors: [Colors.blue.shade200, Colors.blue.shade600],
              begin: Alignment.topCenter,
              end: Alignment.bottomCenter,
            ),
          ),
          child: Center(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Text(
                  _alarm?.label ?? 'זמן לקום!',
                  style: TextStyle(
                    fontSize: MediaQuery.of(context).size.width * 0.07, // גודל גופן רספונסיבי
                    color: Colors.white,
                    fontWeight: FontWeight.bold,
                  ),
                ),
                SizedBox(height: 50),
                Card(
                  margin: EdgeInsets.symmetric(horizontal: MediaQuery.of(context).size.width * 0.05), // שוליים רספונסיביים
                  child: Padding(
                    padding: const EdgeInsets.all(20.0),
                    child: _puzzle.build(context, _onPuzzleSolved),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
