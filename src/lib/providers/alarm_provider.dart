import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:uuid/uuid.dart';
import '../models/alarm_model.dart';
import '../models/puzzle_model.dart';

class AlarmProvider with ChangeNotifier {
  List<Alarm> _alarms = [];
  bool _isLoaded = false;

  List<Alarm> get alarms => _alarms;
  bool get isLoaded => _isLoaded;

  AlarmProvider() {
    loadAlarms();
  }

  Future<void> loadAlarms() async {
    final prefs = await SharedPreferences.getInstance();
    final alarmsJson = prefs.getStringList('alarms') ?? [];
    _alarms = alarmsJson
        .map((jsonString) => Alarm.fromJson(jsonDecode(jsonString)))
        .toList();
    _isLoaded = true;
    notifyListeners();
  }

  Future<void> _saveAlarms() async {
    final prefs = await SharedPreferences.getInstance();
    final alarmsJson =
        _alarms.map((alarm) => jsonEncode(alarm.toJson())).toList();
    await prefs.setStringList('alarms', alarmsJson);
  }

  void addAlarm(
    TimeOfDay time,
    List<bool> days,
    String label,
    PuzzleType puzzleType,
  ) {
    final newAlarm = Alarm(
      id: Uuid().v4(),
      time: time,
      days: days,
      label: label,
      puzzleType: puzzleType,
    );
    _alarms.add(newAlarm);
    _saveAlarms();
    notifyListeners();
  }

  void updateAlarm(Alarm updatedAlarm) {
    int index = _alarms.indexWhere((alarm) => alarm.id == updatedAlarm.id);
    if (index != -1) {
      _alarms[index] = updatedAlarm;
      _saveAlarms();
      notifyListeners();
    }
  }

  void deleteAlarm(String id) {
    _alarms.removeWhere((alarm) => alarm.id == id);
    _saveAlarms();
    notifyListeners();
  }

  Alarm? getAlarmById(String id) {
    try {
      return _alarms.firstWhere((alarm) => alarm.id == id);
    } catch (e) {
      return null;
    }
  }
}
