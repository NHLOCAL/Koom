import 'dart:math';
import 'package:flutter/material.dart';
import '../models/puzzle_model.dart';

// ----- חידת חשבון -----
class MathPuzzle extends Puzzle {
  MathPuzzle._({required super.question, required super.correctAnswer})
      : super(
          type: PuzzleType.math,
        );

  factory MathPuzzle.generate() {
    final random = Random();
    int a = random.nextInt(10) + 1; // 1-10
    int b = random.nextInt(10) + 1; // 1-10
    return MathPuzzle._(
      question: '$a + $b = ?',
      correctAnswer: (a + b).toString(),
    );
  }

  @override
  Widget build(BuildContext context, Function(String) onSolved) {
    final controller = TextEditingController();
    return Column(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        Text(question, style: Theme.of(context).textTheme.headlineMedium),
        SizedBox(height: 20),
        TextField(
            controller: controller,
            keyboardType: TextInputType.number,
            textAlign: TextAlign.center,
            style: Theme.of(context).textTheme.headlineSmall,
            decoration: InputDecoration(
              border: OutlineInputBorder(),
              hintText: 'תשובה',
            ),
          ),
        SizedBox(height: 20),
        ElevatedButton(
          child: Text('בדוק'),
          onPressed: () => onSolved(controller.text),
        ),
      ],
    );
  }
}

// ----- חידת סדרה -----
class SequencePuzzle extends Puzzle {
  SequencePuzzle._({required super.question, required super.correctAnswer})
      : super(
          type: PuzzleType.sequence,
        );

  factory SequencePuzzle.generate() {
    final random = Random();
    int start = random.nextInt(10) + 1;
    int diff = random.nextInt(5) + 1;
    List<int> sequence = List.generate(4, (index) => start + index * diff);
    return SequencePuzzle._(
      question: '${sequence.join(', ')}, ?',
      correctAnswer: (start + 4 * diff).toString(),
    );
  }

  @override
  Widget build(BuildContext context, Function(String) onSolved) {
    final controller = TextEditingController();
    return Column(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        Text("השלם את הסדרה:", style: Theme.of(context).textTheme.titleLarge),
        SizedBox(height: 10),
        Text(question, style: Theme.of(context).textTheme.headlineMedium),
        SizedBox(height: 20),
        TextField(
            controller: controller,
            keyboardType: TextInputType.number,
            textAlign: TextAlign.center,
            style: Theme.of(context).textTheme.headlineSmall,
            decoration: InputDecoration(
              border: OutlineInputBorder(),
              hintText: 'המספר הבא',
            ),
          ),
        SizedBox(height: 20),
        ElevatedButton(
          child: Text('בדוק'),
          onPressed: () => onSolved(controller.text),
        ),
      ],
    );
  }
}

// פונקציית מפעל (Factory) ליצירת חידה לפי סוג
Puzzle createPuzzle(PuzzleType type) {
  switch (type) {
    case PuzzleType.math:
      return MathPuzzle.generate();
    case PuzzleType.sequence:
      return SequencePuzzle.generate();
  }
}
