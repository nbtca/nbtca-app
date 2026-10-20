package space.nbtca.app.core.model

import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    val sub: String = "",
    val name: String = "NBTCA 用户",
    val email: String = "",
    val username: String? = null,
    val avatar: String? = null,
    val roles: List<String> = emptyList(),
    val memberId: String? = null,
    val saturdayRole: String? = null
) {
    val isRepairAdmin: Boolean get() = hasAnyRole(roles, ADMIN_ROLES)
    val isRepairStaff: Boolean get() = isRepairAdmin || hasAnyRole(roles, STAFF_ROLES)

    val repairRoleName: String
        get() = when {
            isRepairAdmin -> "维修管理员"
            isRepairStaff -> "维修技术员"
            else -> "报修用户"
        }

    companion object {
        private val ADMIN_ROLES = setOf(
            "repair admin", "repair-admin", "repair administrator",
            "dev admin", "dev-admin", "devadmin", "developer admin", "admin"
        )
        private val STAFF_ROLES = setOf(
            "repair member", "repair-member", "repair staff", "technician", "member"
        )

        private fun hasAnyRole(values: List<String>, candidates: Set<String>): Boolean =
            values.any { candidates.contains(it.trim().lowercase()) }
    }
}

/** 运行时鉴权状态：真实账号 + 当前界面视角（视角不影响 token）。 */
data class AuthState(
    val profile: UserProfile? = null,
    val isLoggedIn: Boolean = false,
    val viewRole: String? = null,
    val isAuthenticating: Boolean = false
) {
    val effectiveRole: String
        get() {
            if (!isLoggedIn) return "client"
            val p = profile ?: return "client"
            return when (viewRole) {
                "admin" -> if (p.isRepairAdmin) "admin" else highestRole(p)
                "member" -> if (p.isRepairStaff) "member" else highestRole(p)
                "client" -> "client"
                else -> highestRole(p)
            }
        }

    private fun highestRole(p: UserProfile): String = when {
        p.isRepairAdmin -> "admin"
        p.isRepairStaff -> "member"
        else -> "client"
    }

    val isRepairAdmin: Boolean get() = isLoggedIn && effectiveRole == "admin"
    val isRepairStaff: Boolean get() = isLoggedIn && (effectiveRole == "admin" || effectiveRole == "member")

    val effectiveRoleName: String
        get() = when (effectiveRole) {
            "admin" -> "维修管理员"
            "member" -> "维修技术员"
            else -> "报修用户"
        }

    val actualRoleName: String get() = profile?.repairRoleName ?: "未登录"
}
