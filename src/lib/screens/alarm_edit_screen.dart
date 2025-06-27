import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../models/alarm_model.dart';
import '../models/puzzle_model.dart';
import '../providers/alarm_provider.dart';

class AlarmEditScreen extends StatefulWidget {
  final Alarm? alarm;
  const AlarmEditScreen({super.key, this.alarm});

  @override
  _AlarmEditScreenState createState() => _AlarmEditScreenState();
}

class _AlarmEditScreenState extends State<AlarmEditScreen> {
  late TimeOfDay _selectedTime;
  late TextEditingController _labelController;
  late List<bool> _selectedDays;
  late PuzzleType _selectedPuzzleType;
  String? _selectedPuzzleImage; // New field for image puzzle path

  final List<String> _dayLabels = ['א', 'ב', 'ג', 'ד', 'ה', 'ו', 'ש'];
  final List<String> _availablePuzzleImages = [
    'assets/images/default_puzzle.png',
    // Add more image paths here as needed
  ];

  @override
  void initState() {
    super.initState();
    _selectedTime = widget.alarm?.time ?? TimeOfDay.now();
    _labelController = TextEditingController(
      text: widget.alarm?.label ?? 'שעון מעורר',
    );
    _selectedDays = widget.alarm?.days ?? List.filled(7, false);
    _selectedPuzzleType = widget.alarm?.puzzleType ?? PuzzleType.math;
    _selectedPuzzleImage = widget.alarm?.puzzleImage; // Initialize from existing alarm
  }

  @override
  void dispose() {
    _labelController.dispose();
    super.dispose();
  }

  void _saveAlarm() {
    final alarmProvider = Provider.of<AlarmProvider>(context, listen: false);
    if (widget.alarm == null) {
      // הוספת שעון חדש
      alarmProvider.addAlarm(
        _selectedTime,
        _selectedDays,
        _labelController.text,
        _selectedPuzzleType,
        puzzleImage: _selectedPuzzleType == PuzzleType.image ? _selectedPuzzleImage : null,
      );
    } else {
      // עדכון שעון קיים
      final updatedAlarm = widget.alarm!;
      updatedAlarm.time = _selectedTime;
      updatedAlarm.label = _labelController.text;
      updatedAlarm.days = _selectedDays;
      updatedAlarm.puzzleType = _selectedPuzzleType;
      updatedAlarm.puzzleImage = _selectedPuzzleType == PuzzleType.image ? _selectedPuzzleImage : null;
      alarmProvider.updateAlarm(updatedAlarm);
    }
    Navigator.pop(context);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(widget.alarm == null ? 'הוסף שעון' : 'ערוך שעון'),
        actions: [IconButton(icon: Icon(Icons.save), onPressed: _saveAlarm)],
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          children: [
            // בחירת שעה
            Text('בחר שעה', style: Theme.of(context).textTheme.titleLarge),
            SizedBox(height: 10),
            InkWell(
              onTap: () async {
                final time = await showTimePicker(
                  context: context,
                  initialTime: _selectedTime,
                );
                if (time != null) {
                  setState(() {
                    _selectedTime = time;
                  });
                }
              },
              child: Text(
                _selectedTime.format(context),
                style: TextStyle(fontSize: MediaQuery.of(context).size.width * 0.12, fontWeight: FontWeight.bold),
              ),
            ),
            SizedBox(height: 30),

            // תווית
            TextField(
              controller: _labelController,
              decoration: InputDecoration(
                labelText: 'תווית',
                border: OutlineInputBorder(),
              ),
            ),
            SizedBox(height: 30),

            // ימי חזרה
            Text('חזרה בימים', style: Theme.of(context).textTheme.titleLarge),
            SizedBox(height: 10),
            SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              child: ToggleButtons(
                isSelected: _selectedDays,
                onPressed: (index) {
                  setState(() {
                    _selectedDays[index] = !_selectedDays[index];
                  });
                },
                borderRadius: BorderRadius.circular(10),
                children: _dayLabels.map((day) => Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 4.0), // הופחת מ-8.0
                  child: Text(day),
                )).toList(),
              ),
            ),
            SizedBox(height: 30),

            // בחירת חידה
            Text('סוג החידה', style: Theme.of(context).textTheme.titleLarge),
            SizedBox(height: 10),
            DropdownButtonFormField<PuzzleType>(
              value: _selectedPuzzleType,
              items: PuzzleType.values.map((type) {
                String text;
                switch (type) {
                  case PuzzleType.math:
                    text = 'חידת חשבון';
                    break;
                  case PuzzleType.sequence:
                    text = 'חידת סדרה';
                    break;
                  case PuzzleType.image:
                    text = 'חידת תמונה';
                    break;
                }
                return DropdownMenuItem(
                  value: type,
                  child: Text(text),
                );
              }).toList(),
              onChanged: (value) {
                if (value != null) {
                  setState(() {
                    _selectedPuzzleType = value;
                    // Reset selected image if puzzle type changes from image
                    if (value != PuzzleType.image) {
                      _selectedPuzzleImage = null;
                    } else if (_selectedPuzzleImage == null && _availablePuzzleImages.isNotEmpty) {
                      _selectedPuzzleImage = _availablePuzzleImages.first; // Set a default image if none selected
                    }
                  });
                }
              },
              decoration: InputDecoration(border: OutlineInputBorder()),
            ),
            if (_selectedPuzzleType == PuzzleType.image)
              Column(
                children: [
                  SizedBox(height: 30),
                  Text('בחר תמונה לחידה', style: Theme.of(context).textTheme.titleLarge),
                  SizedBox(height: 10),
                  DropdownButtonFormField<String>(
                    value: _selectedPuzzleImage,
                    items: _availablePuzzleImages.map((imagePath) {
                      return DropdownMenuItem(
                        value: imagePath,
                        child: Text(imagePath.split('/').last), // Display just the filename
                      );
                    }).toList(),
                    onChanged: (value) {
                      if (value != null) {
                        setState(() {
                          _selectedPuzzleImage = value;
                        });
                      }
                    },
                    decoration: InputDecoration(border: OutlineInputBorder()),
                  ),
                ],
              ),
          ],
        ),
      ),
    );
  }
}
