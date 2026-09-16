package com.example.timetable.ui

import android.net.http.SslError
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

internal const val JW_WEB_BASE = "https://jwglxt.buct.edu.cn"

// 教务系统会话 Cookie 的 Path 为 /jwglxt，必须用该路径前缀的 URL 才能取出
internal const val JW_COOKIE_SCOPE_URL = "$JW_WEB_BASE/jwglxt/"
private const val TAG = "JwLogin"

/** 自动登录等待超时（毫秒），超时仍未进入教务系统视为账号或密码错误。 */
private const val AUTO_LOGIN_TIMEOUT_MILLIS = 15_000L

/**
 * 教务系统自动登录覆盖层。输入账号密码后：
 * 1. 隐藏的 WebView 在后台加载统一认证页，自动填入学号密码并提交（密码不落盘）；
 * 2. 登录成功（进入 jwglxt 域）→ 取回会话 Cookie 回调 [onLoginSuccess]；
 * 3. 超时仍未成功 → 判定账号或密码错误，回调 [onLoginFailed]；
 * 4. 可切到「手动登录」模式显示 WebView，应对滑块验证码等需要人工操作的场景。
 *
 * 真机适配要点（均已处理）：
 * 1. 学校登录链路的 302 会跳到 http:// 明文地址，AndroidManifest 已通过
 *    networkSecurityConfig 仅对学校域放行明文流量。
 * 2. 门户页是桌面布局且对移动 UA 显示异常，因此 WebView 使用桌面 Chrome UA。
 * 3. 门户登录表单所在 iframe 初始 display:none，加载完成后注入脚本强制显示。
 */
@Composable
fun JwLoginOverlay(
    studentId: String? = null,
    password: String? = null,
    onCancel: () -> Unit,
    onLoginSuccess: (cookies: String) -> Unit,
    onLoginFailed: (reason: String) -> Unit
) {
    var handled by remember { mutableStateOf(false) }
    var manualMode by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf<String?>(null) }
    var webMessage by remember { mutableStateOf<String?>(null) }
    fun proceed(cookies: String) {
        if (handled) return
        if (cookies.isBlank()) {
            webMessage = "尚未获取到登录会话，请先完成登录"
            return
        }
        handled = true
        onLoginSuccess(cookies)
    }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (failed != null) {
                    Text(
                        failed.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "请检查账号和密码后重试",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(24.dp))
                    TextButton(onClick = { onLoginFailed(failed.orEmpty()) }) {
                        Text("确定")
                    }
                } else {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text("正在登录并拉取课表……")
                    webMessage?.let { message ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            message,
                            color = Color.Red,
                            fontSize = 12.sp
                        )
                    }
                    if (!manualMode) {
                        Spacer(Modifier.height(24.dp))
                        Row {
                            TextButton(onClick = { manualMode = true }) {
                                Text("手动登录")
                            }
                            TextButton(onClick = onCancel) { Text("取消") }
                        }
                    } else {
                        Spacer(Modifier.height(16.dp))
                        TextButton(onClick = {
                            CookieManager.getInstance().flush()
                            proceed(
                                CookieManager.getInstance()
                                    .getCookie(JW_COOKIE_SCOPE_URL)
                                    .orEmpty()
                            )
                        }) { Text("完成登录") }
                        TextButton(onClick = onCancel) { Text("取消") }
                    }
                }
            }
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(if (manualMode) 1f else 0f),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        // 学校统一认证门户是桌面布局，移动 UA 会出现空白页
                        settings.userAgentString =
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                                "Chrome/120.0.0.0 Safari/537.36"
                        CookieManager.getInstance().setAcceptCookie(true)
                        webChromeClient = object : WebChromeClient() {
                            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                                if (message.message() == "JWLOGIN_TIMEOUT" && !handled) {
                                    failed = "账号或密码错误"
                                    return true
                                }
                                if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                                    Log.w(TAG, message.message())
                                }
                                return true
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(
                                view: WebView?,
                                url: String?,
                                favicon: android.graphics.Bitmap?
                            ) {
                                super.onPageStarted(view, url, favicon)
                                Log.i(TAG, "onPageStarted url=$url")
                            }
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                if (url != null && url.contains("portal.buct.edu.cn")) {
                                    view?.let { fixPortalLoginIframe(it) }
                                    val id = studentId
                                    val pwd = password
                                    if (!id.isNullOrBlank() && !pwd.isNullOrBlank()) {
                                        view?.let { autofillPortalLogin(it, id, pwd) }
                                    }
                                }
                                if (url != null &&
                                    url.startsWith(JW_WEB_BASE) &&
                                    url.contains("/jwglxt/")
                                ) {
                                    CookieManager.getInstance().flush()
                                    proceed(
                                        CookieManager.getInstance()
                                            .getCookie(JW_COOKIE_SCOPE_URL)
                                            .orEmpty()
                                    )
                                }
                            }
                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?
                            ) {
                                super.onReceivedError(view, request, error)
                                if (request?.isForMainFrame == true && webMessage == null) {
                                    webMessage = error?.description?.toString()
                                        ?: ("错误码 " + (error?.errorCode ?: -1))
                                }
                            }
                            override fun onReceivedSslError(
                                view: WebView?,
                                handler: SslErrorHandler?,
                                error: SslError?
                            ) {
                                if (webMessage == null) {
                                    webMessage = "SSL 证书校验失败（错误码 ${error?.primaryError}），已阻止加载"
                                }
                                handler?.cancel()
                            }
                        }
                    }.also { web ->
                        // 清除上个账号遗留的登录会话并等待删除完成再开始加载，
                        // 否则 WebView 会带着旧会话直接进入旧账号的教务系统
                        val manager = CookieManager.getInstance()
                        if (android.os.Build.VERSION.SDK_INT >= 21) {
                            manager.removeAllCookies { web.loadUrl("$JW_WEB_BASE/") }
                        } else {
                            @Suppress("DEPRECATION")
                            manager.removeAllCookie()
                            web.loadUrl("$JW_WEB_BASE/")
                        }
                    }
                }
            )
        }
    }
}

