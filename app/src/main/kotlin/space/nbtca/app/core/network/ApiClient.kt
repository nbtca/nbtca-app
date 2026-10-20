package space.nbtca.app.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import space.nbtca.app.core.auth.AuthManager
import space.nbtca.app.core.config.AppSettings
import space.nbtca.app.core.model.AssociationCalendar
import space.nbtca.app.core.model.AssociationEvent
import space.nbtca.app.core.model.DiagnosisResponse
import space.nbtca.app.core.model.NewsArticle
import space.nbtca.app.core.model.NewsFeedResponse
import space.nbtca.app.core.model.NewsFeedResult
import space.nbtca.app.core.model.RepairTicket
import space.nbtca.app.core.model.ServiceComponent
import space.nbtca.app.core.model.ServiceGroup
import space.nbtca.app.core.model.ServiceIncident
import space.nbtca.app.core.model.ServiceStatus
import space.nbtca.app.core.model.TicketTimeline
import space.nbtca.app.core.util.epochMillis
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 与 Flutter ApiClient 对齐的双通道客户端：
 * · BFF 通道（资讯 / 协会日历 / 服务状态 / 排障预检）
 * · Saturday 通道（维修工单全生命周期）
 */
class ApiClient(
    private val appSettings: AppSettings,
    private val authManager: AuthManager
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val formMedia = "application/x-www-form-urlencoded".toMediaType()

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    val baseUrl: String get() = appSettings.apiBaseUrl

    fun updateBaseUrl(url: String) {
        appSettings.apiBaseUrl = url
    }

    // ==========================================================
    // 基础请求
    // ==========================================================

    private suspend fun tokenOrNull(): String? = authManager.getValidAccessToken()

    private fun bffUrl(path: String): String = baseUrl.trimEnd('/') + path

    private fun saturdayUrl(path: String): String = AuthManager.SATURDAY_ENDPOINT + path

    private fun execute(
        url: String,
        method: String = "GET",
        token: String? = null,
        body: String? = null,
        contentType: okhttp3.MediaType = jsonMedia
    ): Result<String> {
        return try {
            val builder = Request.Builder().url(url).addHeader("Accept", "application/json")
            if (!token.isNullOrEmpty()) builder.addHeader("Authorization", "Bearer $token")
            when (method) {
                "POST" -> builder.post((body ?: "{}").toRequestBody(contentType))
                "PATCH" -> builder.patch((body ?: "{}").toRequestBody(contentType))
                "DELETE" -> builder.delete((body ?: "{}").toRequestBody(contentType))
                else -> builder.get()
            }
            http.newCall(builder.build()).execute().use { res ->
                val text = res.body?.string().orEmpty()
                if (res.isSuccessful) Result.success(text)
                else Result.failure(ApiException(decodeError(text, res.code), res.code))
            }
        } catch (e: Exception) {
            Result.failure(ApiException(e.message ?: "网络请求失败", null))
        }
    }

    private fun decodeError(body: String, code: Int): String {
        val detail = runCatching {
            val obj = json.parseToJsonElement(body).jsonObject
            listOf("detail", "message", "title", "error")
                .firstNotNullOfOrNull { key -> obj[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } }
        }.getOrNull()
        if (!detail.isNullOrBlank()) return detail
        return when (code) {
            401, 403 -> "登录凭证缺失或已失效，请重新登录"
            404 -> "请求的资源不存在"
            in 500..599 -> "服务暂时不可用 ($code)"
            else -> "请求失败 ($code)"
        }
    }

    /** BFF 统一响应体 {code, message, data}。 */
    private fun unwrapBff(raw: String): JsonElement {
        val root = json.parseToJsonElement(raw)
        if (root is JsonObject && root["code"] is JsonPrimitive) {
            val code = root["code"]!!.jsonPrimitive.contentOrNull?.toIntOrNull() ?: 0
            if (code != 0) {
                val msg = root["message"]?.jsonPrimitive?.contentOrNull ?: "服务返回错误"
                throw ApiException(msg, null)
            }
            return root["data"] ?: JsonObject(emptyMap())
        }
        return root
    }

    // ==========================================================
    // 资讯（BFF）
    // ==========================================================

    suspend fun getNewsFeed(): NewsFeedResult = withContext(Dispatchers.IO) {
        try {
            val res = execute(bffUrl(ApiEndpoints.NEWS_FEED), token = tokenOrNull())
            val raw = res.getOrThrow()
            val data = unwrapBff(raw) as? JsonObject ?: throw ApiException("资讯接口返回了无效数据", null)
            val feed = json.decodeFromJsonElement(NewsFeedResponse.serializer(), data)
            if (feed.featured.isEmpty() && feed.recent.isEmpty()) offlineNewsFeed() else NewsFeedResult(
                featured = feed.featured,
                recent = feed.recent,
                categories = feed.categories,
                isOffline = false
            )
        } catch (_: Exception) {
            offlineNewsFeed()
        }
    }

    suspend fun getArticle(id: String): Result<NewsArticle> = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(id, "UTF-8")
            val res = execute(bffUrl(ApiEndpoints.articleDetail(encoded)), token = tokenOrNull())
            val data = unwrapBff(res.getOrThrow()) as? JsonObject
                ?: return@withContext Result.failure(ApiException("文章接口返回了无效数据", null))
            Result.success(json.decodeFromJsonElement(NewsArticle.serializer(), data))
        } catch (e: Exception) {
            Result.failure(if (e is ApiException) e else ApiException("文章加载失败，请检查网络后重试", null))
        }
    }

    // ==========================================================
    // 工具（BFF）
    // ==========================================================

    suspend fun getAssociationCalendar(): Result<AssociationCalendar> = withContext(Dispatchers.IO) {
        try {
            val res = execute(bffUrl(ApiEndpoints.ASSOCIATION_CALENDAR), token = tokenOrNull())
            val data = unwrapBff(res.getOrThrow()) as? JsonObject
                ?: return@withContext Result.failure(ApiException("协会日历接口返回了无效数据", null))
            val parsed = json.decodeFromJsonElement(AssociationCalendar.serializer(), data)
            Result.success(parsed)
        } catch (e: Exception) {
            Result.failure(if (e is ApiException) e else ApiException("获取协会公共日历失败", null))
        }
    }

    /** 校历：当前教学周与总周数（用于课表按周翻页）。 */
    suspend fun getSchoolWeek(): Pair<Int, Int>? = withContext(Dispatchers.IO) {
        try {
            val res = execute(bffUrl(ApiEndpoints.SCHOOL_CALENDAR), token = tokenOrNull())
            val data = unwrapBff(res.getOrThrow()) as? JsonObject ?: return@withContext null
            val current = data["current_week"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
            val total = data["total_weeks"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
            if (current != null && total != null && current > 0 && total > 0) current to total else null
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getServiceStatus(): ServiceStatus = withContext(Dispatchers.IO) {
        try {
            val res = execute(bffUrl(ApiEndpoints.INFRASTRUCTURE), token = tokenOrNull())
            val data = unwrapBff(res.getOrThrow())
            when (data) {
                is JsonObject -> json.decodeFromJsonElement(ServiceStatus.serializer(), data)
                is JsonArray -> legacyStatus(data)
                else -> ServiceStatus.offline("服务状态接口返回了无效数据")
            }
        } catch (e: Exception) {
            ServiceStatus.offline((e as? ApiException)?.message ?: "服务状态加载失败，请检查网络后重试")
        }
    }

    private fun legacyStatus(nodes: JsonArray): ServiceStatus {
        val components = nodes.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            ServiceComponent(
                id = obj["endpoint"]?.jsonPrimitive?.contentOrNull ?: "",
                name = obj["name"]?.jsonPrimitive?.contentOrNull ?: "",
                state = obj["status"]?.jsonPrimitive?.contentOrNull ?: "unknown",
                error = obj["note"]?.jsonPrimitive?.contentOrNull
            )
        }
        val state = if (components.all { it.state == "operational" }) "operational" else "degraded"
        return ServiceStatus(
            state = state,
            groups = listOf(ServiceGroup(name = "服务节点", infra = false, components = components))
        )
    }

    suspend fun diagnoseIssue(deviceType: String, description: String): DiagnosisResponse = withContext(Dispatchers.IO) {
        try {
            val payload = buildJsonObject {
                put("device_type", deviceType)
                put("description", description)
            }.toString()
            val res = execute(bffUrl(ApiEndpoints.REPAIR_DIAGNOSE), "POST", tokenOrNull(), payload)
            val data = unwrapBff(res.getOrThrow()) as? JsonObject
                ?: return@withContext offlineDiagnosis()
            json.decodeFromJsonElement(DiagnosisResponse.serializer(), data)
        } catch (_: Exception) {
            offlineDiagnosis()
        }
    }

    private fun offlineDiagnosis(): DiagnosisResponse = DiagnosisResponse(
        deviceType = "",
        possibleCauses = listOf("硬件积灰散热不良或硅脂干涸", "驱动组件冲突或硬盘坏道", "系统引导与配置异常"),
        selfHelpSteps = listOf(
            "长按开机键 30 秒释放主板静电后重新通电",
            "使用 Dism++ 清理系统冗余与启动项"
        ),
        recommendedTools = listOf("Dism++", "MemTest64"),
        suggestRepair = true,
        summary = "根据故障描述，建议携带设备参加本周六计算机协会义诊，由现场技术员进行深入检修测试。",
        source = "offline"
    )

    // ==========================================================
    // 维修工单（Saturday 直连）
    // ==========================================================

    suspend fun getClientTickets(limit: Int = 100): List<RepairTicket> = withContext(Dispatchers.IO) {
        requestTickets(saturdayUrl("/client/events?limit=$limit&offset=0&order=desc"), requireAuth = true)
    }

    suspend fun getMemberTickets(limit: Int = 100): List<RepairTicket> = withContext(Dispatchers.IO) {
        requestTickets(saturdayUrl("/member/events?limit=$limit&offset=0&order=desc"), requireAuth = true)
    }

    /** 公开队列按建立时间倒序，保证最新的报修单先出现。 */
    suspend fun getPublicTickets(limit: Int = 100): List<RepairTicket> = withContext(Dispatchers.IO) {
        requestTickets(saturdayUrl("/events?limit=$limit&offset=0&order=desc"), requireAuth = false)
    }

    private suspend fun requestTickets(url: String, requireAuth: Boolean): List<RepairTicket> {
        val token = tokenOrNull()
        if (requireAuth && token.isNullOrEmpty()) return emptyList()
        val res = execute(url, token = token)
        val body = res.getOrNull() ?: return emptyList()
        return parseTicketList(body)
    }

    /** 详情按角色走不同接口：本人可拿 phone/qq，工作人员拿完整日志。 */
    suspend fun getTicketDetail(id: String, fallback: RepairTicket? = null): Result<RepairTicket> =
        withContext(Dispatchers.IO) {
            val state = authManager.state.value
            val encoded = URLEncoder.encode(id, "UTF-8")
            val paths = mutableListOf<String>()
            if (!state.isLoggedIn) {
                paths.add("/events/$encoded")
            } else if (state.isRepairStaff) {
                paths.add("/member/events/$encoded")
                paths.add("/events/$encoded")
            } else {
                paths.add("/client/events/$encoded")
                paths.add("/events/$encoded")
            }
            for (path in paths) {
                val res = execute(saturdayUrl(path), token = tokenOrNull())
                val body = res.getOrNull() ?: continue
                val ticket = parseTicket(body)
                if (ticket != null) return@withContext Result.success(ticket)
            }
            if (fallback != null) Result.success(fallback)
            else Result.failure(ApiException("无法获取工单详情", null))
        }

    suspend fun createTicket(
        problem: String,
        model: String,
        phone: String,
        qq: String
    ): Result<RepairTicket> = withContext(Dispatchers.IO) {
        if (problem.isBlank()) return@withContext Result.failure(ApiException("问题描述不能为空", null))
        if (phone.isBlank()) return@withContext Result.failure(ApiException("电话号码不能为空", null))
        val token = tokenOrNull()
        if (token.isNullOrEmpty()) {
            return@withContext Result.failure(ApiException("登录凭证缺失或已失效，请重新登录", 401))
        }
        val payload = buildJsonObject {
            put("problem", problem.trim())
            put("model", model.ifBlank { "设备" })
            put("phone", phone.trim())
            put("qq", qq.trim())
            put("contactPreference", "phone")
        }.toString()
        val res = execute(saturdayUrl("/client/event"), "POST", token, payload)
        res.fold(
            onSuccess = { body ->
                val ticket = parseTicket(body)
                if (ticket != null) Result.success(ticket)
                else Result.failure(ApiException("工单提交成功但返回数据无法解析", null))
            },
            onFailure = { Result.failure(it) }
        )
    }

    suspend fun updateClientEvent(id: String, problem: String, model: String, phone: String, qq: String):
        Result<RepairTicket> = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put("problem", problem.trim())
            put("model", model.trim())
            put("phone", phone.trim())
            put("qq", qq.trim())
        }.toString()
        val res = execute(
            saturdayUrl("/client/events/${URLEncoder.encode(id, "UTF-8")}"),
            "PATCH", tokenOrNull(), payload
        )
        res.fold(
            onSuccess = { body -> parseTicket(body)?.let { Result.success(it) } ?: Result.failure(ApiException("更新失败", null)) },
            onFailure = { Result.failure(it) }
        )
    }

    suspend fun cancelClientEvent(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        execute(saturdayUrl("/client/events/${URLEncoder.encode(id, "UTF-8")}"), "DELETE", tokenOrNull())
            .map { }
    }

    suspend fun acceptEvent(id: String): Result<RepairTicket> = withContext(Dispatchers.IO) {
        execute(saturdayUrl("/member/events/${URLEncoder.encode(id, "UTF-8")}/accept"), "POST", tokenOrNull())
            .mapCatching { body -> parseTicket(body) ?: throw ApiException("接单返回数据无法解析", null) }
    }

    suspend fun dropEvent(id: String): Result<RepairTicket> = withContext(Dispatchers.IO) {
        execute(saturdayUrl("/member/events/${URLEncoder.encode(id, "UTF-8")}/accept"), "DELETE", tokenOrNull())
            .mapCatching { body -> parseTicket(body) ?: throw ApiException("放弃工单返回数据无法解析", null) }
    }

    suspend fun commitEvent(id: String, content: String, size: String?): Result<RepairTicket> =
        withContext(Dispatchers.IO) {
            val payload = buildJsonObject {
                put("content", content)
                if (!size.isNullOrBlank()) put("size", size)
            }.toString()
            execute(
                saturdayUrl("/member/events/${URLEncoder.encode(id, "UTF-8")}/commit"),
                "POST", tokenOrNull(), payload
            ).mapCatching { body -> parseTicket(body) ?: throw ApiException("提交维修报告返回数据无法解析", null) }
        }

    /**
     * 上传现场照片：POST /member/events/{id}/photos，multipart 字段名 photos。
     * 逐张上传并在失败时返回服务端原始错误，便于定位（服务端仅接受 jpeg/webp）。
     */
    suspend fun uploadEventPhotos(id: String, photos: List<PhotoPayload>): Result<RepairTicket> =
        withContext(Dispatchers.IO) {
            if (photos.isEmpty()) return@withContext Result.success(RepairTicket(id = id))
            val token = tokenOrNull()
            if (token.isNullOrEmpty()) {
                return@withContext Result.failure(ApiException("登录凭证缺失或已失效，请重新登录", 401))
            }
            var latest: RepairTicket? = null
            for (photo in photos) {
                try {
                    val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
                        .addFormDataPart(
                            "photos",
                            photo.fileName,
                            photo.bytes.toRequestBody(photo.contentType.toMediaType())
                        )
                    val request = Request.Builder()
                        .url(saturdayUrl("/member/events/${URLEncoder.encode(id, "UTF-8")}/photos"))
                        .addHeader("Accept", "application/json")
                        .addHeader("Authorization", "Bearer $token")
                        .post(builder.build())
                        .build()
                    http.newCall(request).execute().use { res ->
                        val text = res.body?.string().orEmpty()
                        if (!res.isSuccessful) {
                            val detail = runCatching {
                                val o = json.parseToJsonElement(text).jsonObject
                                (o["detail"] ?: o["message"] ?: o["title"])?.jsonPrimitive?.contentOrNull
                            }.getOrNull()
                            return@withContext Result.failure(
                                ApiException(
                                    "照片上传失败(${res.code})${if (detail.isNullOrBlank()) "" else ": $detail"}",
                                    res.code
                                )
                            )
                        }
                        latest = parseTicket(text) ?: latest
                    }
                } catch (e: Exception) {
                    return@withContext Result.failure(
                        ApiException("照片上传失败: ${e.message ?: "网络异常"}", null)
                    )
                }
            }
            Result.success(latest ?: RepairTicket(id = id))
        }

    suspend fun closeEvent(id: String): Result<RepairTicket> = withContext(Dispatchers.IO) {
        execute(saturdayUrl("/events/${URLEncoder.encode(id, "UTF-8")}/close"), "POST", tokenOrNull())
            .mapCatching { body -> parseTicket(body) ?: throw ApiException("结单返回数据无法解析", null) }
    }

    suspend fun rejectCommitEvent(id: String): Result<RepairTicket> = withContext(Dispatchers.IO) {
        execute(saturdayUrl("/events/${URLEncoder.encode(id, "UTF-8")}/commit"), "DELETE", tokenOrNull())
            .mapCatching { body -> parseTicket(body) ?: throw ApiException("退回工单返回数据无法解析", null) }
    }

    // ==========================================================
    // 解析
    // ==========================================================

    private fun parseTicketList(body: String): List<RepairTicket> {
        return try {
            val flat = body.trim()
            if (flat.startsWith("{")) {
                val obj = json.parseToJsonElement(flat).jsonObject
                val array = obj["data"] as? JsonArray ?: return emptyList()
                array.mapNotNull { parseTicketElement(it) }
            } else {
                val array = json.parseToJsonElement(flat) as? JsonArray ?: return emptyList()
                array.mapNotNull { parseTicketElement(it) }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseTicket(body: String): RepairTicket? = runCatching {
        val root = json.parseToJsonElement(body.trim())
        val element = if (root is JsonObject && root["data"] is JsonObject) root["data"]!! else root
        parseTicketElement(element)
    }.getOrNull()

    private fun parseTicketElement(element: JsonElement): RepairTicket? {
        val obj = element as? JsonObject ?: return null
        fun str(vararg keys: String): String {
            for (key in keys) {
                val value = obj[key]
                if (value is JsonPrimitive) {
                    val content = value.contentOrNull
                    if (!content.isNullOrBlank()) return content
                }
            }
            return ""
        }

        val eventId = str("eventId", "id")
        val model = str("model", "deviceType")
        val problem = str("problem", "description")
        val status = str("status").ifBlank { "open" }

        val member = obj["member"] as? JsonObject
        val logs = obj["logs"] as? JsonArray

        val photos = mutableListOf<String>()
        (obj["photos"] as? JsonArray)?.forEach { item ->
            when (item) {
                is JsonPrimitive -> item.contentOrNull?.takeIf { it.isNotBlank() }?.let { photos.add(it) }
                is JsonObject -> item["url"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { photos.add(it) }
                else -> Unit
            }
        }

        val memberAlias = member?.get("alias")?.jsonPrimitive?.contentOrNull
        val memberIdOfEvent = member?.get("memberId")?.jsonPrimitive?.contentOrNull

        val timeline = mutableListOf<TicketTimeline>()
        logs?.forEach { item ->
            val log = item as? JsonObject ?: return@forEach
            fun logStr(vararg keys: String): String {
                for (key in keys) {
                    val v = log[key]
                    if (v is JsonPrimitive) {
                        val c = v.contentOrNull
                        if (!c.isNullOrBlank() && c != "          ") return c
                    }
                }
                return ""
            }
            val action = logStr("action", "status")
            val operatorId = logStr("memberId")
            val operator = when {
                operatorId.isBlank() -> "系统"
                memberIdOfEvent != null && operatorId == memberIdOfEvent && !memberAlias.isNullOrBlank() -> memberAlias
                operatorId == "0000000000" -> "系统"
                else -> operatorId
            }
            timeline.add(
                TicketTimeline(
                    status = action,
                    title = logStr("actionText", "title").ifBlank { statusTitle(action) },
                    detail = logStr("description", "content", "detail"),
                    operator = operator,
                    timestamp = logStr("gmtCreate", "createdAt")
                )
            )
        }

        val firstLine = problem.lineSequence().firstOrNull()?.trim().orEmpty()
        return RepairTicket(
            id = eventId,
            title = firstLine.ifEmpty { model },
            deviceType = model.ifEmpty { "设备" },
            description = problem,
            status = status,
            clientId = str("clientId", "client_id", "student_id"),
            phone = str("phone", "contact"),
            qq = str("qq"),
            contactPreference = str("contactPreference"),
            size = str("size"),
            photos = photos,
            timeline = timeline,
            createdAt = str("gmtCreate", "created_at"),
            createdEpoch = epochMillis(str("gmtCreate", "created_at")),
            updatedAt = str("gmtModified", "updated_at"),
            memberId = member?.get("memberId")?.jsonPrimitive?.contentOrNull ?: str("memberId"),
            memberName = member?.get("alias")?.jsonPrimitive?.contentOrNull
                ?: member?.get("name")?.jsonPrimitive?.contentOrNull
                ?: str("memberName"),
            memberAvatar = member?.get("avatar")?.jsonPrimitive?.contentOrNull,
            closedByName = (obj["closedBy"] as? JsonObject)?.get("alias")?.jsonPrimitive?.contentOrNull
                ?: (obj["closedBy"] as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull
        )
    }

    /** Saturday 日志 action 与工单 status 两套词表都要覆盖。 */
    private fun statusTitle(status: String): String = when (status.lowercase()) {
        "create", "open" -> "提交报修"
        "accept", "accepted" -> "技术员接单"
        "commit", "committed" -> "提交维修报告"
        "close", "closed", "completed" -> "结单归档"
        "cancel", "cancelled", "canceled" -> "取消预约"
        "reject" -> "退回返工"
        "drop" -> "放弃工单"
        "photo" -> "上传现场照片"
        else -> status
    }

    // ==========================================================
    // 离线兜底（与 Flutter _offlineNewsFeed 完全一致）
    // ==========================================================

    private fun offlineNewsFeed(): NewsFeedResult = NewsFeedResult(
        isOffline = true,
        categories = listOf("技术", "活动"),
        featured = listOf(
            NewsArticle(
                id = "PhoNBT2024",
                title = "影留宁理 - 2024年度摄影展与毕业季活动回顾",
                category = "活动",
                published = "2024 年 6 月 18 日",
                author = "NBTCA 媒体部",
                summary = "光影定格青春，计算机协会联合摄影爱好者呈现宁理校园夏日记忆纪实。",
                coverUrl = "https://oss.nbtca.space/blog/clas/YQL05614-5xNq3t-mid-ulLkGV.jpeg",
                articleUrl = "https://nbtca.space/posts/PhoNBT2024",
                tags = listOf("摄影展", "毕业季", "校园生活")
            )
        ),
        recent = listOf(
            NewsArticle(
                id = "go-struct-craft-1",
                title = "Go 结构体精要 1：空结构体设计",
                category = "技术",
                published = "2026 年 3 月 18 日",
                author = "Dev Team",
                summary = "深入解析 struct{} 的零内存开销特性在信号通道、集合 Set 以及锁字段中的实战用法。",
                coverUrl = "https://oss.nbtca.space/blog/clas/image-20220419101405456-wHMqwe.jpeg",
                articleUrl = "https://nbtca.space/posts/blogs/Tech/GO/go-struct-craft-1-empty-struct",
                tags = listOf("Golang", "性能优化")
            )
        )
    )
}

/** 一张待上传的现场照片（已读入内存，避免缓存文件重名/被回收）。 */
class PhotoPayload(
    val fileName: String,
    val bytes: ByteArray,
    val contentType: String = "image/jpeg"
)

class ApiException(message: String, val statusCode: Int? = null) : Exception(message)
