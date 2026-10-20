package space.nbtca.app.core.network

/** BFF 聚合网关路径（Saturday 工单路径在 ApiClient 内按角色拼接）。 */
object ApiEndpoints {
    const val NEWS_FEED = "/newsroom/feed"
    fun articleDetail(encodedId: String) = "/newsroom/articles/$encodedId"

    const val REPAIR_DIAGNOSE = "/repair/diagnose"

    const val ASSOCIATION_CALENDAR = "/tools/association-calendar"
    const val SCHOOL_CALENDAR = "/tools/calendar"
    const val INFRASTRUCTURE = "/tools/infrastructure"
}
