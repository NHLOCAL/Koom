package top.zekal.koom.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.zekal.koom.Alarm
import top.zekal.koom.AlarmRules
import top.zekal.koom.PuzzleEngine
import top.zekal.koom.PuzzleKind
import top.zekal.koom.R

@Composable
fun RingScreen(alarm: Alarm, onSolved: () -> Unit) {
    BackHandler(enabled = true) { /* Music is owned by the ringing service. */ }
    var completing by remember(alarm.id) { mutableStateOf(false) }
    val finish: () -> Unit = {
        if (!completing) {
            completing = true
            onSolved()
        }
    }
    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Palette.night, Palette.surfaceBright)))
            .systemBarsPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(22.dp))
        Text("בוקר טוב ☀", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(10.dp))
        Text(AlarmRules.clock(alarm), fontSize = 60.sp,
            fontWeight = FontWeight.Black, color = Palette.sunrise)
        Text(alarm.label, color = Palette.muted, fontSize = 16.sp)
        Spacer(Modifier.height(14.dp))
        Text("שאלה אחת וזהו. יום טוב מתחיל בקלות.",
            color = Palette.mint, fontSize = 15.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(22.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = Palette.surface),
            shape = RoundedCornerShape(26.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            when (alarm.puzzle) {
                // Numeric order and image coordinates stay LTR inside the Hebrew UI.
                PuzzleKind.MATH, PuzzleKind.SEQUENCE -> CompositionLocalProvider(
                    LocalLayoutDirection provides LayoutDirection.Ltr
                ) { NumberChallenge(alarm, finish) }
                PuzzleKind.KNOWLEDGE, PuzzleKind.LOGIC -> ChoiceChallenge(alarm, finish)
                PuzzleKind.IMAGE -> CompositionLocalProvider(
                    LocalLayoutDirection provides LayoutDirection.Ltr
                ) { ImageChallenge(alarm.id, finish) }
            }
        }
        Spacer(Modifier.height(26.dp))
        Text("קצת מחשבה, והבוקר שלך מתחיל.", color = Palette.muted,
            fontSize = 13.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ChoiceChallenge(alarm: Alarm, onSolved: () -> Unit) {
    val question = remember(alarm.id) { PuzzleEngine.choices(alarm.puzzle) }
    var error by remember(alarm.id) { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(if (alarm.puzzle == PuzzleKind.KNOWLEDGE) "ידע כללי" else "היגיון קל",
            color = Palette.mint, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(18.dp))
        Text(question.question, fontSize = 23.sp, fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center, lineHeight = 32.sp)
        Spacer(Modifier.height(24.dp))
        question.options.forEachIndexed { index, option ->
            OutlinedButton(
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(bottom = 8.dp),
                onClick = {
                    if (index == question.correctIndex) onSolved() else error = true
                },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text(option, fontSize = 18.sp, textAlign = TextAlign.Center)
            }
        }
        if (error) {
            Text("לא זאת. עוד ניסיון אחד קטן.", color = Palette.sunrise, fontSize = 13.sp)
        }
    }
}

@Composable
private fun NumberChallenge(alarm: Alarm, onSolved: () -> Unit) {
    val challenge = remember(alarm.id) { PuzzleEngine.next(alarm.puzzle) }
    var input by remember(alarm.id) { mutableStateOf("") }
    var error by remember(alarm.id) { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            if (alarm.puzzle == PuzzleKind.SEQUENCE) "מה המספר הבא?" else "חשבון קל לבוקר",
            color = Palette.mint, fontSize = 17.sp
        )
        Spacer(Modifier.height(18.dp))
        Text(challenge.question, fontSize = 28.sp, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(14.dp))
        Text(if (input.isBlank()) "?" else input, fontSize = 38.sp,
            color = if (error) Palette.coral else Palette.sunrise,
            modifier = Modifier.semantics { contentDescription = "תשובה: " + input })
        Spacer(Modifier.height(16.dp))
        listOf(listOf("1", "2", "3"), listOf("4", "5", "6"),
            listOf("7", "8", "9"), listOf("⌫", "0", "✓")).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    Button(
                        modifier = Modifier.weight(1f).height(58.dp),
                        onClick = {
                            error = false
                            when (key) {
                                "⌫" -> input = input.dropLast(1)
                                "✓" -> {
                                    if (input.toIntOrNull() == challenge.answer) onSolved()
                                    else { input = ""; error = true }
                                }
                                else -> if (input.length < 5) input += key
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (key == "✓") Palette.mint else Palette.surfaceBright,
                            contentColor = if (key == "✓") Palette.ink else Color.White
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) { Text(key, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        if (error) Text("כמעט! נסה שוב.", color = Palette.coral)
    }
}

@Composable
private fun ImageChallenge(id: String, onSolved: () -> Unit) {
    val image = ImageBitmap.imageResource(R.drawable.puzzle_image)
    val size = 2 // 2x2 rather than the previous 3x3: gentle morning challenge.
    val tiles = remember(id) { mutableStateListOf<Int>().apply {
        addAll(PuzzleEngine.shuffledTiles(gridSize = size))
    } }
    var selected by remember(id) { mutableIntStateOf(-1) }
    Column(
        Modifier.fillMaxWidth().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("הרכב את התמונה", fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Text("בחר שני אריחים להחלפה", color = Palette.muted)
        Spacer(Modifier.height(18.dp))
        Column(Modifier.fillMaxWidth()) {
            repeat(size) { row ->
                Row(Modifier.fillMaxWidth()) {
                    repeat(size) { col ->
                        val index = row * size + col
                        Box(
                            Modifier.weight(1f).aspectRatio(1f)
                                .padding(3.dp)
                                .border(if (selected == index) 3.dp else 1.dp,
                                    if (selected == index) Palette.sunrise else Palette.muted,
                                    RoundedCornerShape(10.dp))
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    if (selected < 0) selected = index else {
                                        val updated = PuzzleEngine.swap(tiles, selected, index)
                                        tiles.clear()
                                        tiles.addAll(updated)
                                        selected = -1
                                        if (PuzzleEngine.solved(updated)) onSolved()
                                    }
                                }
                                .semantics { contentDescription = "אריח " + (index + 1) }
                        ) {
                            Canvas(Modifier.fillMaxSize()) {
                                val source = tiles[index]
                                val w = image.width / size
                                val h = image.height / size
                                drawImage(
                                    image = image,
                                    srcOffset = IntOffset((source % size) * w, (source / size) * h),
                                    srcSize = IntSize(w, h),
                                    dstSize = IntSize(this.size.width.toInt(), this.size.height.toInt()),
                                    filterQuality = FilterQuality.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("ארבעה חלקים בלבד.", color = Palette.muted)
    }
}
