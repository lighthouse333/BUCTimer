package com.example.timetable.jw

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * 可选保存的教务账号密码。
 *
 * 用户在登录对话框勾选「记住账号密码」后，学号与密码使用 Android Keystore
 * 主密钥（AES256-GCM）加密后存于本机；可在「关于与设置」中删除。
 * 加密库初始化失败时全部方法静默降级为无保存。
 */
class JwCredentialStore(context: Context) {
    private val prefs by lazy {
        runCatching {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }.getOrNull()
    }

    fun save(studentId: String, password: String) {
        prefs?.edit()
            ?.putString(KEY_STUDENT_ID, studentId)
            ?.putString(KEY_PASSWORD, password)
            ?.apply()
    }

    fun loadStudentId(): String? =
        prefs?.getString(KEY_STUDENT_ID, null)?.takeIf { it.isNotBlank() }

    fun loadPassword(): String? =
        prefs?.getString(KEY_PASSWORD, null)?.takeIf { it.isNotBlank() }

    fun clear() {
        prefs?.edit()?.clear()?.apply()
    }

    private companion object {
        const val PREFS_NAME = "jw_credentials"
        const val KEY_STUDENT_ID = "student_id"
        const val KEY_PASSWORD = "password"
    }
}