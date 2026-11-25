package es.planidroid.app.domain

import kotlinx.serialization.Serializable
@Serializable data class AiProposal(val tasks: List<Task>, val uncertainties: List<String> = emptyList(), val assumptions: List<String> = emptyList(), val suggestedQuestions: List<String> = emptyList(), val confidence: Double = 0.0)
interface AiProvider {
    suspend fun parseNaturalLanguage(text: String): AiProposal
    suspend fun suggestTaskBreakdown(task: Task): AiProposal
    suspend fun estimateEffort(task: Task): AiProposal
    suspend fun suggestSchedule(task: Task): AiProposal
    suspend fun summarizeTask(task: Task): String
    suspend fun answerWithUserContext(question: String, tasks: List<Task>): String
}
