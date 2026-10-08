package top.zekal.koom

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
    private val refresh = mutableIntStateOf(0)
    private val requestNotification =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            refresh.intValue++
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KoomTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        val revision = refresh.intValue
                        val result = remember(revision) { runCatching { store.all() } }
                        if (result.isFailure) {
                            Text(
                                "לא ניתן לקרוא את נתוני השעונים. הנתונים הישנים נשמרו. " +
                                    "אל תסיר את האפליקציה. נסה לפתוח אותה שוב."
                            )
                        } else {
                            val alarms = result.getOrThrow()
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
                                    onSave = {
                                        try {
                                            store.upsert(it)
                                            if (!AlarmScheduler(this@MainActivity).update(it)) {
                                                Toast.makeText(
                                                    this@MainActivity,
                                                    "נדרשת הרשאה לשעונים מדויקים",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                            editing = null
                                            adding = false
                                            refresh.intValue++
                                        } catch (e: Exception) {
                                            Toast.makeText(
                                                this@MainActivity,
                                                "שמירת השעון נכשלה", Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    },
                                    onDelete = {
                                        store.delete(base.id)
                                        AlarmScheduler(this@MainActivity).cancel(base.id)
                                        editing = null
                                        adding = false
                                        refresh.intValue++
                                    }
                                )
                            } else {
                                HomeScreen(
                                    alarms = alarms,
                                    preciseAlarms = AlarmScheduler(this@MainActivity).canSchedule(),
                                    notifications = notificationsAllowed(),
                                    fullScreen = fullScreenAllowed(),
                                    onAdd = { adding = true },
                                    onEdit = { editing = it },
                                    onToggle = {
                                        val updated = it.copy(enabled = !it.enabled)
                                        store.upsert(updated)
                                        if (!AlarmScheduler(this@MainActivity).update(updated)) {
                                            Toast.makeText(
                                                this@MainActivity,
                                                "יש לאפשר שעונים מדויקים בהגדרות",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                        refresh.intValue++
                                    },
                                    onFixAlarmPermission = { openExactAlarmSettings() },
                                    onFixNotifications = { askForNotificationPermission() },
                                    onFixFullScreen = { openFullScreenSettings() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            if (AlarmScheduler(this).canSchedule()) AlarmScheduler(this).reconcile()
        } catch (_: Exception) { /* Errors are shown if storage cannot load. */ }
        refresh.intValue++
    }

    private fun notificationsAllowed(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun fullScreenAllowed(): Boolean =
        Build.VERSION.SDK_INT < 34 ||
            getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    private fun askForNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestNotification.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= 31) {
            startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    .setData(Uri.parse("package:" + packageName))
            )
        }
    }

    private fun openFullScreenSettings() {
        if (Build.VERSION.SDK_INT >= 34) {
            startActivity(
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                    .setData(Uri.parse("package:" + packageName))
            )
        }
    }
}
