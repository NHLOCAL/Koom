import 'dart:math';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart'; // For rootBundle
import 'dart:ui' as ui; // For Image
import 'dart:async'; // For Completer
import '../models/puzzle_model.dart';

// ----- חידת חשבון -----
class MathPuzzle extends Puzzle {
  final String _correctAnswer;

  MathPuzzle._({required super.question, required String correctAnswer})
      : _correctAnswer = correctAnswer,
        super(
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
  bool solve(String answer) {
    return answer.trim() == _correctAnswer;
  }

  @override
  Widget build(BuildContext context, VoidCallback onSolved) {
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
          onPressed: () {
            if (solve(controller.text)) {
              onSolved();
            } else {
              ScaffoldMessenger.of(context).showSnackBar(
                SnackBar(
                  content: Text('תשובה שגויה, נסה שוב!'),
                  backgroundColor: Colors.red,
                ),
              );
            }
          },
        ),
      ],
    );
  }
}

// ----- חידת סדרה -----
class SequencePuzzle extends Puzzle {
  final String _correctAnswer;

  SequencePuzzle._({required super.question, required String correctAnswer})
      : _correctAnswer = correctAnswer,
        super(
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
  bool solve(String answer) {
    return answer.trim() == _correctAnswer;
  }

  @override
  Widget build(BuildContext context, VoidCallback onSolved) {
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
          onPressed: () {
            if (solve(controller.text)) {
              onSolved();
            } else {
              ScaffoldMessenger.of(context).showSnackBar(
                SnackBar(
                  content: Text('תשובה שגויה, נסה שוב!'),
                  backgroundColor: Colors.red,
                ),
              );
            }
          },
        ),
      ],
    );
  }
}

// ----- חידת תמונה -----
class ImagePuzzle extends Puzzle {
  final String imagePath;
  final int gridSize;

  ImagePuzzle({required this.imagePath, this.gridSize = 3})
      : super(type: PuzzleType.image, question: 'סדר את התמונה');

  @override
  bool solve(String answer) {
    // הלוגיקה של הפתרון מטופלת בתוך ה-build method
    return true;
  }

  @override
  Widget build(BuildContext context, VoidCallback onSolved) {
    return ImagePuzzleWidget(imagePath: imagePath, gridSize: gridSize, onSolved: onSolved);
  }
}

class ImagePuzzleWidget extends StatefulWidget {
  final String imagePath;
  final int gridSize;
  final VoidCallback onSolved;

  const ImagePuzzleWidget({
    super.key,
    required this.imagePath,
    required this.gridSize,
    required this.onSolved,
  });

  @override
  _ImagePuzzleWidgetState createState() => _ImagePuzzleWidgetState();
}

class _ImagePuzzleWidgetState extends State<ImagePuzzleWidget> {
  List<int> _shuffledIndexes = [];
  List<Uint8List> _imagePieces = [];
  bool _isSolved = false;

  @override
  void initState() {
    super.initState();
    _loadImageAndSplit();
  }

  Future<void> _loadImageAndSplit() async {
    final ByteData data = await rootBundle.load(widget.imagePath);
    final List<int> bytes = data.buffer.asUint8List();
    final Completer<ui.Image> completer = Completer();
    ui.decodeImageFromList(Uint8List.fromList(bytes), (result) {
      completer.complete(result);
    });
    final ui.Image originalImage = await completer.future;

    final int pieceWidth = originalImage.width ~/ widget.gridSize;
    final int pieceHeight = originalImage.height ~/ widget.gridSize;

    List<Uint8List> tempPieces = [];
    for (int i = 0; i < widget.gridSize; i++) {
      for (int j = 0; j < widget.gridSize; j++) {
        final ui.Image piece = await _cropImage(
          originalImage,
          j * pieceWidth,
          i * pieceHeight,
          pieceWidth,
          pieceHeight,
        );
        final ByteData? byteData = await piece.toByteData(format: ui.ImageByteFormat.png);
        if (byteData != null) {
          tempPieces.add(byteData.buffer.asUint8List());
        }
      }
    }

    setState(() {
      _imagePieces = tempPieces;
      _shuffledIndexes = List.generate(widget.gridSize * widget.gridSize, (index) => index);
      _shuffledIndexes.shuffle(Random());
      _checkIfSolved();
    });
  }

  Future<ui.Image> _cropImage(
    ui.Image originalImage,
    int x,
    int y,
    int width,
    int height,
  ) async {
    final recorder = ui.PictureRecorder();
    final canvas = Canvas(recorder);
    canvas.drawImageRect(
      originalImage,
      Rect.fromLTWH(x.toDouble(), y.toDouble(), width.toDouble(), height.toDouble()),
      Rect.fromLTWH(0, 0, width.toDouble(), height.toDouble()),
      Paint(),
    );
    final picture = recorder.endRecording();
    return await picture.toImage(width, height);
  }

  void _swapPieces(int index1, int index2) {
    setState(() {
      final temp = _shuffledIndexes[index1];
      _shuffledIndexes[index1] = _shuffledIndexes[index2];
      _shuffledIndexes[index2] = temp;
      _checkIfSolved();
    });
  }

  void _checkIfSolved() {
    _isSolved = true;
    for (int i = 0; i < _shuffledIndexes.length; i++) {
      if (_shuffledIndexes[i] != i) {
        _isSolved = false;
        break;
      }
    }
    if (_isSolved) {
      widget.onSolved();
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_imagePieces.isEmpty) {
      return CircularProgressIndicator();
    }

    return GridView.builder(
      shrinkWrap: true,
      gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount: widget.gridSize,
        crossAxisSpacing: 4.0,
        mainAxisSpacing: 4.0,
      ),
      itemCount: _shuffledIndexes.length,
      itemBuilder: (context, index) {
        return GestureDetector(
          onTap: _isSolved ? null : () => _handleTap(index),
          child: Image(
            image: MemoryImage(_imagePieces[_shuffledIndexes[index]]),
            fit: BoxFit.cover,
          ),
        );
      },
    );
  }

  // This is a simplified tap handler. For a real puzzle, you'd need to implement
  // logic to select two pieces and swap them, or move a piece into an empty spot.
  // For now, tapping a piece will swap it with the first piece.
  void _handleTap(int tappedIndex) {
    if (_shuffledIndexes.length > 1) {
      _swapPieces(tappedIndex, 0); // Swap with the first piece for demonstration
    }
  }
}

// פונקציית מפעל (Factory) ליצירת חידה לפי סוג
Puzzle createPuzzle(PuzzleType type, {String? imagePath}) {
  switch (type) {
    case PuzzleType.math:
      return MathPuzzle.generate();
    case PuzzleType.sequence:
      return SequencePuzzle.generate();
    case PuzzleType.image:
      return ImagePuzzle(imagePath: imagePath ?? 'assets/images/default_puzzle.png');
  }
}
