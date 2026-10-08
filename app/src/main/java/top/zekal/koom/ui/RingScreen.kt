package top.zekal.koom.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.zekal.koom.Alarm
import top.zekal.koom.AlarmRules
import top.zekal.koom.NumberChallenge
import top.zekal.koom.PuzzleEngine
import top.zekal.koom.PuzzleKind
import top.zekal.koom.R

@Composable
fun RingScreen(alarm: Alarm, onSolved: () -> Unit) {
    BackHandler(enabled = true) { /* The ringing service remains active. */ }
    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Palette.night, Palette.surfaceBright)))
            .padding(horizontal = 22.dp)
            .systemBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Text("זה הזמן לקום.", style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(5.dp))
        Text(AlarmRules.clock(alarm), fontSize = 64.sp, fontWeight = FontWeight.Black,
            color = Palette.sunrise)
        Text(alarm.label, fontSize = 18.sp, color = Palette.muted)
        Spacer(Modifier.height(18.dp))
        Text("השעון ייפסק רק כשנסיים את החידה", fontSize = 14.sp,
            color = Palette.mint, textAlign = TextAlign.Center)
        Spacer(Modifier.height(25.dp))

        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Palette.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            when (alarm.puzzle) {
                PuzzleKind.IMAGE -> ImageChallenge(onSolved = onSolved, id = alarm.id)
                else -> NumberChallengeScreen(alarm = alarm, onSolved = onSolved)
            }
        }
        Spacer(Modifier.weight(1f))
        Text("תחשוב, תפתור, ותתחיל את היום.", color = Palette.muted, fontSize = 13.sp)
        Spacer(Modifier.height(22.dp))
    }
}

@Composable
private fun NumberChallengeScreen(alarm: Alarm, onSolved: () -> Unit) {
    var round by remember(alarm.id) { mutableIntStateOf(0) }
    val challenge = remember(alarm.id, round) { PuzzleEngine.next(alarm.puzzle) }
    var input by remember(alarm.id, round) { mutableStateOf("") }
    var error by remember(alarm.id, round) { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("שלב " + (round + 1) + " מתוך 3", color = Palette.mint)
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { round.toFloat() / 3f },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(5.dp)),
            color = Palette.sunrise, trackColor = Palette.surfaceBright
        )
        Spacer(Modifier.height(30.dp))
        Text(
            if (alarm.puzzle == PuzzleKind.SEQUENCE) "מה המספר הבא?" else "כמה יוצא?",
            fontSize = 17.sp, color = Palette.muted
        )
        Spacer(Modifier.height(12.dp))
        Text(
            challenge.question, fontSize = 28.sp, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(18.dp))
        Text(
            if (input.isBlank()) "?" else input,
            fontSize = 42.sp, fontWeight = FontWeight.Bold,
            color = if (error) Palette.coral else Palette.sunrise,
            modifier = Modifier.semantics { contentDescription = "תשובה: " + input }
        )
        Spacer(Modifier.height(14.dp))
        listOf(listOf("1", "2", "3"), listOf("4", "5", "6"),
            listOf("7", "8", "9"), listOf("⌫", "0", "✓")).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { symbol ->
                    Button(
                        modifier = Modifier.weight(1f).height(57.dp),
                        onClick = {
                            error = false
                            when (symbol) {
                                "⌫" -> input = input.dropLast(1)
                                "✓" -> {
                                    if (input.toIntOrNull() == challenge.answer) {
                                        if (round == 2) onSolved() else round++
                                    } else {
                                        error = true
                                        input = ""
                                    }
                                }
                                else -> if (input.length < 5) input += symbol
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor =
                                if (symbol == "✓") Palette.mint else Palette.surfaceBright,
                            contentColor = if (symbol == "✓") Palette.ink else Color.White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(2.dp)
                    ) {
                        Text(symbol, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        if (error) Text("לא בדיוק. נסה שוב!", color = Palette.coral)
    }
}

@Composable
private fun ImageChallenge(id: String, onSolved: () -> Unit) {
    val image = ImageBitmap.imageResource(R.drawable.puzzle_image)
    val tiles = remember(id) { mutableStateListOf<Int>().apply {
        addAll(PuzzleEngine.shuffledTiles())
    } }
    var selected by remember(id) { mutableIntStateOf(-1) }
    Column(
        Modifier.fillMaxWidth().padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("סדר את התמונה", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("בחר שני אריחים כדי להחליף ביניהם", color = Palette.muted)
        Spacer(Modifier.height(22.dp))
        Column(Modifier.fillMaxWidth()) {
            repeat(3) { row ->
                Row(Modifier.fillMaxWidth()) {
                    repeat(3) { col ->
                        val index = row * 3 + col
                        Box(
                            Modifier.weight(1f).aspectRatio(1f)
                                .padding(2.dp)
                                .border(
                                    if (selected == index) 3.dp else 1.dp,
                                    if (selected == index) Palette.sunrise else Palette.muted,
                                    RoundedCornerShape(7.dp)
                                )
                                .clip(RoundedCornerShape(7.dp))
                                .clickable {
                                    if (selected < 0) selected = index
                                    else {
                                        val order = PuzzleEngine.swap(tiles, selected, index)
                                        tiles.clear()
                                        tiles.addAll(order)
                                        selected = -1
                                        if (PuzzleEngine.solved(order)) onSolved()
                                    }
                                }
                                .semantics {
                                    contentDescription = "אריח " + (index + 1)
                                }
                        ) {
                            Canvas(Modifier.fillMaxSize()) {
                                val piece = tiles[index]
                                val srcWidth = image.width / 3
                                val srcHeight = image.height / 3
                                drawImage(
                                    image = image,
                                    srcOffset = IntOffset((piece % 3) * srcWidth, (piece / 3) * srcHeight),
                                    srcSize = IntSize(srcWidth, srcHeight),
                                    dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                                    filterQuality = FilterQuality.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("טיפ: הפינות והצבעים יעזרו לך.", color = Palette.muted)
    }
}
