package space.nbtca.app.core.academic

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** 学期选项（学年 / 学期）。 */
data class TermOption(val value: String, val label: String, val selected: Boolean = false)

data class TermCatalog(
    val years: List<TermOption>,
    val semesters: List<TermOption>,
    val selectedYear: String,
    val selectedSemester: String
)

/**
 * 网页内同步：在已登录的 WebView 里用页面同源 fetch 取数据，
 * 绕开"把浏览器 Cookie 搬到 OkHttp"被判未登录的问题。
 */
object AcademicWebSync {

    /** 读取可选学期列表。 */
    /** 读取可选学期列表：先看当前页面/子框架，再依次尝试多个课表入口 URL。 */
    /** 读取可选学期列表：当前页面/子框架 → 多个课表入口；结果写 localStorage 防页面刷新丢失。 */
    val TERMS_SCRIPT: String = """
        (function () {
          const KEY = 'nbt_terms';
          window.__nbtTerms = null;
          const emit = (v) => { window.__nbtTerms = v; try { localStorage.setItem(KEY, v); } catch (e) {} };
          const pickIn = (doc) => {
            if (!doc) return null;
            const y = doc.getElementById('xnm'), s = doc.getElementById('xqm');
            if (!y || !s) return null;
            const current = (el) => {
              const sel = el.querySelector('option[selected]') || el.options[el.options.length - 1];
              return sel ? sel.value : '';
            };
            const items = (el) => Array.from(el.options).map(o => ({
              v: o.value || (o.textContent || '').trim(),
              t: (o.textContent || '').trim()
            })).filter(o => o.v && o.t);
            return { yHtml: y.outerHTML || '', sHtml: s.outerHTML || '' };
          };
          (async () => {
            let found = pickIn(document);
            if (!found) {
              for (let i = 0; i < window.frames.length && !found; i++) {
                try { found = pickIn(window.frames[i].document); } catch (e) {}
              }
            }
            const urls = [
              '/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151&layout=default',
              '/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151&layout=default&jsdm=xs',
              '/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151'
            ];
            let debug = '';
            for (const u of urls) {
              if (found) break;
              try {
                const res = await fetch(u, { credentials: 'include', headers: { 'X-Requested-With': 'XMLHttpRequest' } });
                const html = await res.text();
                debug += u + '=>' + res.status + '/' + html.length + '/xnm=' + (html.indexOf('id="xnm"') >= 0) + '; ';
                found = pickIn(new DOMParser().parseFromString(html, 'text/html'));
              } catch (e) {
                debug += u + '=>err ' + e + '; ';
              }
            }
            emit(found
              ? JSON.stringify(Object.assign({ debug: debug }, found))
              : JSON.stringify({ error: 'no-term', debug: debug, href: location.href }));
          })().catch(e => emit(JSON.stringify({ error: String(e) })));
        })();
    """.trimIndent()

    /** 指定学期的课表 / 考试 / 成绩。 */
    fun dataScript(xnm: String, xqm: String): String = """
        (function () {
          const KEY = 'nbt_sync';
          window.__nbtSync = null;
          try { localStorage.removeItem(KEY); } catch (e) {}
          const emit = (v) => { window.__nbtSync = v; try { localStorage.setItem(KEY, v); } catch (e) {} };
          const enc = (o) => Object.keys(o).map(k => encodeURIComponent(k) + '=' + encodeURIComponent(o[k])).join('&');
          const H = (u, o) => fetch(u, Object.assign({ credentials: 'include' }, o || {}));
          const XNM = '$xnm', XQM = '$xqm';
          (async () => {
            const ttRes = await H('/jwglxt/kbcx/xskbcx_cxXsgrkb.html', {
              method: 'POST',
              headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8', 'X-Requested-With': 'XMLHttpRequest' },
              body: enc({ xnm: XNM, xqm: XQM })
            });
            const tt = await ttRes.text();
            const pages = async (path, code, sort) => {
              let out = [], page = 1, total = 1;
              while (page <= total && page <= 5) {
                const body = enc({ xnm: XNM, xqm: XQM, 'queryModel.showCount': '50', 'queryModel.currentPage': String(page), 'queryModel.sortName': sort, 'queryModel.sortOrder': 'asc' });
                const res = await H(path + '?doType=query&gnmkdm=' + code, {
                  method: 'POST',
                  headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8', 'X-Requested-With': 'XMLHttpRequest' },
                  body
                });
                const text = await res.text();
                let data = null;
                try { data = JSON.parse(text); } catch (e) { break; }
                if (!data || !data.items) break;
                out = out.concat(data.items);
                total = data.totalPage || 1;
                page += 1;
              }
              return out;
            };
            const exams = await pages('/jwglxt/kwgl/kscx_cxXsksxxIndex.html', 'N358105', 'kssj');
            const grades = await pages('/jwglxt/cjcx/cjcx_cxXsgrcj.html', 'N305005', 'kch');
            emit(JSON.stringify({ tt: tt, exams: exams, grades: grades }));
          })().catch(e => emit(JSON.stringify({ error: String(e) })));
        })();
    """.trimIndent()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun unwrap(rawResult: String): JsonObject {
        val outer = json.parseToJsonElement(rawResult)
        val text = (outer as? JsonPrimitive)?.contentOrNull ?: rawResult
        return json.parseToJsonElement(text).jsonObject
    }

