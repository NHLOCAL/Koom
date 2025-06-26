import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';
import '../models/alarm_model.dart';
import '../providers/alarm_provider.dart';

class AlarmListItem extends StatelessWidget {
  final Alarm alarm;

  const AlarmListItem({Key? key, required this.alarm}) : super(key: key);

  String _formatDays() {
    final weekDays = ['א', 'ב', 'ג', 'ד', 'ה', 'ו', 'ש'];
    List<String> activeDays = [];
    for (int i = 0; i < alarm.days.length; i++) {
      if (alarm.days[i]) {
        activeDays.add(weekDays[(i + 6) % 7]); // התאמה לסדר התצוגה
      }
    }
    if (activeDays.isEmpty) return 'חד פעמי';
    if (activeDays.length == 7) return 'כל יום';
    return activeDays.join(', ');
  }

  @override
  Widget build(BuildContext context) {
    final timeFormat = MaterialLocalizations.of(
      context,
    ).formatTimeOfDay(alarm.time, alwaysUse24HourFormat: true);

    return ListTile(
      onTap: () {
        Navigator.pushNamed(context, '/edit', arguments: alarm);
      },
      leading: Icon(Icons.alarm, size: 40),
      title: Text(
        timeFormat,
        style: TextStyle(
          fontSize: 32,
          fontWeight: FontWeight.bold,
          color: alarm.isActive ? Colors.black : Colors.grey,
        ),
      ),
      subtitle: Text(
        '${alarm.label}, ${_formatDays()}',
        style: TextStyle(color: alarm.isActive ? Colors.black54 : Colors.grey),
      ),
      trailing: Switch(
        value: alarm.isActive,
        onChanged: (bool value) {
          alarm.isActive = value;
          Provider.of<AlarmProvider>(context, listen: false).updateAlarm(alarm);
        },
      ),
    );
  }
}
