# Koom alarm reliability implementation plan

> Execution: systematic diagnosis, focused regression tests, independent review.

**Goal:** Finish the native Android alarm reliability work, especially Android 12 process death, screen-off delivery, and reboot recovery.

**Architecture:** Keep alarms owned by `AlarmManager.setAlarmClock()` and an explicit foreground-service `PendingIntent`. Persist the pending occurrence as well as user settings so reopening the app cannot move an overdue alarm to tomorrow. Keep sound and schedule state accessible during Direct Boot.

**Tech stack:** Kotlin, Compose, Android API 26+, target 36, compile SDK 37, Gradle 9.6.0, JDK 17.

**Spec:** The user's request to complete the existing `rewrite/android-native-v2` work and fix Android 12 alarm failures after RAM cleanup. Existing PR: https://github.com/NHLOCAL/Koom/pull/1.

## Constraints

- Continue the existing branch without replacing the original installed package or deleting user alarms.
- No always-running polling service or attempt to override Android's force-stop state.
- Screen off, ordinary process death, Doze, and reboot are distinct acceptance cases.
- A fully powered-off phone needs vendor RTC integration unavailable through public Android APIs.
- Keep Hebrew RTL UI, system/file ringtone selection, and bundled gentle fallback.
- Use only meaningful checks tied to a concrete failure or requirement.
- Changes to the existing PR are authorized by the continuation request. Publishing a production release is not part of this plan.

## 1. Repair the existing integration test

**Files:** `.github/workflows/android.yml`, `scripts/verify-android12-process-death.sh`.

- Read the previous failing API 31 and 35 job logs.
- Identify that instrumentation completed, then the shell test failed because the target APK had been uninstalled.
- Explicitly reinstall the APK after the instrumentation runner finishes.
- Check installation and wait for the initial activity launch before scheduling.
- Run the repaired baseline test through existing GitHub Actions.

## 2. Correct lifecycle failures demonstrated by code and tests

**Files:** `AlarmScheduler.kt`, `AlarmStore.kt`, `RingService.kt`, `SystemReceiver.kt`, relevant regression tests.

- Pin pending-occurrence preservation across reconciliation and stale delivery rejection with tests.
- Persist and validate each scheduled occurrence, preserving it on app resume and reboot.
- Correct boot and playback lifecycle findings established by the independent audit.
- Ensure all accept/reschedule transitions remain durable without a visible activity.

## 3. Complete the selected-sound lifecycle

**Files:** `AlarmSounds.kt`, `ui/AlarmEditor.kt`, `MainActivity.kt`, sound tests as needed.

- Address the PR review finding about superseded and abandoned imported files.
- Preserve sounds referenced by saved alarms or the active editor.
- Verify the gentle offline fallback remains packaged and plays.

## 4. Verify the actual device lifecycle and document evidence

**Files:** integration script, debug-only hooks, workflow, `README.md`, `docs/alarm-reliability.md`.

- Execute ordinary process-death and screen-off/Doze checks on API 31 and 35.
- Execute reboot-before-unlock recovery without a live test runner in the app process.
- Save diagnostic logs and fail on missing installation, undelivered alarms, or failed playback.
- Run JVM tests, compile APK/instrumentation, and lint.
- Perform an independent final review, fix important findings, and update the PR.
- Deliver the verified APK and distinguish emulator evidence from untested OEM behavior.

## Review focus

- Reconciliation races with an alarm whose due time has just passed.
- Replayed or stale intents after an alarm is edited, canceled, or rearmed.
- Direct Boot followed by first unlock while an alarm is already ringing.
- Process death during playback and restart from durable active state.
- Audio imports canceled in the editor or replaced/deleted while still referenced.

## Progress and decisions

- Baseline: `7f2e6ed86243ec32839c56e18ca02ccf2277fdfa`.
- Existing run 37859181939: build/unit/lint passed; both emulator jobs failed at the missing-APK precondition of the process-death script.
- Work runs in a fresh, isolated clone of the existing PR branch. There is no need for a second worktree.
- Local emulator acceleration is unavailable. Install available local build tools and use the existing GitHub-hosted Android emulators for device verification.

## Implementation decisions

- Occurrence tokens are part of PendingIntent data identity, not only intent extras.
- Ordinary reconciliation preserves pending deadlines. Explicit wall-time changes recalculate with a 15-second delivery grace.
- Recovery catches up for 10 minutes; stale one-shots are marked missed and repeats advance.
- BOOT_COUNT distinguishes a physical boot from repeated boot broadcasts. Recent active playback has a separate recovery alarm.
- Service teardown is start-ID-aware. Each active service start finishes interrupted recurring scheduling.
- Successful draft sound selections remain protected until draft completion to preserve older saved activity snapshots.
- Baseline run 37879089126 passed ordinary process-death delivery on API 31 and 35 after the APK reinstall fix.
- This file records the plan and acceptance criteria. Final results and artifacts are recorded on PR #1 and its linked workflow runs.
