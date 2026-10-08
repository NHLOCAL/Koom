package top.zekal.koom.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import top.zekal.koom.AlarmRules
import top.zekal.koom.PuzzleKind
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(
    alarms: List<Alarm>,
    preciseAlarms: Boolean,
    notifications: Boolean,
    fullScreen: Boolean,
    onAdd: () -> Unit,
    onEdit: (Alarm) -> Unit,
    onToggle: (Alarm) -> Unit,
    onFixAlarmPermission: () -> Unit,
    onFixNotifications: () -> Unit,
    onFixFullScreen: () -> Unit
) {
    val next = alarms.mapNotNull { alarm ->
        AlarmRules.next(alarm, ZonedDateTime.now())?.let { alarm to it }
    }.minByOrNull { it.second }

    Scaffold(
        containerColor = Palette.night,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("שעון חדש", fontWeight = FontWeight.Bold) },
                icon = { Text("+", fontSize = 26.sp) },
                onClick = onAdd,
                containerColor = Palette.sunrise,
                contentColor = Palette.ink
            )
        }
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(20.dp))
            Text("קום.", fontSize = 48.sp, fontWeight = FontWeight.Black)
            Text("בוקר טוב מתחיל בהחלטה לקום.", color = Palette.muted, fontSize = 15.sp)
            Spacer(Modifier.height(30.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Palette.sunrise, Palette.coral)
                        )
                    ).padding(24.dp)
            ) {
                Column {
                    Text("ההשכמה הבאה", color = Palette.ink.copy(alpha = .75f))
                    Spacer(Modifier.height(10.dp))
                    if (next == null) {
                        Text("עוד לא קבענו", color = Palette.ink, fontSize = 32.sp,
                            fontWeight = FontWeight.Black)
                        Text("מוסיפים שעון ומתחילים.", color = Palette.ink)
                    } else {
                        Text(
                            AlarmRules.clock(next.first),
                            color = Palette.ink, fontSize = 62.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            next.second.format(
                                DateTimeFormatter.ofPattern("EEEE", Locale.forLanguageTag("he"))
                            ) + "  ·  " + next.first.label,
                            color = Palette.ink, fontSize = 16.sp
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))

            if (!preciseAlarms) PermissionWarning(
                "צריך הרשאה לשעונים מדויקים",
                "ללא הרשאה זו, שעונים עלולים לא לצלצל בזמן.",
                onFixAlarmPermission
            )
            if (!notifications) PermissionWarning(
                "הודעות אינן מאופשרות",
                "אפשר להציג התראה מלאה על מסך הנעילה לאחר אישור.",
                onFixNotifications
            )
            if (!fullScreen) PermissionWarning(
                "פתיחת מסך ההתעוררות חסומה",
                "אשר פתיחה במסך מלא כדי להציג חידות בזמן הצלצול.",
                onFixFullScreen
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("השעונים שלי", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.weight(1f))
                Text(alarms.size.toString(), color = Palette.muted)
            }
            Spacer(Modifier.height(12.dp))
            if (alarms.isEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Palette.surface),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("⏰", fontSize = 42.sp)
                        Spacer(Modifier.height(12.dp))
                        Text("בוא נקבע השכמה ראשונה", fontSize = 18.sp)
                        Text(
                            "שעון חכם שמכבים רק אחרי שחושבים.",
                            color = Palette.muted, textAlign = TextAlign.Center
                        )
                    }
                }
            }
            alarms.sortedWith(compareBy<Alarm> { !it.enabled }.thenBy { it.hour }.thenBy { it.minute })
                .forEach { alarm ->
                    AlarmCard(
                        alarm = alarm,
                        onEdit = { onEdit(alarm) },
                        onToggle = { onToggle(alarm) }
                    )
                    Spacer(Modifier.height(10.dp))
                }
            Spacer(Modifier.height(110.dp))
        }
    }
}

@Composable
private fun PermissionWarning(title: String, detail: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Palette.surfaceBright)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(detail, fontSize = 13.sp, color = Palette.muted)
            }
            TextButton(onClick = onClick) { Text("אפשר") }
        }
    }
}

@Composable
private fun AlarmCard(alarm: Alarm, onEdit: () -> Unit, onToggle: () -> Unit) {
    val puzzleName = when (alarm.puzzle) {
        PuzzleKind.MATH -> "חשבון"
        PuzzleKind.SEQUENCE -> "סדרה"
        PuzzleKind.IMAGE -> "תמונה"
    }
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Palette.surface),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onEdit).padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    AlarmRules.clock(alarm),
                    fontSize = 34.sp, fontWeight = FontWeight.Bold,
                    color = if (alarm.enabled) Color.White else Palette.muted
                )
                Spacer(Modifier.height(4.dp))
                Text(alarm.label, fontSize = 15.sp)
                Text(
                    AlarmRules.daysText(alarm.daysMask) + "  ·  " + puzzleName,
                    fontSize = 13.sp, color = Palette.muted
                )
            }
            Switch(
                checked = alarm.enabled,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Palette.ink,
                    checkedTrackColor = Palette.mint
                )
            )
        }
    }
}
