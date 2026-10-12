package top.zekal.koom

/** A durable, single-use delivery identity, separate from the user's repeating alarm. */
data class AlarmOccurrence(val atMillis: Long, val token: String) {
    init {
        require(atMillis > 0 && token.isNotBlank()) { "Invalid scheduled occurrence" }
    }
}
