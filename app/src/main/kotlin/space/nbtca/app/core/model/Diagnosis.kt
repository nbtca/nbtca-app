package space.nbtca.app.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DiagnosisResponse(
    @SerialName("device_type") val deviceType: String = "",
    @SerialName("possible_causes") val possibleCauses: List<String> = emptyList(),
    @SerialName("self_help_steps") val selfHelpSteps: List<String> = emptyList(),
    @SerialName("recommended_tools") val recommendedTools: List<String> = emptyList(),
    @SerialName("suggest_repair") val suggestRepair: Boolean = true,
    val summary: String = "",
    val source: String = "heuristic"
)
