#!/usr/bin/env bash
# Disposable-emulator tests; no app or instrumentation callbacks between kill and delivery.
set -euo pipefail

TEST_PACKAGE="top.zekal.koom.beta"
DEBUG_COMPONENT="$TEST_PACKAGE/top.zekal.koom.DebugAlarmTestReceiver"
DIAGNOSTIC_ID="__koom_diagnostic_test__"
SCHEDULE_ACTION="top.zekal.koom.DEBUG_SCHEDULE_ALARM"
STATUS_ACTION="top.zekal.koom.DEBUG_ALARM_STATUS"
CLEANUP_ACTION="top.zekal.koom.DEBUG_CLEANUP_ALARM"
TEST_PIN="482631"
ARTIFACTS="${KOOM_LIFECYCLE_ARTIFACTS:-app/build/reports/lifecycle}"
BUDGET_END=$((SECONDS + 230))
PIN_SET=0
IDLE_CHANGED=0
DEVICE_CONFIG_CHANGED=0
ORIGINAL_IDLE_CONSTANTS="null"
ORIGINAL_DEEP_ENABLED="1"
ORIGINAL_MIN_TIME_TO_ALARM="null"
ORIGINAL_MIN_DEVICE_IDLE_FUZZ="null"
ORIGINAL_MAX_DEVICE_IDLE_FUZZ="null"
CASE_NAME="setup"
CASE_DIR="$ARTIFACTS/$CASE_NAME"
mkdir -p "$CASE_DIR"

fail() { printf 'FAIL [%s]: %s\n' "$CASE_NAME" "$*" >&2; exit 1; }
adbs() { timeout 8s adb shell "$@" | tr -d '\r'; }
budget() { (( SECONDS < BUDGET_END )) || fail "Lifecycle checks exceeded their time budget"; }

# This script sets a real PIN and reboots. It must never target a personal device.
[[ "${KOOM_DISPOSABLE_EMULATOR:-}" == "1" ]] || fail "Set KOOM_DISPOSABLE_EMULATOR=1 for a disposable emulator"
[[ "$(adbs getprop ro.kernel.qemu)" == "1" ]] || fail "An Android emulator is required"
[[ "$(adbs am get-current-user)" == "0" ]] || fail "The disposable emulator must use user 0"

capture() {
    local directory="$1"
    mkdir -p "$directory"
    timeout 3s adb logcat -b all -d -v threadtime > "$directory/logcat.txt" 2>&1 || true
    for service in alarm deviceidle power battery user; do
        timeout 3s adb shell dumpsys "$service" > "$directory/$service.txt" 2>&1 || true
    done
    timeout 3s adb shell dumpsys activity intents > "$directory/pending-intents.txt" 2>&1 || true
    timeout 3s adb shell dumpsys activity activities > "$directory/activities.txt" 2>&1 || true
    timeout 3s adb shell dumpsys activity services "$TEST_PACKAGE" > "$directory/services.txt" 2>&1 || true
    timeout 3s adb shell dumpsys package "$TEST_PACKAGE" > "$directory/package.txt" 2>&1 || true
}

restore_device_config() {
    local namespace="$1" key="$2" original="$3"
    if [[ "$original" == "null" ]]; then
        adbs device_config delete "$namespace" "$key" >/dev/null
    else
        adbs device_config put "$namespace" "$key" "$original" >/dev/null
    fi
}

restore_idle() {
    adbs dumpsys deviceidle unforce >/dev/null
    adbs dumpsys battery reset >/dev/null
    if [[ "$DEVICE_CONFIG_CHANGED" == "1" ]]; then
        restore_device_config device_idle min_time_to_alarm "$ORIGINAL_MIN_TIME_TO_ALARM"
        restore_device_config alarm_manager min_device_idle_fuzz "$ORIGINAL_MIN_DEVICE_IDLE_FUZZ"
        restore_device_config alarm_manager max_device_idle_fuzz "$ORIGINAL_MAX_DEVICE_IDLE_FUZZ"
        DEVICE_CONFIG_CHANGED=0
    fi
    if [[ "$IDLE_CHANGED" == "1" ]]; then
        if [[ "$ORIGINAL_IDLE_CONSTANTS" == "null" ]]; then
            adbs settings delete global device_idle_constants >/dev/null
        else
            adbs settings put global device_idle_constants "$ORIGINAL_IDLE_CONSTANTS" >/dev/null
        fi
        if [[ "$ORIGINAL_DEEP_ENABLED" == "0" ]]; then
            adbs dumpsys deviceidle disable deep >/dev/null
        fi
        IDLE_CHANGED=0
    fi
}

