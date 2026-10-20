package space.nbtca.app.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AssociationEvent(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val location: String = "",
    val url: String = "",
    val start: String? = null,
    val end: String? = null,
    @SerialName("all_day") val allDay: Boolean = false
)

@Serializable
data class AssociationCalendar(
    val name: String = "NBTCA 计算机协会日历",
    val updated: String = "",
    val events: List<AssociationEvent> = emptyList()
)
