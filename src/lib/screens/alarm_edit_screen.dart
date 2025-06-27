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

  final List<String> _dayLabels = ['א', 'ב', 'ג', 'ד', 'ה', 'ו', 'ש'];

  @override
  void initState() {
    super.initState();
    _selectedTime = widget.alarm?.time ?? TimeOfDay.now();
    _labelController = TextEditingController(
      text: widget.alarm?.label ?? 'שעון מעורר',
    );
    _selectedDays = widget.alarm?.days ?? List.filled(7, false);
    _selectedPuzzleType = widget.alarm?.puzzleType ?? PuzzleType.math;
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
      );
    } else {
      // עדכון שעון קיים
      final updatedAlarm = widget.alarm!;
      updatedAlarm.time = _selectedTime;
      updatedAlarm.label = _labelController.text;
      updatedAlarm.days = _selectedDays;
      updatedAlarm.puzzleType = _selectedPuzzleType;
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
                return DropdownMenuItem(
                  value: type,
                  child: Text(
                    type == PuzzleType.math ? 'חידת חשבון' : 'חידת סדרה',
                  ),
                );
              }).toList(),
              onChanged: (value) {
                if (value != null) {
                  setState(() {
                    _selectedPuzzleType = value;
                  });
                }
              },
              decoration: InputDecoration(border: OutlineInputBorder()),
            ),
          ],
        ),
      ),
    );
  }
}
