package com.example.timetable.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class GitHubUpdateProvider(
    private val context: Context
) : AppUpdateProvider {
    override suspend fun checkForUpdate(): UpdateCheckResult {
        val connection = openConnection(LATEST_RELEASE_URL)
        val response = connection.inputStream.bufferedReader().use { it.readText() }
        val release = JSONObject(response)
        check(!release.optBoolean("draft") && !release.optBoolean("prerelease")) {
            "最新版本不是正式发布版本"
        }
        val body = release.optString("body")
        val versionName = release.getString("tag_name").removePrefix("v")
        val versionCode = parseReleaseVersionCode(body)
            ?: error("Release 说明中缺少 versionCode")
        val assets = release.getJSONArray("assets")
        val apkAsset = (0 until assets.length())
            .map(assets::getJSONObject)
            .firstOrNull { it.getString("name").endsWith(".apk", ignoreCase = true) }
            ?: error("Release 中没有 APK 文件")
        val digest = apkAsset.optString("digest").removePrefix("sha256:")
        check(digest.matches(Regex("[0-9a-fA-F]{64}"))) { "Release 中缺少有效的 APK SHA-256" }

        val currentVersionCode = currentVersionCode()
        if (versionCode <= currentVersionCode) {
            return UpdateCheckResult.UpToDate(System.currentTimeMillis())
        }
        return UpdateCheckResult.Available(
            AppUpdateInfo(
                versionCode = versionCode,
                versionName = versionName,
                releaseNotes = body,
                releaseUrl = release.getString("html_url"),
                apkUrl = apkAsset.getString("browser_download_url"),
                apkName = apkAsset.getString("name"),
                apkSize = apkAsset.optLong("size"),
                sha256 = digest.lowercase()
            )
        )
    }

    override suspend fun downloadUpdate(
        info: AppUpdateInfo,
        onProgress: (Int) -> Unit
    ): File {
        val updateDirectory = File(context.cacheDir, "updates").apply { mkdirs() }
        updateDirectory.listFiles()?.forEach(File::delete)
        val destination = File(updateDirectory, info.apkName)
        val connection = openConnection(info.apkUrl)
        val totalBytes = connection.contentLengthLong.takeIf { it > 0 } ?: info.apkSize
        connection.inputStream.use { input ->
            destination.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var copied = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    copied += count
                    if (totalBytes > 0) onProgress(((copied * 100) / totalBytes).toInt().coerceIn(0, 100))
                }
            }
        }
        check(destination.sha256() == info.sha256) { "APK 完整性校验失败" }
        verifyPackageAndSignature(destination, info.versionCode)
        return destination
    }

    override fun requestInstall(apk: File): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }
        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk
        )
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        )
        return true
    }

    @Suppress("DEPRECATION")
    private fun verifyPackageAndSignature(apk: File, expectedVersionCode: Long) {
        val packageManager = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        val archive = requireNotNull(packageManager.getPackageArchiveInfo(apk.absolutePath, flags)) {
            "无法读取下载的 APK"
        }
        check(archive.packageName == context.packageName) { "APK 包名与当前应用不一致" }
        val archiveVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            archive.longVersionCode
        } else archive.versionCode.toLong()
        check(archiveVersionCode == expectedVersionCode) { "APK 版本号与 Release 声明不一致" }
        val installed = packageManager.getPackageInfo(context.packageName, flags)
        val archiveSignatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            archive.signingInfo?.apkContentsSigners.orEmpty()
        } else archive.signatures.orEmpty()
        val installedSignatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            installed.signingInfo?.apkContentsSigners.orEmpty()
        } else installed.signatures.orEmpty()
        check(archiveSignatures.isNotEmpty() && archiveSignatures.size == installedSignatures.size &&
            archiveSignatures.zip(installedSignatures).all { (first, second) ->
                first.toByteArray().contentEquals(second.toByteArray())
            }
        ) { "APK 签名与当前应用不一致" }
    }

    @Suppress("DEPRECATION")
    private fun currentVersionCode(): Long {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode
        else info.versionCode.toLong()
    }

    private fun openConnection(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "BUCTimer-Android")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/lighthouse333/ClassSchedule/releases/latest"
    }
}
