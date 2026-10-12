package top.zekal.koom.ui

import androidx.compose.foundation.background
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.zekal.koom.Alarm
import top.zekal.koom.AlarmEvent
import top.zekal.koom.AlarmRules
import top.zekal.koom.PuzzleKind
import java.text.SimpleDateFormat
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

/**
 * Three primary destinations: alarms, preferences and reliability diagnostics.
 * NavigationBar / TopAppBar / floating action comply with Material 3 conventions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    alarms: List<Alarm>,
    preciseAlarms: Boolean,
    notifications: Boolean,
    fullScreen: Boolean,
    systemNextAlarmMillis: Long?,
    recentEvents: List<AlarmEvent>,
    batteryExempt: Boolean,
    onAdd: () -> Unit,
    onEdit: (Alarm) -> Unit,
    onToggle: (Alarm) -> Unit,
    onFixAlarmPermission: () -> Unit,
    onFixNotifications: () -> Unit,
    onFixFullScreen: () -> Unit,
    onTestAlarm: () -> Unit,
    onBatterySettings: () -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        "שעונים" to Icons.Default.Alarm,
        "הגדרות" to Icons.Default.Settings,
        "בדיקה" to Icons.Default.VerifiedUser
    )
    Scaffold(
        containerColor = Palette.night,
        topBar = {
            TopAppBar(
                title = { Text("קום.", fontWeight = FontWeight.Black) },
                actions = {
                    IconButton(onClick = { tab = 2 }) {
                        Icon(Icons.Default.FactCheck, contentDescription = "בדיקת תקינות")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Palette.night)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Palette.surface) {
                tabs.forEachIndexed { index, entry ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Icon(entry.second, contentDescription = null) },
                        label = { Text(entry.first) },
                        alwaysShowLabel = true,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Palette.ink,
                            selectedTextColor = Palette.mint,
                            indicatorColor = Palette.mint
                        )
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab == 0) {
                ExtendedFloatingActionButton(
                    onClick = onAdd,
                    icon = { Icon(Icons.Default.AddAlarm, contentDescription = null) },
                    text = { Text("שעון חדש", fontWeight = FontWeight.Bold) },
                    containerColor = Palette.sunrise, contentColor = Palette.ink
                )
            }
        }
    ) { insets ->
        when (tab) {
            0 -> AlarmListTab(alarms, onEdit, onToggle, Modifier.padding(insets))
            1 -> SettingsTab(
                preciseAlarms, notifications, fullScreen, batteryExempt,
                onFixAlarmPermission, onFixNotifications, onFixFullScreen,
                onBatterySettings, Modifier.padding(insets)
            )
            else -> DiagnosticsTab(
                alarms, systemNextAlarmMillis, recentEvents, onTestAlarm,
                Modifier.padding(insets)
            )
        }
    }
}

@Composable
private fun AlarmListTab(
    alarms: List<Alarm>, onEdit: (Alarm) -> Unit, onToggle: (Alarm) -> Unit,
    modifier: Modifier = Modifier
) {
    val now = ZonedDateTime.now()
    val next = alarms.mapNotNull { alarm ->
        AlarmRules.next(alarm, now)?.let { alarm to it }
    }.minByOrNull { it.second }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("בוקר טוב מתחיל ברגע שמחליטים לקום.", color = Palette.muted, fontSize = 15.sp)
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
                    .background(Brush.linearGradient(listOf(Palette.sunrise, Palette.coral)))
                    .padding(22.dp)
            ) {
                Column {
                    Text("ההשכמה הבאה", color = Palette.ink.copy(alpha = .8f))
                    Spacer(Modifier.height(8.dp))
                    if (next == null) {
                        Text("עדיין אין שעון פעיל", color = Palette.ink,
                            fontSize = 26.sp, fontWeight = FontWeight.Black)
                        Text("לחץ על שעון חדש כדי להתחיל", color = Palette.ink)
                    } else {
                        Text(AlarmRules.clock(next.first), color = Palette.ink,
                            fontSize = 60.sp, fontWeight = FontWeight.Black)
                        Text(
                            next.second.format(DateTimeFormatter.ofPattern(
                                "EEEE", Locale.forLanguageTag("he"))) + "  ·  " + next.first.label,
                            color = Palette.ink, fontSize = 15.sp
                        )
                    }
                }
            }
        }
        item {
            Spacer(Modifier.height(9.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("השעונים שלי", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.weight(1f))
                Text(alarms.size.toString(), color = Palette.muted)
            }
        }
        if (alarms.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Palette.surface),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.AlarmAdd, contentDescription = null,
                            tint = Palette.mint, modifier = Modifier.size(46.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("השכמה ראשונה?", style = MaterialTheme.typography.titleMedium)
                        Text("משהו קטן לחשוב עליו, ובוקר חדש מתחיל.",
                            color = Palette.muted, textAlign = TextAlign.Center)
                    }
                }
            }
        }
        items(
            alarms.sortedWith(compareBy<Alarm> { !it.enabled }
                .thenBy { it.hour }.thenBy { it.minute }),
            key = { it.id }
        ) { alarm ->
            AlarmCard(alarm, { onEdit(alarm) }, { onToggle(alarm) })
        }
    }
}

@Composable
private fun AlarmCard(alarm: Alarm, onEdit: () -> Unit, onToggle: () -> Unit) {
    val puzzleLabel = when (alarm.puzzle) {
        PuzzleKind.KNOWLEDGE -> "ידע כללי"
        PuzzleKind.LOGIC -> "היגיון"
        PuzzleKind.MATH -> "חשבון"
        PuzzleKind.SEQUENCE -> "סדרה"
        PuzzleKind.IMAGE -> "תמונה"
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = Palette.surface),
        shape = RoundedCornerShape(21.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onEdit)
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(AlarmRules.clock(alarm), fontSize = 35.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (alarm.enabled) Color.White else Palette.muted)
                Text(alarm.label, fontSize = 15.sp)
                Spacer(Modifier.height(4.dp))
                Text(AlarmRules.daysText(alarm.daysMask) + " · " + puzzleLabel,
                    color = Palette.muted, fontSize = 12.sp)
                Text(alarm.soundLabel, color = Palette.mint, fontSize = 12.sp)
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "עריכת שעון")
            }
            Switch(
                checked = alarm.enabled,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = Palette.mint,
                    checkedThumbColor = Palette.ink
                )
            )
        }
    }
}

@Composable
private fun SettingsTab(
    preciseAlarms: Boolean, notifications: Boolean,
    fullScreen: Boolean, batteryExempt: Boolean,
    onFixAlarmPermission: () -> Unit, onFixNotifications: () -> Unit,
    onFixFullScreen: () -> Unit, onBatterySettings: () -> Unit,
    modifier: Modifier
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)
    ) {
        Text("הגדרות האפליקציה", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text("כל שעון משתמש במנגינה ובחידה שנבחרו בעריכה שלו.",
            color = Palette.muted)
        Spacer(Modifier.height(22.dp))
        StatusItem("שעונים מדויקים", preciseAlarms,
            "מערכת Android אחראית לתזמון גם כשהאפליקציה סגורה.",
            onFixAlarmPermission)
        StatusItem("התראות", notifications,
            "כולל הצגת ההשכמה במסך הנעילה.",
            onFixNotifications)
        StatusItem("התראה במסך מלא", fullScreen,
            "פתיחת מסך החידה בזמן הצלצול.",
            onFixFullScreen)

        Spacer(Modifier.height(16.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = Palette.surface),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.BatterySaver, contentDescription = null,
                        tint = Palette.sunrise)
                    Spacer(Modifier.width(9.dp))
                    Text("פעילות ברקע", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (batteryExempt) "המערכת מגדירה את Koom כפטורה מאופטימיזציות סוללה."
                    else "Koom אינה מוחרגת מאופטימיזציות סוללה. שעונים מדויקים אמורים לפעול " +
                        "גם כך, אבל יצרני מכשירים מסוימים עשויים להגביל אפליקציות.",
                    color = Palette.muted, fontSize = 14.sp
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onBatterySettings) {
                    Icon(Icons.Default.BatteryFull, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("בדוק הגבלות סוללה")
                }
                Spacer(Modifier.height(10.dp))
                Text(manufacturerAutostartAdvice(), color = Palette.muted, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "חשוב: עצירה כפויה (Force stop) בהגדרות Android מבטלת שעונים מתוזמנים " +
                "עד להפעלת האפליקציה מחדש. אף אפליקציה רגילה אינה יכולה לעקוף זאת.",
            color = Palette.sunrise, fontSize = 13.sp
        )
        Spacer(Modifier.height(50.dp))
    }
}

@Composable
private fun StatusItem(title: String, healthy: Boolean, description: String, onFix: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        colors = CardDefaults.cardColors(containerColor = Palette.surface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(if (healthy) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (healthy) Palette.mint else Palette.coral)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(description, color = Palette.muted, fontSize = 12.sp)
            }
            if (!healthy) TextButton(onClick = onFix) { Text("תקן") }
        }
    }
}

@Composable
private fun DiagnosticsTab(
    alarms: List<Alarm>, systemNextAlarmMillis: Long?,
    recentEvents: List<AlarmEvent>, onTestAlarm: () -> Unit,
    modifier: Modifier
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)
    ) {
        Text("בדיקת השכמה", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(10.dp))
        Text("דרך קצרה לבדוק ש-Koom באמת מצלצלת בלי להישאר פתוחה.",
            color = Palette.muted)
        Spacer(Modifier.height(18.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = Palette.surface),
            shape = RoundedCornerShape(23.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Text("השעון הבא שהמכשיר מדווח עליו", color = Palette.muted)
                Text(
                    systemNextAlarmMillis?.let {
                        SimpleDateFormat("dd/MM  HH:mm:ss", Locale.forLanguageTag("he"))
                            .format(Date(it))
                    } ?: "אין התראה רשומה",
                    color = if (systemNextAlarmMillis == null) Palette.coral else Palette.mint,
                    fontSize = 23.sp, fontWeight = FontWeight.Bold
                )
                Text("יכול לכלול גם שעון מאפליקציה אחרת.",
                    color = Palette.muted, fontSize = 11.sp)
                Spacer(Modifier.height(16.dp))
                Button(
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    onClick = onTestAlarm,
                    shape = RoundedCornerShape(17.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Palette.mint, contentColor = Palette.ink
                    )
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("בדיקת צלצול בעוד 20 שניות", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                Text("לחץ ואז חזור למסך הבית של הטלפון או נעל את המסך.",
                    color = Palette.muted, fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ListAlt, contentDescription = null, tint = Palette.mint)
            Spacer(Modifier.width(8.dp))
            Text("יומן פעילות מקומי", style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(12.dp))
        if (recentEvents.isEmpty()) {
            Text("לא נרשמו עדיין אירועים.", color = Palette.muted)
        } else {
            recentEvents.take(18).forEach { event ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp),
                    shape = RoundedCornerShape(13.dp),
                    colors = CardDefaults.cardColors(containerColor = Palette.surface)
                ) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        val time = SimpleDateFormat("HH:mm:ss", Locale.US)
                            .format(Date(event.atMillis))
                        val failed = event.stage.contains("FAILED") ||
                            event.stage.contains("BLOCKED")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (failed) Icons.Default.ErrorOutline else Icons.Default.Check,
                                contentDescription = null,
                                tint = if (failed) Palette.coral else Palette.mint,
                                modifier = Modifier.size(19.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(time + "  " + eventDescription(event.stage),
                                fontSize = 13.sp,
                                color = if (failed) Palette.coral else Color.White)
                        }
                        if (event.detail.isNotBlank()) {
                            Text(event.detail, fontSize = 11.sp, color = Palette.muted)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(35.dp))
    }
}

private fun eventDescription(stage: String): String = when (stage) {
    "SCHEDULED" -> "השכמה נרשמה במערכת"
    "CANCELLED" -> "תזמון קודם הוחלף"
    "SCHEDULE_FAILED" -> "שגיאה בתזמון"
    "SCHEDULE_BLOCKED" -> "אין הרשאת תזמון"
    "RECEIVED" -> "Android הפעיל את ההשכמה"
    "NOTIFIED" -> "נוצרה התראה"
    "NOTIFICATION_BLOCKED" -> "התראות חסומות"
    "NOTIFICATION_FAILED" -> "שגיאה בהתראה"
    "SERVICE_REQUESTED" -> "הופעל שירות הצלצול"
    "SERVICE_FOREGROUND" -> "שירות השמע פועל"
    "SERVICE_FAILED" -> "שירות הצלצול נכשל"
    "AUDIO_STARTED" -> "מנגינת השכמה הופעלה"
    "AUDIO_FAILED" -> "בעיה בהפעלת שמע"
    "AUDIO_PRIMARY_FAILED" -> "קובץ שמע לא זמין, מנגינת גיבוי"
    "SOUND_FILE_MISSING" -> "קובץ המנגינה לא נמצא"
    "SYSTEM_RESCHEDULED" -> "השעונים שוחזרו לאחר אירוע מערכת"
    "RESCHEDULE_FAILED" -> "שחזור תזמונים נכשל"
    "RECEIVER_FAILED" -> "קליטת ההשכמה נכשלה"
    "STOPPED" -> "הצלצול הופסק"
    "IGNORED" -> "אירוע שעון לא פעיל"
    else -> stage
}

/** OEM task killers may force-stop apps independently of Android battery optimization. */
private fun manufacturerAutostartAdvice(): String {
    return when (Build.MANUFACTURER.lowercase(java.util.Locale.ROOT)) {
        "xiaomi", "redmi", "poco" ->
            "במכשירי Xiaomi/Redmi: הגדרות > יישומים > הרשאות > הפעלה אוטומטית. " +
            "אפשר את Koom והחרג אותה ממנקה הזיכרון. בחלק מהדגמים ניתן לנעול אותה במסך היישומים האחרונים."
        "samsung" ->
            "במכשירי Samsung: טיפול במכשיר > סוללה > מגבלות שימוש ברקע > " +
            "יישומים שלעולם אינם ישנים. הוסף את Koom והוצא אותה מיישומים בשינה עמוקה."
        "huawei", "honor" ->
            "במכשירי Huawei/Honor: הגדרות > סוללה > הפעלת יישומים, עבור לניהול ידני " +
            "ואפשר הפעלה אוטומטית, הפעלה משנית ופעילות ברקע."
        "oppo", "realme", "oneplus", "vivo", "iqoo" ->
            "במכשיר זה בדוק בנוסף לאופטימיזציית סוללה את הגדרות הפעלה אוטומטית, " +
            "פעילות ברקע ואת החרגת Koom ממנקה הזיכרון של היצרן."
        else ->
            "אם ניקוי ה-RAM עוצר גם שעונים מדויקים: חפש במנהל היישומים " +
            "הפעלה אוטומטית, פעילות ברקע או החרגה מניקוי זיכרון. " +
            "ביטול אופטימיזציית סוללה לבדו אינו מונע Force Stop של היצרן."
    }
}
