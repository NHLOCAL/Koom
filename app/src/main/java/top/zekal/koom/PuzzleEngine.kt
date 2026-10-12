package top.zekal.koom

import kotlin.random.Random

data class NumberChallenge(val question: String, val answer: Int)
data class ChoiceQuestion(val question: String, val options: List<String>, val correctIndex: Int) {
    init {
        require(options.size == 3 && options.distinct().size == 3)
        require(correctIndex in 0..2)
    }
}

private data class SourceQuestion(
    val question: String,
    val correct: String,
    val distractor1: String,
    val distractor2: String
)

/**
 * Gently stimulating, curated Hebrew knowledge and logic questions.
 * Each challenge is a single tap, three possible answers, no trick wording.
 */
object PuzzleEngine {
    private val knowledge = listOf(
        SourceQuestion("מהי בירת צרפת?", "פריז", "רומא", "לונדון"),
        SourceQuestion("איזה איבר אחראי על הנשימה?", "ריאות", "כליות", "קיבה"),
        SourceQuestion("באיזה מכשיר מודדים טמפרטורה?", "מדחום", "מצפן", "משקל"),
        SourceQuestion("איזה בעל חיים הוא יונק?", "דולפין", "כריש", "תמנון"),
        SourceQuestion("איזו יבשת כוללת את ברזיל?", "אמריקה הדרומית", "אירופה", "אוסטרליה"),
        SourceQuestion("איזה צבע מתקבל מערבוב כחול וצהוב בצבעי ציור?", "ירוק", "סגול", "כתום"),
        SourceQuestion("מהו הכוכב שנמצא במרכז מערכת השמש?", "השמש", "הירח", "מאדים"),
        SourceQuestion("כמה דקות יש בשעה?", "60", "50", "100"),
        SourceQuestion("איזה כלי מוזיקלי מכיל קלידים?", "פסנתר", "חליל", "כינור"),
        SourceQuestion("איזה כלי משמש למציאת כיוון הצפון?", "מצפן", "מחשבון", "מדחום"),
        SourceQuestion("מהו הספר הראשון בחומש?", "בראשית", "שמות", "ויקרא"),
        SourceQuestion("מהו היום שאחרי יום שני?", "שלישי", "רביעי", "ראשון"),
        SourceQuestion("איזו חיה בדרך כלל בונה כוורת?", "דבורה", "נמלה", "פרפר"),
        SourceQuestion("מה מקיף את כדור הארץ במסלול טבעי?", "הירח", "השמש", "נוגה"),
        SourceQuestion("מהו המספר הגדול מבין הבאים?", "100", "90", "85"),
        SourceQuestion("באיזה חוש משתמשים כדי להבחין במנגינה?", "שמיעה", "טעם", "ריח"),
        SourceQuestion("איזה חומר משמש בדרך כלל להכנת חלון שקוף?", "זכוכית", "בטון", "עץ"),
        SourceQuestion("באיזה עונה באה פריחת עצים רבים?", "אביב", "חורף", "סתיו"),
        SourceQuestion("איזו פעולה עושים עם מסרק?", "מסרקים שיער", "חותכים לחם", "כותבים"),
        SourceQuestion("איזה חודש עברי בא אחרי תשרי?", "חשוון", "אלול", "ניסן")
    )

