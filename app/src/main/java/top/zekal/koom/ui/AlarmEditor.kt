package top.zekal.koom.ui

import android.app.Activity
import android.app.TimePickerDialog
import android.content.Intent
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.zekal.koom.Alarm
import top.zekal.koom.AlarmSounds
import top.zekal.koom.PuzzleKind

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditor(
    alarm: Alarm,
    isNew: Boolean,
    onBack: () -> Unit,
    onSave: (Alarm) -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var hour by remember(alarm.id) { mutableIntStateOf(alarm.hour) }
    var minute by remember(alarm.id) { mutableIntStateOf(alarm.minute) }
    var label by remember(alarm.id) { mutableStateOf(alarm.label) }
    var daysMask by remember(alarm.id) { mutableIntStateOf(alarm.daysMask) }
    var puzzle by remember(alarm.id) { mutableStateOf(alarm.puzzle) }
    var soundFile by remember(alarm.id) { mutableStateOf(alarm.soundFile) }
    var soundLabel by remember(alarm.id) { mutableStateOf(alarm.soundLabel) }
    var importing by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(Unit) {
        onDispose { runCatching { player?.release() } }
    }

    LaunchedEffect(player) {
        if (player != null) {
            delay(9_000L)
            player?.release()
            player = null
        }
    }

    val chooseSound: (Uri, String?) -> Unit = { uri, preferredLabel ->
        scope.launch {
            importing = true
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    AlarmSounds.copyFromUri(context, uri, preferredLabel)
                }
            }
            result.onSuccess { choice ->
                soundFile = choice.fileName
                soundLabel = choice.label
                Toast.makeText(context, "המנגינה נבחרה", Toast.LENGTH_SHORT).show()
            }.onFailure { error ->
                Toast.makeText(context,
                    "לא ניתן לפתוח את השמע: " + (error.localizedMessage ?: "נסה קובץ אחר"),
                    Toast.LENGTH_LONG).show()
            }
            importing = false
        }
    }

    val ringtonePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = if (Build.VERSION.SDK_INT >= 33) {
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            if (uri != null) {
                val name = runCatching { RingtoneManager.getRingtone(context, uri)?.getTitle(context) }
                    .getOrNull() ?: "רינגטון מהטלפון"
                chooseSound(uri, name)
            } else {
                Toast.makeText(context, "לא נבחר רינגטון", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            // The imported audio is immediately copied into device-protected app storage.
            chooseSound(uri, null)
        }
    }

    Scaffold(
        containerColor = Palette.night,
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "שעון חדש" else "עריכת שעון") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "חזרה")
                    }
                },
                actions = {
                    IconButton(
                        enabled = !importing,
                        onClick = {
                            onSave(alarm.copy(hour = hour, minute = minute,
                                label = label.trim().ifBlank { "שעון מעורר" },
                                daysMask = daysMask, puzzle = puzzle,
                                soundFile = soundFile, soundLabel = soundLabel,
                                enabled = if (isNew) true else alarm.enabled))
                        }
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "שמור שעון",
                            tint = Palette.mint)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Palette.night)
            )
        }
    ) { insets ->
        Column(
            Modifier.fillMaxSize().padding(insets)
                .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            Text("מתי נקום?", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(14.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Palette.surface)
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TextButton(onClick = {
                        TimePickerDialog(context, { _, h, m -> hour = h; minute = m },
                            hour, minute, true).show()
                    }) {
                        Text("%02d:%02d".format(hour, minute), fontSize = 56.sp,
                            fontWeight = FontWeight.Black, color = Palette.sunrise)
                    }
                    Text("גע בשעה כדי לשנות", color = Palette.muted)
                }
            }

            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = label, onValueChange = { if (it.length <= 60) label = it },
                label = { Text("כינוי לשעון") }, singleLine = true
            )
            Spacer(Modifier.height(22.dp))
            Text("ימים", style = MaterialTheme.typography.titleMedium)
            Text("אם לא בוחרים יום, השעון חד פעמי", color = Palette.muted,
                fontSize = 13.sp)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("א", "ב", "ג", "ד", "ה", "ו", "ש").forEachIndexed { i, day ->
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

            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.MusicNote, contentDescription = null, tint = Palette.mint)
                Spacer(Modifier.width(8.dp))
                Text("מנגינת השכמה", style = MaterialTheme.typography.titleMedium)
            }
            Text(soundLabel, color = Palette.muted, fontSize = 14.sp)
            Spacer(Modifier.height(10.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = {
                    val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE,
                            RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_RINGTONE)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "בחר מנגינת השכמה")
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, AlarmSounds.defaultUri())
                    }
                    try { ringtonePicker.launch(intent) } catch (_: Exception) {
                        Toast.makeText(context, "לא נמצא בורר רינגטונים", Toast.LENGTH_LONG).show()
                    }
                }, enabled = !importing, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.LibraryMusic, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("רינגטון")
                }
                OutlinedButton(onClick = {
                    try { filePicker.launch(arrayOf("audio/*")) } catch (_: Exception) {
                        Toast.makeText(context, "לא ניתן לבחור קובץ", Toast.LENGTH_LONG).show()
                    }
                }, enabled = !importing, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("קובץ")
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    runCatching { player?.release() }
                    player = null
                    soundFile = null
                    soundLabel = "צלצול הטלפון"
                }, enabled = !importing) {
                    Icon(Icons.Default.Restore, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("ברירת מחדל")
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    val old = player
                    if (old != null) {
                        runCatching { old.stop(); old.release() }
                        player = null
                    } else {
                        try {
                            player = AlarmSounds.start(context, soundFile, loop = false)
                        } catch (error: Exception) {
                            Toast.makeText(context, "ניגון לא זמין: " + error.message,
                                Toast.LENGTH_LONG).show()
                        }
                    }
                }, enabled = !importing) {
                    Icon(if (player == null) Icons.Default.PlayArrow else Icons.Default.Stop,
                        contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (player == null) "האזן" else "עצור")
                }
            }
            if (importing) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("הקובץ יישמר באחסון הפרטי של קום כדי שינגן גם כשהאפליקציה סגורה.",
                fontSize = 12.sp, color = Palette.muted)

            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lightbulb, contentDescription = null, tint = Palette.mint)
                Spacer(Modifier.width(8.dp))
                Text("איך מכבים את השעון?", style = MaterialTheme.typography.titleMedium)
            }
            Text("שאלה אחת, ברמה נוחה לבוקר", fontSize = 13.sp, color = Palette.muted)
            Spacer(Modifier.height(10.dp))
            PuzzleKind.entries.forEach { kind ->
                val (title, icon) = when (kind) {
                    PuzzleKind.KNOWLEDGE -> "ידע כללי: 3 תשובות" to Icons.Default.School
                    PuzzleKind.LOGIC -> "היגיון יומיומי" to Icons.Default.Psychology
                    PuzzleKind.MATH -> "חשבון קל" to Icons.Default.Calculate
                    PuzzleKind.SEQUENCE -> "השלמת סדרה" to Icons.Default.Numbers
                    PuzzleKind.IMAGE -> "ארבעה חלקי תמונה" to Icons.Default.Image
                }
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 6.dp)
                        .background(Palette.surface, RoundedCornerShape(16.dp))
                        .selectable(selected = puzzle == kind, role = Role.RadioButton,
                            onClick = { puzzle = kind })
                        .padding(horizontal = 10.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(icon, contentDescription = null, tint = Palette.muted)
                    Spacer(Modifier.width(10.dp))
                    Text(title, modifier = Modifier.weight(1f), fontSize = 15.sp)
                    RadioButton(selected = puzzle == kind, onClick = null)
                }
            }

            Spacer(Modifier.height(22.dp))
            Button(
                onClick = {
                    onSave(alarm.copy(hour = hour, minute = minute,
                        label = label.trim().ifBlank { "שעון מעורר" },
                        daysMask = daysMask, puzzle = puzzle,
                        soundFile = soundFile, soundLabel = soundLabel,
                        enabled = if (isNew) true else alarm.enabled))
                },
                enabled = !importing,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Palette.sunrise, contentColor = Palette.ink),
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("שמירת השעון", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            if (!isNew) {
                TextButton(onClick = { showDelete = true },
                    modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Delete, contentDescription = null,
                        tint = Palette.coral)
                    Spacer(Modifier.width(6.dp))
                    Text("מחיקת השעון", color = Palette.coral)
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("למחוק את השעון?") },
            text = { Text("כל ההשכמות העתידיות של השעון הזה יתבטלו.") },
            confirmButton = {
                TextButton(onClick = { showDelete = false; onDelete() }) { Text("מחק") }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("ביטול") }
            }
        )
    }
}
