package com.example.timetable.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.util.Date

@Composable
fun AboutSettingsScreen(
    updateState: AppUpdateUiState,
    automaticUpdateChecks: Boolean,
    updatePopupReminders: Boolean,
    lastUpdateCheck: Long,
    onAutomaticUpdateChecksChange: (Boolean) -> Unit,
    onUpdatePopupRemindersChange: (Boolean) -> Unit,
    onCheckForUpdate: () -> Unit,
    onDownloadUpdate: (com.example.timetable.update.AppUpdateInfo) -> Unit,
    onInstallUpdate: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    @Suppress("DEPRECATION")
    val packageInfo = remember {
        context.packageManager.getPackageInfo(context.packageName, 0)
    }
    val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        packageInfo.longVersionCode
    } else {
        @Suppress("DEPRECATION")
        packageInfo.versionCode.toLong()
    }
    var updateToConfirm by remember { mutableStateOf<com.example.timetable.update.AppUpdateInfo?>(null) }
    BackHandler(onBack = onBack)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("返回") }
            Text(
                text = "关于与设置",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        SettingsSectionTitle("应用")
        Text("BUCTimer", fontWeight = FontWeight.Bold)
        Text("当前版本：${packageInfo.versionName}（$versionCode）")

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
        SettingsSectionTitle("更新")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("自动检查更新")
                Text("每天最多检查一次", fontSize = 12.sp)
            }
            Switch(
                checked = automaticUpdateChecks,
                onCheckedChange = onAutomaticUpdateChecksChange
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("有可用更新时弹窗提醒")
                Text("选择稍后更新后，24 小时内不再提醒同一版本", fontSize = 12.sp)
            }
            Switch(
                checked = updatePopupReminders,
                onCheckedChange = onUpdatePopupRemindersChange
            )
        }
        if (lastUpdateCheck > 0) {
            Text(
                "最近检查：${DateFormat.getDateTimeInstance().format(Date(lastUpdateCheck))}",
                fontSize = 12.sp
            )
        }
        UpdateStatus(
            state = updateState,
            onCheck = onCheckForUpdate,
            onDownload = { updateToConfirm = it },
            onInstall = onInstallUpdate
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
        SettingsSectionTitle("项目")
        TextButton(onClick = { uriHandler.openUri(REPOSITORY_URL) }) {
            Text("GitHub 项目地址")
        }
        TextButton(onClick = { uriHandler.openUri(RELEASES_URL) }) {
            Text("版本发布与下载")
        }
        TextButton(onClick = { uriHandler.openUri(ISSUES_URL) }) {
            Text("问题反馈")
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
        SettingsSectionTitle("说明")
        Text("课程、课表、待办和番茄钟数据保存在设备本地，课表文件仅在本地解析。")
        Text("本项目为开源软件，源代码与许可信息可在 GitHub 项目页面查看。")
    }

    updateToConfirm?.let { info ->
        AlertDialog(
            onDismissRequest = { updateToConfirm = null },
            title = { Text("下载 ${info.versionName}") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("大小：${formatFileSize(info.apkSize)}")
                    Text(info.releaseNotes, modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    updateToConfirm = null
                    onDownloadUpdate(info)
                }) { Text("下载") }
            },
            dismissButton = {
                TextButton(onClick = { updateToConfirm = null }) { Text("取消") }
            }
        )
    }
}

@Composable
fun UpdateAvailableDialog(
    info: com.example.timetable.update.AppUpdateInfo,
    onUpdateNow: () -> Unit,
    onUpdateLater: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onUpdateLater,
        title = { Text("发现新版本 ${info.versionName}") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("大小：${formatFileSize(info.apkSize)}")
                Text(info.releaseNotes, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = onUpdateNow) { Text("马上更新") }
        },
        dismissButton = {
            TextButton(onClick = onUpdateLater) { Text("稍后更新") }
        }
    )
}

@Composable
private fun UpdateStatus(
    state: AppUpdateUiState,
    onCheck: () -> Unit,
    onDownload: (com.example.timetable.update.AppUpdateInfo) -> Unit,
    onInstall: () -> Unit
) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        when (state) {
            AppUpdateUiState.Idle -> Text("尚未检查更新")
            AppUpdateUiState.Checking -> {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("正在检查 GitHub Release…")
            }
            is AppUpdateUiState.UpToDate -> Text(
                "当前已是最新版本 · ${DateFormat.getDateTimeInstance().format(Date(state.checkedAt))}"
            )
            is AppUpdateUiState.Available -> {
                Text("发现新版本 ${state.info.versionName}", fontWeight = FontWeight.Bold)
                TextButton(onClick = { onDownload(state.info) }) { Text("查看并下载") }
            }
            is AppUpdateUiState.Downloading -> {
                LinearProgressIndicator(
                    progress = { state.progress / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("正在下载并校验：${state.progress}%")
            }
            is AppUpdateUiState.Ready -> {
                Text("APK 下载及安全校验已通过")
                state.message?.let { Text(it, fontSize = 12.sp) }
                TextButton(onClick = onInstall) { Text("安装更新") }
            }
            is AppUpdateUiState.Error -> Text("更新失败：${state.message}")
        }
        if (state !is AppUpdateUiState.Checking && state !is AppUpdateUiState.Downloading) {
            TextButton(onClick = onCheck) { Text("检查更新") }
        }
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

private fun formatFileSize(bytes: Long): String = when {
    bytes <= 0 -> "未知"
    bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
    else -> "%.1f KB".format(bytes / 1024.0)
}

private const val REPOSITORY_URL = "https://github.com/lighthouse333/ClassSchedule"
private const val RELEASES_URL = "$REPOSITORY_URL/releases"
private const val ISSUES_URL = "$REPOSITORY_URL/issues"
