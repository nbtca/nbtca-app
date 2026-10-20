package space.nbtca.app.core.util

import space.nbtca.app.core.model.AssociationEvent

private val PLACEHOLDER_TITLES = setOf(
    "新建日程", "新日程", "untitled", "untitled event", "no title", "(no title)",
    "无标题", "无标题日程", "未命名"
)

private val REPAIR_SIGNALS = listOf("义诊", "义务维修", "免费维修", "维修活动", "维修日", "电脑维修")
private val NON_REPAIR_SIGNALS = listOf("设计", "立牌", "海报", "策划", "统计", "会议", "例会", "议程", "文档", "总结")

/** Google Calendar 里未填标题的占位日程（如「新建日程」）没有信息量，直接隐藏。 */
fun AssociationEvent.isPlaceholderEvent(): Boolean {
    val t = title.trim().lowercase()
    return t.isEmpty() || PLACEHOLDER_TITLES.contains(t)
}

/** 只有真正描述「维修 / 义诊」场次的活动才算维修日，排除「维修日立牌设计」这类待办。 */
fun AssociationEvent.isRepairDayEvent(): Boolean {
    val text = "${title} ${description}".lowercase()
    if (NON_REPAIR_SIGNALS.any { text.contains(it) }) return false
    return REPAIR_SIGNALS.any { text.contains(it) }
}

fun List<AssociationEvent>.withoutPlaceholders(): List<AssociationEvent> =
    filterNot { it.isPlaceholderEvent() }