    fun parseTerms(rawResult: String): TermCatalog {
        val root = unwrap(rawResult)
        (root["error"] as? JsonPrimitive)?.contentOrNull?.let { code ->
            android.util.Log.e("AcademicSync", "terms failed: $rawResult")
            val debug = root["debug"]?.jsonPrimitive?.contentOrNull ?: ""
            throw AcademicException("未取到学期列表（$code）${if (debug.isBlank()) "" else "：$debug"}")
        }
        val years = parseOptions(root["yHtml"]?.jsonPrimitive?.contentOrNull.orEmpty())
        val semesters = parseOptions(root["sHtml"]?.jsonPrimitive?.contentOrNull.orEmpty())
        if (years.isEmpty() || semesters.isEmpty()) {
            android.util.Log.e("AcademicSync", "terms empty: $rawResult")
            throw AcademicException("未取到可选学期，请重试")
        }
        // 学年按标签升序展示；默认选中当前学年（8 月之后算新学年）
        val sortedYears = years.sortedBy { it.label }
        val today = java.time.LocalDate.now()
        val currentStart = if (today.monthValue >= 8) today.year else today.year - 1
        val currentYear = sortedYears.firstOrNull { it.label.startsWith("$currentStart-") }
        val defaultSemester = when (today.monthValue) {
            in 2..7 -> "12"
            in 8..12 -> "3"
            else -> "16"
        }
        return TermCatalog(
            years = sortedYears,
            semesters = semesters,
            selectedYear = currentYear?.value ?: sortedYears.first().value,
            selectedSemester = semesters.firstOrNull { it.value == defaultSemester }?.value
                ?: semesters.firstOrNull { it.selected }?.value
                ?: semesters.first().value
        )
    }

    /** 从 <select> 原始 HTML 里解析 option（JS 侧在该页面取 options 会返回空）。 */
    private fun parseOptions(html: String): List<TermOption> {
        if (html.isBlank()) return emptyList()
        val regex = Regex("<option\\b([^>]*)>([\\s\\S]*?)</option>", RegexOption.IGNORE_CASE)
        return regex.findAll(html).mapNotNull { match ->
            val attrs = match.groupValues[1]
            val value = Regex("value\\s*=\\s*[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE)
                .find(attrs)?.groupValues?.get(1)?.trim().orEmpty()
            val label = match.groupValues[2].replace(Regex("<[^>]*>"), " ").trim()
            if (value.isEmpty() || label.isEmpty()) null
            else TermOption(value, label, Regex("\\bselected\\b", RegexOption.IGNORE_CASE).containsMatchIn(attrs))
        }.toList()
    }

    fun parse(store: AcademicStore, rawResult: String, termLabel: String): AcademicSnapshot {
        val root = unwrap(rawResult)
        (root["error"] as? JsonPrimitive)?.contentOrNull?.let {
            throw AcademicException("教务网页请求失败：$it")
        }
        val exams = (root["exams"] as? JsonArray ?: JsonArray(emptyList()))
        val grades = (root["grades"] as? JsonArray ?: JsonArray(emptyList()))
        val snapshot = AcademicSnapshot(
            studentId = store.studentId,
            termLabel = termLabel,
            timetable = AcademicClient.parseTimetableRaw(root["tt"]?.jsonPrimitive?.contentOrNull.orEmpty()),
            exams = AcademicClient.parseExamRows(exams),
            grades = AcademicClient.parseGradeRows(grades),
            updatedAt = System.currentTimeMillis()
        )
        store.saveSnapshot(snapshot)
        return snapshot
    }
}