/**
 * 在门户登录页（同域 iframe 内）自动填入学号与密码并点击登录。
 * 密码仅作为本次登录的输入，App 不持久化；日志只输出长度，不记录明文。
 */
private fun autofillPortalLogin(view: WebView, studentId: String, password: String) {
    val quotedId = org.json.JSONObject.quote(studentId)
    val quotedPwd = org.json.JSONObject.quote(password)
    view.evaluateJavascript(
        "(function(){ " +
            "if (window.__jwAutoAuthDone) return; " +
            "window.__jwAutoAuthDone = true; " +
            "var f = document.getElementById('iframeObj'); " +
            "if (!f || !f.contentWindow) return; " +
            "var W = f.contentWindow, D = W.document; " +
            "function setV(el, v) { " +
            " var setter = Object.getOwnPropertyDescriptor(W.HTMLInputElement.prototype, 'value').set; " +
            " setter.call(el, v); " +
            " el.dispatchEvent(new W.Event('input', {bubbles:true})); " +
            " el.dispatchEvent(new W.Event('change', {bubbles:true})); " +
            " el.dispatchEvent(new W.Event('blur', {bubbles:true})); " +
            "} " +
            "var u = D.querySelector('input[placeholder=\"请输入学号/工号\"], input[placeholder^=\"请输入学号\"]'); " +
            "var p = D.querySelector('input[type=\"password\"]'); " +
            "if (u) { u.focus(); setV(u, " + quotedId + "); } " +
            "if (p) { p.focus(); setV(p, " + quotedPwd + "); } " +
            "console.log('JWAUTOFILL ul=' + (u ? u.value.length : -1) + ' pl=' + (p ? p.value.length : -1)); " +
            "window.setTimeout(function() { " +
            " var bs = D.querySelectorAll('button'); var b = null; " +
            " for (var i = 0; i < bs.length; i++) { " +
            "  var t = (bs[i].innerText || ''); " +
            "  if (t.indexOf('登录') >= 0 && t.indexOf('短信') < 0 && bs[i].getBoundingClientRect().width > 0) b = bs[i]; " +
            " } " +
            " if (b) b.click(); " +
            "}, 800); " +
            "window.__jwLoginStart = Date.now(); " +
            "window.__jwTimeoutCheck = setInterval(function() { " +
            " if (window.__jwLoginStart && (Date.now() - window.__jwLoginStart) > " +
            AUTO_LOGIN_TIMEOUT_MILLIS + ") { " +
            "  clearInterval(window.__jwTimeoutCheck); " +
            "  console.log('JWLOGIN_TIMEOUT'); " +
            " } " +
            "}, 2000); " +
            "})()"
    ) {}
}

/**
 * 门户登录 iframe（#iframeObj）初始 display:none，依赖页面脚本的校验回调才会
 * 显示，该回调在 WebView 中会失败；这里强制显示并撑满视口，并持续保持以
 * 对抗页面脚本的重置，直到主文档跳转离开门户为止。手动模式也需要此修复。
 */
private fun fixPortalLoginIframe(view: WebView) {
    view.evaluateJavascript(
        "(function(){ " +
            "if (window.__jwFixTimer) return; " +
            "function fix() { " +
            " var f = document.getElementById('iframeObj'); " +
            " if (!f) { " +
            "  var t = window.__jwFixTimer; " +
            "  if (t) { clearInterval(t); window.__jwFixTimer = null; } " +
            "  return; " +
            " } " +
            " f.style.setProperty('display','block','important'); " +
            " f.style.setProperty('width','100vw','important'); " +
            " f.style.setProperty('height', window.innerHeight + 'px','important'); " +
            " f.style.setProperty('max-height','none','important'); " +
            " var p = f.parentElement; " +
            " if (p && p !== document.body && p !== document.documentElement) { " +
            "  p.style.setProperty('transform','none','important'); " +
            "  p.style.setProperty('width','100vw','important'); " +
            "  var ps = p.style; " +
            "  if (ps && typeof ps.cssText === 'string' && ps.cssText.indexOf('display:none') >= 0) { " +
            "   p.style.setProperty('display','block','important'); " +
            "  } " +
            " } " +
            " if (document.documentElement) document.documentElement.style.height='100%'; " +
            " if (document.body) { document.body.style.margin='0'; document.body.style.height='100%'; } " +
            "} " +
            "fix(); " +
            "window.__jwFixTimer = setInterval(fix, 500); " +
            "})()"
    ) {}
}