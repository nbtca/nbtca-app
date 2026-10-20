package space.nbtca.app.core.academic

import android.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class AcademicException(message: String) : Exception(message)

/**
 * 教务系统（jwxt）客户端，行为对齐 nbtca/nbtcal + Prompt：
 * webvpn 单点登录 → 课表 / 考试 / 成绩。
 * 官方接口需要学号 + 密码，因此登录后把数据全部缓存到本地。
 */
object AcademicClient {

    private const val WEBVPN_ENTRY = "https://webvpn.nbt.edu.cn/"
    private const val JWXT_HOST = "jwxt-443.webvpn.nbt.edu.cn"
    private const val JWXT_BASE = "https://$JWXT_HOST"
    private const val SSO_ENTRY = "/sso/jziotlogin"
    private const val JWXT_MENU = "/jwglxt/xtgl/index_initMenu.html"
    private const val TT_INDEX = "/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151&layout=default"
    private const val TT_DATA = "/jwglxt/kbcx/xskbcx_cxXsgrkb.html"
    private const val EXAM_INDEX = "/jwglxt/kwgl/kscx_cxXsksxxIndex.html"
    private const val EXAM_CODE = "N358105"
    private const val GRADE_INDEX = "/jwglxt/cjcx/cjcx_cxDgXscj.html"
    private const val GRADE_QUERY = "/jwglxt/cjcx/cjcx_cxXsgrcj.html"
    private const val GRADE_CODE = "N305005"
    private const val RANDOM_ALPHABET = "ABCDEFGHJKMNPQRSTWXYZabcdefhijkmnprstwxyz2345678"
    private const val UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun client(): OkHttpClient {
        val jar = object : CookieJar {
            private val cookies = mutableListOf<Cookie>()
            override fun saveFromResponse(url: HttpUrl, list: List<Cookie>) {
                list.forEach { incoming ->
                    cookies.removeAll { it.name == incoming.name && it.domain == incoming.domain }
                    cookies.add(incoming)
                }
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> =
                cookies.filter { it.matches(url) }
        }
        return OkHttpClient.Builder()
            .cookieJar(jar)
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 用「教务网页登录」收集到的 Cookie 拉取全部数据。
     * 学校在失败若干次后会强制验证码，纯 HTTP 登录不可靠，因此以网页登录为主路径。
     */
    fun syncWithCookies(store: AcademicStore): AcademicSnapshot {
        val cookies = store.cookies
        if (cookies.isNullOrBlank()) {
            throw AcademicException("请先在「个人中心 → 教务系统账号」完成教务网页登录")
        }
        val jar = JarCookieStore(cookies)
        val http = cookieClient(jar)

        // 网页登录只完成到 webvpn；教务的 SSO 接力与数据请求都在后台用 Cookie 完成。
        runCatching { get(http, "$JWXT_BASE$SSO_ENTRY") }
        val menuHtml = runCatching { get(http, "$JWXT_BASE$JWXT_MENU") }.getOrNull().orEmpty()
        if (store.studentId.isBlank()) {
            extractStudentId(menuHtml)?.let { detected -> store.studentId = detected }
        }

        val indexHtml = get(http, "$JWXT_BASE$TT_INDEX")
        if (looksLikeLoginPage(indexHtml)) {
            // 不要清空 Cookie：网页会话可能仍有效，只是 OkHttp 复放被判未登录。
            throw AcademicException("后台直连被教务判定未登录，请在「个人中心 → 教务系统账号」重新点一次教务网页登录")
        }
        val snapshot = fetchAll(http, store, indexHtml)
        // 把 SSO 过程中新增的 Cookie 一并保存，下次直接复用。
        store.cookies = jar.header()
        return snapshot
    }

    /** 极简 Cookie 容器：种子来自 WebView，同步过程中吸收 Set-Cookie 以便持久化。 */
    private class JarCookieStore(seed: String) {
        private val values = linkedMapOf<String, String>()

        init {
            seed.split(";").forEach { part ->
                val index = part.indexOf('=')
                if (index > 0) {
                    val name = part.substring(0, index).trim()
                    val value = part.substring(index + 1).trim()
                    if (name.isNotEmpty() && value.isNotEmpty()) values[name] = value
                }
            }
        }

        fun header(): String = values.entries.joinToString("; ") { "${it.key}=${it.value}" }

        fun absorb(response: okhttp3.Response) {
            response.headers.values("Set-Cookie").forEach { raw ->
                val first = raw.substringBefore(';')
                val index = first.indexOf('=')
                if (index > 0) {
                    val name = first.substring(0, index).trim()
                    val value = first.substring(index + 1).trim()
                    if (name.isNotEmpty()) values[name] = value
                }
            }
        }
    }

    /** 兼容旧调用：有 Cookie 走 Cookie，否则提示去网页登录。 */
    fun sync(store: AcademicStore): AcademicSnapshot = syncWithCookies(store)

    /** 带 Cookie 自动维护的客户端（会话种子来自 WebView 登录）。 */
    private fun cookieClient(jar: JarCookieStore): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("Cookie", jar.header())
                    .header("User-Agent", UA)
                    .build()
                val response = chain.proceed(request)
                jar.absorb(response)
                response
            }
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

    private fun fetchAll(http: OkHttpClient, store: AcademicStore, indexHtml: String): AcademicSnapshot {
        val years = parseSelect(indexHtml, "xnm")
        val semesters = parseSelect(indexHtml, "xqm")
        if (years.isEmpty() || semesters.isEmpty()) {
            throw AcademicException("登录状态异常：未获取到学期列表，请重试")
        }
        val year = years.firstOrNull { it.third } ?: years.first()
        val semester = semesters.firstOrNull { it.third } ?: semesters.first()

        val timetable = fetchTimetable(http, year.first, semester.first)
        val exams = fetchRecords(http, EXAM_INDEX, EXAM_CODE, EXAM_INDEX, year.first, semester.first, "kssj", ::parseExam)
        val grades = fetchRecords(http, GRADE_INDEX, GRADE_CODE, GRADE_QUERY, year.first, semester.first, "kch", ::parseGrade)

        val snapshot = AcademicSnapshot(
            studentId = store.studentId,
            termLabel = "${year.second} · ${semester.second}",
            timetable = timetable,
            exams = exams,
            grades = grades,
            updatedAt = System.currentTimeMillis()
        )
        store.saveSnapshot(snapshot)
        return snapshot
    }

    // ---------------- 登录 ----------------

    private fun login(http: OkHttpClient, username: String, password: String) {
        val entry = getResponse(http, WEBVPN_ENTRY)
        val html = entry.first
        val form = parseLoginForm(html) ?: throw AcademicException("教务登录页结构已变化，暂时无法登录")
        android.util.Log.d(
            "AcademicSync",
            "login page ok: action=${form.action} execution=${form.execution} saltLen=${form.salt.length} hidden=${form.hidden.keys}"
        )
        val encrypted = encryptPassword(password, form.salt)

        val bodyBuilder = FormBody.Builder()
        // 先带上登录页的 hidden 字段（lt / dllt / cllt / execution 等），再覆盖关键项
        val overridden = setOf("username", "password", "_eventId", "cllt", "dllt", "execution")
        form.hidden.forEach { (key, value) ->
            if (key.isNotEmpty() && key !in overridden) bodyBuilder.add(key, value)
        }
        bodyBuilder.add("username", username.trim())
        bodyBuilder.add("password", encrypted)
        bodyBuilder.add("_eventId", "submit")
        bodyBuilder.add("cllt", "userNameLogin")
        bodyBuilder.add("dllt", "generalLogin")
        bodyBuilder.add("execution", form.execution)

        val actionUrl = resolveAction(entry.second, form.action) ?: throw AcademicException("教务登录地址解析失败")
        android.util.Log.d("AcademicSync", "post target: $actionUrl")
        val request = Request.Builder()
            .url(actionUrl)
            .header("User-Agent", UA)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Referer", entry.second)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .post(bodyBuilder.build())
            .build()
        val text = runCatching {
            http.newCall(request).execute().use { it.body?.string().orEmpty() }
        }.getOrElse { throw AcademicException("无法连接教务系统：${it.message}") }

        android.util.Log.d("AcademicSync", "login POST done, stillLoginPage=${looksLikeLoginPage(text)}")
        if (looksLikeLoginPage(text)) {
            throw AcademicException(describeLoginFailure(text))
        }

        // CAS 通过后还要走 webvpn → 教务的 SSO 接力，否则 jwxt 仍是未登录状态。
        // 顺序对齐 nbtca/Prompt 的 verifyJwxtSession()。
        val sso = getResponse(http, "$JWXT_BASE$SSO_ENTRY")
        android.util.Log.d("AcademicSync", "sso landed: ${sso.second}")
        val menu = getResponse(http, "$JWXT_BASE$JWXT_MENU")
        android.util.Log.d(
            "AcademicSync",
            "menu landed: ${menu.second} loginPage=${looksLikeLoginPage(menu.first)}"
        )

        val (timetableHtml, finalUrl) = getResponse(http, "$JWXT_BASE$TT_INDEX")
        val landedOnJwxt = finalUrl.contains(JWXT_HOST, ignoreCase = true)
        val hasTermSelect = Regex("<select\\b[^>]*(?:id|name)=[\"']xnm[\"']", RegexOption.IGNORE_CASE)
            .containsMatchIn(timetableHtml) &&
            Regex("<select\\b[^>]*(?:id|name)=[\"']xqm[\"']", RegexOption.IGNORE_CASE)
                .containsMatchIn(timetableHtml)
        android.util.Log.d(
            "AcademicSync",
            "jwxt verify: host=${landedOnJwxt} termSelect=$hasTermSelect loginPage=${looksLikeLoginPage(timetableHtml)}"
        )
        if (!landedOnJwxt || !hasTermSelect) {
            throw AcademicException(
                if (looksLikeLoginPage(timetableHtml)) {
                    "登录未通过：教务仍要求登录，请检查学号密码或是否需要在浏览器完成验证"
                } else {
                    "教务课表页解析失败（页面结构可能已更新）"
                }
            )
        }
    }

    /** 从教务主页里找出学号（各版本字段名略有差异，逐个尝试）。 */
    private fun extractStudentId(html: String): String? {
        val patterns = listOf(
            Regex("id=[\"']xh_id[\"'][^>]*value=[\"'](\\d{6,})[\"']", RegexOption.IGNORE_CASE),
            Regex("name=[\"']xh_id[\"'][^>]*value=[\"'](\\d{6,})[\"']", RegexOption.IGNORE_CASE),
            Regex("xh_id\\s*[:=]\\s*[\"'](\\d{6,})[\"']", RegexOption.IGNORE_CASE),
            Regex("(?:学号|考生号)\\s*[：:]\\s*(\\d{6,})")
        )
        for (pattern in patterns) {
            val found = pattern.find(html)?.groupValues?.getOrNull(1)
            if (!found.isNullOrBlank()) return found
        }
        return null
    }

    /** 把教务返回的错误页翻译成可读原因。 */
    private fun describeLoginFailure(html: String): String = when {
        html.contains("密码错误") || html.contains("账号或密码") -> "学号或密码不正确"
        html.contains("冻结") || html.contains("锁定") -> "账号已被锁定，请先在网页端解锁"
        html.contains("验证码") || html.contains("滑块") || html.contains("fido") ->
            "教务要求交互式验证，请先在浏览器登录一次后再回来同步"
        else -> "登录未通过，请检查学号与密码"
    }

    /** 判定响应是否还是登录页（两代结构都要识别）。 */
    private fun looksLikeLoginPage(html: String): Boolean =
        html.contains("pwdEncryptSalt") ||
            html.contains("pwdFromId") ||
            html.contains("loginFromId") ||
            html.contains("authserver/login")

    private fun encryptPassword(password: String, salt: String): String {
        if (salt.length != 16) throw AcademicException("教务登录页结构已变化（盐值异常）")
        val random = SecureRandom()
        fun randomChars(length: Int): String = buildString {
            repeat(length) { append(RANDOM_ALPHABET[random.nextInt(RANDOM_ALPHABET.length)]) }
        }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(salt.toByteArray(Charsets.UTF_8), "AES"),
            IvParameterSpec(randomChars(16).toByteArray(Charsets.UTF_8))
        )
        return Base64.encodeToString(cipher.doFinal((randomChars(64) + password).toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    /**
     * action 可能是相对路径，按登录页最终 URL 解析；
     * 关键：CAS 表单 action 上的 ?service= 决定登录后回跳哪里，
     * 不带上它就无法为 webvpn 签发票据（之后的 jwxt 请求会一直被弹回登录页）。
     * 对齐 nbtca/Prompt 的 parseLoginForm 行为。
     */
    private fun resolveAction(pageUrl: String, action: String): String? {
        val base = runCatching { pageUrl.toHttpUrl() }.getOrNull() ?: return null
        val resolved = runCatching { base.resolve(action) }.getOrNull() ?: return null
        val service = base.queryParameter("service")
        val builder = resolved.newBuilder()
        if (!service.isNullOrEmpty() && resolved.queryParameter("service").isNullOrEmpty()) {
            builder.addQueryParameter("service", service)
        }
        return builder.build().toString()
    }

    private data class LoginForm(
        val action: String,
        val execution: String,
        val salt: String,
        val hidden: Map<String, String>
    )

    /**
     * 教务登录页有两代结构：旧版 form id=pwdFromId，新版 id=loginFromId
     * （还有 fido 专用表单）。这里按"包含 pwdEncryptSalt 的表单块"定位，
     * 并收集其中的 hidden 字段，避免学校改版后直接失败。
     */
    private fun parseLoginForm(html: String): LoginForm? {
        val formBlocks = Regex("<form\\b[^>]*>[\\s\\S]*?</form>", RegexOption.IGNORE_CASE)
            .findAll(html).map { it.value }.toList()
        val block = formBlocks.firstOrNull {
            it.contains("pwdEncryptSalt", true) && it.contains("execution", true) &&
                !it.contains("_fido", true)
        } ?: formBlocks.firstOrNull { it.contains("pwdEncryptSalt", true) }
        if (block == null) return null

        val formTag = Regex("<form\\b[^>]*>", RegexOption.IGNORE_CASE).find(block)?.value ?: return null
        val action = attr(formTag, "action") ?: "/authserver/login"
        val execution = Regex("<input\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(block)
            .map { it.value }
            .firstOrNull { attr(it, "id") == "execution" || attr(it, "name") == "execution" }
            ?.let { attr(it, "value") } ?: return null
        val salt = Regex("<input\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(block)
            .map { it.value }
            .firstOrNull { attr(it, "id") == "pwdEncryptSalt" || attr(it, "name") == "pwdEncryptSalt" }
            ?.let { attr(it, "value") } ?: return null

        val hidden = linkedMapOf<String, String>()
        Regex("<input\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(block).forEach { match ->
            val tag = match.value
            val type = attr(tag, "type")?.lowercase()
            val name = attr(tag, "name") ?: return@forEach
            if (type != "hidden") return@forEach
            if (name == "username" || name == "password") return@forEach
            hidden[name] = attr(tag, "value").orEmpty()
        }
        return LoginForm(action, execution, salt, hidden)
    }

    private fun attr(tag: String, name: String): String? =
        Regex("$name\\s*=\\s*[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE)
            .find(tag)?.groupValues?.get(1)

    // ---------------- 课表 / 考试 / 成绩 ----------------

    /** 供网页桥接复用：把课表接口原始 JSON 解析成课程列表。 */
    fun parseTimetableRaw(body: String): List<TimetableCourse> = parseTimetableBody(body)

    fun parseExamRows(rows: JsonArray): List<ExamItem> =
        rows.mapNotNull { (it as? JsonObject)?.let(::parseExam) }

    fun parseGradeRows(rows: JsonArray): List<GradeItem> =
        rows.mapNotNull { (it as? JsonObject)?.let(::parseGrade) }

    private fun fetchTimetable(http: OkHttpClient, xnm: String, xqm: String): List<TimetableCourse> {
        val form = FormBody.Builder().add("xnm", xnm).add("xqm", xqm).build()
        val request = Request.Builder()
            .url("$JWXT_BASE$TT_DATA")
            .header("User-Agent", UA)
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Referer", "$JWXT_BASE$TT_INDEX")
            .post(form)
            .build()
        return parseTimetableBody(execute(http, request))
    }

    private fun parseTimetableBody(body: String): List<TimetableCourse> {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull()
        val rows = when (root) {
            is JsonArray -> root
            is JsonObject -> (root["kbList"] as? JsonArray) ?: JsonArray(emptyList())
            else -> JsonArray(emptyList())
        }
        return rows.mapNotNull { element ->
            val row = element as? JsonObject ?: return@mapNotNull null
            val name = str(row, "kcmc") ?: return@mapNotNull null
            val weekday = str(row, "xqj")?.toIntOrNull() ?: return@mapNotNull null
            val periods = str(row, "jcs") ?: str(row, "jc") ?: ""
            val range = Regex("\\d{1,2}").findAll(periods).map { it.value.toInt() }.toList()
            TimetableCourse(
                name = name,
                teacher = str(row, "xm") ?: str(row, "jsxm") ?: "",
                location = str(row, "cdmc") ?: "",
                weekday = weekday,
                startPeriod = range.firstOrNull() ?: 0,
                endPeriod = range.lastOrNull() ?: (range.firstOrNull() ?: 0),
                weeks = parseWeeks(str(row, "zcd") ?: "")
            )
        }.sortedWith(compareBy({ it.weekday }, { it.startPeriod }))
    }

    private fun parseWeeks(text: String): List<Int> {
        val weeks = sortedSetOf<Int>()
        val normalized = text.replace("，", ",").replace("、", ",").replace("；", ",")
            .replace("~", "-").replace("～", "-").replace("—", "-").replace("至", "-")
        normalized.split(",").forEach { raw ->
            val part = raw.trim()
            if (part.isEmpty()) return@forEach
            val odd = part.contains("单")
            val even = part.contains("双")
            val cleaned = part.replace(Regex("[第周()（）单双\\s]"), "")
            val match = Regex("^(\\d{1,2})(?:-(\\d{1,2}))?$").find(cleaned) ?: return@forEach
            val start = match.groupValues[1].toIntOrNull() ?: return@forEach
            val end = match.groupValues[2].toIntOrNull() ?: start
            for (week in start..end) {
                if (odd && week % 2 == 0) continue
                if (even && week % 2 == 1) continue
                weeks.add(week)
            }
        }
        return weeks.toList()
    }

    private fun <T> fetchRecords(
        http: OkHttpClient,
        indexPath: String,
        code: String,
        queryPath: String,
        xnm: String,
        xqm: String,
        sortName: String,
        parse: (JsonObject) -> T
    ): List<T> {
        val out = mutableListOf<T>()
        var page = 1
        var totalPages = 1
        while (page <= totalPages && page <= 20) {
            val url = "$JWXT_BASE$queryPath?doType=query&gnmkdm=$code"
            val form = FormBody.Builder()
                .add("xnm", xnm)
                .add("xqm", xqm)
                .add("queryModel.showCount", "50")
                .add("queryModel.currentPage", page.toString())
                .add("queryModel.sortName", sortName)
                .add("queryModel.sortOrder", "asc")
                .build()
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Accept", "application/json")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", "$JWXT_BASE$indexPath?gnmkdm=$code")
                .post(form)
                .build()
            val body = execute(http, request)
            val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject
                ?: throw AcademicException("教务返回了无法解析的数据")
            val items = root["items"] as? JsonArray ?: break
            items.forEach { element ->
                (element as? JsonObject)?.let { out.add(parse(it)) }
            }
            totalPages = (root["totalPage"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 1
            if (items.isEmpty()) break
            page += 1
        }
        return out
    }

    private fun parseExam(row: JsonObject): ExamItem = ExamItem(
        courseName = str(row, "kcmc") ?: "",
        examName = str(row, "ksmc") ?: "",
        timeText = str(row, "kssj") ?: "",
        location = str(row, "cdmc") ?: "",
        seat = str(row, "zwh") ?: "",
        assessment = str(row, "khfs") ?: ""
    )

    private fun parseGrade(row: JsonObject): GradeItem = GradeItem(
        courseCode = str(row, "kch") ?: "",
        courseName = str(row, "kcmc") ?: "",
        grade = str(row, "cj") ?: "",
        credits = str(row, "xf") ?: "",
        gradePoint = str(row, "jd") ?: "",
        nature = str(row, "kcxzmc") ?: "",
        remark = str(row, "cjbz") ?: "",
        voided = (str(row, "cjsfzf") ?: "") in setOf("是", "1")
    )

    // ---------------- HTTP 工具 ----------------

    private fun get(http: OkHttpClient, url: String): String {
        val request = Request.Builder().url(url).header("User-Agent", UA).get().build()
        return execute(http, request)
    }

    private fun getResponse(http: OkHttpClient, url: String): Pair<String, String> {
        val request = Request.Builder().url(url).header("User-Agent", UA).get().build()
        return runCatching {
            http.newCall(request).execute().use { response ->
                (response.body?.string().orEmpty()) to response.request.url.toString()
            }
        }.getOrElse { throw AcademicException("无法连接教务系统：${it.message}") }
    }

    private fun execute(http: OkHttpClient, request: Request): String {
        return runCatching {
            http.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw AcademicException("教务请求失败（HTTP ${response.code}）")
                }
                if (body.contains("pwdEncryptSalt") || body.contains("pwdFromId")) {
                    throw AcademicException("登录状态已失效，请重新填写密码后同步")
                }
                body
            }
        }.getOrElse { error ->
            if (error is AcademicException) throw error
            android.util.Log.e("AcademicSync", "request failed: ${request.url}", error)
            val detail = error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
            throw AcademicException("教务请求失败：$detail")
        }
    }

    private fun parseSelect(html: String, name: String): List<Triple<String, String, Boolean>> {
        val select = Regex(
            "<select\\b[^>]*(?:id|name)\\s*=\\s*[\"']$name[\"'][^>]*>([\\s\\S]*?)</select>",
            RegexOption.IGNORE_CASE
        ).find(html)?.groupValues?.get(1) ?: return emptyList()
        val option = Regex("<option\\b([^>]*)>([\\s\\S]*?)</option>", RegexOption.IGNORE_CASE)
        return option.findAll(select).mapNotNull { match ->
            val attrs = match.groupValues[1]
            val value = attr(attrs, "value")?.trim().orEmpty()
            val label = stripTags(match.groupValues[2]).trim()
            if (value.isEmpty() || label.isEmpty()) null
            else Triple(value, label, Regex("\\bselected\\b", RegexOption.IGNORE_CASE).containsMatchIn(attrs))
        }.toList()
    }

    private fun stripTags(html: String): String =
        html.replace(Regex("<[^>]*>"), " ").replace("&nbsp;", " ").replace(Regex("\\s+"), " ")

    private fun str(obj: JsonObject, key: String): String? {
        val value = obj[key] as? JsonPrimitive ?: return null
        return value.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
    }
}
