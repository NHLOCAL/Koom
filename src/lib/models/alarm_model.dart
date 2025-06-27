import 'package:flutter/material.dart';
import 'puzzle_model.dart';

class Alarm {
  final String id;
  TimeOfDay time;
  bool isActive;
  String label;
  List<bool> days; // [Sun, Mon, Tue, Wed, Thu, Fri, Sat]
  PuzzleType puzzleType;
  String? puzzleImage; // New field for image puzzle

  Alarm({
    required this.id,
    required this.time,
    this.isActive = true,
    this.label = 'שעון מעורר',
    required this.days,
    this.puzzleType = PuzzleType.math,
    this.puzzleImage, // Initialize new field
  });

  // המרה מ-JSON
  factory Alarm.fromJson(Map<String, dynamic> json) {
    return Alarm(
      id: json['id'],
      time: TimeOfDay(hour: json['hour'], minute: json['minute']),
      isActive: json['isActive'],
      label: json['label'],
      days: List<bool>.from(json['days']),
      puzzleType: PuzzleType.values.byName(json['puzzleType']),
      puzzleImage: json['puzzleImage'], // Deserialize new field
    );
  }

  // המרה ל-JSON
  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'hour': time.hour,
      'minute': time.minute,
      'isActive': isActive,
      'label': label,
      'days': days,
      'puzzleType': puzzleType.name,
      'puzzleImage': puzzleImage, // Serialize new field
    };
  }
}
