package com.example.timetable.jw

import android.content.Context

/**
 * 教务系统登录会话的持久化存储（只存会话 Cookie 与学号，不存密码）。
 */
class JwSessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("jw_session", Context.MODE_PRIVATE)

    fun saveCookies(cookies: String) {
        prefs.edit().putString(KEY_COOKIES, cookies).apply()
    }

    fun loadCookies(): String? = prefs.getString(KEY_COOKIES, null)

    fun saveStudentId(studentId: String) {
        prefs.edit().putString(KEY_STUDENT_ID, studentId).apply()
    }

    fun loadStudentId(): String? = prefs.getString(KEY_STUDENT_ID, null)

    fun clear() {
        prefs.edit().remove(KEY_COOKIES).remove(KEY_STUDENT_ID).apply()
    }

    private companion object {
        const val KEY_COOKIES = "cookies"
        const val KEY_STUDENT_ID = "student_id"
    }
}