wait_for_doze_settings() {
    local attempt config_deadline=$((SECONDS + 12))
    for attempt in {1..8}; do
        budget
        (( SECONDS < config_deadline )) || break
        adbs dumpsys deviceidle > "$CASE_DIR/configured-deviceidle.txt"
        adbs dumpsys alarm > "$CASE_DIR/configured-alarm.txt"
        if grep -Eq '^[[:space:]]*min_time_to_alarm=0[[:space:]]*$' "$CASE_DIR/configured-deviceidle.txt" &&
           grep -Eq '^[[:space:]]*min_device_idle_fuzz=0[[:space:]]*$' "$CASE_DIR/configured-alarm.txt" &&
           grep -Eq '^[[:space:]]*max_device_idle_fuzz=0[[:space:]]*$' "$CASE_DIR/configured-alarm.txt"; then
            printf 'Verified forced-Doze settings in DeviceIdle and AlarmManager dumps\n'
            return
        fi
        sleep 1
    done
    fail "Device idle settings did not take effect (see configured-deviceidle.txt and configured-alarm.txt)"
}

cleanup() {
    local result=$?
    trap - EXIT INT TERM
    set +e
    capture "$ARTIFACTS/exit"
    # Best effort even on assertion/timeout failure; never force-stop the package.
    restore_idle >> "$ARTIFACTS/cleanup.txt" 2>&1
    if [[ "$PIN_SET" == "1" ]]; then
        timeout 5s adb shell locksettings clear --old "$TEST_PIN" --user 0 >> "$ARTIFACTS/cleanup.txt" 2>&1
    fi
    timeout 5s adb shell am broadcast --user 0 --receiver-foreground -n "$DEBUG_COMPONENT" \
        -a "$CLEANUP_ACTION" --es request_id final-cleanup >> "$ARTIFACTS/cleanup.txt" 2>&1
    timeout 3s adb shell input keyevent KEYCODE_WAKEUP >> "$ARTIFACTS/cleanup.txt" 2>&1
    timeout 3s adb shell wm dismiss-keyguard >> "$ARTIFACTS/cleanup.txt" 2>&1
    [[ "$result" == "0" ]] || printf 'Failure evidence: %s\n' "$ARTIFACTS" >&2
    exit "$result"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

status_value() {
    local item
    local -a items
    read -r -a items <<< "$DEBUG_STATUS"
    for item in "${items[@]}"; do
        if [[ "$item" == "$1="* ]]; then printf '%s\n' "${item#*=}"; return; fi
    done
    fail "Debug status omitted $1"
}

require_status() {
    [[ "$(status_value "$1")" == "$2" ]] || fail "Expected $1=$2; received $DEBUG_STATUS"
}

debug_hook() {
    local action="$1" request="$2"
    shift 2
    budget
    adbs am broadcast --user 0 --receiver-foreground -n "$DEBUG_COMPONENT" -a "$action" \
        --es request_id "$request" "$@" > "$CASE_DIR/$request.txt"
    DEBUG_STATUS="$(sed -n 's/^Broadcast completed:.*data="\([^"]*\)".*/\1/p' "$CASE_DIR/$request.txt" | tail -n 1)"
    [[ -n "$DEBUG_STATUS" ]] || fail "Debug receiver returned no status (see $request.txt)"
    require_status request "$request"
    require_status ok true
}

read_delivery_logs() {
    timeout 8s adb logcat -b main -b system -d -v threadtime \
        KoomDelivery:I KoomDebug:I KoomRing:E AndroidRuntime:E '*:S' \
        | tr -d '\r' > "$CASE_DIR/delivery.log"
}

begin_case() {
    CASE_NAME="$1"
    CASE_DIR="$ARTIFACTS/$CASE_NAME"
    mkdir -p "$CASE_DIR"
    printf 'Testing %s\n' "$CASE_NAME"
    debug_hook "$CLEANUP_ACTION" "$CASE_NAME-cleanup"
    require_status active false
    adbs input keyevent KEYCODE_HOME >/dev/null
    sleep 1
    timeout 8s adb logcat -b all -c
}

schedule_diagnostic() {
    debug_hook "$SCHEDULE_ACTION" "$CASE_NAME-schedule" --el delay_ms "$1"
    require_status enabled true
    require_status active false
    require_status exact true
    EXPECTED_DUE="$(status_value due_ms)"
    EXPECTED_TOKEN="$(status_value token)"
    EXPECTED_URI="$(status_value uri)"
    [[ "$EXPECTED_DUE" =~ ^[0-9]+$ && "$EXPECTED_TOKEN" != "none" ]] || fail "Missing durable occurrence"
    [[ "$EXPECTED_URI" == "koom://alarm/$DIAGNOSTIC_ID?occurrence=$EXPECTED_TOKEN" ]] || fail "Unexpected diagnostic URI"
}

assert_queued() {
    local phase="$1"
    adbs dumpsys alarm > "$CASE_DIR/$phase-alarm.txt"
    adbs dumpsys activity intents > "$CASE_DIR/$phase-intents.txt"
    # Android 12/15 print the URI in PendingIntentRecord, and its identity in the
    # queued Alarm. A retained PendingIntent or a package name alone proves nothing.
    python3 - "$CASE_DIR/$phase-alarm.txt" "$CASE_DIR/$phase-intents.txt" \
        "$TEST_PACKAGE" "$EXPECTED_URI" "$EXPECTED_DUE" > "$CASE_DIR/$phase-verified.txt" <<'PY'
import pathlib
import re
import sys

alarm_text, intent_text = (pathlib.Path(p).read_text() for p in sys.argv[1:3])
package, uri, due = sys.argv[3:]
# AOSP groups packages under "* package: N items" and prints records as "#N: ...".
headers = list(re.finditer(r"(?m)^[ \t]*(?:#\d+:[ \t]*|\*[ \t]*)?PendingIntentRecord\{([0-9a-f]+)\s+([^\s}]+)\s+([^\n]+)", intent_text))
records = []
for index, header in enumerate(headers):
    end = headers[index + 1].start() if index + 1 < len(headers) else len(intent_text)
    block = intent_text[header.start():end]
    if (header.group(2) == package and "startForegroundService" in header.group(3)
            and re.search(r"\bdat=" + re.escape(uri) + r"(?=\s|}|$)", block)
            and "act=top.zekal.koom.action.FIRE" in block
            and "RingService" in block and "canceled=true" not in block):
        records.append(header.group(1))
if len(records) != 1:
    raise SystemExit(f"FAIL: expected one diagnostic PendingIntent with URI {uri}; found {records}")
record = records[0]
start = re.search(r"(?im)^([ \t]*)(?:\d+ pending alarms|Pending alarm batches):[^\n]*\n", alarm_text)
if not start:
    raise SystemExit("FAIL: AlarmManager pending-alarm section missing")
# Stop at the next peer section so delivery history/stats cannot satisfy this check.
base_indent = len(start.group(1))
queue_lines = []
for line in alarm_text[start.end():].splitlines():
    indent = len(line) - len(line.lstrip())
    if line.strip() and indent <= base_indent and not line.lstrip().startswith("Batch{"):
        break
    queue_lines.append(line)
queue = "\n".join(queue_lines)
found = []
for header in re.finditer(r"(?m)^([ \t]*)RTC_WAKEUP #\d+: (Alarm\{[^\n]+})", queue):
    block = [header.group(0)]
    for line in queue[header.end():].splitlines():
        if line.strip() and len(line) - len(line.lstrip()) <= len(header.group(1)):
            break
        block.append(line)
    block = "\n".join(block)
    if (re.search(r"\borigWhen " + re.escape(due) + r"\b", header.group(2))
            and re.search(r"\bwindow=0(?:ms)?(?:\s|$)", block)
            and "Alarm clock:" in block
            and re.search(r"operation=.*PendingIntentRecord\{" + re.escape(record) + r"\s", block)):
        found.append(block)
if len(found) != 1:
    raise SystemExit(f"FAIL: no unique queued exact alarm for PendingIntentRecord{{{record}}} at {due}")
print(f"Verified URI={uri} due_ms={due} PendingIntentRecord={record}")
print(found[0])
PY
    cat "$CASE_DIR/$phase-verified.txt"
}

kill_and_prove_absent() {
    read_delivery_logs
    if grep -Eq "(RECEIVED|SERVICE_FOREGROUND|AUDIO_STARTED) \($DIAGNOSTIC_ID\)" "$CASE_DIR/delivery.log"; then
        fail "Alarm fired before the process-death precondition"
    fi
    # Convert only pidof's documented no-match status to success. A failed adb
    # transport must not be mistaken for an absent process.
    adbs "pidof $TEST_PACKAGE || [ \$? = 1 ]" > "$CASE_DIR/pid-before.txt"
    [[ -s "$CASE_DIR/pid-before.txt" ]] || fail "No live process was available for am kill"
    adbs am kill "$TEST_PACKAGE"
    sleep 2
    adbs "pidof $TEST_PACKAGE || [ \$? = 1 ]" > "$CASE_DIR/pid-after.txt"
    [[ ! -s "$CASE_DIR/pid-after.txt" ]] || fail "am kill did not remove the app process"
    local now
    now="$(adbs date +%s)"
    [[ "$now" =~ ^[0-9]+$ ]] || fail "Could not read device time"
    (( now * 1000 + 2000 < EXPECTED_DUE )) || fail "Process absence was not proven before the deadline"
    DELIVERY_WAIT_SECONDS=$(((EXPECTED_DUE - now * 1000 + 999) / 1000 + 12))
    printf 'Process absent at device_seconds=%s; due_ms=%s\n' "$now" "$EXPECTED_DUE" | tee "$CASE_DIR/process-absent.txt"
    # From this point until all delivery stages appear, only host logcat is read.
}

await_delivery() {
    local end=$((SECONDS + DELIVERY_WAIT_SECONDS))
    while (( SECONDS < end )); do
        budget
        read_delivery_logs
        if grep -Fq "RECEIVED ($DIAGNOSTIC_ID)" "$CASE_DIR/delivery.log" &&
           grep -Fq "SERVICE_FOREGROUND ($DIAGNOSTIC_ID)" "$CASE_DIR/delivery.log" &&
           grep -Fq "AUDIO_STARTED ($DIAGNOSTIC_ID)" "$CASE_DIR/delivery.log"; then
            printf 'PASS: %s delivered into foreground audio playback after process death\n' "$CASE_NAME"
            return
        fi
        sleep 1
    done
    fail "Missing RECEIVED, SERVICE_FOREGROUND or AUDIO_STARTED after process death"
}

assert_ring_activity() {
    local attempt
    for attempt in {1..6}; do
        adbs dumpsys activity activities > "$CASE_DIR/ring-activity.txt"
        if grep -E '(mResumedActivity|topResumedActivity|ResumedActivity)' "$CASE_DIR/ring-activity.txt" \
            | grep -F "$TEST_PACKAGE/" | grep -q 'RingActivity'; then
            timeout 5s adb exec-out screencap -p > "$CASE_DIR/lock-screen.png" || true
            return
        fi
        sleep 1
    done
    fail "RingActivity did not become the resumed full-screen activity"
}

# connectedDebugAndroidTest can uninstall the target APK; CI must reinstall it.
adbs pm path "$TEST_PACKAGE" | grep -q '^package:' || fail "Reinstall the debug APK after instrumentation cleanup"
adbs input keyevent KEYCODE_WAKEUP >/dev/null
adbs wm dismiss-keyguard >/dev/null
adbs am start -W -n "$TEST_PACKAGE/top.zekal.koom.MainActivity" > "$CASE_DIR/start.txt"
API_LEVEL="$(adbs getprop ro.build.version.sdk)"
adbs cmd appops set "$TEST_PACKAGE" SCHEDULE_EXACT_ALARM allow
if (( API_LEVEL >= 33 )); then adbs pm grant "$TEST_PACKAGE" android.permission.POST_NOTIFICATIONS; fi
if (( API_LEVEL >= 34 )); then adbs cmd appops set "$TEST_PACKAGE" USE_FULL_SCREEN_INTENT allow; fi
debug_hook "$STATUS_ACTION" setup-status
require_status unlocked true
require_status secure false
require_status exact true
require_status notifications true
require_status full_screen true
adbs input keyevent KEYCODE_HOME >/dev/null

begin_case ordinary-process-death
schedule_diagnostic 17000
assert_queued before-kill
kill_and_prove_absent
await_delivery
capture "$CASE_DIR/after-delivery"

begin_case screen-off-doze-process-death
ORIGINAL_IDLE_CONSTANTS="$(adbs settings get global device_idle_constants)"
[[ "$ORIGINAL_IDLE_CONSTANTS" =~ ^[A-Za-z0-9_=.,+-]+$ ]] || fail "Unexpected device_idle_constants format"
ORIGINAL_DEEP_ENABLED="$(adbs dumpsys deviceidle enabled deep)"
ORIGINAL_MIN_TIME_TO_ALARM="$(adbs device_config get device_idle min_time_to_alarm)"
ORIGINAL_MIN_DEVICE_IDLE_FUZZ="$(adbs device_config get alarm_manager min_device_idle_fuzz)"
ORIGINAL_MAX_DEVICE_IDLE_FUZZ="$(adbs device_config get alarm_manager max_device_idle_fuzz)"
for original in "$ORIGINAL_MIN_TIME_TO_ALARM" "$ORIGINAL_MIN_DEVICE_IDLE_FUZZ" "$ORIGINAL_MAX_DEVICE_IDLE_FUZZ"; do
    [[ "$original" =~ ^(null|-?[0-9]+)$ ]] || fail "Unexpected DeviceConfig duration"
done
printf 'device_idle_constants=%s\ndeep_enabled=%s\ndevice_idle/min_time_to_alarm=%s\nalarm_manager/min_device_idle_fuzz=%s\nalarm_manager/max_device_idle_fuzz=%s\n' \
    "$ORIGINAL_IDLE_CONSTANTS" "$ORIGINAL_DEEP_ENABLED" "$ORIGINAL_MIN_TIME_TO_ALARM" \
    "$ORIGINAL_MIN_DEVICE_IDLE_FUZZ" "$ORIGINAL_MAX_DEVICE_IDLE_FUZZ" > "$CASE_DIR/original-idle-settings.txt"
# Flag all writes before starting so a partially applied setup is restored too.
IDLE_CHANGED=1
DEVICE_CONFIG_CHANGED=1
# API 31 reads DeviceConfig; newer releases can override it through global Settings.
# AlarmManager separately advances idle exit by 2–15 minutes before an alarm clock.
# Remove both margins only for this disposable forced-Doze test, restoring them later.
IDLE_PREFIX="$ORIGINAL_IDLE_CONSTANTS"
[[ "$IDLE_PREFIX" != "null" ]] || IDLE_PREFIX=""
adbs settings put global device_idle_constants "${IDLE_PREFIX:+$IDLE_PREFIX,}min_time_to_alarm=0" >/dev/null
adbs device_config put device_idle min_time_to_alarm 0 >/dev/null
adbs device_config put alarm_manager min_device_idle_fuzz 0 >/dev/null
adbs device_config put alarm_manager max_device_idle_fuzz 0 >/dev/null
wait_for_doze_settings
adbs dumpsys deviceidle enable deep >/dev/null
adbs dumpsys battery unplug >/dev/null
schedule_diagnostic 22000
adbs input keyevent KEYCODE_SLEEP >/dev/null
sleep 1
adbs dumpsys deviceidle force-idle deep > "$CASE_DIR/force-idle.txt"
[[ "$(adbs dumpsys deviceidle get deep)" == "IDLE" ]] || fail "Device did not enter forced deep idle"
debug_hook "$STATUS_ACTION" doze-status
require_status interactive false
require_status idle true
assert_queued before-kill
kill_and_prove_absent
await_delivery
capture "$CASE_DIR/after-delivery"
restore_idle

begin_case reboot-before-first-unlock
PIN_SET=1
adbs locksettings set-pin --user 0 "$TEST_PIN" > "$CASE_DIR/set-pin.txt"
debug_hook "$STATUS_ACTION" pin-status
require_status secure true
require_status unlocked true
schedule_diagnostic 85000
assert_queued before-reboot
adbs cat /proc/sys/kernel/random/boot_id > "$CASE_DIR/boot-id-before.txt"
timeout 8s adb reboot
# No input event, credential verification or app activity may unlock the user here.
BOOT_END=$((SECONDS + 65))
timeout 55s adb wait-for-device
while [[ "$(adbs getprop sys.boot_completed)" != "1" ]]; do
    budget
    (( SECONDS < BOOT_END )) || fail "Reboot did not finish before the diagnostic deadline"
    sleep 1
done
adbs cat /proc/sys/kernel/random/boot_id > "$CASE_DIR/boot-id-after.txt"
! cmp -s "$CASE_DIR/boot-id-before.txt" "$CASE_DIR/boot-id-after.txt" || fail "The device did not actually reboot"
while :; do
    read_delivery_logs
    grep -Fq 'SYSTEM_RESCHEDULED () android.intent.action.LOCKED_BOOT_COMPLETED' "$CASE_DIR/delivery.log" && break
    budget
    (( SECONDS < BOOT_END )) || fail "No LOCKED_BOOT_COMPLETED restoration was observed"
    sleep 1
done
# Put the PIN-protected device back to sleep without entering credentials. The
# later resumed-activity assertion must therefore prove a lock-screen launch.
adbs input keyevent KEYCODE_SLEEP >/dev/null
sleep 1
debug_hook "$STATUS_ACTION" locked-status
require_status unlocked false
require_status secure true
require_status interactive false
require_status exact true
require_status notifications true
require_status full_screen true
require_status due_ms "$EXPECTED_DUE"
require_status token "$EXPECTED_TOKEN"
require_status active false
assert_queued restored-before-kill
kill_and_prove_absent
await_delivery
assert_ring_activity
debug_hook "$STATUS_ACTION" delivered-while-locked
require_status unlocked false
require_status active true
capture "$CASE_DIR/after-delivery"
printf 'PASS: all lifecycle cases, including restored exact alarm and RingActivity before first unlock\n'
