package top.zekal.koom

import android.Manifest
import android.app.NotificationManager
import android.os.PowerManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import top.zekal.koom.ui.AlarmEditor
import top.zekal.koom.ui.HomeScreen
import top.zekal.koom.ui.KoomTheme
import java.time.LocalTime

class MainActivity : ComponentActivity() {
    private val store by lazy { AlarmStore(this) }
    private val diagnostics by lazy { AlarmDiagnostics(this) }
    private val refresh = mutableIntStateOf(0)
    private val showNotificationIntroduction = mutableStateOf(false)
    private val permissionPrefs by lazy { getSharedPreferences("koom_permission_ui", MODE_PRIVATE) }

    private val notificationPermissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            diagnostics.record(if (granted) "NOTIFICATION_GRANTED" else "NOTIFICATION_DENIED")
            refresh.intValue++
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { AlarmNotification.ensureChannel(this) }
        showNotificationIntroduction.value =
            Build.VERSION.SDK_INT >= 33 && !hasPostNotificationsPermission() &&
                !permissionPrefs.getBoolean("notification_intro_seen", false)

        setContent {
            KoomTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        val revision = refresh.intValue
                        val result = remember(revision) { runCatching { store.all() } }
                        if (result.isFailure) {
                            Text("לא ניתן לקרוא את השעונים השמורים. " +
                                "הנתונים הישנים לא נמחקו. אל תסיר את האפליקציה.")
                        } else {
                            val all = result.getOrThrow().filterNot {
                                it.id == AlarmScheduler.TEST_ALARM_ID
                            }
                            var editing by remember { mutableStateOf<Alarm?>(null) }
                            var adding by remember { mutableStateOf(false) }

                            if (adding || editing != null) {
                                val draft = remember(adding) {
                                    val time = LocalTime.now().plusMinutes(5)
                                    Alarm(hour = time.hour, minute = time.minute)
                                }
                                val base = editing ?: draft
                                AlarmEditor(
                                    alarm = base,
                                    isNew = adding,
                                    onBack = { editing = null; adding = false },
                                    onSave = { updated ->
                                        try {
                                            val scheduler = AlarmScheduler(this@MainActivity)
                                            if (updated.enabled && !scheduler.canSchedule()) {
                                                message("לא נשמר כשעון פעיל: חסרה הרשאה לשעון מדויק")
                                                openExactAlarmSettings()
                                            } else {
                                                store.upsert(updated)
                                                val ok = scheduler.update(updated)
                                                if (ok) {
                                                    editing = null
                                                    adding = false
                                                } else {
                                                    store.upsert(updated.copy(enabled = false))
                                                    message("השעון נשמר כבוי כי Android סירב לתזמן אותו")
                                                }
                                                refresh.intValue++
                                            }
                                        } catch (error: Exception) {
                                            diagnostics.record("SAVE_FAILED", detail = error.message.orEmpty())
                                            message("שמירת השעון נכשלה: " + error.message)
                                        }
                                    },
                                    onDelete = {
                                        try {
                                            store.delete(base.id)
                                            AlarmScheduler(this@MainActivity).cancel(base.id)
                                            editing = null
                                            adding = false
                                            refresh.intValue++
                                        } catch (error: Exception) {
                                            message("מחיקה נכשלה: " + error.message)
                                        }
                                    }
                                )
                            } else {
                                val scheduler = AlarmScheduler(this@MainActivity)
                                HomeScreen(
                                    alarms = all,
                                    preciseAlarms = scheduler.canSchedule(),
                                    notifications = AlarmNotification.notificationsAllowed(this@MainActivity),
                                    fullScreen = fullScreenAllowed(),
                                    systemNextAlarmMillis = scheduler.nextSystemAlarmMillis(),
                                    recentEvents = diagnostics.recent(),
                                    batteryExempt = (getSystemService(POWER_SERVICE) as PowerManager)
                                        .isIgnoringBatteryOptimizations(packageName),
                                    onAdd = { adding = true },
                                    onEdit = { editing = it },
                                    onToggle = { alarm ->
                                        try {
                                            val updated = alarm.copy(enabled = !alarm.enabled)
                                            if (updated.enabled && !scheduler.canSchedule()) {
                                                message("השעון לא הופעל: נדרשת הרשאה לשעון מדויק")
                                                openExactAlarmSettings()
                                            } else {
                                                store.upsert(updated)
                                                if (!scheduler.update(updated)) {
                                                    store.upsert(updated.copy(enabled = false))
                                                    message("השעון נשמר כבוי: תזמון מערכת נכשל")
                                                }
                                                refresh.intValue++
                                            }
                                        } catch (error: Exception) {
                                            message("השינוי נכשל: " + error.message)
                                        }
                                    },
                                    onFixAlarmPermission = { openExactAlarmSettings() },
                                    onFixNotifications = { requestOrOpenNotificationSettings() },
                                    onFixFullScreen = { openFullScreenSettings() },
                                    onBatterySettings = { openBatterySettings() },
                                    onTestAlarm = {
                                        when {
                                            !scheduler.canSchedule() -> {
                                                message("הבדיקה לא יכולה להתחיל בלי הרשאת שעון מדויק")
                                                openExactAlarmSettings()
                                            }
                                            store.activeIds().isNotEmpty() -> {
                                                message("סיים קודם את הצלצול הפעיל")
                                            }
                                            else -> {
                                                val ok = scheduler.scheduleTest(20_000L)
                                                message(
                                                    if (ok) "הבדיקה תצלצל בעוד 20 שניות. נעל את המסך."
                                                    else "Android סירב לרשום את בדיקת ההשכמה"
                                                )
                                                if (!AlarmNotification.notificationsAllowed(this@MainActivity)) {
                                                    requestOrOpenNotificationSettings()
                                                }
                                                refresh.intValue++
                                            }
                                        }
                                    }
                                )
                            }
                        }

                        if (showNotificationIntroduction.value) {
                            AlertDialog(
                                onDismissRequest = { closeIntroduction() },
                                title = { Text("אפשר התראות השכמה") },
                                text = { Text("כדי שהשעון יוכל להעיר אותך גם כשהמסך נעול " +
                                    "והאפליקציה סגורה, צריך לאשר התראות.") },
                                confirmButton = {
                                    TextButton(onClick = {
                                        closeIntroduction()
                                        requestOrOpenNotificationSettings()
                                    }) { Text("אישור התראות") }
                                },
                                dismissButton = {
                                    TextButton(onClick = { closeIntroduction() }) { Text("לא עכשיו") }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            val scheduler = AlarmScheduler(this)
            if (scheduler.canSchedule()) scheduler.reconcile()
        } catch (e: Exception) {
            diagnostics.record("RESCHEDULE_FAILED", detail = e.message.orEmpty())
        }
        refresh.intValue++
        try {
            if (store.activeIds().isNotEmpty()) {
                startActivity(Intent(this, RingActivity::class.java))
            }
        } catch (e: Exception) {
            diagnostics.record("RESTORE_RING_FAILED", detail = e.message.orEmpty())
        }
    }

    private fun closeIntroduction() {
        permissionPrefs.edit().putBoolean("notification_intro_seen", true).apply()
        showNotificationIntroduction.value = false
    }

    private fun hasPostNotificationsPermission() =
        Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun fullScreenAllowed() =
        Build.VERSION.SDK_INT < 34 ||
            getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    private fun requestOrOpenNotificationSettings() {
        if (!hasPostNotificationsPermission() && Build.VERSION.SDK_INT >= 33 &&
            !permissionPrefs.getBoolean("notification_requested", false)) {
            permissionPrefs.edit().putBoolean("notification_requested", true).apply()
            notificationPermissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            safeStart(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        }
    }

    private fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= 31) {
            safeStart(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                .setData(Uri.parse("package:" + packageName)))
        } else {
            message("אין צורך בהרשאה זו בגרסת Android שלך")
        }
    }

    private fun openFullScreenSettings() {
        if (Build.VERSION.SDK_INT >= 34) {
            safeStart(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                .setData(Uri.parse("package:" + packageName)))
        } else {
            message("מסך מלא מאושר בגרסת Android שלך")
        }
    }

    private fun openBatterySettings() {
        // Samsung publishes an official deep link to its Never Sleeping Apps list.
        if (Build.MANUFACTURER.equals("samsung", ignoreCase = true)) {
            val samsungSettings = Intent(
                "com.samsung.android.sm.ACTION_OPEN_CHECKABLE_LISTACTIVITY"
            ).setPackage("com.samsung.android.lool").putExtra("activity_type", 2)
            if (runCatching { startActivity(samsungSettings) }.isSuccess) return
        }
        // OEM-specific private autostart lists do not expose a standard API.
        safeStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:$packageName")))
    }

    private fun safeStart(intent: Intent) {
        try { startActivity(intent) }
        catch (e: Exception) {
            diagnostics.record("SETTINGS_FAILED", detail = e.message.orEmpty())
            message("לא ניתן לפתוח הגדרות. פתח הגדרות > אפליקציות > קום.")
        }
    }

    private fun message(value: String) {
        Toast.makeText(this, value, Toast.LENGTH_LONG).show()
    }
}
