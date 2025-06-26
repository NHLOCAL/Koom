import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../providers/alarm_provider.dart';
import '../widgets/alarm_list_item.dart';

class HomeScreen extends StatelessWidget {
  const HomeScreen({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text('שעון מעורר עם חידות'), centerTitle: true),
      body: Consumer<AlarmProvider>(
        builder: (context, alarmProvider, child) {
          if (!alarmProvider.isLoaded) {
            return Center(child: CircularProgressIndicator());
          }
          if (alarmProvider.alarms.isEmpty) {
            return Center(
              child: Text(
                'אין שעונים מעוררים.\nלחץ על + כדי להוסיף אחד.',
                textAlign: TextAlign.center,
                style: TextStyle(fontSize: 18),
              ),
            );
          }
          return ListView.builder(
            itemCount: alarmProvider.alarms.length,
            itemBuilder: (context, index) {
              final alarm = alarmProvider.alarms[index];
              return Dismissible(
                key: Key(alarm.id),
                direction: DismissDirection.startToEnd,
                onDismissed: (_) {
                  alarmProvider.deleteAlarm(alarm.id);
                  ScaffoldMessenger.of(
                    context,
                  ).showSnackBar(SnackBar(content: Text('השעון נמחק')));
                },
                background: Container(
                  color: Colors.red,
                  alignment: Alignment.centerRight,
                  padding: EdgeInsets.symmetric(horizontal: 20),
                  child: Icon(Icons.delete, color: Colors.white),
                ),
                child: AlarmListItem(alarm: alarm),
              );
            },
          );
        },
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: () {
          Navigator.pushNamed(context, '/edit');
        },
        child: Icon(Icons.add),
        tooltip: 'הוסף שעון חדש',
      ),
    );
  }
}
