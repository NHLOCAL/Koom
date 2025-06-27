import 'package:flutter/material.dart';

// סוגי החידות הזמינים
enum PuzzleType { math, sequence, image }

// מחלקה אבסטרקטית (בסיס) לכל חידה
abstract class Puzzle {
  final PuzzleType type;
  final String question;

  Puzzle({
    required this.type,
    required this.question,
  });

  // מתודה לבדיקת התשובה - כעת אבסטרקטית
  bool solve(String answer);

  // מתודה ליצירת הווידג'ט של החידה
  Widget build(BuildContext context, VoidCallback onSolved);
}
