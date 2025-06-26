import 'package:flutter/material.dart';

// סוגי החידות הזמינים
enum PuzzleType { math, sequence }

// מחלקה אבסטרקטית (בסיס) לכל חידה
abstract class Puzzle {
  final PuzzleType type;
  final String question;
  final String correctAnswer;

  Puzzle({
    required this.type,
    required this.question,
    required this.correctAnswer,
  });

  // מתודה לבדיקת התשובה
  bool solve(String answer) {
    return answer.trim() == correctAnswer;
  }

  // מתודה ליצירת הווידג'ט של החידה
  Widget build(BuildContext context, Function(String) onSolved);
}
