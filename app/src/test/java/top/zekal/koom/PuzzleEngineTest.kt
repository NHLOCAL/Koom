package top.zekal.koom

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class PuzzleEngineTest {
    @Test fun allMathAnswersMatchExpressions() {
        repeat(300) {
            val challenge = PuzzleEngine.next(PuzzleKind.MATH, Random(it))
            val tokens = challenge.question.removeSuffix(" = ?").split(" ")
            val first = tokens[0].toInt()
            val last = tokens[2].toInt()
            val result = when (tokens[1]) {
                "+" -> first + last
                "−" -> first - last
                "×" -> first * last
                else -> error("Unexpected operator")
            }
            assertEquals(challenge.question, result, challenge.answer)
        }
    }

    @Test fun allSequenceAnswersMatchDifference() {
        repeat(100) {
            val challenge = PuzzleEngine.next(PuzzleKind.SEQUENCE, Random(it))
            val numbers = challenge.question.split("  ·  ")
                .dropLast(1).map { value -> value.trim().toInt() }
            val step = numbers[1] - numbers[0]
            assertTrue(numbers.zipWithNext().all { (a, b) -> b - a == step })
            assertEquals(numbers.last() + step, challenge.answer)
        }
    }

    @Test fun imageChallengeNeverStartsSolved() {
        repeat(150) {
            val tiles = PuzzleEngine.shuffledTiles(Random(it))
            assertEquals((0..8).toSet(), tiles.toSet())
            assertFalse(PuzzleEngine.solved(tiles))
        }
    }

    @Test fun swappingTwoTilesTwiceRestoresState() {
        val tiles = PuzzleEngine.shuffledTiles(Random(5))
        val once = PuzzleEngine.swap(tiles, 0, 8)
        val twice = PuzzleEngine.swap(once, 0, 8)
        assertEquals(tiles, twice)
    }

    @Test fun curatedChoicesAreValidAndEasyToAnswer() {
        for (kind in listOf(PuzzleKind.KNOWLEDGE, PuzzleKind.LOGIC)) {
            assertTrue(PuzzleEngine.questionCount(kind) >= 15)
            repeat(200) { seed ->
                val q = PuzzleEngine.choices(kind, Random(seed))
                assertTrue(q.question.isNotBlank())
                assertEquals(3, q.options.distinct().size)
                assertTrue(q.correctIndex in 0..2)
                assertTrue(q.options[q.correctIndex].isNotBlank())
            }
        }
    }

    @Test fun imageCanUseEasierTwoByTwoGrid() {
        repeat(100) { seed ->
            val tiles = PuzzleEngine.shuffledTiles(Random(seed), gridSize = 2)
            assertEquals((0..3).toSet(), tiles.toSet())
            assertFalse(PuzzleEngine.solved(tiles))
        }
    }

    @Test fun identityImagePuzzleIsSolved() {
        assertTrue(PuzzleEngine.solved((0..8).toList()))
    }
}
