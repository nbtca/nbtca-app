package space.nbtca.app.core.util

/** ICS/RSS 里常见的 HTML 实体还原，避免界面上直接显示 &amp;#10; 这类转义。 */
fun htmlUnescape(raw: String): String {
    if (raw.isEmpty()) return raw
    var text = raw
    // 上游可能双重转义（&amp;#10;），做两轮还原。
    repeat(2) {
        text = text
            .replace("&#10;", "\n")
            .replace("&#13;", "\n")
            .replace("&#9;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&amp;", "&")
    }
    return text.replace(Regex("\\n{3,}"), "\n\n").trim()
}