    private val logic = listOf(
        SourceQuestion("אם היום יום שלישי, איזה יום יהיה מחר?", "רביעי", "שני", "חמישי"),
        SourceQuestion("שלוש מנורות דולקות. אחת נכבית. כמה נשארו דולקות?", "שתיים", "אחת", "שלוש"),
        SourceQuestion("מה לא שייך לקבוצה: תפוח, בננה, גזר?", "גזר", "תפוח", "בננה"),
        SourceQuestion("אם אורי עומד לפני יוסי בתור, מי ראשון מביניהם?", "אורי", "יוסי", "שניהם יחד"),
        SourceQuestion("מה הכיוון ההפוך ממזרח?", "מערב", "צפון", "דרום"),
        SourceQuestion("אם ספר מונח על שולחן, מה נמצא מתחת לספר?", "השולחן", "התקרה", "החלון"),
        SourceQuestion("יש חמישה כיסאות, שניים תפוסים. כמה פנויים?", "שלושה", "שניים", "ארבעה"),
        SourceQuestion("אם כל הציפורים בחצר עפות משם, כמה ציפורים נשארות בחצר?", "אפס", "אחת", "שתיים"),
        SourceQuestion("יוסי גבוה מדני. דני גבוה מרון. מי הגבוה ביותר?", "יוסי", "דני", "רון"),
        SourceQuestion("אם התחלת לקרוא בעמוד 8, מהו העמוד הבא?", "9", "7", "10"),
        SourceQuestion("מה בא קודם: ללבוש נעליים או לקשור את השרוכים שלהן?", "ללבוש נעליים", "לקשור שרוכים", "באותו זמן"),
        SourceQuestion("בסל שלושה תפוחים. הוצאת תפוח אחד. כמה נותרו בסל?", "שניים", "שלושה", "אחד"),
        SourceQuestion("אם שעון מתקדם בדקה אחת, מה יקרה אחרי 08:59?", "09:00", "08:60", "09:01"),
        SourceQuestion("מה לא שייך: כף, מזלג, אופניים?", "אופניים", "כף", "מזלג"),
        SourceQuestion("בחדר שני חלונות, רק אחד פתוח. כמה חלונות סגורים?", "אחד", "אפס", "שניים"),
        SourceQuestion("אם רכב נוסע צפונה ופונה לאחור, לאיזה כיוון הוא פונה?", "דרומה", "מזרחה", "מערבה"),
        SourceQuestion("איזו מילה נמצאת בסדר האלפביתי ראשונה?", "אור", "בית", "גג"),
        SourceQuestion("אם יש 4 כוסות וכולן נקיות, כמה כוסות מלוכלכות?", "אפס", "אחת", "ארבע"),
        SourceQuestion("מה קודם בסדר הרגיל: בוקר או צהריים?", "בוקר", "צהריים", "שניהם יחד"),
        SourceQuestion("שלושה ספרים על מדף. מוסיפים שניים. כמה כעת?", "חמישה", "ארבעה", "שישה")
    )

    fun choices(kind: PuzzleKind, random: Random = Random.Default): ChoiceQuestion {
        val pool = when (kind) {
            PuzzleKind.KNOWLEDGE -> knowledge
            PuzzleKind.LOGIC -> logic
            else -> error("Not a multiple-choice kind")
        }
        val source = pool.random(random)
        val shuffled = listOf(source.correct, source.distractor1, source.distractor2).shuffled(random)
        return ChoiceQuestion(source.question, shuffled, shuffled.indexOf(source.correct))
    }

    fun questionCount(kind: PuzzleKind): Int = when (kind) {
        PuzzleKind.KNOWLEDGE -> knowledge.size
        PuzzleKind.LOGIC -> logic.size
        else -> 0
    }

    fun next(kind: PuzzleKind, random: Random = Random.Default): NumberChallenge =
        when (kind) {
            PuzzleKind.SEQUENCE -> {
                val start = random.nextInt(1, 15)
                val step = random.nextInt(1, 5)
                val seq = (0..3).map { start + it * step }
                NumberChallenge(seq.joinToString("  ·  ") + "  ·  ?", start + 4 * step)
            }
            PuzzleKind.MATH -> when (random.nextInt(3)) {
                0 -> {
                    val a = random.nextInt(5, 21)
                    val b = random.nextInt(3, 16)
                    NumberChallenge("$a + $b = ?", a + b)
                }
                1 -> {
                    val a = random.nextInt(15, 41)
                    val b = random.nextInt(2, a)
                    NumberChallenge("$a − $b = ?", a - b)
                }
                else -> {
                    val a = random.nextInt(2, 8)
                    val b = random.nextInt(2, 7)
                    NumberChallenge("$a × $b = ?", a * b)
                }
            }
            else -> error("Use choices or image tiles for this kind")
        }

    fun shuffledTiles(random: Random = Random.Default, gridSize: Int = 3): List<Int> {
        require(gridSize in 2..3)
        val original = (0 until gridSize * gridSize).toList()
        var shuffled: List<Int>
        do { shuffled = original.shuffled(random) } while (shuffled == original)
        return shuffled
    }

    fun swap(tiles: List<Int>, first: Int, second: Int): List<Int> =
        tiles.toMutableList().apply {
            val temp = this[first]
            this[first] = this[second]
            this[second] = temp
        }

    fun solved(tiles: List<Int>): Boolean = tiles.indices.all { tiles[it] == it }
}
