package com.example.timetable.jw

import java.net.HttpURLConnection
import java.net.URL

/**
 * 北京化工大学教务系统在线接口客户端（纯 HTTP）。
 *
 * 复用登录会话 Cookie 调用教务 JSON API。本期只提供课表接口，
 * 后续成绩查询等在此新增 `fetchXxx` 方法即可。
 */
object JwApiClient {
    private const val JW_BASE = "https://jwglxt.buct.edu.cn"
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

    suspend fun fetchSchedule(cookies: String, studentId: String, year: Int, xqm: String): String =
        fetchText(scheduleUrl(studentId, year, xqm), cookies)

    private fun fetchText(url: String, cookies: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("Cookie", cookies)
            setRequestProperty("Referer", "$JW_BASE/")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            val code = connection.responseCode
            val input = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = input?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                error("教务系统返回错误（HTTP $code）" + if (body.isNotEmpty()) "：${body.take(200)}" else "")
            }
            return body
        } finally {
            connection.disconnect()
        }
    }
}