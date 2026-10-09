#!/usr/bin/env bash
# End-to-end Android test. The UI, app process AND test runner are not kept alive.
set -euo pipefail

PACKAGE="top.zekal.koom.beta"
COMPONENT="$PACKAGE/top.zekal.koom.DebugAlarmTestReceiver"
ALARM_ID="__koom_diagnostic_test__"

echo "== Start app once to leave Android's stopped-package state =="
if ! adb shell pm path "$PACKAGE" | grep -q '^package:'; then
  echo "FAIL: install the debug APK after connectedDebugAndroidTest cleanup before this test"
  exit 1
fi
adb shell am start -W -n "$PACKAGE/top.zekal.koom.MainActivity" >/dev/null
adb shell input keyevent KEYCODE_HOME
sleep 1

echo "== Scheduling exact system alarm via debug broadcast =="
adb logcat -c
adb shell am broadcast -n "$COMPONENT" -a top.zekal.koom.DEBUG_SCHEDULE_ALARM
sleep 1
PRE_LOGS="$(adb logcat -d -s KoomDelivery:I '*:S' | tr -d '\r')"
if ! grep -Fq "SCHEDULED ($ALARM_ID)" <<< "$PRE_LOGS"; then
  echo "FAIL: debug receiver did not register an exact alarm"
  echo "$PRE_LOGS" | tail -n 60
  adb shell dumpsys package "$PACKAGE" | grep "stopped=" || true
  exit 1
fi

echo "== Checking AlarmManager owns this alarm BEFORE killing the process =="
ALARM_DUMP="$(adb shell dumpsys alarm)"
if ! grep -Fq "$PACKAGE" <<< "$ALARM_DUMP"; then
  echo "Android has no queued alarm for $PACKAGE"
  exit 1
fi
grep -F -m 4 "$PACKAGE" <<< "$ALARM_DUMP"

echo "== Killing the app process without force-stopping the package =="
adb shell am kill "$PACKAGE"
sleep 2
if adb shell pidof "$PACKAGE" | grep -q '[0-9]'; then
  echo "FAIL: process was not killed. This test would be a false positive."
  exit 1
fi

echo "== Android must cold-start the app and play the alarm within 35 seconds =="
success=0
for attempt in $(seq 1 38); do
  LOGS="$(adb logcat -d -s KoomDelivery:I '*:S' | tr -d '\r')"
  if echo "$LOGS" | grep -F "RECEIVED ($ALARM_ID)" >/dev/null &&
     echo "$LOGS" | grep -F "AUDIO_STARTED ($ALARM_ID)" >/dev/null; then
    success=1
    break
  fi
  sleep 1
done

if [ "$success" != 1 ]; then
  echo "FAIL: Android did not deliver the alarm into playback after process death"
  adb logcat -d -s KoomDelivery:I KoomRing:E AndroidRuntime:E '*:S' | tail -n 130
  exit 1
fi

echo "PASS: Android cold-started Koom and started audible alarm playback."
adb logcat -d -s KoomDelivery:I '*:S' | grep -E 'RECEIVED|AUDIO_STARTED'
# Stop test alarm cleanly; preserve all real alarm registrations.
adb shell am start -n "$PACKAGE/top.zekal.koom.MainActivity" >/dev/null
