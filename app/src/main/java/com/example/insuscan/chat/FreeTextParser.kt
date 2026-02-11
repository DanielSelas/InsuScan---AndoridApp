package com.example.insuscan.chat

// Regex-based local text parser for common chat intents.
// Tries to match patterns before falling back to LLM in Phase 5.
object FreeTextParser {

    sealed class ParseResult {
        data class RemoveItem(val name: String) : ParseResult()
        data class UpdateWeight(val name: String, val grams: Int) : ParseResult()
        data class AddItem(val name: String) : ParseResult()
        data class AnswerClarification(val value: Float) : ParseResult()
        object Confirm : ParseResult()
        object EditMode : ParseResult()
        object Unknown : ParseResult()
    }

    fun parse(input: String, currentState: ChatState): ParseResult {
        val text = input.trim().lowercase()

        // "confirm", "ok", "yes", "looks good", "correct", "lgtm"
        if (text.matches(Regex("^(confirm|ok|yes|looks good|correct|lgtm|done|good)$"))) {
            return ParseResult.Confirm
        }

        // "edit", "change", "fix", "modify"
        if (text.matches(Regex("^(edit|change|fix|modify|update)$"))) {
            return ParseResult.EditMode
        }

        // "remove [name]" or "delete [name]"
        val removeMatch = Regex("^(?:remove|delete)\\s+(.+)$").find(text)
        if (removeMatch != null) {
            return ParseResult.RemoveItem(removeMatch.groupValues[1].trim())
        }

        // "[name] [number] grams" or "[name] [number]g"
        val weightMatch = Regex("^(.+?)\\s+(\\d+)\\s*(?:grams?|g)$").find(text)
        if (weightMatch != null) {
            val name = weightMatch.groupValues[1].trim()
            val grams = weightMatch.groupValues[2].toIntOrNull()
            if (grams != null) {
                return ParseResult.UpdateWeight(name, grams)
            }
        }

        // "add [name]"
        val addMatch = Regex("^add\\s+(.+)$").find(text)
        if (addMatch != null) {
            return ParseResult.AddItem(addMatch.groupValues[1].trim())
        }

        // During CLARIFYING state, a bare number is a carb value
        if (currentState == ChatState.CLARIFYING) {
            val numMatch = Regex("^(\\d+(?:\\.\\d+)?)$").find(text)
            if (numMatch != null) {
                val value = numMatch.groupValues[1].toFloatOrNull()
                if (value != null) {
                    return ParseResult.AnswerClarification(value)
                }
            }
        }

        return ParseResult.Unknown
    }
}
