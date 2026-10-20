package space.nbtca.app.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ServiceComponent(
    val id: String = "",
    val name: String = "",
    val state: String = "unknown",
    val since: Long = 0,
    val failed: List<String> = emptyList(),
    val error: String? = null
)

@Serializable
data class ServiceGroup(
    val name: String = "",
    val infra: Boolean = false,
    val components: List<ServiceComponent> = emptyList()
)

@Serializable
data class ServiceIncident(
    val title: String = "",
    val impact: String = "",
    val updatedAt: String? = null
)

@Serializable
data class ServiceStatus(
    val state: String = "unknown",
    val updated: Long = 0,
    val groups: List<ServiceGroup> = emptyList(),
    val incidents: List<ServiceIncident> = emptyList(),
    val message: String? = null,
    val isOffline: Boolean = false
) {
    val totalComponents: Int get() = groups.sumOf { it.components.size }
    val healthyComponents: Int get() = groups.sumOf { g -> g.components.count { it.state == "operational" } }

    companion object {
        fun offline(message: String): ServiceStatus =
            ServiceStatus(state = "unknown", isOffline = true, message = message)
    }
}
