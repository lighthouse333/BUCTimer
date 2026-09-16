package com.example.timetable.jw

import java.net.HttpURLConnection
import java.net.URL

/**
 * 登录会话已失效（请求返回的不是数据 JSON，而是登录页 HTML 等）。
 */
class JwSessionExpiredException(message: String) : Exception(message)

/**
 * 北京化工大学教务系统在线接口客户端（纯 HTTP）。
 *
 * 复用登录会话 Cookie 调用教务 JSON API。除课表外，还提供成绩与考试
 * 接口（均为 POST 表单），以及 GPA 官方值所在的学业情况页面。
 */
object JwApiClient {
    internal const val JW_BASE = "https://jwglxt.buct.edu.cn"
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    /**
     * 用户语义学期代码 → 正方教务系统内部学期代码（xqm 下拉框实际值）。
     * 注意：不能直接传 1/2/3，必须映射为 3=秋冬、12=春夏、16=暑假。
     */
    internal fun xqmForSemester(semester: Int): String? = when (semester) {
        1 -> "3"
        2 -> "12"
        3 -> "16"
        else -> null
    }

    internal fun scheduleUrl(studentId: String, year: Int, xqm: String): String =
        "$JW_BASE/jwglxt/kbcx/xskbcx_cxXsKb.html" +
            "?gnmkdm=N2145&layout=default&su=$studentId&xnm=$year&xqm=$xqm"

    internal fun gradesUrl(studentId: String): String =
        "$JW_BASE/jwglxt/cjcx/cjcx_cxXsgrcj.html" +
            "?doType=query&gnmkdm=N305005&su=$studentId"

    internal fun examsUrl(studentId: String): String =
        "$JW_BASE/jwglxt/kwgl/kscx_cxXsksxxIndex.html" +
            "?doType=query&gnmkdm=N358105&su=$studentId"

    /** 成绩查询页面地址（作为数据接口的 Referer）。 */
    internal fun gradesPageUrl(studentId: String): String =
        "$JW_BASE/jwglxt/cjcx/cjcx_cxDgXscj.html?gnmkdm=N305005&su=$studentId"

    /** 考试查询页面地址（作为数据接口的 Referer）。 */
    internal fun examsPageUrl(studentId: String): String =
        "$JW_BASE/jwglxt/kwgl/kscx_cxXsksxxIndex.html?gnmkdm=N358105&su=$studentId"

    internal fun gpaPageUrl(studentId: String): String =
        "$JW_BASE/jwglxt/xsxy/xsxyqk_cxXsxyqkIndex.html" +
            "?gnmkdm=N105515&su=$studentId"

    suspend fun fetchSchedule(cookies: String, studentId: String, year: Int, xqm: String): String =
        fetchText(scheduleUrl(studentId, year, xqm), cookies)

    suspend fun fetchGrades(cookies: String, studentId: String, year: Int, xqm: String): String =
        fetchText(
            url = gradesUrl(studentId),
            cookies = cookies,
            body = gradesBody(year, xqm),
            referer = gradesPageUrl(studentId)
        )

    suspend fun fetchExams(cookies: String, studentId: String, year: Int, xqm: String): String =
        fetchText(
            url = examsUrl(studentId),
            cookies = cookies,
            body = examsBody(year, xqm),
            referer = examsPageUrl(studentId)
        )

    /** 学业情况页面（HTML），官方平均学分绩点内嵌其中。 */
    suspend fun fetchGpaPage(cookies: String, studentId: String): String =
        fetchText(gpaPageUrl(studentId), cookies)

    private fun gradesBody(year: Int, xqm: String): String =
        queryModelBody("xnm=$year&xqm=$xqm")

    private fun examsBody(year: Int, xqm: String): String =
        queryModelBody("xnm=$year&xqm=$xqm&ksmcdmb_id=&kch=&kc=&ksrq=&kkbm_id=")

    private fun queryModelBody(extra: String): String =
        extra + "&_search=false&nd=" + System.currentTimeMillis() +
            "&queryModel.showCount=100&queryModel.currentPage=1" +
            "&queryModel.sortName=&queryModel.sortOrder=asc&time=0"

    private fun fetchText(
        url: String,
        cookies: String,
        body: String? = null,
        referer: String = "$JW_BASE/"
    ): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            requestMethod = if (body == null) "GET" else "POST"
            setRequestProperty("Cookie", cookies)
            setRequestProperty("Referer", referer)
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("X-Requested-With", "XMLHttpRequest")
            if (body != null) {
                doOutput = true
                setRequestProperty(
                    "Content-Type",
                    "application/x-www-form-urlencoded;charset=UTF-8"
                )
            }
        }
        try {
            if (body != null) {
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val input = if (code in 200..299) connection.inputStream else connection.errorStream
            val result = input?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                android.util.Log.e(
                    "JwApiClient",
                    "请求失败 url=$url code=$code body=${result.take(800)}"
                )
                error(
                    "教务系统返回错误（HTTP $code）" +
                        if (result.isNotEmpty()) "：${result.take(200)}" else ""
                )
            }
            // 数据接口约定返回 JSON；拿到 HTML 说明会话失效被重定向到了登录页
            if (body != null && !result.trimStart().startsWith("{")) {
                throw JwSessionExpiredException("登录会话已过期，请重新登录")
            }
            return result
        } finally {
            connection.disconnect()
        }
    }
}