package space.nbtca.app.core.model

import kotlinx.serialization.Serializable

@Serializable
data class TicketTimeline(
    val status: String = "",
    val title: String = "",
    val detail: String = "",
    val operator: String = "",
    val timestamp: String = ""
)

@Serializable
data class RepairTicket(
    val id: String = "",
    val title: String = "",
    val deviceType: String = "",
    val description: String = "",
    val status: String = "open",
    val clientId: String = "",
    val phone: String = "",
    val qq: String = "",
    val contactPreference: String = "",
    val size: String = "",
    val photos: List<String> = emptyList(),
    val timeline: List<TicketTimeline> = emptyList(),
    val createdAt: String = "",
    val createdEpoch: Long = 0L,
    val updatedAt: String = "",
    val memberId: String? = null,
    val memberName: String? = null,
    val memberAvatar: String? = null,
    val closedByName: String? = null
) {
    val normStatus: String get() = status.trim().lowercase()

    val isOpen: Boolean get() = normStatus == "open" || normStatus == "pending"
    val isAccepted: Boolean get() = normStatus == "accepted" || normStatus == "repairing"
    val isCommitted: Boolean get() = normStatus == "committed"
    val isClosed: Boolean get() = normStatus == "closed" || normStatus == "completed"
    val isCancelled: Boolean get() = normStatus == "cancelled" || normStatus == "canceled"

    val statusName: String
        get() = when {
            isOpen -> "待受理"
            isAccepted -> "维修中"
            isCommitted -> "待审核"
            isClosed -> "已完成"
            isCancelled -> "已取消"
            else -> status
        }

    val deviceTypeName: String get() = deviceType.ifEmpty { "设备" }

    /** 联系方式仅本人、已接单技术员与管理员可见。 */
    fun canViewContact(user: UserProfile?, isStaff: Boolean, isAdmin: Boolean): Boolean {
        if (user == null) return false
        if (isAdmin) return true
        if (isStaff) {
            val mid = user.memberId
            return isAccepted || isCommitted || isClosed ||
                (!mid.isNullOrEmpty() && memberId == mid)
        }
        return true
    }
}
