package dev.weblogcat.probe

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.delay
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

private val Blue = Color(0xff315fdf)
private val Ink = Color(0xff14253d)
private val Muted = Color(0xff64748b)

@Composable fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(primary = Blue, background = Color(0xfff2f5fa), surface = Color.White, onSurface = Ink), content = content)
}

@Composable fun PageCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = Color.White, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable fun PageHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onClick = onBack) { Text("‹", fontSize = 30.sp, lineHeight = 30.sp) }
        Spacer(Modifier.width(14.dp))
        Column { Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold); Text(subtitle, fontSize = 13.sp, color = Muted) }
    }
}

class MainActivity : ComponentActivity() {
    private var grantResult by mutableStateOf<Boolean?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent { AppTheme { Home() } }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handleIntent(intent) }
    private fun handleIntent(intent: Intent) {
        if (intent.getBooleanExtra("wireless_grant_finished", false)) {
            intent.removeExtra("wireless_grant_finished"); intent.removeExtra("wireless_grant_success")
            grantResult = LogService.hasReadLogs(this)
        } else if (intent.getBooleanExtra("start", false)) startLogs("start", intent.getIntExtra("port", LogService.configuredPort(this)))
    }
    private fun startLogs(action: String, port: Int) {
        if (!LogService.hasReadLogs(this)) { stopService(Intent(this, LogService::class.java)); Toast.makeText(this, "请先授予 READ_LOGS 权限", Toast.LENGTH_LONG).show(); return }
        startForegroundService(Intent(this, LogService::class.java).setAction(action).putExtra("port", port))
    }
    @Composable private fun Home() {
        var tick by remember { mutableIntStateOf(0) }
        var selected by remember { mutableStateOf("") }
        var expanded by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { UpdateChecker.check(this@MainActivity) }
        LaunchedEffect(Unit) { while (true) { tick++; delay(1000) } }
        @Suppress("UNUSED_EXPRESSION") tick
        val service = LogService.active
        val granted = LogService.hasReadLogs(this)
        val addresses = remember(tick) { addresses() }
        val address = selected.takeIf { it in addresses } ?: addresses.firstOrNull().orEmpty()
        val url = if (granted && service != null && service.listening() && address.isNotEmpty()) "http://${address.substringAfter(" · ")}:${service.port}/?token=${LogService.token(this)}" else ""
        val qr = remember(url) { if (url.isEmpty()) null else qrBitmap(url) }
        val status = when {
            !granted -> "尚未授予日志读取权限，请先配置授权。"
            service == null -> LogService.lastFailure.ifEmpty { "服务未启动" }
            service.error.isNotEmpty() -> service.error
            service.verifying -> "正在等待系统允许全设备日志访问…"
            service.listening() -> "服务运行中 · 端口 ${service.port}"
            else -> "正在启动…"
        }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("DEVICE LOG CONSOLE", fontSize = 11.sp, color = Muted, letterSpacing = 1.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("WebLogcat", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                            (UpdateChecker.state as? UpdateState.Available)?.let { update ->
                                TextButton(onClick = { openUpdateUrl(this@MainActivity, update.release.pageUrl) }) { Text("NEW", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                            }
                        }
                        Text("在浏览器里，查看手机实时日志", color = Muted, fontSize = 14.sp)
                    }
                    FilledTonalButton(onClick = { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }) { Text("设置") }
                }
                PageCard {
                    Text("日志服务", fontSize = 18.sp, fontWeight = FontWeight.SemiBold); Text(status, color = Muted, fontSize = 14.sp)
                    Button(onClick = { if (service != null) stopService(Intent(this@MainActivity, LogService::class.java)) else startLogs("start", LogService.configuredPort(this@MainActivity)); tick++ }, modifier = Modifier.fillMaxWidth()) { Text(if (service == null) "启动服务" else "停止服务") }
                    if (!granted) FilledTonalButton(onClick = { startActivity(Intent(this@MainActivity, WirelessSetupActivity::class.java)) }, modifier = Modifier.fillMaxWidth()) { Text("配置日志访问权限") }
                }
                PageCard {
                    Text("浏览器连接", fontSize = 18.sp, fontWeight = FontWeight.SemiBold); Text("在同一网络的设备上扫码，或复制链接打开。", color = Muted, fontSize = 13.sp)
                    Box { TextButton(onClick = { expanded = true }, enabled = addresses.isNotEmpty()) { Text(address.ifEmpty { "没有可用网络" }) }; DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) { addresses.forEach { item -> DropdownMenuItem(text = { Text(item) }, onClick = { selected = item; expanded = false }) } } }
                    if (url.isNotEmpty() && service != null) {
                        val access = service.accessCode.current()
                        Text("网页访问验证码", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(access.code ?: "暂时锁定", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Blue, letterSpacing = 3.sp)
                        Text(if (access.locked) "错误次数过多，请等待 ${access.remainingSeconds} 秒后重试。" else "在网页输入验证码。${access.remainingSeconds} 秒后更新，使用后立即失效。", color = Muted, fontSize = 13.sp)
                    }
                    if (qr != null) Image(qr.asImageBitmap(), contentDescription = "扫码打开日志网页", modifier = Modifier.size(220.dp).align(Alignment.CenterHorizontally).background(Color.White))
                    SelectionContainer { Text(url.ifEmpty { "启动服务后显示访问链接" }, color = Muted, fontSize = 13.sp) }
                    FilledTonalButton(onClick = { if (url.isNotEmpty()) { getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("WebLogcat", url)); Toast.makeText(this@MainActivity, "链接已复制", Toast.LENGTH_SHORT).show() } else Toast.makeText(this@MainActivity, "请先启动服务", Toast.LENGTH_SHORT).show() }, modifier = Modifier.fillMaxWidth()) { Text("复制访问链接") }
                }
            }
        }
        grantResult?.let { success -> AlertDialog(onDismissRequest = { grantResult = null }, title = { Text(if (success) "授权成功" else "授权未完成") }, text = { Text(if (success) "READ_LOGS 已授权。现在可以启动服务；如果系统询问日志访问权限，请选择允许。" else "未检测到 READ_LOGS 授权，请检查无线调试后重试。") }, confirmButton = { TextButton(onClick = { grantResult = null }) { Text("知道了") } }) }
    }
    private fun addresses(): List<String> = try {
        Collections.list(NetworkInterface.getNetworkInterfaces()).sortedBy { if (it.name.startsWith("wlan")) 0 else 1 }.flatMap { nic -> Collections.list(nic.inetAddresses).filterIsInstance<Inet4Address>().filter { !it.isLoopbackAddress && !it.isLinkLocalAddress }.map { "${nic.name} · ${it.hostAddress}" } }
    } catch (_: Exception) { emptyList() }
    private fun qrBitmap(url: String): Bitmap? = try {
        val matrix = QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 600, 600)
        Bitmap.createBitmap(600, 600, Bitmap.Config.RGB_565).apply { for (y in 0 until 600) for (x in 0 until 600) setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE) }
    } catch (_: Exception) { null }
}
