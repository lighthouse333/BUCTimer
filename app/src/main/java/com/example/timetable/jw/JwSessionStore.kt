package com.example.timetable.jw

import android.content.Context

/**
 * 教务系统登录会话的持久化存储（只存会话 Cookie，不存密码）。
 */
class JwSessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("jw_session", Context.MODE_PRIVATE)

    fun saveCookies(cookies: String) {
        prefs.edit().putString(KEY_COOKIES, cookies).apply()
    }

    fun loadCookies(): String? = prefs.getString(KEY_COOKIES, null)

    fun clear() {
        prefs.edit().remove(KEY_COOKIES).apply()
    }

    private companion object {
        const val KEY_COOKIES = "cookies"
    }
}