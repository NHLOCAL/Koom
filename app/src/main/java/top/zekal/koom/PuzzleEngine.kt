package top.zekal.koom

import kotlin.random.Random

data class NumberChallenge(val question: String, val answer: Int)

/** Pure challenge generator: no UI context, no platform dependencies. */
object PuzzleEngine {
    fun next(kind: PuzzleKind, random: Random = Random.Default): NumberChallenge {
        return when (kind) {
            PuzzleKind.SEQUENCE -> {
                val start = random.nextInt(2, 25)
                val step = random.nextInt(2, 9)
                val numbers = (0..3).map { start + it * step }
                NumberChallenge(numbers.joinToString("  ·  ") + "  ·  ?", start + 4 * step)
            }
            PuzzleKind.MATH -> when (random.nextInt(3)) {
                0 -> {
                    val a = random.nextInt(14, 70)
                    val b = random.nextInt(12, 51)
                    NumberChallenge("$a + $b = ?", a + b)
                }
                1 -> {
                    val a = random.nextInt(35, 95)
                    val b = random.nextInt(11, a)
                    NumberChallenge("$a − $b = ?", a - b)
                }
                else -> {
                    val a = random.nextInt(4, 13)
                    val b = random.nextInt(4, 13)
                    NumberChallenge("$a × $b = ?", a * b)
                }
            }
            PuzzleKind.IMAGE -> error("Image challenges use the tile engine")
        }
    }

    fun shuffledTiles(random: Random = Random.Default): List<Int> {
        val initial = (0..8).toList()
        var shuffled: List<Int>
        do { shuffled = initial.shuffled(random) } while (shuffled == initial)
        return shuffled
    }

    fun swap(tiles: List<Int>, first: Int, second: Int): List<Int> =
        tiles.toMutableList().apply {
            val tmp = this[first]
            this[first] = this[second]
            this[second] = tmp
        }

    fun solved(tiles: List<Int>): Boolean = tiles.indices.all { tiles[it] == it }
}
