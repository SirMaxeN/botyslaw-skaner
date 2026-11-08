package eu.komars.forgescanner.automation

/** Geometry repair is not a substitute for resolving an ambiguous OCR value. */
object CounterSelectionPolicy {
    enum class Action { ACCEPT, RETRY_SAME_REGION, CONFIRM_VALUE, RETRY_OR_REDRAW }

    fun decide(choiceCount: Int, unambiguous: Boolean, attempt: Int): Action {
        require(choiceCount >= 0 && attempt >= 0)
        if (unambiguous && choiceCount == 1) return Action.ACCEPT
        if (attempt == 0) return Action.RETRY_SAME_REGION
        return if (choiceCount > 0) Action.CONFIRM_VALUE else Action.RETRY_OR_REDRAW
    }
}
