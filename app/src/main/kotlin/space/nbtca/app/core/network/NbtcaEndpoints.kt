package space.nbtca.app.core.network

/**
 * 上游服务的真实地址。App 不再经过自建 BFF，所有数据直接向这些公开服务请求。
 */
object NbtcaEndpoints {
    const val SPACE_BASE = "https://nbtca.space"
    const val RSS = "$SPACE_BASE/rss.xml"
    const val ARTICLE_BASE = "$SPACE_BASE/posts"

    const val CALENDAR_BASE = "https://ical.nbtca.space"
    const val SCHOOL_ICS = "$CALENDAR_BASE/school.ics"
    const val EVENTS_ICS = "$CALENDAR_BASE/events.ics"

    const val STATUS_API = "https://status.nbtca.space/api/status"

    fun articleUrl(id: String): String = "$ARTICLE_BASE/${id.trim('/')}/"
}
