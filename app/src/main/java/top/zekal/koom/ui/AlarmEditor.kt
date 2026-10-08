package top.zekal.koom.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.zekal.koom.Alarm
import top.zekal.koom.PuzzleKind

@Composable
fun AlarmEditor(
    alarm: Alarm,
    isNew: Boolean,
    onBack: () -> Unit,
    onSave: (Alarm) -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var hour by remember(alarm.id) { mutableIntStateOf(alarm.hour) }
    var minute by remember(alarm.id) { mutableIntStateOf(alarm.minute) }
    var label by remember(alarm.id) { mutableStateOf(alarm.label) }
    var daysMask by remember(alarm.id) { mutableIntStateOf(alarm.daysMask) }
    var puzzle by remember(alarm.id) { mutableStateOf(alarm.puzzle) }
    var showDelete by remember { mutableStateOf(false) }

    Scaffold(containerColor = Palette.night) { insets ->
        Column(
            Modifier.fillMaxSize().padding(insets)
                .verticalScroll(rememberScrollState()).padding(20.dp)
        ) {
            TextButton(onClick = onBack) { Text("חזרה לשעונים") }
            Spacer(Modifier.height(14.dp))
            Text(
                if (isNew) "שעון חדש" else "עריכת השעון",
                style = MaterialTheme.typography.headlineMedium
            )
            Text("מתי מתחילים את היום?", color = Palette.muted)
            Spacer(Modifier.height(28.dp))

            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Palette.surface)
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TextButton(onClick = {
                        TimePickerDialog(
                            context, { _, h, m -> hour = h; minute = m },
                            hour, minute, true
                        ).show()
                    }) {
                        Text(
                            "%02d:%02d".format(hour, minute),
                            fontSize = 62.sp, fontWeight = FontWeight.Black,
                            color = Palette.sunrise
                        )
                    }
                    Text("לחץ לשינוי השעה", color = Palette.muted)
                }
            }

            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = label,
                onValueChange = { if (it.length <= 60) label = it },
                label = { Text("שם השעון") },
                singleLine = true
            )
            Spacer(Modifier.height(24.dp))
            Text("ימי חזרה", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            Text("ללא בחירה: השכמה חד פעמית", color = Palette.muted, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))

            val days = listOf("א", "ב", "ג", "ד", "ה", "ו", "ש")
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                days.forEachIndexed { i, day ->
                    FilterChip(
                        selected = daysMask and (1 shl i) != 0,
                        onClick = { daysMask = daysMask xor (1 shl i) },
                        label = { Text(day, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Palette.mint,
                            selectedLabelColor = Palette.ink
                        )
                    )
                }
            }
            Spacer(Modifier.height(22.dp))

            Text("איך מכבים את הצלצול?", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            Text("פותרים את החידה. אין קיצור דרך במסך.", color = Palette.muted, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            PuzzleKind.entries.forEach { kind ->
                val description = when (kind) {
                    PuzzleKind.MATH -> "3 תרגילי חשבון"
                    PuzzleKind.SEQUENCE -> "3 סדרות מספרים"
                    PuzzleKind.IMAGE -> "הרכבת תמונה מ-9 אריחים"
                }
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 6.dp)
                        .background(Palette.surface, RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = puzzle == kind,
                        onClick = { puzzle = kind }
                    )
                    Text(description, modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(18.dp))

            Button(
                onClick = {
                    onSave(
                        alarm.copy(
                            hour = hour, minute = minute,
                            label = label.trim().ifEmpty { "שעון מעורר" },
                            daysMask = daysMask, puzzle = puzzle,
                            enabled = if (isNew) true else alarm.enabled
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Palette.sunrise, contentColor = Palette.ink
                ),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text("שמירת השעון", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            if (!isNew) {
                Spacer(Modifier.height(10.dp))
                TextButton(
                    onClick = { showDelete = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("מחיקת השעון", color = Palette.coral) }
            }
            Spacer(Modifier.height(28.dp))
        }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("למחוק את השעון?") },
            text = { Text("המחיקה תבטל את ההשכמות העתידיות שלו.") },
            confirmButton = {
                TextButton(onClick = { showDelete = false; onDelete() }) { Text("מחק") }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("ביטול") }
            }
        )
    }
}
