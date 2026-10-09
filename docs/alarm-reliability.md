# Alarm reliability design and verification

Updated: 2026-10-09. Native Android branch `rewrite/android-native-v2`, version 2.2.1, version code 5. Integration and current results: [PR #1](https://github.com/NHLOCAL/Koom/pull/1).

## Scope

The reported Android 12 failure occurs after an OEM RAM cleaner, despite battery optimization being disabled. Ordinary process death, screen-off idle, reboot, force-stop, and a completely powered-off phone are separate cases. An AOSP emulator can test the first three. It cannot establish what a particular OEM cleaner does or make powered-off hardware execute an application.

The app keeps its native AlarmManager architecture and separate beta package. There is no permanent polling service, automatic volume change, or attempt to undo the user's force-stop decision.

## Defects addressed

| Area | Failure | Correction |
| --- | --- | --- |
| CI | Instrumentation uninstalled the APK before the process-death test. | Reinstall and verify the APK before the shell test. |
| Reconciliation | Recomputing from now could move a just-overdue alarm to tomorrow. | Persist and restore the occurrence's deadline and token. |
| Edited alarms | A queued old event could consume a new alarm. | Tokenized PendingIntent identity, invalidate before cancel, reject stale tokens. |
| Repeating alarms | Death between acceptance and rescheduling could lose the next repeat. | Restore each active recurring schedule on every service start, preserving an already persisted next occurrence. |
| Service teardown | An older empty start could stop a valid start already queued by Android. | Use stopSelfResult(startId); preserve newer requests and detach a failed service's fallback notification before stopping. |
| Active deletion | Removing a ringing alarm could leave playback running. | Remove it from the active queue and refresh playback. |
| Boot | Repeated boot broadcasts could discard recent ringing or pre-unlock playback. | Compare BOOT_COUNT and use a dedicated recent-ringing recovery event. |
| Imported sounds | Abandoned copies leaked; early deletion could break an older saved editor snapshot. | Protect saved, draft and copying files; retain successful draft selections until editing finishes. |
| Audio errors | A failure after successful start could remain silent. | Handle asynchronous MediaPlayer errors with a finite fallback chain. |

## Durable scheduling

`setAlarmClock()` owns delivery through an explicit foreground-service PendingIntent. The data URI includes the alarm ID and a random occurrence token. Extras alone do not provide distinct PendingIntent identity.

Device-protected SharedPreferences store definitions, pending occurrences, the active queue and start timestamps. One transaction accepts a matching delivery, disables a one-shot, adds its active ID, records its start time and removes the consumed occurrence.

AlarmManager registration is outside that transaction. Registration persists the occurrence first, and restoration re-registers the same deadline and token. Every active service start repairs recurring scheduling even if a retried FIRE token was already consumed. This covers death both before writing the next occurrence and after that write but before OS registration.

Normal reconciliation preserves the saved deadline. TIME_SET and TIMEZONE_CHANGED recalculate local schedules, except for a 15-second grace period around an event that just became due. Diagnostic alarms have absolute deadlines and do not become daily alarms.

### Recovery policy

An overdue occurrence can catch up for 10 minutes. Later recovery records MISSED and offers a normal missed-alarm notification when allowed. One-shots become disabled; repeats schedule their next occurrence. This is a product decision to avoid unexpected alarms hours or days later.

A recent already-ringing alarm can resume after an actual reboot through a separate alarm-clock event five seconds after recovery is detected. One-shots stay disabled. BOOT_COUNT distinguishes a reboot from repeated broadcasts or the Android 15 stopped-state transition. First unlock does not erase playback that started during Direct Boot. The 10-minute policy applies to recovery, not to uninterrupted ringing, which continues until dismissal.

## Audio and imported-file ownership

RingService promptly promotes itself, requests transient audio focus with USAGE_ALARM, holds MediaPlayer's partial wake lock, vibrates and returns START_STICKY while active. Teardown releases the player, listener, focus and vibrator.

Playback tries the selected copy or normal phone ringtone, the bundled gentle chime, then the system alarm. Synchronous and asynchronous errors advance that bounded chain. Player-identity and destruction checks reject obsolete callbacks. Exhausted attempts log the failure and use a notification fallback when allowed. AUDIO_STARTED proves MediaPlayer startup, not physical speaker output or an unmuted alarm stream.

Custom sounds are copied into device-protected app storage and decoded before acceptance. Saved alarm references, live drafts and in-flight copies protect files from pruning. All successful selections remain owned until the draft finishes: Android may have saved a previous selection before an import completes while the activity is stopped. Fresh app launch cleans abandoned files. Form state and draft ownership survive configuration changes.

## Automated acceptance gates

Current outcomes and downloadable logs are attached to [workflow runs](https://github.com/NHLOCAL/Koom/actions/workflows/android.yml). These are gate definitions, not claims that every future run passes.

| Gate | Required evidence | Boundary |
| --- | --- | --- |
| JVM | Calendar, recurrence, DST, grace-period and puzzle tests pass. | Pure rules. |
| Build/lint | APK and instrumentation compile; no lint errors. | Warnings remain visible. |
| Store/scheduler | Atomic acceptance, stale rejection, cancellation, preserved deadline, boot retention and missed policy. | Some cases seed a disk snapshot instead of physically killing each instruction. |
| Service | Queued empty/valid starts preserve playback; partially completed recurring schedules recover. | Android service tests with instrumentation alive. |
| Sound | Packaged chime, real copy/decode, reference protection and cleanup. | No injected media-server death or speaker measurement. |
| Process death | Exact OS registration; live process killed with am kill; PID absent before due; fresh RECEIVED, SERVICE_FOREGROUND and AUDIO_STARTED. | Ordinary process removal, not force-stop. |
| Screen-off Doze | Noninteractive and forced deep idle, followed by the same process-death and playback checks. | Disposable emulator settings are restored. |
| Direct Boot | Real PIN, changed boot ID, locked user, restored same due/token, process removal, delivery and resumed RingActivity while still locked. | Real emulator lock state, not OEM firmware behavior. |

Between verified process absence and playback the host only reads Logcat. It does not keep target-process instrumentation alive or use a debug hook to trigger delivery. The queue assertion correlates PendingIntent URI and record identity with the exact RTC_WAKEUP alarm and deadline; a package name or old delivery history is insufficient.

Debug hooks are excluded from release builds. The script refuses PIN/reboot operations without explicit disposable-emulator configuration and ro.kernel.qemu=1. Logs, dumps, screenshot and reports upload even on failure. Cleanup restores idle settings, simulated battery, PIN and diagnostic state as far as the emulator connection permits.

### Baseline evidence

The repaired baseline at `960d0ca167ac31084dbd59e6ea29c0b3c2c617c4` passed build and ordinary process-death delivery on API 31 and API 35 in [run 37879089126](https://github.com/NHLOCAL/Koom/actions/runs/37879089126). That establishes that the previous workflow failure was its missing-APK precondition. Extended results for the subsequent code are recorded on the PR.

## OEM and platform limits

Disabling battery optimization does not prove that vendor auto-start controls or a RAM cleaner permit alarms. On the physical device, compare the pending alarm and package stopped state before and after the actual cleaner. Koom's local diagnostics add device/API context, background restrictions, alarm volume and available last-process-exit information.

am kill, removing a recent task, Android 13+ Active apps Stop, and Settings Force Stop have different semantics. Active apps Stop leaves future scheduled alarms; force-stop prevents ordinary autonomous operation, and Android 15 explicitly cancels the stopped package's PendingIntents. An app cannot reliably countermand that stopped-state decision.

Shutdown clears AlarmManager registrations. Boot receivers restore schedules once Android starts. Powering on a fully switched-off phone needs vendor RTC/firmware support; public application APIs do not provide a portable implementation. Reboot test success is not proof of powered-off wake-up or OEM-cleaner immunity.

## Official references

Reviewed 2026-10-09. Recovery windows and beta packaging are Koom decisions.

1. [AlarmManager](https://developer.android.com/reference/android/app/AlarmManager): alarm clocks, process-independent PendingIntent, shutdown and idle.
2. [Schedule alarms](https://developer.android.com/develop/background-work/services/alarms): permissions and API selection.
3. [Foreground-service background starts](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start): user-requested exact-alarm exemption.
4. [Foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types): mediaPlayback requirements; systemExempted eligibility does not require changing a working audio service's type.
5. [Direct Boot](https://developer.android.com/privacy-and-security/direct-boot): storage, receivers and PIN-based verification.
6. [Doze and App Standby](https://developer.android.com/training/monitoring-device-state/doze-standby): alarm clocks and idle testing.
7. [AOSP DeskClock AlarmStateManager](https://android.googlesource.com/platform/packages/apps/DeskClock/+/refs/heads/main/src/com/android/deskclock/alarms/AlarmStateManager.kt): direct service PendingIntent rationale and the 15-second alarm-fire buffer.
8. [Service.stopSelfResult](https://developer.android.com/reference/android/app/Service#stopSelfResult(int)): newer start requests not yet received by callbacks.
9. [MediaPlayer.OnErrorListener](https://developer.android.com/reference/android/media/MediaPlayer.OnErrorListener): errors after successful startup.
10. [Android 15 stopped-state changes](https://developer.android.com/about/versions/15/behavior-changes-all): canceled PendingIntents and stopped-state behavior.
11. [User stopping a foreground service](https://developer.android.com/develop/background-work/services/fgs/handle-user-stopping): Active apps Stop and future alarms.
12. [AOSP app power management](https://source.android.com/docs/core/power/app_mgmt): platform and manufacturer restrictions.
