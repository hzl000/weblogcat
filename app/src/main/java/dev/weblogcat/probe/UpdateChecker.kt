package dev.weblogcat.probe

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URL

internal data class UpdateRelease(val version: String, val pageUrl: String, val apkUrl: String?)
internal sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object Current : UpdateState
    data class Available(val release: UpdateRelease) : UpdateState
    data object Failed : UpdateState
}

internal fun isNewerVersion(remote: String, local: String): Boolean {
    fun parse(value: String): List<BigInteger>? {
        val text = value.removePrefix("v")
        if (!text.matches(Regex("[0-9]+(\\.[0-9]+)*"))) return null
        return text.split('.').map { BigInteger(it) }
    }
    val latest = parse(remote) ?: throw IllegalArgumentException("无效的远端版本号")
    val current = parse(local) ?: throw IllegalArgumentException("无效的当前版本号")
    for (index in 0 until maxOf(latest.size, current.size)) {
        val comparison = (latest.getOrNull(index) ?: BigInteger.ZERO).compareTo(current.getOrNull(index) ?: BigInteger.ZERO)
        if (comparison != 0) return comparison > 0
    }
    return false
}

internal object UpdateChecker {
    private const val API = "https://api.github.com/repos/hzl000/weblogcat/releases/latest"
    private const val CACHE_MS = 12 * 60 * 60 * 1000L
    private val mutex = Mutex()
    private var lastSuccess = 0L
    var state: UpdateState by mutableStateOf(UpdateState.Idle)
        private set

    suspend fun latestRelease(): UpdateRelease = withContext(Dispatchers.IO) { fetchRelease() }

    suspend fun check(context: Context, force: Boolean = false) = mutex.withLock {
        if (!force && lastSuccess != 0L && SystemClock.elapsedRealtime() - lastSuccess < CACHE_MS) return@withLock
        val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName
        state = UpdateState.Checking
        try {
            val release = withContext(Dispatchers.IO) { fetchRelease() }
            state = if (isNewerVersion(release.version, version ?: "")) UpdateState.Available(release) else UpdateState.Current
            lastSuccess = SystemClock.elapsedRealtime()
        } catch (cancel: kotlinx.coroutines.CancellationException) {
            lastSuccess = 0L
            state = UpdateState.Idle
            throw cancel
        } catch (_: Exception) {
            lastSuccess = 0L
            state = UpdateState.Failed
        }
    }

    private fun fetchRelease(): UpdateRelease {
        val connection = URL(API).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "WebLogcat-Android")
            if (connection.responseCode != 200) throw IllegalStateException("请求失败")
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val version = json.getString("tag_name")
            val page = trustedUrl(json.getString("html_url"), "/hzl000/weblogcat/releases/tag/") ?: throw IllegalArgumentException("无效的发布页面")
            val assets = json.optJSONArray("assets")
            val apk = (0 until (assets?.length() ?: 0)).firstNotNullOfOrNull { index ->
                val item = assets!!.optJSONObject(index) ?: return@firstNotNullOfOrNull null
                if (!item.optString("name").endsWith(".apk", ignoreCase = true)) null
                else trustedUrl(item.optString("browser_download_url"), "/hzl000/weblogcat/releases/download/")
            }
            return UpdateRelease(version, page, apk)
        } finally { connection.disconnect() }
    }

    private fun trustedUrl(value: String, prefix: String): String? {
        val uri = Uri.parse(value)
        return value.takeIf { uri.scheme == "https" && uri.host == "github.com" && uri.port == -1 && uri.path?.startsWith(prefix) == true }
    }
}

internal fun openUpdateUrl(context: Context, url: String) {
    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    catch (_: ActivityNotFoundException) { Toast.makeText(context, "没有可用的浏览器", Toast.LENGTH_SHORT).show() }
}
